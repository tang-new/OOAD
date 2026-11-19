#!/usr/bin/env python3
"""Code-anchored RBAC1 diagrams. Python stdlib + Graphviz + JDK; no business dependencies.
Run after compiling: python tools/render-rbac1-design.py --strict-compiled
Without strict mode source diagrams are marked SOURCE_ONLY when bytecode is stale.
"""
from pathlib import Path
import argparse, datetime, hashlib, html, json, os, re, shutil, subprocess, tempfile, textwrap, urllib.request
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'docs/design'
VERSION = '1.2025.2'
GRAPHVIZ_DOT = os.environ.get('GRAPHVIZ_DOT') or 'dot'
INDEX = {}; WARNINGS = []; FIGURES = []; REFS = []; CURRENT_FIGURE = None
CALL_CHAINS = []; INPUTS = {}
EXTRA_INPUTS = ['docs/final-acceptance.adoc', 'docs/acceptance/local-results.json', 'docs/acceptance/shared-http.json', 'tools/render-rbac1-design.test.py', 'rbac-contract/src/main/java/com/xmu/rbac/contract/ApiErrors.java', 'pom.xml', '.gitattributes', 'tools/render-rbac1-design.scenarios.py', 'rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql', 'docker-compose.yml', 'deploy/full-stack.sh', 'rbac-web/nginx.conf', 'deploy/full-course-nginx.conf', 'deploy/deployment-evidence.json', 'course-integration/REAL-CENTERS-VERIFICATION.md']

def run(args):
    p = subprocess.run([str(a) for a in args], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if p.returncode: raise RuntimeError(f'{args[0]} failed: {p.stdout}\n{p.stderr}')
    return p.stdout

TEXT_SUFFIXES = frozenset({'.java','.py','.sql','.yml','.yaml','.json','.xml','.properties','.conf','.sh','.cmd','.md','.adoc','.dot','.puml','.svg','.txt','.log'})
TEXT_NAMES = frozenset({'.gitattributes','.gitignore','Dockerfile'})
HASH_RULES = {'algorithm':'SHA-256','textNormalization':'CRLF bytes (0D 0A) -> LF (0A) ONLY; no stripping, decoding, BOM removal, standalone CR or whitespace normalization','textSuffixes':sorted(TEXT_SUFFIXES),'textNames':sorted(TEXT_NAMES),'binaryRule':'All other files, especially .class/.jar/.png, hashed byte-for-byte; never normalize binary CRLF','methodAndOrderGuards':'Unchanged declaration/javap and ordered callsite checks; this only canonicalizes text line endings'}

def normalized_text_bytes(data):
    return data.replace(b'\r\n', b'\n')

def sha(path):
    path=Path(path);data=path.read_bytes()
    if path.suffix.lower() in TEXT_SUFFIXES or path.name in TEXT_NAMES:data=normalized_text_bytes(data)
    return hashlib.sha256(data).hexdigest()

def source_text(path):
    # Unlike read_text's universal-newline mode, do NOT change standalone CR.
    return normalized_text_bytes(Path(path).read_bytes()).decode('utf-8')
def find_java(tool):
    home = os.environ.get('JAVA_HOME')
    if home and (Path(home)/'bin'/f'{tool}.exe').exists(): return Path(home)/'bin'/f'{tool}.exe'
    javap = shutil.which('javap')
    if javap and (Path(javap).parent/f'{tool}.exe').exists(): return Path(javap).parent/f'{tool}.exe'
    return shutil.which(tool) or tool

def collect():
    for path in EXTRA_INPUTS:
        file=ROOT/path
        if file.exists():INPUTS[path]={'sha256':sha(file),'source':source_text(file)}
    for module in ['rbac-ddd','rbac-structured','rbac-gateway']:
        for path in sorted((ROOT/module/'src/main/java').rglob('*.java')):
            source=source_text(path); package=re.search(r'package\s+([\w.]+)',source).group(1)
            if path.stem=='package-info': continue
            name=package+'.'+path.stem
            bytecode=ROOT/module/'target/classes'/Path(*name.split('.')).with_suffix('.class')
            status='COMPILED' if bytecode.exists() and bytecode.stat().st_mtime>=path.stat().st_mtime else 'PENDING_COMPILE'
            INDEX[name]={'path':path.relative_to(ROOT).as_posix(),'sha256':sha(path),'source':source,'bytecode':status,'classPath':bytecode.relative_to(ROOT).as_posix(),'classSha256':sha(bytecode) if bytecode.exists() else None,'signatures':[]}
    # One javap process per module avoids a long, internally inconsistent per-class snapshot.
    for module in ['rbac-ddd','rbac-structured','rbac-gateway']:
        names=[n for n,e in INDEX.items() if e['path'].startswith(module+'/') and (ROOT/module/'target/classes'/Path(*n.split('.')).with_suffix('.class')).exists()]
        if not names:continue
        try:
            result=run([find_java('javap'),'-p','-classpath',ROOT/module/'target/classes',*names])
            for block in result.split('Compiled from ')[1:]:
                declaration=re.search(r'\b(?:class|interface|enum)\s+([\w.$]+)',block)
                if declaration and declaration.group(1) in INDEX:INDEX[declaration.group(1)]['signatures']=block.splitlines()[1:]
        except RuntimeError:
            WARNINGS.append('javap batch unavailable during concurrent build: '+module)
            for n in names:INDEX[n]['bytecode']='PENDING_COMPILE'
    if any(x['bytecode']!='COMPILED' for x in INDEX.values()): WARNINGS.append('Some source files are newer than/missing bytecode: final compile/signature verification pending. Source snapshot is not a build/test pass.')

def key(short,mode):
    prefix={'ddd':'com.xmu.rbac.ddd.','structured':'com.xmu.rbac.structured.','gateway':'com.xmu.rbac.gateway.'}[mode]
    matches=[k for k in INDEX if k.startswith(prefix) and k.endswith('.'+short)]
    if len(matches)!=1: raise ValueError(f'Class not unique/existing: {mode}:{short}: {matches}')
    return matches[0]

def method(k,m):
    entry=INDEX[k]; src=re.sub(r'@[\w.]+(?:\([^)]*\))?', '', entry['source'])
    # Only declarations, not call sites. javap is a second check when current compiled output exists.
    pattern=r'(?:public|private|protected|static|final|default|synchronized|abstract|\n|[;}])\s*(?:(?:public|private|protected|static|final|default|synchronized|abstract)\s+)*(?:<[^;{}]+>\s*)?[\w.$<>?,\[\] ]+?\s+'+re.escape(m)+r'\s*\(([^)]*)\)\s*(?:throws\s+[\w., ]+)?[;{]'
    match=re.search(pattern,src)
    if not match: raise ValueError(f'Missing method declaration: {k}.{m}')
    compiled=[s.strip().rstrip(';') for s in entry['signatures'] if re.search(r'\.'+re.escape(m)+r'\(',s) or re.search(r'\s'+re.escape(m)+r'\(',s)]
    if entry['bytecode']=='COMPILED' and not compiled: raise ValueError(f'javap missing method: {k}.{m}')
    signature=compiled[0] if compiled and entry['bytecode']=='COMPILED' else re.sub(r'\s+',' ',match.group(0).strip(' ;{}\n'))
    REFS.append({'figure':CURRENT_FIGURE,'class':k,'method':m,'path':entry['path'],'sourceSha256':entry['sha256'],'bytecode':entry['bytecode'],'signature':signature,'compiledSignatureCandidates':compiled})
    # FQ names shortened only on drawing, full javap preserved in index.
    return short_signature(signature)

def short_signature(signature):
    return re.sub(r'(?:[a-z_]\w*\.)+([A-Z]\w*)',r'\1',signature).replace('$','.')

def java_body(source, name):
    """Locate a real method body, preserving offsets while masking strings/comments for braces."""
    mask=re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', lambda m:' '*len(m.group()),source,flags=re.S)
    declaration=re.search(r'\b(?:public|private|protected)\s+(?:[\w.$<>?,\[\]\s]+?)\b'+re.escape(name)+r'\s*\([^)]*\)\s*(?:throws\s+[\w.,\s]+)?\{',mask)
    if not declaration:raise ValueError('Method body missing: '+name)
    start=declaration.end();depth=1;end=start
    while end<len(mask) and depth:
        if mask[end]=='{':depth+=1
        elif mask[end]=='}':depth-=1
        end+=1
    if depth:raise ValueError('Unbalanced method body: '+name)
    return source[start:end-1]

def verify_call_chains():
    global CURRENT_FIGURE
    CURRENT_FIGURE='call-chain-review'
    # Ordered lexical callsites plus manual branch review. Not a Java call-graph or behavioral test.
    specs=[
      ('S01','ddd','RoleApplicationService','link',['repository.lockGrants()','authorization.require(','repository.role(senior)','repository.role(junior)','authorization.requireRoleManagement(actor,role)','authorization.requireRoleManagement(actor,other)','repository.hierarchy(','graph.link(','requireAffectedGrants(','repository.saveEdges(','complete(']),
      ('S01/S06','ddd','RoleApplicationService','requireAffectedGrants',['graph.affectedBy(','authorization.requireGraphGrant(','authorization.upperBound(','policy.calculate(']),
      ('S01/S06','ddd','RoleApplicationService','complete',['integrity.requireAdministrator()','repository.advanceVersion()','audits.local(']),
      ('S02','ddd','DirectoryApplicationService','assign',['grants.lockGrants()','actor(','directory.user(id,true)','managed(','grants.role(roleId)','authorization.upperBound(','aggregate(profile).assign(','directory.assign(','done(']),
      ('S02','ddd','DirectoryApplicationService','done',['integrity.requireAdministrator()','grants.advanceVersion()','audits.local(']),
      ('SIM','ddd','AuthorizationController','check',['rest.principal(','authorization.require(','directory.user(','authorization.unavailableReason(','authorization.effective(','authorization.api(','result.put("allowed"','result.put("reason"','result.put("trace"']),
      ('SIM','ddd','AuthorizationApplicationService','unavailableReason',['repository.systemEnabled(system)','repository.identity(userId)','DomainException.require(target!=null','return"USER_DISABLED"','returnenabled?null:"SYSTEM_DISABLED"']),
      ('S02','ddd','JpaDirectoryRepositoryAdapter','saveUser',['e.setSubject(','UUID.randomUUID()','em.persist(e)','em.flush()','returnuser(e)']),
      ('S03','ddd','AuthorizationApplicationService','effective',['reads.fresh(','stableEffective(','returnstableEffective(']),
      ('S03','ddd','AuthorizationApplicationService','stableEffective',['repository.freshenRead()','repository.version()','repository.systemEnabled(','repository.identity(userId)','cache.get(','policy.calculate(','repository.version()','cache.put(','repository.version()']),
      ('S04','ddd','AuthorizationApplicationService','stableApi',['repository.freshenRead()','repository.version()','repository.operation(','repository.identity(user)','effective(user,system)','repository.identity(user)','policy.allows(','repository.version()']),
      ('S01/S02','ddd','AuthorizationApplicationService','upperBound',['effective(actor.id(),"rbac-center")','repository.hierarchy(system).requireGrantable(','policy.requireWithin(']),
      ('S06','ddd','RoleApplicationService','status',['repository.lockGrants()','authorization.require(','repository.role(id)','authorization.requireRoleManagement(','role.changeStatus(','requireAffectedGrants(','repository.saveRole(','complete(']),
      ('S05','ddd','AuditApplicationService','external',['canonical(before)','canonical(after)','newAuditFact(','fresh.execute(','repository.find(requestId)','existing.requireReplay(fact)','repository.append(fact)','catch(DataIntegrityViolationException','fresh.execute(','repository.find(requestId)','existing.requireReplay(fact)']),
      ('S06','ddd','AuditApplicationService','local',['grants.systemEnabled(','grants.assignments(','grants.hierarchy(','grants.permissions(','newAuditFact(','repository.append(fact)']),
      ('LOGIN','ddd','IdentityApplicationService','login',['loginAttempts.attempt(','DomainException.require(token!=null','returntoken']),
      ('LOGIN','ddd','LoginAttemptApplicationService','attempt',['repository.lockLogin(','policy.locked(','encoder.matches(','policy.evaluate(','repository.saveLoginState(','if(!result.accepted())','audits.local(','returnnull','repository.systemEnabled(','tokens.issue(','audits.local(']),
      ('S01/S02/S06','structured','ManagementService','begin',['az.lock()','az.center(','az.root(','az.captureUpper(']),
      ('S01/S02/S06','structured','ManagementService','finish',['db.flush()','az.preserveAdministrator()','g.version++','audit.success(']),
      ('S01','structured','ManagementService','inherit',['begin(','az.protectRole(a,s)','az.protectRole(a,j)','db.one(','reachable(junior,senior)','db.save(edge)','changedUpper(','finish(']),
      ('S02','structured','ManagementService','assign',['begin(','az.scope(','db.one(','az.upper(','db.save(ur)','finish(']),
      ('S03','structured','AuthorizationService','snapshot',['active(u,s)','if(tx)','for(intattempt=0','version()','namespace+":authz:"','l1.get(','redis.opsForValue().get(','calculate(','if(version!=version())','l1.put(','redis.opsForValue().set(']),
      ('S06','structured','ManagementService','roleStatus',['begin(','az.protectRole(','r.status=status','changedUpper(','finish(']),
      ('S05','structured','AuditService','external',['trusted(','authz.permission(','safe(input.before()','safe(input.after()','isolated.execute(','db.one(','same(existing,payload)','db.save(log)','db.flush()','catch(DataIntegrityViolationException','isolated.execute(','db.one(','same(log,payload)']),
      ('S04','gateway','GatewayController','forward',['routes.resolve(','auth.authorize(','BoundedBody.read(','proxy.forward(']),
      ('S04','gateway','AuthBoundary','authorize',['identity.check(','response.getStatusCode().value()!=200','response.getBody()','operationId.equals(decision.operationId())','if(!decision.allowed())'])
    ]
    for scenario,mode,short,name,tokens in specs:
        k=key(short,mode);entry=INDEX[k];method(k,name)
        body=java_body(entry['source'],name);normalized=re.sub(r'\s+','',body);position=0;positions=[]
        if short=='JpaDirectoryRepositoryAdapter' and name=='saveUser' and normalized.count('e.setSubject(')!=1:
            raise ValueError('Subject must be assigned once before initial INSERT')
        for token in tokens:
            token=re.sub(r'\s+','',token);found=normalized.find(token,position)
            if found<0:raise ValueError(f'Call-chain drift: {scenario} {k}.{name}: missing/out-of-order {token}')
            positions.append(found);position=found+len(token)
        CALL_CHAINS.append({'scenario':scenario,'class':k,'method':name,'path':entry['path'],'sourceSha256':entry['sha256'],'bodySha256':hashlib.sha256(normalized_text_bytes(body.encode('utf-8'))).hexdigest(),'orderedTokens':tokens,'normalizedOffsets':positions,'status':'ORDERED_SOURCE_CHECKED','limit':'Lexical callsite order + manual branch review; not runtime/behavior proof'})
    write('call-chain-review','json',json.dumps(CALL_CHAINS,ensure_ascii=False,indent=2))

def write(name,ext,content):
    path=OUT/(name+'.'+ext); path.write_text(content,encoding='utf-8'); return path

def dot_render(name,body,engine='dot'):
    path=write(name,'dot',body)
    for fmt in ['svg','png']:
        args=[GRAPHVIZ_DOT,'-K'+engine]
        if engine=='neato': args+=['-n2']
        run(args+['-T'+fmt,path,'-o',OUT/(name+'.'+fmt)])
    FIGURES.append(name)

def class_diagram(name,mode,nodes,edges):
    global CURRENT_FIGURE
    CURRENT_FIGURE=name
    # Fixed compass layout; Service center. Bounding checks use actual Graphviz plain geometry.
    positions={'C':(0,0),'L':(-1000,0),'R':(1000,0),'T':(0,360),'B':(0,-360),'LB':(-1000,-360),'RB':(1000,-360),'RT':(1000,360)}
    rows=[]
    for alias,(short,slot,methods) in nodes.items():
        k=key(short,mode); ent=INDEX[k]; src=ent['source']; sigs=[]
        for m in methods:
            rendered=method(k,m)
            candidates=[s.strip().rstrip(';') for s in ent['signatures'] if re.search(r'\s'+re.escape(m)+r'\(',s)]
            sigs.extend([short_signature(s) for s in candidates] if ent['bytecode']=='COMPILED' and len(candidates)>1 else [rendered])
        field_lines=[]
        for a,b,kind,role in edges:
            if a==alias and kind=='association':
                # Role labels must correspond to an actual field declaration.
                field=re.search(r'\b([\w.$]+(?:<[^;=]+>)?(?:\[\])?)\s+'+re.escape(role)+r'\s*(?:[;=,])',src)
                if not field: raise ValueError(f'Missing field {k}.{role}')
                field_lines.append('- '+role+': '+field.group(1))
        # Show actual scalar state, never fabricate fields for a conceptual model.
        for typ,field in re.findall(r'(?:public|private|protected)\s+(?:final\s+)?(String|long|Long|boolean|Boolean|int|Integer|Instant|Set<Long>)\s+(\w+)\s*[;=]',src):
            field_lines.append('- '+field+': '+typ)
            if len(field_lines)>=6:break
        if not field_lines:
            record=re.search(r'public\s+record\s+'+re.escape(short)+r'\(([^)]*)\)',src)
            if record:
                parts=record.group(1).split(',')
                if short=='AuditFact':parts=[p for p in parts if p.strip().split()[-1] in ['id','requestId','systemCode','actorSubject','payloadHash','at']]
                field_lines.extend('- '+part.strip() for part in parts[:6])
        def cell(lines,bg):
            wrapped=[]
            for line in lines:
                wrapped.extend(textwrap.wrap(line,95,break_long_words=False,break_on_hyphens=False) or [' '])
            return '<TR><TD ALIGN="LEFT" BGCOLOR="'+bg+'">'+'<BR ALIGN="LEFT"/>'.join(html.escape(l) for l in wrapped)+'</TD></TR>'
        stereotype='«interface» ' if re.search(r'public\s+interface\s+'+short,src) else ''
        table='<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="10">'+cell([stereotype+short],'#dcecff')+'<HR/>'+cell(field_lines or [' '],'#ffffff')+'<HR/>'+cell(sigs,'#ffffff')+'</TABLE>'
        x,y=positions[slot]; rows.append(f'"{alias}" [pos="{x},{y}!",label=<{table}>];')
    for a,b,kind,role in edges:
        source=INDEX[key(nodes[a][0],mode)]['source']
        if kind=='implementation' and not re.search(r'implements\s+[^\{]*\b'+re.escape(nodes[b][0])+r'\b',source):
            raise ValueError(f'Implementation relation not in source: {name}:{a}->{b}')
        if kind=='dependency' and nodes[b][0] not in source:
            raise ValueError(f'Dependency target not referenced in source: {name}:{a}->{b}')
        opts={'association':'style=solid,arrowhead=vee','dependency':'style=dashed,arrowhead=vee','implementation':'style=dashed,arrowhead=empty'}[kind]
        if kind=='association': opts+=',label="'+role+'",fontsize=14'
        rows.append(f'"{a}" -> "{b}" [{opts}];')
    rows.extend(['"_top" [pos="0,480!",shape=point,width=0,height=0,style=invis,label=""];','"_bottom" [pos="0,-480!",shape=point,width=0,height=0,style=invis,label=""];'])
    body='digraph G { graph [overlap=true,splines=line,outputorder=edgesfirst,pad=0.3,bgcolor="white",label="'+name+' — current source snapshot",labelloc=t]; node [shape=plain,fontname="Microsoft YaHei",fontsize=15]; edge [color="#49627b",arrowsize=1.2,fontname="Microsoft YaHei"];\n'+'\n'.join(rows)+'\n}'
    dot_render(name,body,'neato')
    plain=run([GRAPHVIZ_DOT,'-Kneato','-n2','-Tplain',OUT/(name+'.dot')])
    boxes={}
    for line in plain.splitlines():
        fields=line.split()
        if fields and fields[0]=='node':
            _,a,x,y,w,h=fields[:6];boxes[a.strip('"')]=tuple(map(float,(x,y,w,h)))
    for a,(x,y,w,h) in boxes.items():
        for b,(xx,yy,ww,hh) in boxes.items():
            if a<b and abs(x-xx)<(w+ww)/2 and abs(y-yy)<(h+hh)/2: raise ValueError(f'Box overlap {name} {a} {b}')
    # A straight edge must not intersect a third-party box; segment clipping by dense deterministic sampling.
    for a,b,kind,role in edges:
        x,y,_,_=boxes[a];xx,yy,_,_=boxes[b]
        for c,(cx,cy,cw,ch) in boxes.items():
            if c in [a,b]:continue
            for i in range(1,1000):
                px=x+(xx-x)*i/1000;py=y+(yy-y)*i/1000
                if abs(px-cx)<cw/2 and abs(py-cy)<ch/2: raise ValueError(f'Edge crosses {name}: {a}->{b} through {c}')

class Sequence:
    def __init__(self,name,mode,participants,entry,method_name,args):
        global CURRENT_FIGURE
        CURRENT_FIGURE=name
        self.name=name;self.mode=mode;self.participants=participants;self.count=0
        self.lines=['@startuml','hide footbox','skinparam defaultFontName Microsoft YaHei','skinparam defaultFontSize 14','skinparam ParticipantPadding 24','skinparam BoxPadding 12','skinparam sequenceMessageAlign left','skinparam maxMessageSize 100','title '+name+' — source snapshot (not a runtime trace)','participant "obj: 调用者" as obj']
        for alias,short in participants.items(): self.lines+=['participant "'+alias+': '+short+'" as '+alias]
        self.lines+=['activate obj'];self.open('obj',entry,method_name,args)
        self.entry=entry
    def open(self,a,b,m,args=''):
        method(key(self.participants[b],self.mode if b not in ['gw','route','boundary','http','proxy'] else 'gateway'),m)
        self.count+=1;self.lines += [f'{a} -> {b}: {self.count}. {m}({args})',f'activate {b}']
    def close(self,b,a,label=''):
        if label:self.lines += [f'{b} --> {a}: {label}']
        self.lines += [f'deactivate {b}']
    def call(self,a,b,m,args='',reply=''):self.open(a,b,m,args);self.close(b,a,reply)
    def raw(self,s):
        lines=s.splitlines();i=0
        command=re.compile(r'^(?:note |alt |else(?: |$)|end(?: |$)|group |critical |loop |opt |activate |deactivate |[\w]+\s+[-.]+[<>])')
        while i<len(lines):
            line=lines[i]
            if line.startswith('note ') and ': ' in line:
                head,text=line.split(': ',1);body=[text];i+=1
                while i<len(lines) and not command.match(lines[i]):body.append(lines[i]);i+=1
                self.lines.extend([head,*body,'end note'])
            else:self.lines.append(line);i+=1
    def finish(self):
        self.close(self.entry,'obj');self.lines+=['deactivate obj','@enduml']
        balance={}
        for line in self.lines:
            if line.startswith('activate '):
                actor=line.split()[1];balance[actor]=balance.get(actor,0)+1
            if line.startswith('deactivate '):
                actor=line.split()[1];balance[actor]=balance.get(actor,0)-1
                if balance[actor]<0:raise ValueError('Activation underflow '+self.name+': '+actor)
        if any(balance.values()):raise ValueError('Unbalanced activation '+self.name+': '+str(balance))
        write(self.name,'puml','\n'.join(self.lines))

def handler(mode, service_method):
    if mode!='structured': return service_method
    src=INDEX[key('ManagementController',mode)]['source']
    position=src.index('management.'+service_method+'(')
    declarations=list(re.finditer(r'public\s+[\w<>?,. ]+\s+(\w+)\s*\(',src[:position]))
    if not declarations: raise ValueError('Cannot resolve handler '+service_method)
    return declarations[-1].group(1)

def classes():
    d='ddd';s='structured'
    class_diagram('S01-ddd-class',d,{'svc':('RoleApplicationService','C',['link','complete']),'repo':('AuthorizationRepository','L',['lockGrants','hierarchy','saveEdges']),'model':('RoleHierarchy','R',['link','unlink','requireGrantable']),'adapter':('JpaAuthorizationRepositoryAdapter','LB',['hierarchy','saveEdges']),'ctl':('RoleController','T',['link']),'auth':('AuthorizationApplicationService','B',['requireRoleManagement','requireGraphGrant','upperBound']),'role':('Role','RB',['superAdministratorCapability','requireManageable'])},[('ctl','svc','association','roles'),('svc','repo','association','repository'),('svc','model','dependency',''),('adapter','repo','implementation',''),('svc','auth','association','authorization'),('model','role','association','roles')])
    class_diagram('S02-ddd-class',d,{'svc':('DirectoryApplicationService','C',['assign','transfer']),'repo':('DirectoryRepository','L',['assign','user']),'model':('User','R',['assign','transfer','changeStatus']),'adapter':('JpaDirectoryRepositoryAdapter','LB',['assign']),'elig':('DepartmentEligibility','RB',['requireEligible','retained']),'auth':('AuthorizationApplicationService','B',['upperBound'])},[('svc','repo','association','directory'),('svc','model','dependency',''),('model','elig','dependency',''),('adapter','repo','implementation',''),('svc','auth','association','authorization')])
    class_diagram('S03-ddd-class',d,{'svc':('AuthorizationApplicationService','C',['effective','stableEffective','api','stableApi']),'repo':('AuthorizationRepository','L',['version','freshenRead','hierarchy']),'model':('AuthorizationPolicy','R',['calculate','allows','requireWithin']),'adapter':('JpaAuthorizationRepositoryAdapter','LB',['freshenRead','version','hierarchy']),'graph':('RoleHierarchy','RB',['paths']),'reads':('AuthorizationReadTransaction','T',['fresh']),'cache':('EffectiveCache','B',['get','put'])},[('svc','repo','association','repository'),('svc','model','association','policy'),('model','graph','dependency',''),('adapter','repo','implementation',''),('svc','reads','association','reads'),('svc','cache','association','cache')])
    class_diagram('S06-ddd-class',d,{'svc':('RoleApplicationService','C',['status','complete']),'repo':('AuthorizationRepository','L',['lockGrants','saveRole','advanceVersion']),'role':('Role','R',['changeStatus','requireManageable']),'ctl':('RoleController','T',['status']),'adapter':('JpaAuthorizationRepositoryAdapter','LB',['saveRole','advanceVersion']),'integrity':('GrantIntegrityService','B',['requireAdministrator']),'audit':('AuditApplicationService','RB',['local','denied'])},[('ctl','svc','association','roles'),('svc','repo','association','repository'),('adapter','repo','implementation',''),('svc','role','dependency',''),('svc','integrity','association','integrity'),('svc','audit','association','audits')])
    class_diagram('SIM-ddd-class',d,{'svc':('AuthorizationApplicationService','C',['unavailableReason','effective','api']),'repo':('AuthorizationRepository','L',['systemEnabled','identity']),'policy':('AuthorizationPolicy','R',['calculate','allows']),'ctl':('AuthorizationController','T',['explain','check']),'adapter':('JpaAuthorizationRepositoryAdapter','LB',['systemEnabled','identity'])},[('ctl','svc','association','authorization'),('svc','repo','association','repository'),('svc','policy','association','policy'),('adapter','repo','implementation','')])
    class_diagram('LOGIN-ddd-class',d,{'svc':('LoginAttemptApplicationService','C',['attempt']),'repo':('AuthorizationRepository','L',['lockLogin','saveLoginState']),'policy':('LoginAttemptPolicy','R',['locked','evaluate']),'ctl':('IdentityApplicationService','T',['login']),'adapter':('JpaAuthorizationRepositoryAdapter','LB',['lockLogin','saveLoginState']),'audit':('AuditApplicationService','B',['local'])},[('ctl','svc','association','loginAttempts'),('svc','repo','association','repository'),('adapter','repo','implementation',''),('svc','policy','association','policy'),('svc','audit','association','audits')])
    for n in ['S05']:
        class_diagram(n+'-ddd-class',d,{'svc':('AuditApplicationService','C',['external','local','denied']),'repo':('AuditRepository','L',['find','append']),'model':('AuditFact','R',['requireReplay']),'adapter':('JpaAuditRepositoryAdapter','LB',['find','append']),'ctl':(('IdentityController' if n=='S05' else 'RoleApplicationService'),'T',(['audit'] if n=='S05' else ['status','complete']))},[('svc','repo','association','repository'),('svc','model','dependency',''),('adapter','repo','implementation',''),('ctl','svc','association','audits')])
    for n,methods,model,modelmethods in [('S01',['inherit','reachable'],'RoleHierarchy',[]),('S02',['assign','moveUser'],'UserRole',[]),('S06',['roleStatus','finish'],'Role',[])]:
        class_diagram(n+'-structured-class',s,{'svc':('ManagementService','C',methods),'repo':('Store','L',['save','where','lock']),'model':(model,'R',modelmethods),'ctl':('ManagementController','T',([handler(s,'inherit')] if n=='S01' else [handler(s,'assign')] if n=='S02' else [handler(s,'roleStatus')])),'audit':('AuditService','B',['success','denied'])},[('ctl','svc','association','management'),('svc','repo','association','db'),('svc','model','dependency',''),('svc','audit','association','audit')])
    class_diagram('S03-structured-class',s,{'svc':('AuthorizationService','C',['snapshot','calculate','walk']),'repo':('Store','L',['where','all']),'model':('Role','R',[]),'ctl':('ManagementService','T',['effectiveUser'])},[('svc','repo','association','db'),('svc','model','dependency',''),('ctl','svc','association','az')])
    class_diagram('S05-structured-class',s,{'svc':('AuditService','C',['external','same','success','denied']),'repo':('Store','L',['one','save','flush']),'model':('AuditLog','R',[]),'ctl':('AuthController','T',['write'])},[('svc','repo','association','db'),('svc','model','dependency',''),('ctl','svc','association','audit')])
    for mode in [d,s]:
        class_diagram('S04-'+mode+'-class','gateway',{'svc':('AuthBoundary','C',['authorize']),'repo':('IdentityClient','L',['check']),'ctl':('GatewayController','T',['forward']),'route':('RouteRegistry','R',['resolve'])},[('svc','repo','association','identity'),('ctl','svc','association','auth'),('ctl','route','association','routes')])

def sequences():
    path=ROOT/'tools/render-rbac1-design.scenarios.py'
    exec(compile(INPUTS[path.relative_to(ROOT).as_posix()]['source'],str(path),'exec'),globals())
    build_sequences()

def packages():
    for mode in ['ddd','structured']:
        prefix='com.xmu.rbac.'+mode+'.';deps=set();counts={}
        for name,entry in INDEX.items():
            if not name.startswith(prefix):continue
            package=name.rsplit('.',1)[0][len(prefix):];counts[package]=counts.get(package,0)+1
            for imported in re.findall(r'import\s+(?:static\s+)?(com\.xmu\.rbac\.'+mode+r'\.[\w.*]+)',entry['source']):
                target=imported.rsplit('.',1)[0][len(prefix):]
                if package!=target:deps.add((package,target))
        nodes='\n'.join(json.dumps(p)+' [label='+json.dumps((p or '(module root)')+'\n'+str(n)+' source files')+'];' for p,n in counts.items())
        edges='\n'.join(json.dumps(a)+' -> '+json.dumps(b)+';' for a,b in sorted(deps) if a in counts and b in counts)
        dot_render(mode+'-packages','digraph G {rankdir=LR;node [shape=folder,fontname="Microsoft YaHei"];graph [label="'+mode+' — explicit Java imports only (wildcards preserved)"];'+nodes+edges+'}')

def er():
    sqlpath=ROOT/'rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql';sql=sqlpath.read_text(encoding='utf-8');tables=re.findall(r'CREATE TABLE (\w+)\s*\((.*?)\) ENGINE=',sql,re.S)
    if len(tables)!=15: raise ValueError(f'Expected 15 actual tables, got {len(tables)}; review ER generator')
    nodes=[];edges=[]
    for name,body in tables:
        cols=[];constraints=[]
        for line in body.splitlines():
            line=line.strip().rstrip(',')
            if re.match(r'\w+\s+(?:BIGINT|TINYINT|VARCHAR|CHAR|BOOLEAN|INT|TIMESTAMP|JSON)',line):cols.append(line)
            elif line:constraints.append(line)
        label='<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="5"><TR><TD BGCOLOR="#dcecff"><B>'+name+'</B></TD></TR><HR/>'+''.join('<TR><TD ALIGN="LEFT">'+html.escape('<PK> '+c if 'PRIMARY KEY' in c else c)+'</TD></TR>' for c in cols)+'</TABLE>'
        nodes.append(name+' [shape=plain,label=<'+label+'>];')
        for fk,target,pk in re.findall(r'FOREIGN KEY\((\w+)\) REFERENCES (\w+)\((\w+)\)',body):edges.append(f'{name} -> {target} [label="{fk} → {pk}",arrowhead=crow,arrowtail=tee,dir=both];')
    # Child -> parent FK direction, explicit cardinality via edge labels instead of misleading crow foot reversal.
    edges=[x.replace('arrowhead=crow,arrowtail=tee','arrowhead=tee,arrowtail=crow') for x in edges]
    dot_render('schema-15-er','digraph G {rankdir=LR;graph [nodesep=0.7,ranksep=2.0,splines=polyline,label="15-table physical ER — V1 DDL; nullable FK means optional parent; no inferred audit FK"];node [fontname="Microsoft YaHei",fontsize=11];edge [fontname="Microsoft YaHei",fontsize=10];'+''.join(nodes+edges)+'}')
    write('schema-constraints','json',json.dumps({name:{'columns':body.strip().splitlines(),'ddlSha256':sha(sqlpath)} for name,body in tables},ensure_ascii=False,indent=2))
    dot_render('role-senior-junior-dag','digraph G {rankdir=TB;node [shape=box];graph [label="Semantic example, NOT database snapshot: senior inherits junior; multiple juniors/parents legal"];A [label="A senior: p1"];B [label="B: p2"];C [label="C: p3"];D [label="D junior: p4"]; A->B;A->C;B->D;C->D; note [shape=note,label="A effective = {p1,p2,p3,p4}\\nD contributes once, sources retain both paths\\nDisabled B cuts A→B→D, not A→C→D\\nD→A rejected by link/reachable"]; }')

def deployment_snapshot(evidence, local, shared, runtime_evidence='current', root=None, configuration_sha=None, evidence_sha=None):
    """Validate immutable recorded evidence; never turn rebuilds into runtime acceptance.

    current (default): reject absent/mismatching current JARs.
    archived: validate historical rounds and report identities only. Current JARs
    may differ or be absent on compile-only CI; no runtime/build acceptance follows.
    """
    if runtime_evidence not in ('current','archived'):raise ValueError('Unknown runtime evidence mode: '+runtime_evidence)
    root=ROOT if root is None else Path(root)
    expected=['mysql','redis','rbac-backend','rbac-gateway','rbac-web','course-backend','course-web']
    if evidence.get('format')!='rbac1-local-final-dual-swarm-v1':raise ValueError('Expected nested local-final dual Swarm evidence')
    if evidence.get('status')!='local-final-dual-runtime-passed' or evidence.get('activeModule')!='rbac-ddd':raise ValueError('Expected completed local dual runtime with final DDD active')
    rounds={}
    for module in ['rbac-structured','rbac-ddd']:
        r=evidence.get('rounds',{}).get(module)
        if not isinstance(r,dict):raise ValueError('Missing deployment round: '+module)
        services=r.get('services',[]);probes=r.get('probes',[])
        roles={e['name'].removeprefix(evidence['stack']+'_') for e in services}
        if roles!=set(expected) or len(services)!=7 or len(probes)!=11 or not all(p.get('passed') is True for p in probes):raise ValueError('Incomplete seven-service / eleven-probe archived round: '+module)
        if not r.get('nodes') or not all(n.get('Status')=='Ready' for n in r['nodes']):raise ValueError('Missing/unready archived nodes: '+module)
        if not all(e.get('imageMatchesLocalBuild') is True and e.get('imageId')==e.get('expectedLocalImageId') and re.fullmatch(r'sha256:[0-9a-f]{64}',e.get('imageId','')) for e in services):raise ValueError('Archived image identity not proven: '+module)
        rounds[module]={'recordedAt':r.get('timestamp'),'stage':r.get('stage'),'status':r.get('status'),'finalAcceptance':r.get('finalAcceptance'),'verificationScope':r.get('verificationScope'),'serviceCount':len(services),'probeCount':len(probes),'passedProbes':sum(p.get('passed') is True for p in probes),'services':[{k:e.get(k) for k in ['name','image','imageId','expectedLocalImageId','imageMatchesLocalBuild','replicas','ports','containerId','persistentVolumes','health','ping']} for e in services],'nodes':[{k:n.get(k) for k in ['Hostname','Status','ManagerStatus','TLSStatus']} for n in r.get('nodes',[])],'probes':[{k:p[k] for k in ['url','via','status','expected','passed','health'] if k in p} for p in probes]}
    same_frontends=all(next(e['imageId'] for e in rounds['rbac-structured']['services'] if e['name'].endswith('_'+role))==next(e['imageId'] for e in rounds['rbac-ddd']['services'] if e['name'].endswith('_'+role)) for role in ['rbac-web','course-web'])
    same_volumes=all(next(e['persistentVolumes'] for e in rounds['rbac-structured']['services'] if e['name'].endswith('_'+role))==next(e['persistentVolumes'] for e in rounds['rbac-ddd']['services'] if e['name'].endswith('_'+role)) for role in ['mysql','redis'])
    same_data_containers=all(next(e['containerId'] for e in rounds['rbac-structured']['services'] if e['name'].endswith('_'+role))==next(e['containerId'] for e in rounds['rbac-ddd']['services'] if e['name'].endswith('_'+role)) for role in ['mysql','redis'])
    if evidence.get('localFinalDeploymentAccepted') is not True or not same_frontends or not same_volumes or not same_data_containers:raise ValueError('Local-final acceptance / same frontend / persistent resources evidence inconsistent')
    snapshot={'configurationSha256':configuration_sha,'scope':'Archived final LOCAL dual-runtime evidence; renderer performs no new live probe; no external server/CI claim. Per-round finalAcceptance=false means runtime-only scope, not local deployment failure.','evidencePath':'deploy/deployment-evidence.json','evidenceSha256':evidence_sha,'recordedAt':evidence.get('timestamp'),'stage':'local-final','status':evidence.get('status'),'tag':evidence.get('tag'),'stack':evidence.get('stack'),'archivedLocalFinalDeploymentAccepted':evidence.get('localFinalDeploymentAccepted'),'finalProjectAcceptance':evidence.get('finalProjectAcceptance'),'externalDeployment':evidence.get('externalDeployment'),'activeModule':evidence.get('activeModule'),'sameFrontendImageIds':same_frontends,'samePersistentVolumes':same_volumes,'sameDataServiceContainers':same_data_containers,'rounds':rounds,'localJars':{}}
    artifacts=evidence.get('prerequisites',{}).get('canonicalArtifacts',{})
    if set(artifacts)!={'rbac-structured','rbac-ddd','rbac-gateway','course-backend'}:raise ValueError('Missing/corrupt archived canonical artifact set')
    for module,artifact in artifacts.items():
        path=artifact.get('path','');recorded_sha=artifact.get('sha256','')
        if not path or Path(path).is_absolute() or '..' in Path(path).parts or not re.fullmatch(r'[0-9a-f]{64}',recorded_sha):raise ValueError('Invalid archived canonical artifact: '+module)
        jar=root/path;digest=sha(jar) if jar.is_file() else None
        if runtime_evidence=='current':
            if digest is None:raise ValueError('Canonical JAR missing: '+path)
            if digest!=recorded_sha:raise ValueError('Current JAR differs from final recorded canonical: '+module)
        snapshot['localJars'][path]={'sha256':digest,'exists':digest is not None,'recordedCanonicalSha256':recorded_sha,'comparisonPerformed':runtime_evidence=='current','matchesRecordedCanonical':True if runtime_evidence=='current' else None,'claim':'Current build raw bytes only. Archived mode does not bind these artifacts to historical runtime acceptance.'}
    snapshot.update({'runtimeEvidenceMode':runtime_evidence,'currentRuntimeVerified':False,'artifactsMatchCurrentBuild':runtime_evidence=='current','artifactMatchClaim':'RAW_SHA_MATCH_TO_DATED_ARCHIVE' if runtime_evidence=='current' else 'NOT_ESTABLISHED_IN_ARCHIVED_MODE','currentBuildAcceptanceClaim':False})
    if shared.get('summary')!={'pass':51,'fail':0}:raise ValueError('Expected archived shared HTTP 51 PASS')
    for short in ['structured','ddd']:
        if shared['modules'][short]['jarSha256']!=evidence['prerequisites']['canonicalArtifacts']['rbac-'+short]['sha256']:raise ValueError('Shared HTTP canonical identity mismatch: '+short)
    snapshot['localAcceptanceSummary']={'source':'docs/acceptance/local-results.json','sharedHttp':shared['summary'],'verify':local['verify'],'junitTotal':local['junitTotal'],'course':local['course'],'frontendUnit':local['frontendUnit'],'browser':local['browser']}
    return snapshot

def runtime_evidence_note(snapshot):
    return chr(10).join([
        'Evidence mode: '+snapshot['runtimeEvidenceMode']+'; historical LOCAL archive only.',
        'currentRuntimeVerified=false; artifactsMatchCurrentBuild='+str(snapshot['artifactsMatchCurrentBuild']).lower()+'.',
        ('Historical localFinalDeploymentAccepted=true; not current CI/build acceptance.' if snapshot['runtimeEvidenceMode']=='archived' else 'Recorded LOCAL deployment accepted; current JARs byte-match this dated archive.'),
        'Historical shared/course/frontend reports bind archived canonical SHA only.',
        'Current source/class signatures and method order are checked separately.',
        'No new live probe, current business/final acceptance, external server or CI runtime claim.'])

def ancillary(runtime_evidence='current'):
    global CURRENT_FIGURE
    CURRENT_FIGURE='role-state'
    role=key('Role','ddd');method(role,'changeStatus')
    write('role-state','puml','''@startuml
skinparam defaultFontName Microsoft YaHei
hide empty description
left to right direction
title Role actual two-state behavior (DDD domain + application guard)
[*] --> ENABLED : create(... enabled=true ...)
ENABLED --> DISABLED : changeStatus(false)
DISABLED --> ENABLED : changeStatus(true)
note bottom of ENABLED
Repeated status assignment is legal (self transition).
end note
note bottom of DISABLED
Role.changeStatus is an assignment, not an event publisher.
status → requireRoleManagement → changeStatus;
when enabling: upperBound; then saveRole → complete.
complete → GrantIntegrityService.requireAdministrator
→ version + same-transaction audit.
Privileged actor may disable a role only if integrity holds.
Structured: ManagementService.roleStatus assigns string status;
finish → preserveAdministrator prevents last admin loss.
No fictitious RoleStatusChangedEvent.
end note
@enduml''')
    compose=INPUTS['docker-compose.yml']['source']
    expected=['mysql','redis','rbac-backend','rbac-gateway','rbac-web','course-backend','course-web']
    for service in expected:
        if not re.search(r'^  '+re.escape(service)+r':',compose,re.M):raise ValueError('Deployment definition changed: missing '+service)
    evidence=json.loads(INPUTS['deploy/deployment-evidence.json']['source'])
    local=json.loads(INPUTS['docs/acceptance/local-results.json']['source']);shared=json.loads(INPUTS['docs/acceptance/shared-http.json']['source'])
    snapshot=deployment_snapshot(evidence,local,shared,runtime_evidence,configuration_sha=INPUTS['docker-compose.yml']['sha256'],evidence_sha=INPUTS['deploy/deployment-evidence.json']['sha256'])
    write('deployment-snapshot','json',json.dumps(snapshot,ensure_ascii=False,indent=2))
    timestamp=str(evidence.get('timestamp','unknown'))
    write('swarm-deployment','puml',r'''@startuml
skinparam defaultFontName Microsoft YaHei
title EVIDENCE_TITLE — mode EVIDENCE_MODE (no renderer live probe)
actor Browser
node "Swarm private overlay (attachable)" {
 node "application services (one active central backend)" {
  component "rbac-web\nTLS :443 / published default 8443" as web
  component "course-web\nTLS :443 / published default 9443" as cw
  component "rbac-backend\nMODULE=structured|ddd; default :8081\nno published port" as center
  component "rbac-gateway :8090 default\nno published port" as gateway
  component "course-backend :8080 default\nno published port" as course
 }
 node "MySQL constrained to manager; Redis on overlay" {
  database "MySQL 8 / mysql-data\nrbac_center + rbac_course separate schemas" as mysql
  database "Redis 7\nnamespace + version cache" as redis
 }
}
Browser --> web : HTTPS default 8443
Browser --> cw : HTTPS default 9443
web --> center : /api | /auth | /.well-known
cw --> center : /auth | /.well-known
cw --> gateway : /api/ (no direct course proxy)
gateway --> center : IdentityClient.check /auth/api-permissions/{operationId}
gateway --> course : ProxyClient.forward after allowed
course --> center : synchronous audit POST / GET + trusted internal identity
center --> mysql : business + SUCCESS audit_log same local TX
course --> mysql : separate course TX; NOT distributed atomicity
center --> redis : versioned cache
note bottom of center
Both nginx boundaries deny /integration and /identity externally.
Secrets/configtree and RSA files are configured; no values rendered.
deployment-evidence.json: EVIDENCE_STATUS
recorded TIMESTAMP; immutable tag EVIDENCE_TAG.
structured -> DDD: each 7/7 services, 11/11 probes, exact recorded image IDs.
Same frontend images + MySQL/Redis persistent resources; final active = rbac-ddd.
RUNTIME_EVIDENCE_NOTE
end note
@enduml'''.replace('TIMESTAMP',timestamp).replace('EVIDENCE_STATUS',str(evidence.get('status','UNVERIFIED'))).replace('EVIDENCE_TAG',str(evidence.get('tag','UNVERIFIED'))).replace('RUNTIME_EVIDENCE_NOTE',runtime_evidence_note(snapshot)).replace('EVIDENCE_MODE',runtime_evidence).replace('EVIDENCE_TITLE','Historical LOCAL dual Swarm' if runtime_evidence=='archived' else 'Verified LOCAL archive with matching current JARs'))

def plantuml():
    jar=Path(tempfile.gettempdir())/f'rbac1-plantuml-{VERSION}.jar'
    if not jar.exists():urllib.request.urlretrieve(f'https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/{VERSION}/plantuml-{VERSION}.jar',jar)
    for fmt in ['svg','png']:
        run([find_java('java'),'-Dfile.encoding=UTF-8','-jar',jar,'-charset','UTF-8','-t'+fmt,*sorted(OUT.glob('*.puml'))])
    for p in sorted(OUT.glob('*.puml')): FIGURES.append(p.stem)
    return {'plantumlVersion':VERSION,'jarSha256':sha(jar),'java':str(find_java('java'))}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--runtime-evidence',choices=['current','archived'],default='current',help='current (default) requires exact current JAR SHA match; archived validates historical local evidence only, never current runtime/build acceptance');parser.add_argument('--strict-compiled',action='store_true');parser.add_argument('--check',action='store_true',help='check saved source hashes without rendering');args=parser.parse_args();OUT.mkdir(parents=True,exist_ok=True)
    if args.check:
        data=json.loads((OUT/'source-index.json').read_text(encoding='utf-8'))
        if data.get('runtimeEvidenceMode','current')!=args.runtime_evidence:raise SystemExit('Evidence mode differs from saved snapshot; regenerate/check with the explicit matching --runtime-evidence mode')
        if data.get('currentRuntimeVerified') is not False or data.get('artifactsMatchCurrentBuild') is not (args.runtime_evidence=='current'):raise SystemExit('Invalid saved runtime/build acceptance claims for evidence mode')
        if data.get('hashRules')!=HASH_RULES:raise SystemExit('STALE hash rules: regenerate with current text/binary hash policy')
        changed=[p for p,v in data['sources'].items() if not (ROOT/p).exists() or sha(ROOT/p)!=v['sha256']]
        for path,entry in data['sources'].items():
            if entry.get('classSha256'):
                bytecode=ROOT/entry['classPath']
                if not bytecode.exists() or sha(bytecode)!=entry['classSha256']:changed.append(entry['classPath'])
        current_java={p.relative_to(ROOT).as_posix() for module in ['rbac-ddd','rbac-structured','rbac-gateway'] for p in (ROOT/module/'src/main/java').rglob('*.java') if p.stem!='package-info'}
        added=current_java-set(data['sources'])
        changed.extend(sorted(added))
        if data.get('generatorSha256') and sha(Path(__file__))!=data['generatorSha256']:changed.append('tools/render-rbac1-design.py')
        for p,digest in data.get('artifacts',{}).items():
            if not (OUT/p).exists() or sha(OUT/p)!=digest:changed.append('docs/design/'+p)
        if changed: raise SystemExit('STALE diagrams: '+'; '.join(changed))
        deployment_snapshot(json.loads(source_text(ROOT/'deploy/deployment-evidence.json')),json.loads(source_text(ROOT/'docs/acceptance/local-results.json')),json.loads(source_text(ROOT/'docs/acceptance/shared-http.json')),args.runtime_evidence)
        print('Source hashes and explicit '+args.runtime_evidence+' evidence policy match saved diagram snapshot. Not a test/deployment assertion.');return
    collect()
    if args.strict_compiled and any(e['bytecode']!='COMPILED' for e in INDEX.values()):
        write('generation-status','json',json.dumps({'status':'PENDING_COMPILE','files':[e['path'] for e in INDEX.values() if e['bytecode']!='COMPILED']},indent=2));raise SystemExit('PENDING_COMPILE: compile all three modules before --strict-compiled; no success claimed')
    verify_call_chains();classes();sequences();packages();er();ancillary(args.runtime_evidence);tool=plantuml()
    sources={e['path']:{'sha256':e['sha256'],'bytecode':e['bytecode'],'classPath':e['classPath'],'classSha256':e['classSha256'],'javap':e['signatures']} for e in INDEX.values()}
    for path,entry in INPUTS.items():sources[path]={'sha256':entry['sha256']}
    changed=[p for p,v in sources.items() if sha(ROOT/p)!=v['sha256']]
    changed.extend(e['classPath'] for e in INDEX.values() if e['classSha256'] and (not (ROOT/e['classPath']).exists() or sha(ROOT/e['classPath'])!=e['classSha256']))
    if changed:raise RuntimeError('Sources changed DURING generation; rerun after compile: '+str(changed))
    index={'runtimeEvidenceMode':args.runtime_evidence,'currentRuntimeVerified':False,'artifactsMatchCurrentBuild':args.runtime_evidence=='current','hashRules':HASH_RULES,'generatorSha256':sha(Path(__file__)),'artifacts':{p.name:sha(p) for f in sorted(set(FIGURES)) for p in [OUT/(f+'.svg'),OUT/(f+'.png')]},'generatedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'strictCompiled':args.strict_compiled,'status':'SOURCE_ONLY_PENDING_COMPILE' if WARNINGS else ('STRICT_COMPILED_CHECKED' if args.strict_compiled else 'SIGNATURE_CHECKED'),'warnings':WARNINGS,'tool':tool,'sources':sources,'references':REFS,'callChains':CALL_CHAINS,'figures':sorted(set(FIGURES)),'checks':{'classBoxes':'PASS','classEdgesThirdPartyBoxes':'PASS','symbols':'PASS','orderedSourceCallChains':'PASS','sourceHashesStable':'PASS','sequenceActivationBalance':'PASS','plantumlSyntax':'PASS','pngVisualInspection':'MANUAL_REQUIRED','tests':'NOT_RUN','deployment':'ARCHIVED_LOCAL_FINAL_CHECKED_NO_NEW_LIVE_PROBE'}}
    write('source-index','json',json.dumps(index,ensure_ascii=False,indent=2));write('generation-status','json',json.dumps({k:index[k] for k in ['generatedAt','runtimeEvidenceMode','currentRuntimeVerified','artifactsMatchCurrentBuild','hashRules','strictCompiled','status','warnings','checks']},ensure_ascii=False,indent=2))
    write('index','adoc','= 当前源码图索引\n\n自动生成；源码哈希见 source-index.json。文本 SHA-256 仅归一化 CRLF→LF；class/jar/png 等二进制逐字节哈希。STRICT_COMPILED_CHECKED / SIGNATURE_CHECKED 仅表示编译产物、符号及受检查源码调用顺序对齐，不等于业务测试或部署通过。\n\n'+'\n\n'.join('== '+f+'\n\nimage::'+f+'.svg['+f+']\n\nlink:'+f+'.png[PNG] / link:'+f+('.puml[PlantUML]' if (OUT/(f+'.puml')).exists() else '.dot[DOT]') for f in sorted(set(FIGURES))))
    print(json.dumps({'status':index['status'],'figures':len(set(FIGURES)),'warnings':WARNINGS,'visual':'Every PNG must be opened; renderer does not assert manual inspection.'},ensure_ascii=False))
if __name__=='__main__':
    try:main()
    except Exception as error:
        OUT.mkdir(parents=True,exist_ok=True)
        write('generation-status','json',json.dumps({'status':'FAILED','reason':str(error),'tests':'NOT_RUN','deployment':'UNVERIFIED'},ensure_ascii=False,indent=2))
        raise
