#!/usr/bin/env python3
"""Reproduce RBAC1 shared artifacts. Python 3 + PyYAML; --check never writes.
Source snapshot pinned to the teacher repository commit; no database/network access.
Migration verification is deliberately a separate, explicit docker/mysql invocation.
"""
import argparse, copy, csv, hashlib, io, json, re
from pathlib import Path
import yaml
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'rbac-contract/src/main/resources'
CAT = RES / 'catalog'
COMMIT = 'fde08d5dd417abc543145aff33ca8378deb9ca36'
SOURCE = 'course-project-mgt-reference/api/openapi.yaml'
SOURCE_SHA256 = '4c944b94b0b18cbd20aa0091c20954927129bc586e97bb73dd79049f2814e40c'
METHODS = {'get','post','put','patch','delete','head','options'}
OUTPUTS = {}
def emit(path, text): OUTPUTS[RES / path] = text

def q(v):
    if v is None: return 'NULL'
    if isinstance(v, bool): return '1' if v else '0'
    if isinstance(v, int): return str(v)
    return "'" + str(v).replace("'", "''") + "'"
def insert(table, **values):
    return 'INSERT INTO '+table+' ('+', '.join(values)+') VALUES ('+', '.join(map(q,values.values()))+');\n'

def ddl():
    tables = {
'sys_system': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','code VARCHAR(64) NOT NULL UNIQUE','name VARCHAR(128) NOT NULL','status VARCHAR(16) NOT NULL', 'version BIGINT NOT NULL DEFAULT 0', "CHECK(status IN ('ENABLED','DISABLED'))"],
'sys_department': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','parent_id BIGINT NULL','code VARCHAR(64) NOT NULL UNIQUE','name VARCHAR(128) NOT NULL','path VARCHAR(1024) NOT NULL','status VARCHAR(16) NOT NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(parent_id) REFERENCES sys_department(id)',"CHECK(status IN ('ENABLED','DISABLED'))"],
'sys_user': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','subject VARCHAR(128) NOT NULL UNIQUE','username VARCHAR(128) NOT NULL UNIQUE','password_hash VARCHAR(100) NOT NULL','name VARCHAR(128) NOT NULL','no VARCHAR(64) NULL','phone VARCHAR(32) NULL','department_id BIGINT NOT NULL','status VARCHAR(16) NOT NULL','token_version BIGINT NOT NULL DEFAULT 0','must_change_password BOOLEAN NOT NULL DEFAULT true','failed_login_count INT NOT NULL DEFAULT 0','locked_until TIMESTAMP(3) NULL','version BIGINT NOT NULL DEFAULT 0','created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','FOREIGN KEY(department_id) REFERENCES sys_department(id)',"CHECK(status IN ('ACTIVE','DISABLED','DELETED'))",'CHECK(token_version>=0 AND failed_login_count>=0 AND version>=0)','INDEX ix_user_department_status(department_id,status,id)','INDEX ix_user_name(name)'],
'sys_role': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','code VARCHAR(64) NOT NULL','name VARCHAR(128) NOT NULL','description VARCHAR(500) NULL','status VARCHAR(16) NOT NULL','builtin BOOLEAN NOT NULL DEFAULT false','identity_role VARCHAR(16) NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','UNIQUE(system_id,code)',"CHECK(status IN ('ENABLED','DISABLED'))", "CHECK(identity_role IS NULL OR identity_role IN ('student','teacher','admin'))"],
'sys_permission': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','code VARCHAR(128) NOT NULL','name VARCHAR(128) NOT NULL','kind VARCHAR(8) NOT NULL','status VARCHAR(16) NOT NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','UNIQUE(system_id,kind,code)',"CHECK(kind IN ('UI','API'))", "CHECK(status IN ('ENABLED','DISABLED'))"],
'sys_role_permission': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','role_id BIGINT NOT NULL','permission_id BIGINT NOT NULL','FOREIGN KEY(role_id) REFERENCES sys_role(id)','FOREIGN KEY(permission_id) REFERENCES sys_permission(id)','UNIQUE(role_id,permission_id)'],
'sys_user_role': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','user_id BIGINT NOT NULL','role_id BIGINT NOT NULL','assigned_by BIGINT NULL','assigned_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','FOREIGN KEY(user_id) REFERENCES sys_user(id)','FOREIGN KEY(role_id) REFERENCES sys_role(id)','FOREIGN KEY(assigned_by) REFERENCES sys_user(id)','UNIQUE(user_id,role_id)'],
'sys_department_allowed_role': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','department_id BIGINT NOT NULL','role_id BIGINT NOT NULL','FOREIGN KEY(department_id) REFERENCES sys_department(id)','FOREIGN KEY(role_id) REFERENCES sys_role(id)','UNIQUE(department_id,role_id)'],
'sys_role_hierarchy': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','senior_role_id BIGINT NOT NULL','junior_role_id BIGINT NOT NULL','FOREIGN KEY(senior_role_id) REFERENCES sys_role(id)','FOREIGN KEY(junior_role_id) REFERENCES sys_role(id)','UNIQUE(senior_role_id,junior_role_id)','CHECK(senior_role_id<>junior_role_id)'],
'sys_ui_resource': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','parent_id BIGINT NULL','type VARCHAR(16) NOT NULL','code VARCHAR(128) NOT NULL','name VARCHAR(128) NOT NULL','route VARCHAR(255) NULL','component VARCHAR(64) NULL','sort_order INT NOT NULL DEFAULT 0','status VARCHAR(16) NOT NULL','permission_id BIGINT NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','FOREIGN KEY(parent_id) REFERENCES sys_ui_resource(id)','FOREIGN KEY(permission_id) REFERENCES sys_permission(id)','UNIQUE(system_id,code)',"CHECK(type IN ('MENU','BUTTON'))", "CHECK(status IN ('ENABLED','DISABLED'))",'INDEX ix_ui_tree(system_id,parent_id,sort_order,id)'],
'sys_api_operation': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','operation_id VARCHAR(128) NOT NULL','method VARCHAR(16) NOT NULL','path_template VARCHAR(500) NOT NULL','permission_id BIGINT NULL','required_role VARCHAR(64) NULL','access_policy VARCHAR(32) NOT NULL','enabled BOOLEAN NOT NULL DEFAULT true','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','FOREIGN KEY(permission_id) REFERENCES sys_permission(id)','UNIQUE(system_id,operation_id)','UNIQUE(system_id,method,path_template)',"CHECK(access_policy IN ('PUBLIC','AUTHENTICATED','PROTECTED'))", "CHECK(method IN ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'))", "CHECK(access_policy<>'PROTECTED' OR permission_id IS NOT NULL OR required_role IS NOT NULL)"],
'sys_ui_api_mapping': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','ui_resource_id BIGINT NOT NULL','api_operation_id BIGINT NOT NULL','FOREIGN KEY(ui_resource_id) REFERENCES sys_ui_resource(id)','FOREIGN KEY(api_operation_id) REFERENCES sys_api_operation(id)','UNIQUE(ui_resource_id,api_operation_id)'],
'sys_token_revocation': ['jti VARCHAR(128) PRIMARY KEY','subject VARCHAR(128) NOT NULL','expires_at TIMESTAMP(3) NOT NULL','created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','INDEX ix_revocation_expiry(expires_at)','INDEX ix_revocation_subject(subject)'],
'sys_grant_version': ['id TINYINT PRIMARY KEY','version BIGINT NOT NULL DEFAULT 0','CHECK(id=1)','CHECK(version>=0)'],
'sys_audit_log': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','request_id CHAR(36) NOT NULL UNIQUE','source_system_code VARCHAR(64) NOT NULL','origin VARCHAR(16) NOT NULL','actor_id BIGINT NULL','actor_subject VARCHAR(128) NULL','actor_name VARCHAR(128) NULL','actor_roles_json JSON NOT NULL','event_type VARCHAR(64) NOT NULL','object_type VARCHAR(64) NULL','object_id VARCHAR(128) NULL','target VARCHAR(500) NOT NULL','before_json JSON NULL','after_json JSON NULL','result VARCHAR(16) NOT NULL','reason VARCHAR(255) NULL','trace_id VARCHAR(64) NOT NULL','payload_hash VARCHAR(64) NOT NULL','occurred_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)',"CHECK(origin IN ('COURSE','INTERNAL'))", "CHECK(result IN ('SUCCESS','DENIED','FAILURE'))",'INDEX ix_audit_system_time(source_system_code,occurred_at,id)','INDEX ix_audit_system_actor(source_system_code,actor_id,occurred_at,id)','INDEX ix_audit_system_object(source_system_code,object_type,object_id,occurred_at,id)']}
    text = '-- RBAC1 EMPTY DEVELOPMENT DATABASE ONLY. Never baseline/migrate an existing production database.\nSET time_zone = \'+00:00\';\n'
    for name, cols in tables.items():
        text += '\nCREATE TABLE '+name+' (\n  '+',\n  '.join(cols)+'\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci'+(' AUTO_INCREMENT=10000' if name=='sys_user' else '')+';\n'
    emit('db/migration/V1__rbac1_schema.sql',text)
    return tables

def ref(n): return {'$ref':'#/components/schemas/'+n}
def arr(s): return {'type':'array','items':ref(s) if isinstance(s,str) else s}
def obj(p, required=None): return {'type':'object','additionalProperties':False,'properties':p,'required':list(p) if required is None else required}
def string(maximum=None, **kw): return dict(type='string', **({'maxLength':maximum} if maximum else {}), **kw)
def enum(*values): return {'type':'string','enum':list(values)}
ID={'type':'integer','format':'int64','minimum':1}; BOOL={'type':'boolean'}; DATE=string(format='date-time'); NULL={'nullable':True}; STATUS=enum('ENABLED','DISABLED'); USERSTATUS=enum('ACTIVE','DISABLED','DELETED')
def nullable(s): return dict(s,nullable=True)
def page(item): return obj({'page':{'type':'integer','minimum':1},'pageSize':{'type':'integer','minimum':1,'maximum':100},'total':{'type':'integer','format':'int64','minimum':0},'items':arr(item)})
def response(s, description='成功'): return {'description':description,'content':{'application/json':{'schema':s}}}
def oas():
    teacher=yaml.safe_load((CAT/'course-openapi.source.yaml').read_text(encoding='utf8'))
    schemas=copy.deepcopy(teacher['components']['schemas'])
    # Retain only the transitive schemas of the seven provider operations.
    external={p:copy.deepcopy(v) for p,v in teacher['paths'].items() if p.startswith('/auth/')}
    needed=set()
    def visit(x):
        if isinstance(x,dict):
            if '$ref' in x and x['$ref'].startswith('#/components/schemas/'):
                n=x['$ref'].split('/')[-1]
                if n not in needed: needed.add(n);visit(schemas[n])
            for v in x.values():visit(v)
        elif isinstance(x,list):
            for v in x:visit(v)
    visit(external)
    schemas={n:v for n,v in schemas.items() if n in needed}
    schemas['Error']=obj({'code':string(minLength=1),'message':string(minLength=1),'details':{}},['code','message'])
    schemas['LoginInput']['properties']['systems']['maxItems']=1
    schemas['LoginInput']['properties']['username'].pop('example',None)
    schemas['IdentityCurrentUserVO']['required']=['subject','roles','permissions','name','no']
    schemas['AuditVO']=obj({'id':ID,'at':DATE,'actor':obj({'id':nullable(ID),'name':string(),'roles':arr(string()),'deleted':BOOL}),'type':string(),'target':string(500),'before':nullable({'type':'object','additionalProperties':True}),'after':nullable({'type':'object','additionalProperties':True}),'objectType':nullable(string()),'objectId':nullable(string())},['id','at','actor','type','target','before','after'])
    schemas['LoginVO']['properties']['tokenType']=enum('Bearer')
    schemas['IdentityAuditLog']=copy.deepcopy(schemas['AuditVO'])
    schemas['IdentityAuditLog']['properties']['type']=enum('score_changed','group_changed','milestone_changed','roster_changed')
    schemas['IdentityAuditLog']['properties']['actor']['properties']['roles']=arr(ref('Role'))
    schemas['IdentityAuditLog']['properties']['actor']['properties']['id']=copy.deepcopy(ID)
    # Page is explicitly concrete; avoid allOf + additionalProperties ambiguity.
    schemas['PageIdentityAuditLog']=page('IdentityAuditLog')
    def strict(x):
        if isinstance(x,dict):
            if x.get('type')=='object' and 'properties' in x:x['additionalProperties']=False
            for v in x.values(): strict(v)
        elif isinstance(x,list):
            for v in x:strict(v)
    strict(schemas)
    schemas.update({
    'SystemVO':obj({'id':ID,'code':string(64),'name':string(128),'status':STATUS}),
    'DepartmentVO':obj({'id':ID,'parentId':nullable(ID),'code':string(64),'name':string(128),'path':string(1024),'status':STATUS,'children':arr('DepartmentVO')}),
    'UserVO':obj({'id':ID,'subject':string(128),'username':string(128),'name':string(128),'no':nullable(string(64)),'phone':nullable(string(32)),'departmentId':ID,'departmentName':string(128),'status':USERSTATUS,'version':{'type':'integer','format':'int64','minimum':0}}),
    'RoleVO':obj({'id':ID,'systemCode':string(64),'code':string(64),'name':string(128),'description':nullable(string(500)),'status':STATUS,'builtin':BOOL,'identityRole':nullable(enum('student','teacher','admin')),'version':{'type':'integer','format':'int64','minimum':0}}),
    'PermissionVO':obj({'id':ID,'systemCode':string(64),'code':string(128),'name':string(128),'kind':enum('UI','API'),'status':STATUS}),
    'AssignmentVO':obj({'id':ID,'userId':ID,'roleId':ID,'systemCode':string(64),'roleCode':string(64),'roleName':string(128),'assignedAt':DATE,'assignedBy':nullable(ID)}),
    'EffectiveVO':obj({'directRoles':arr('RoleVO'),'effectiveRoles':arr('RoleVO'),'permissions':arr('PermissionVO'),'sources':arr(obj({'permissionCode':string(128),'roleCode':string(64),'inherited':BOOL,'path':arr(string())}))}),
    'UiResourceVO':obj({'id':ID,'systemCode':string(64),'parentId':nullable(ID),'type':enum('MENU','BUTTON'),'code':string(128),'name':string(128),'route':nullable(string(255)),'component':nullable(string(64)),'sortOrder':{'type':'integer'},'status':STATUS,'permissionCode':nullable(string(128)),'children':arr('UiResourceVO')}),
    'ApiOperationVO':obj({'id':ID,'systemCode':string(64),'operationId':string(128),'method':enum('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'),'pathTemplate':string(500),'permissionCode':nullable(string(128)),'requiredRole':nullable(string(64)),'accessPolicy':enum('PUBLIC','AUTHENTICATED','PROTECTED'),'enabled':BOOL}),
    'MappingVO':obj({'uiResourceId':ID,'apiOperationId':ID}),
    'HierarchyVO':obj({'roles':arr('RoleVO'),'edges':arr(obj({'seniorRoleId':ID,'juniorRoleId':ID}))})})
    paths=external
    for p,v in external.items():
        for m,o in v.items():
            if m not in METHODS:continue
            o.pop('servers',None)
            o['x-required-permission']='PUBLIC' if p=='/auth/login' else ('audit:write' if m=='post' and p=='/auth/audit-logs' else 'audit:read' if p=='/auth/audit-logs' else '@authenticated')
            if p=='/auth/audit-logs':
                o.setdefault('parameters',[]).append({'name':'X-Audit-Service-Key','in':'header','required':True,'schema':string(minLength=1),'description':'可信课程后端服务密钥；不得提供给浏览器。'})
                if m=='get':o['responses']['200']=response(ref('PageIdentityAuditLog'))
                else:o['description']+='\nobjectType须与objectId前缀一致。同requestId同sourceSystem/subject/规范化内容返回相同id；异体400 AUDIT_DUPLICATE_CONFLICT。'
    version={'type':'integer','format':'int64','minimum':0}
    schemas.update({
    'PasswordInput':obj({'oldPassword':string(256,minLength=1),'newPassword':string(256,minLength=1)}),
    'SystemCreateInput':obj({'code':string(64,minLength=1),'name':string(128,minLength=1)}),
    'NameInput':obj({'name':string(128,minLength=1)}),
    'StatusInput':obj({'status':STATUS}), 'UserStatusInput':obj({'status':USERSTATUS}),
    'DepartmentCreateInput':obj({'parentId':nullable(ID),'code':string(64,minLength=1),'name':string(128,minLength=1)},['code','name']),
    'RoleIdsInput':obj({'roleIds':arr(ID)}), 'PermissionIdsInput':obj({'permissionIds':arr(ID)}),
    'UserCreateInput':obj({'username':string(128,minLength=1),'name':string(128,minLength=1),'no':nullable(string(64)),'phone':nullable(string(32)),'departmentId':ID,'password':string(256,minLength=1)},['username','name','departmentId','password']),
    'UserUpdateInput':obj({'name':string(128,minLength=1),'no':nullable(string(64)),'phone':nullable(string(32)),'version':version},[]),
    'DepartmentChangeInput':obj({'departmentId':ID,'version':version},['departmentId']),
    'RoleCreateInput':obj({'systemCode':string(64,minLength=1),'code':string(64,minLength=1),'name':string(128,minLength=1),'description':nullable(string(500)),'identityRole':nullable(enum('student','teacher','admin'))},['systemCode','code','name']),
    'RoleUpdateInput':obj({'name':string(128,minLength=1),'description':nullable(string(500)),'version':version},[]),
    'AssignmentInput':obj({'roleId':ID}),
    'PermissionCreateInput':obj({'systemCode':string(64,minLength=1),'code':string(128,minLength=1),'name':string(128,minLength=1),'kind':enum('UI','API')}),
    'UiCreateInput':obj({'parentId':nullable(ID),'type':enum('MENU','BUTTON'),'code':string(128,minLength=1),'name':string(128,minLength=1),'route':nullable(string(255)),'component':nullable(string(64)),'sortOrder':{'type':'integer','default':0},'permissionCode':string(128,minLength=1),'status':STATUS},['type','code','name','permissionCode']),
    'ApiCreateInput':obj({'operationId':string(128,minLength=1),'method':enum('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'),'pathTemplate':string(500,minLength=1,pattern='^/'),'permissionCode':nullable(string(128)),'requiredRole':nullable(string(64)),'accessPolicy':enum('PUBLIC','AUTHENTICATED','PROTECTED'),'enabled':BOOL},['operationId','method','pathTemplate','accessPolicy','enabled']),
    'MappingsInput':obj({'mappings':arr('MappingVO')}),
    'AuthorizationInput':obj({'systemCode':string(64,minLength=1),'userId':ID,'kind':enum('UI','API'),'permission':string(128,minLength=1),'operationId':string(128,minLength=1)},['systemCode','kind']),
    'DecisionVO':obj({'allowed':BOOL,'reason':string(),'traceId':string(64)}),
    'ExplanationVO':obj({'allowed':BOOL,'reason':string(),'traceId':string(64),'trace':arr(string()),'effective':ref('EffectiveVO')}),
    'MappingCoverageVO':obj({'mappings':arr('MappingVO'),'unmappedUi':arr('UiResourceVO'),'unmappedApi':arr('ApiOperationVO')}),
    'MeVO':obj({'user':ref('UserVO'),'effective':ref('EffectiveVO')}),
    'DashboardVO':obj({n:{'type':'integer','format':'int64','minimum':0} for n in ['users','roles','systems']})})
    schemas['AuthorizationInput']['oneOf']=[{'properties':{'kind':enum('UI')},'required':['permission'],'not':{'required':['operationId']}},{'properties':{'kind':enum('API')},'required':['operationId'],'not':{'required':['permission']}}]
    for n in ['Ui','Api']:
        schemas[n+'UpdateInput']=copy.deepcopy(schemas[n+'CreateInput']);schemas[n+'UpdateInput']['required']=[]
    table=(ROOT/'docs/implementation-contract.adoc').read_text(encoding='utf8')
    rows=re.findall(r'^\|(GET|POST|PATCH|PUT|DELETE) (/[^ |]+) \|([^\n]+) \|([^\n]+)$',table,re.M)
    assert len(rows)==52,len(rows)
    # Explicit operationIds are stable and not derived from translated labels.
    definitions=[
    ('getMe',None,'MeVO'),('changePassword','PasswordInput',None),('listSystems',None,arr('SystemVO')),('createSystem','SystemCreateInput','SystemVO'),('updateSystem','NameInput','SystemVO'),('setSystemStatus','StatusInput','SystemVO'),
    ('getDepartmentTree',None,arr('DepartmentVO')),('createDepartment','DepartmentCreateInput','DepartmentVO'),('updateDepartment','NameInput','DepartmentVO'),('deleteDepartment',None,None),('getDepartmentAssignableRoles',None,arr('RoleVO')),('replaceDepartmentAssignableRoles','RoleIdsInput',None),
    ('listUsers',None,'PageUserVO'),('createUser','UserCreateInput','UserVO'),('getUser',None,'UserVO'),('updateUser','UserUpdateInput','UserVO'),('setUserStatus','UserStatusInput','UserVO'),('deleteUser',None,None),('changeUserDepartment','DepartmentChangeInput','UserVO'),
    ('listRoles',None,'PageRoleVO'),('createRole','RoleCreateInput','RoleVO'),('getRole',None,'RoleVO'),('updateRole','RoleUpdateInput','RoleVO'),('setRoleStatus','StatusInput','RoleVO'),('deleteRole',None,None),('getRolePermissions',None,arr('PermissionVO')),('replaceRolePermissions','PermissionIdsInput',None),('getRoleEffectivePermissions',None,'EffectiveVO'),('getRoleHierarchy',None,'HierarchyVO'),('addRoleInheritance',None,None),('deleteRoleInheritance',None,None),
    ('listUserRoleAssignments',None,arr('AssignmentVO')),('createUserRoleAssignment','AssignmentInput','AssignmentVO'),('deleteUserRoleAssignment',None,None),('getUserEffectivePermissions',None,'EffectiveVO'),('listPermissions',None,'PagePermissionVO'),('createPermission','PermissionCreateInput','PermissionVO'),('setPermissionStatus','StatusInput','PermissionVO'),('getUiResources',None,arr('UiResourceVO')),('createUiResource','UiCreateInput','UiResourceVO'),('updateUiResource','UiUpdateInput','UiResourceVO'),('deleteUiResource',None,None),('getMyMenus',None,arr('UiResourceVO')),('listApiOperations',None,'PageApiOperationVO'),('createApiOperation','ApiCreateInput','ApiOperationVO'),('updateApiOperation','ApiUpdateInput','ApiOperationVO'),('getUiApiMappings',None,'MappingCoverageVO'),('replaceUiApiMappings','MappingsInput',None),('checkAuthorization','AuthorizationInput','DecisionVO'),('explainAuthorization','AuthorizationInput','ExplanationVO'),('queryAuditLogs',None,'PageAuditVO'),('getDashboard',None,'DashboardVO')]
    assert len(rows)==len(definitions),(len(rows),len(definitions))
    for name in ['UserVO','RoleVO','PermissionVO','ApiOperationVO','AuditVO']:schemas['Page'+name]=page(name)
    for (method,path,description,permission),(oid,inp,out) in zip(rows,definitions):
        out=ref(out) if isinstance(out,str) else out
        wrapper=obj({'code':enum('OK'),'message':string(),'data':out or {'type':'object','nullable':True,'enum':[None]},'timestamp':DATE})
        op={'operationId':oid,'summary':description.split('→')[0].strip(),'x-required-permission':permission.strip(),'x-required-audience':'rbac-center' if path not in ['/me','/menus/me','/auth/password'] else '@current','responses':{'200':response(wrapper)}}
        if inp:op['requestBody']={'required':True,'content':{'application/json':{'schema':ref(inp)}}}
        op['parameters']=[]
        for arg in re.findall(r'\{([^}]+)\}',path):op['parameters'].append({'name':arg,'in':'path','required':True,'schema':string(64,minLength=1) if arg=='code' else ID})
        query={
        'listUsers':{'keyword':string(),'departmentId':ID,'status':USERSTATUS},'listRoles':{'systemCode':string(64,minLength=1),'keyword':string(),'status':STATUS},'listPermissions':{'systemCode':string(64),'kind':enum('UI','API'),'keyword':string()},'listApiOperations':{'keyword':string()},'getDepartmentAssignableRoles':{'systemCode':string(64)},'listUserRoleAssignments':{'systemCode':string(64)},'queryAuditLogs':{'systemCode':string(64),'actor':string(),'actorId':ID,'target':string(500),'from':DATE,'to':DATE}}
        for n,s in query.get(oid,{}).items():op['parameters'].append({'name':n,'in':'query','required':oid=='listRoles' and n=='systemCode','schema':s})
        if isinstance(out,dict) and out.get('$ref','').split('/')[-1].startswith('Page'):
            op['parameters'] += [{'$ref':'#/components/parameters/Page'},{'$ref':'#/components/parameters/PageSize'}]
        for status in ['400','401','403','404','409','503']:op['responses'][status]={'$ref':'#/components/responses/'+{'400':'BadRequest','401':'Unauthorized','403':'Forbidden','404':'NotFound','409':'Conflict','503':'Unavailable'}[status]}
        paths.setdefault('/api/v1'+path,{})[method.lower()]=op
    # Private service-to-service projections; these do not alter the teacher's seven provider operations.
    schemas['IntegrationAuthoritiesVO']=obj({'authorities':arr(string())})
    schemas['IdentityUserLookupVO']=obj({'userId':string(128),'name':string(128),'no':string(64)})
    for path,oid,out in [('/integration/authorities','getIntegrationAuthorities','IntegrationAuthoritiesVO'),('/identity/users/{userId}','lookupUnifiedIdentityUser','IdentityUserLookupVO')]:
        parameters=[{'name':'X-Audit-Service-Key','in':'header','required':True,'schema':string(minLength=1),'description':'Only trusted course backend; never exposed in frontend configuration.'}]
        if '{userId}' in path:parameters.append({'name':'userId','in':'path','required':True,'schema':string(128,minLength=1)})
        paths[path]={'get':{'operationId':oid,'x-required-permission':'@authenticated','x-required-audience':'@current','x-trusted-service':True,'description':'Service key plus validated current user; lookup additionally requires admin identity and TEC API capability. No target subject/system overrides for authority projection.','parameters':parameters,'responses':{'200':response(ref(out)),**{status:{'$ref':'#/components/responses/'+name} for status,name in [('400','BadRequest'),('401','Unauthorized'),('403','Forbidden'),('404','NotFound'),('503','Unavailable')]}}}}
    for path,oid,out in [('/.well-known/jwks.json','getJwks',obj({'keys':arr(obj({'kty':enum('RSA'),'use':enum('sig'),'kid':string(),'alg':enum('RS256'),'n':string(),'e':string()}))})),('/api/v1/actuator/health','getHealth',{'type':'object','required':['status'],'properties':{'status':string()},'additionalProperties':True}),('/api/v1/actuator/info','getInfo',{'type':'object','additionalProperties':True})]:
        paths[path]={'get':{'operationId':oid,'x-required-permission':'PUBLIC','security':[],'responses':{'200':response(out)}}}
    components={'schemas':schemas,'parameters':copy.deepcopy(teacher['components']['parameters']),'responses':copy.deepcopy(teacher['components']['responses']),'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer','bearerFormat':'JWT'}}}
    for status,name in [('400','BadRequest'),('401','Unauthorized'),('403','Forbidden'),('404','NotFound'),('409','Conflict'),('503','Unavailable')]:components['responses'][name]=response(ref('Error'),status+' 业务错误')
    # Remove unused teacher parameters/responses to avoid unrelated business schemas.
    used=set()
    def refs(x):
        if isinstance(x,dict):
            if '$ref' in x:used.add(x['$ref'])
            for v in x.values():refs(v)
        elif isinstance(x,list):
            for v in x:refs(v)
    refs(paths);refs(schemas)
    components['parameters']={n:v for n,v in components['parameters'].items() if '#/components/parameters/'+n in used}
    components['responses']={n:v for n,v in components['responses'].items() if '#/components/responses/'+n in used}
    components['parameters']['Page']['schema']={'type':'integer','minimum':1,'default':1}
    components['parameters']['PageSize']['schema']={'type':'integer','minimum':1,'maximum':100,'default':20}
    document={'openapi':'3.0.3','info':{'title':'RBAC1 统一授权中心','version':'2.0.0','description':'冻结实施合同；单aud JWT；UI/API分离；成功code=OK，错误裸字符串code。仅新空开发库。'},'servers':[{'url':'http://localhost:8081'}],'security':[{'bearerAuth':[]}],'paths':paths,'components':components}
    def clean(x):
        if isinstance(x,dict):
            if x.get('required') == []: del x['required']
            for v in x.values(): clean(v)
        elif isinstance(x,list):
            for v in x: clean(v)
    clean(document)
    emit('openapi/rbac-api.yaml',yaml.safe_dump(document,allow_unicode=True,sort_keys=False))
    return document

def course_catalog():
    raw=(CAT/'course-openapi.source.yaml').read_bytes(); doc=yaml.safe_load(raw)
    assert hashlib.sha256(raw).hexdigest()==SOURCE_SHA256, 'Pinned teacher source snapshot changed'
    rows=[]; excluded=[]
    for path,value in doc['paths'].items():
        for method,op in value.items():
            if method not in METHODS:continue
            item={'operationId':op['operationId'],'method':method.upper(),'path':path,'x-required-permission':op.get('x-required-permission'),'x-required-role':op.get('x-required-role')}
            if op.get('x-provider')=='unified-identity' or path.startswith('/auth/'):excluded.append(item);continue
            item.update(permissionCode=op.get('x-required-permission') or 'course:access',requiredRole=op.get('x-required-role'),accessPolicy='PROTECTED')
            rows.append(item)
    assert len(excluded)==7 and len(rows)==80
    assert next(r for r in rows if r['operationId']=='copyCourse')['requiredRole']=='teacher'
    manifest={'source':SOURCE,'commit':COMMIT,'sha256':hashlib.sha256(raw).hexdigest(),'snapshot':'course-openapi.source.yaml','excludedProviderOperations':excluded,'operations':rows}
    emit('catalog/course-operations.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    return rows, manifest

# Course UI grouping is an explicit, reviewable operation-id mapping, not authorization inference.
GROUPS={
'overview':['getCurrentUser','listStudentCourses','listTeacherCourses'],
'courses':['listAdminCourses','getCourse','createCourse','copyCourse','submitCourseReview','resetCourseToApplication','reviewCourse','updateCourse','deleteCourse','updateCourseDescription','setCourseLeaders'],
'classes':['listClasses','createClasses','updateClass','deleteClass','setClassTeachers'],
'roster':['listClassStudents','addStudent','searchStudentCandidates','removeStudent','importRoster'],
'groups':['getCourseGroupRule','updateCourseGroupRule','getClassGroupRule','setClassGroupRule','deleteClassGroupRule','listCourseGroups','createGroupAsStudent','listClassGroups','createGroupAsTeacher','getGroup','updateGroup','dissolveGroup','listGroupJoinRequests','createGroupJoinRequest','decideGroupJoinRequest','cancelGroupJoinRequest','removeGroupMemberAsLeader','transferGroupLeader','leaveGroup'],
'milestones':['listMilestones','createMilestone','updateMilestone','deleteMilestone'],
'submissions':['listSubmissions','getGroupSubmission','submitMaterials','getMyMilestoneSubmission','submitMyMaterials','getSubmissionById','reviewSubmission'],
'oral':['listOralSlots','createOralSlot','deleteOralSlot','bookOralSlot','cancelBooking','listOralStatus','createGroupOralRecord','createStudentOralRecord','updateOralRecord','summarizeOralMemo'],
'grades':['getClassGrades','exportClassGrades','getMyGrades'],
'files':['downloadFile'], 'audit':['listAuditLogs'],
'administration':['listTerms','createTerm','updateTerm','deleteTerm','lookupIdentityUser','listTeachers','createTeacher','updateTeacher','deleteTeacher','replaceCurrentTerm','clearCurrentTerm']}

def seed(document, course, manifest):
    text='-- Generated RBAC1 empty-development seed. root is unusable until controlled bootstrap.\nSET time_zone = \'+00:00\';\n'
    def add(table,**v):
        nonlocal text;text+=insert(table,**v)
    for i,code,name in [(1,'rbac-center','统一授权中心'),(2,'course-project-management','课程项目管理')]:add('sys_system',id=i,code=code,name=name,status='ENABLED',version=0)
    for i,parent,code,name,path in [(1,None,'HQ','总部','/1/'),(2,1,'Finance','Finance','/1/2/'),(3,1,'Engineering','Engineering','/1/3/')]:add('sys_department',id=i,parent_id=parent,code=code,name=name,path=path,status='ENABLED',version=0)
    add('sys_user',id=1,subject='rbac-u1',username='root',password_hash='UNINITIALIZED',name='系统管理员',department_id=1,status='ACTIVE',token_version=0,must_change_password=True,failed_login_count=0,version=0)
    roles={}
    for rid,system,code,identity in [(1,1,'SUPER_ADMIN',None),(2,1,'RBAC_ADMIN',None),(3,1,'AUDITOR',None),(4,2,'teacher','teacher'),(5,2,'student','student'),(6,2,'admin','admin')]:
        roles[code]=rid;add('sys_role',id=rid,system_id=system,code=code,name=code,status='ENABLED',builtin=True,identity_role=identity,version=0)
    for department in [1,2,3]:
        for code,rid in roles.items():
            if code=='SUPER_ADMIN' and department!=1:continue
            add('sys_department_allowed_role',department_id=department,role_id=rid)
    add('sys_user_role',user_id=1,role_id=1,assigned_by=None)
    add('sys_grant_version',id=1,version=0)
    permissions={};grants=set();mapping=set();uis={};apis={};coverage=[]
    def permission(system,kind,code):
        key=(system,kind,code)
        if key not in permissions:
            permissions[key]=len(permissions)+1
            add('sys_permission',id=permissions[key],system_id=system,code=code,name=code,kind=kind,status='ENABLED',version=0)
        return permissions[key]
    def grant(role,pid):grants.add((roles[role],pid))
    def ui(system,code,route,component,parent=None,kind='MENU'):
        uid=len(uis)+1;pid=permission(system,'UI',code);uis[(system,code)]=(uid,pid)
        add('sys_ui_resource',id=uid,system_id=system,parent_id=parent,type=kind,code=code,name=code,route=route,component=component,sort_order=uid,status='ENABLED',permission_id=pid,version=0)
        return uid,pid
    center=[]
    for path,methods in document['paths'].items():
        for method,op in methods.items():
            if method not in METHODS:continue
            code=op['x-required-permission'];policy='PUBLIC' if code=='PUBLIC' else 'AUTHENTICATED' if code=='@authenticated' else 'PROTECTED'
            pid=None if policy!='PROTECTED' else permission(1,'API',code)
            aid=len(apis)+1;apis[(1,op['operationId'])]=aid
            add('sys_api_operation',id=aid,system_id=1,operation_id=op['operationId'],method=method.upper(),path_template=path,permission_id=pid,required_role=None,access_policy=policy,enabled=True,version=0)
            center.append((aid,op['operationId'],code,method,path))
            if pid:
                grant('SUPER_ADMIN',pid)
                if code!='rbac:system:edit':grant('RBAC_ADMIN',pid)
                if code.endswith(':view') or code=='audit:read':grant('AUDITOR',pid)
    pages=[('dashboard','Dashboard', ['getDashboard']),('systems','Systems',['listSystems','createSystem','updateSystem','setSystemStatus']),('departments','Departments',['getDepartmentTree','createDepartment','updateDepartment','deleteDepartment','getDepartmentAssignableRoles','replaceDepartmentAssignableRoles']),('users','Users',['listUsers','createUser','getUser','updateUser','setUserStatus','deleteUser','changeUserDepartment','listUserRoleAssignments','createUserRoleAssignment','deleteUserRoleAssignment','getUserEffectivePermissions']),('roles','Roles',['listRoles','createRole','getRole','updateRole','setRoleStatus','deleteRole','getRolePermissions','replaceRolePermissions','getRoleEffectivePermissions']),('hierarchy','Hierarchy',['getRoleHierarchy','addRoleInheritance','deleteRoleInheritance']),('permissions','Permissions',['listPermissions','createPermission','setPermissionStatus']),('ui-resources','UiResources',['getUiResources','createUiResource','updateUiResource','deleteUiResource','getMyMenus']),('api-operations','ApiOperations',['listApiOperations','createApiOperation','updateApiOperation']),('mappings','Mappings',['getUiApiMappings','replaceUiApiMappings']),('authorization','Authorization',['checkAuthorization','explainAuthorization']),('audit','Audit',['queryAuditLogs'])]
    assigned=set()
    for slug,component,ids in pages:
        parent,pid=ui(1,'rbac:ui:'+slug+':view','/'+slug,component)
        for role in ['SUPER_ADMIN','RBAC_ADMIN']:
            grant(role,pid)
        if slug not in ['authorization']:grant('AUDITOR',pid)
        for aid,oid,code,method,path in center:
            if oid not in ids:continue
            assigned.add(oid)
            target=parent
            if method!='get':
                target,pid=ui(1,'rbac:ui:'+oid,None,None,parent,'BUTTON');grant('SUPER_ADMIN',pid)
                if code!='rbac:system:edit':grant('RBAC_ADMIN',pid)
            mapping.add((target,aid));coverage.append([1,oid,method.upper(),path,code,'', '中心管理',target,aid])
    # Authenticated self-service controls are deliberately separate from the 12 management pages.
    for aid,oid,code,method,path in center:
        if oid in {'getIntegrationAuthorities','lookupUnifiedIdentityUser'}:
            coverage.append([1,oid,method.upper(),path,code,'','可信服务专用（不向浏览器开放）','',aid])
        elif oid not in assigned and code!='PUBLIC':
            target,pid=ui(1,'rbac:ui:'+oid,None,None,None,'BUTTON')
            for role in ['SUPER_ADMIN','RBAC_ADMIN','AUDITOR']:grant(role,pid)
            mapping.add((target,aid));coverage.append([1,oid,method.upper(),path,code,'','身份/本人',target,aid])
    assert set(sum(GROUPS.values(),[]))=={r['operationId'] for r in course}
    for row in course:
        pid=permission(2,'API',row['permissionCode']);aid=len(apis)+1;apis[(2,row['operationId'])]=aid
        add('sys_api_operation',id=aid,system_id=2,operation_id=row['operationId'],method=row['method'],path_template=row['path'],permission_id=pid,required_role=row['requiredRole'],access_policy='PROTECTED',enabled=True,version=0)
    # All API grants are enumerated below, never inferred at runtime from role names/UI mappings.
    for code in sorted({r['permissionCode'] for r in course}|{'course:access','audit:read','audit:write'}):
        pid=permission(2,'API',code);grant('admin',pid)
        if code in ['course:access','audit:read','audit:write','CRS']:grant('teacher',pid)
        if code in ['course:access','audit:write']:grant('student',pid)
    # Audit identity provider endpoints are center-hosted; course business snapshot excludes them.
    student_groups={'overview','courses','groups','milestones','submissions','oral','grades','files'}
    teacher_groups=student_groups|{'classes','roster','audit'}
    byid={r['operationId']:r for r in course}
    student_ops={'getCurrentUser','listStudentCourses','getCourse','listCourseGroups','createGroupAsStudent','getGroup','listGroupJoinRequests','createGroupJoinRequest','decideGroupJoinRequest','cancelGroupJoinRequest','removeGroupMemberAsLeader','transferGroupLeader','leaveGroup','listMilestones','getGroupSubmission','submitMaterials','getMyMilestoneSubmission','submitMyMaterials','getSubmissionById','listOralSlots','bookOralSlot','cancelBooking','getMyGrades','downloadFile'}
    teacher_excluded={'listStudentCourses','listAdminCourses','createGroupAsStudent','createGroupJoinRequest','getGroupSubmission','submitMaterials','getMyMilestoneSubmission','submitMyMaterials','bookOralSlot','cancelBooking','getMyGrades'}
    for role,groups in [('admin',set(GROUPS)),('teacher',teacher_groups),('student',student_groups)]:
        for group in GROUPS:
            if group not in groups:continue
            uid,pid=ui(2,'course:ui:'+role+':'+group,'/'+role+'/'+group,'Course'+group.title());grant(role,pid)
            for oid in GROUPS[group]:
                if role=='student' and oid not in student_ops: continue
                if role=='teacher' and (oid in teacher_excluded or (byid[oid]['requiredRole']=='admin' and oid!='resetCourseToApplication')): continue
                row=byid[oid];target=uid
                if row['method']!='GET':
                    target,pid=ui(2,'course:ui:'+role+':'+oid,None,None,uid,'BUTTON');grant(role,pid)
                aid=apis[(2,oid)];mapping.add((target,aid));coverage.append([2,oid,row['method'],row['path'],row['permissionCode'],row['requiredRole'] or '',group,target,aid])
    for rid,pid in sorted(grants):add('sys_role_permission',role_id=rid,permission_id=pid)
    for uid,aid in sorted(mapping):add('sys_ui_api_mapping',ui_resource_id=uid,api_operation_id=aid)
    text+='ALTER TABLE sys_user AUTO_INCREMENT=10000;\n'
    emit('db/migration/V2__rbac1_seed.sql',text)
    buf=io.StringIO(newline='');writer=csv.writer(buf,lineterminator='\n');writer.writerow(['systemId','operationId','method','path','permissionCode','requiredRole','category','uiResourceId','apiOperationId']);writer.writerows(coverage)
    emit('catalog/ui-api-coverage.csv',buf.getvalue())
    sample_ids=['listTerms','createTerm','copyCourse','importRoster','createGroupJoinRequest','submitMaterials','createMilestone','bookOralSlot','getMyGrades','downloadFile']
    samples=[dict(byid[oid],category=next(g for g,ids in GROUPS.items() if oid in ids)) for oid in sample_ids]
    emit('catalog/coverage-samples.json',json.dumps({'source':SOURCE,'commit':COMMIT,'sha256':manifest['sha256'],'samples':samples},ensure_ascii=False,indent=2)+'\n')
    return {'tables':15,'centerOperations':len(center),'courseOperations':len(course),'centerPages':len(pages),'permissions':len(permissions),'uiResources':len(uis),'mappings':len(mapping),'rolePermissionGrants':len(grants)}

def validate(document):
    ids=set();count=0
    def walk(x):
        if isinstance(x,dict):
            if '$ref' in x:
                r=x['$ref'];assert r.startswith('#/'),r
                node=document
                for key in r[2:].split('/'):node=node[key.replace('~1','/').replace('~0','~')]
            for value in x.values():walk(value)
        elif isinstance(x,list):
            for v in x:walk(v)
    walk(document)
    for path,methods in document['paths'].items():
        for method,op in methods.items():
            if method not in METHODS:continue
            assert op['operationId'] not in ids,op['operationId'];ids.add(op['operationId']);count+=1
            assert 'x-required-permission' in op
            assert path.startswith('/api/v1/') or path.startswith('/auth/') or path in {'/.well-known/jwks.json','/integration/authorities','/identity/users/{userId}'}
            params=methods.get('parameters',[])+op.get('parameters',[])
            assert set(re.findall(r'\{([^}]+)\}',path))=={p['name'] for p in params if p.get('in')=='path'}
    assert document['components']['schemas']['RoleVO']['properties']['identityRole']['enum']==['student','teacher','admin']
    return count

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    ddl();document=oas();course,manifest=course_catalog();counts=seed(document,course,manifest);counts['validatedOasOperations']=validate(document)
    old=list((RES/'db/migration').glob('V*.sql'))
    expected={p for p in OUTPUTS if p.suffix=='.sql'}
    if args.check:
        bad=[str(p.relative_to(ROOT)) for p,text in OUTPUTS.items() if not p.exists() or p.read_text(encoding='utf8')!=text]
        bad += [str(p.relative_to(ROOT)) for p in old if p not in expected]
        if bad:raise SystemExit('Generated artifacts differ: '+', '.join(bad))
    else:
        for p,text in OUTPUTS.items():p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8',newline='\n')
        for p in old:
            if p not in expected:p.unlink()
    print(json.dumps({'mode':'check' if args.check else 'generate','sourceSha256':manifest['sha256'],**counts},ensure_ascii=False))
if __name__=='__main__':main()
