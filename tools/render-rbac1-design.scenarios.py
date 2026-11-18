"""Maintained source-flow projections; loaded in renderer globals, not a business dependency.
Ordered callsite checks are in the renderer; notes identify omitted internals and transaction callbacks.
"""
def end_use_case(q):
    q.close('svc','ctl'); q.finish()

def local_completion(q, d, audit_args, repository='repo'):
    if d:
        q.call('svc','integrity','requireAdministrator')
        q.call('svc',repository,'advanceVersion')
    else:
        q.raw('note over svc: finish: Store.flush → preserveAdministrator → g.version++')
    q.call('svc','audit','local' if d else 'success',audit_args)

def affected_grants(q):
    q.open('svc','svc','requireAffectedGrants','actor, graph, changedRole, permissions')
    q.call('svc','graph','affectedBy','changedRole.id()')
    q.raw('loop changed role and every affected senior')
    q.call('svc','auth','requireGraphGrant','actor, graph, affectedId')
    q.call('svc','auth','upperBound','actor, systemCode, proposedClosure')
    q.raw('end\nnote over auth: All checks precede saveRole/saveEdges; actor facts still precede mutation.\nSpecial-capability traversal includes disabled nodes.')
    q.close('svc','svc')

def build_sequences():
    for mode in ['ddd','structured']:
        d=mode=='ddd'
        # S01: exact graph policy and persistence order; exceptions abort interaction.
        p={'ctl':'RoleController' if d else 'ManagementController',
           'svc':'RoleApplicationService' if d else 'ManagementService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService',
           'repo':'AuthorizationRepository' if d else 'Store',
           'graph':'RoleHierarchy' if d else 'AuthorizationService',
           'audit':'AuditApplicationService' if d else 'AuditService'}
        if d:p['integrity']='GrantIntegrityService'
        else:p.pop('graph',None)
        q=Sequence('S01-'+mode+'-sequence',mode,p,'ctl','link' if d else handler(mode,'inherit'),
                   'bearer, seniorId, juniorId' if d else 'actor, seniorId, juniorId')
        q.open('ctl','svc','link' if d else 'inherit','principal, senior, junior, true' if d else 'actor, senior, junior, true')
        q.raw('group local write transaction: lock before grant reads')
        q.call('svc','repo' if d else 'auth','lockGrants' if d else 'lock')
        q.call('svc','auth','require' if d else 'center','principal, rbac:role:edit, true' if d else 'actor, rbac:role:edit')
        if d:
            q.call('svc','repo','role','senior');q.call('svc','repo','role','junior')
            q.call('svc','auth','requireRoleManagement','actor, role');q.call('svc','auth','requireRoleManagement','actor, other')
            q.raw('note over auth: Role.requireManageable checks SUPER_ADMIN capability.\nCross-system rejected before graph mutation.')
            q.call('svc','repo','hierarchy','role.systemCode()')
            q.raw('alt self / cycle / duplicate')
            q.open('svc','graph','link','senior, junior');q.raw('graph --> obj: DomainException; abort / rollback');q.close('graph','svc')
            q.raw('else valid edge');q.call('svc','graph','link','senior, junior')
            affected_grants(q)
            q.raw('note over auth: requireGrantable traverses even disabled nodes:\nno pre-planted path to SUPER_ADMIN; permission-set inclusion alone is insufficient.')
            q.call('svc','repo','saveEdges','systemCode, graph.edges()');q.raw('end')
        else:
            q.call('svc','auth','root','actor');q.call('svc','auth','captureUpper','actor')
            q.call('svc','auth','protectRole','actor, seniorRole');q.call('svc','auth','protectRole','actor, juniorRole')
            q.call('svc','repo','one','RoleHierarchy.class, predicate, senior, junior')
            q.raw('note over svc: reachable(junior, senior): DFS queries outgoing edges.\nSelf / cross-system / duplicate / cycle throws BizException and aborts interaction.')
            q.raw('alt legal new edge');q.call('svc','repo','save','edge');q.call('svc','svc','changedUpper','actor, seniorRole')
            q.raw('note over auth: upper compares captured pre-mutation actor snapshot.\nSpecial-capability traversal includes disabled nodes;\na latent SUPER_ADMIN path is rejected and rolled back.\nelse invalid graph\nsvc --> obj: BizException; rollback\nend')
        local_completion(q,d,'actor, system, type, target, SUCCESS, null' if d else 'actor, operation, target')
        q.raw('end\nnote over svc: Transaction proxy commits only normal return.\nAny exception aborts: no edge/version/SUCCESS fact persists.')
        end_use_case(q)
        # S02: user rules precede DirectoryRepository.assign; eligibility owns role admission.
        p={'ctl':'DirectoryController' if d else 'ManagementController',
           'svc':'DirectoryApplicationService' if d else 'ManagementService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService',
           'repo':'AuthorizationRepository' if d else 'Store',
           'user':'User' if d else 'Store','audit':'AuditApplicationService' if d else 'AuditService'}
        if d:p.update({'directory':'DirectoryRepository','eligibility':'DepartmentEligibility','integrity':'GrantIntegrityService'})
        else:p.pop('user',None)
        q=Sequence('S02-'+mode+'-sequence',mode,p,'ctl','assign' if d else handler(mode,'assign'),'bearer, userId, input' if d else 'actor, userId, input')
        q.open('ctl','svc','assign','principal, userId, roleId' if d else 'actor, userId, roleId')
        q.raw('group local write transaction')
        q.call('svc','repo' if d else 'auth','lockGrants' if d else 'lock')
        q.call('svc','auth','require' if d else 'center','principal, rbac:assignment:edit, false' if d else 'actor, rbac:assignment:edit')
        if d:
            q.call('svc','directory','user','userId, true');q.raw('note over svc: managed(actor, department) checks OrganizationPolicy scope.')
            q.call('svc','repo','role','roleId');q.call('svc','auth','upperBound','actor, systemCode, proposed')
            q.call('svc','repo','eligibility','departmentId')
            q.raw('alt user inactive / department mismatch')
            q.open('svc','user','assign','role, eligibility');q.raw('user --> obj: DomainException; abort / rollback');q.close('user','svc')
            q.raw('else user admits assignment');q.open('svc','user','assign','role, eligibility')
            q.call('user','eligibility','requireEligible','role');q.close('user','svc')
            q.raw('note over eligibility: Missing eligibility or disabled role throws here.\nUser then rejects duplicate direct assignment; all exceptions\nabort before DirectoryRepository.assign.')
            q.call('svc','directory','assign','userId, roleId, actor.id()');q.raw('end')
        else:
            q.call('svc','auth','captureUpper','actor');q.call('svc','auth','scope','actor, user.departmentId')
            q.raw('note over svc: ACTIVE user and enabled role/system required.')
            q.call('svc','repo','one','DepartmentAllowedRole.class, predicate, departmentId, roleId')
            q.raw('alt admitted');q.call('svc','auth','upper','actor, systemCode, proposed');q.call('svc','repo','save','ur')
            q.raw('else disabled / forbidden\nsvc --> obj: BizException; abort / rollback\nend')
        local_completion(q,d,'actor, rbac-center, ROLE_ASSIGNED, subject, SUCCESS, null' if d else 'actor, ROLE_ASSIGNED, userId')
        q.raw('end\nnote over svc: Assignment, administrator-integrity check, version and SUCCESS audit\nshare the transaction; exceptions cannot commit a partial success.')
        end_use_case(q)
        # S03: distinct cache algorithms, not a generic duplicated model.
        p={'ctl':'DirectoryController' if d else 'ManagementController','svc':'DirectoryApplicationService' if d else 'ManagementService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService','model':'AuthorizationPolicy' if d else 'Store','graph':'RoleHierarchy' if d else 'Role'}
        if d:p.update({'reads':'AuthorizationReadTransaction','repo':'AuthorizationRepository','cache':'EffectiveCache'})
        else:p.pop('graph',None)
        q=Sequence('S03-'+mode+'-sequence',mode,p,'ctl','effective' if d else handler(mode,'effectiveUser'),'bearer, id, system' if d else 'actor, user, system')
        q.open('ctl','svc','effective' if d else 'effectiveUser','principal, id, system' if d else 'actor, user, system')
        q.open('svc','auth','effective' if d else 'snapshot','userId, system')
        if d:
            q.raw('alt actual outer transaction is read-only')
            q.open('auth','reads','fresh','query callback');q.call('reads','auth','stableEffective','userId, system')
            q.raw('note over reads,repo: Supplier.get callback runs inside REQUIRES_NEW / READ_COMMITTED / readOnly.\nSame stableEffective loop as expanded below; each retry freshenRead clears JPA context.')
            q.close('reads','auth');q.raw('else no outer read-only transaction')
            q.open('auth','auth','stableEffective','userId, system')
            q.raw('loop at most 3 attempts')
            q.call('auth','repo','version');q.call('auth','repo','systemEnabled','system');q.call('auth','repo','identity','userId')
            q.raw('alt inactive target\nnote over auth: empty Effective; no stale user permissions\nelse ACTIVE and no actual transaction');q.call('auth','cache','get','system, userId, version')
            q.raw('else active write transaction\nnote over auth: bypass get AND put\nend\nalt result still null')
            q.call('auth','repo','assignments','userId, system');q.call('auth','repo','hierarchy','system');q.call('auth','repo','permissions','system')
            q.open('auth','model','calculate','system, directIds, graph, permissions');q.open('model','graph','paths','safeDirect')
            q.raw('loop each direct role');q.call('graph','graph','visit','id, new ArrayList<>(), result');q.raw('end');q.close('graph','model');q.close('model','auth')
            q.raw('end');q.call('auth','repo','version')
            q.raw('alt version changed\nnote over auth: continue; never publish mixed snapshot\nelse version unchanged\nopt ACTIVE target and no actual transaction');q.call('auth','cache','put','system, userId, version, result');q.raw('end')
            q.call('auth','repo','version');q.raw('note over auth: final version equal → return; changed again → next attempt\nend\nend\nalt all attempts unstable\nauth --> obj: DomainException 503 AUTHORIZATION_UNSTABLE\nelse stable\nnote over auth: valid cached OR calculated result\nend')
            q.close('auth','auth');q.raw('end')
        else:
            q.call('auth','auth','require','User.class, userId');q.call('auth','auth','system','system');q.call('auth','auth','active','user, softwareSystem')
            q.raw('alt actual transaction active');q.call('auth','auth','direct','userId, systemId')
            q.open('auth','auth','calculate','directIds, systemId');structured_calculation(q);q.close('auth','auth')
            q.raw('note over auth: No cache reads OR writes in transaction.\nelse nontransactional read\nloop at most 3 attempts');q.call('auth','auth','version')
            q.raw('note over auth: namespace:authz:system:user:version → l1.get;\nmiss → redis.opsForValue().get; Redis exception falls back to DB.\nalt no cached result');q.call('auth','auth','direct','userId, systemId')
            q.open('auth','auth','calculate','directIds, systemId');structured_calculation(q);q.close('auth','auth')
            q.raw('else cached result\nnote over auth: no recalculation\nend');q.call('auth','auth','version')
            q.raw('alt version changed\nnote over auth: continue, no publishing\nelse unchanged\nnote over auth: l1.put and Redis set, then return result\nend\nend\nnote over auth: exhausted attempts → 503 AUTHORIZATION_UNAVAILABLE\nend\nnote over auth: namespace is configured by rbac.cache.namespace, not hardcoded.\nSystem disabled within calculate yields empty; snapshot validates active user/system first.')
        q.raw('note over auth: Disabled intermediate role cuts that path; diamond permission id dedup\npreserves independent source paths. No direct roles → empty permissions.')
        q.close('auth','svc');end_use_case(q)
        # S04: shared gateway, backend policy implementation is version specific.
        p={'gw':'GatewayController','route':'RouteRegistry','boundary':'AuthBoundary','http':'IdentityClient',
           'ctl':'IdentityController' if d else 'AuthController','auth':'AuthorizationApplicationService' if d else 'AuthorizationService','proxy':'ProxyClient'}
        q=Sequence('S04-'+mode+'-sequence',mode,p,'gw','forward','request')
        q.call('gw','route','resolve','request.method, request.URI');q.open('gw','boundary','authorize','operationId, bearer')
        q.open('boundary','http','check','operationId, bearer');q.open('http','ctl','api','bearer, operationId' if d else 'actor, operationId')
        q.raw('note over ctl: Identity verified first; DDD unknown aud becomes 401 TOKEN_INVALID, not 404.')
        q.call('ctl','auth','api','userId, systemCode, operationId')
        if d:q.raw('ref over ctl,auth: S04-ddd-policy-sequence expands api → fresh/stableApi version bracket')
        q.close('ctl','http');q.close('http','boundary')
        q.raw('alt invalid identity / unknown operation / denied / timeout\nboundary --> obj: GatewayFailure 401 / 403 / 503\nelse allowed\nnote over boundary: Only HTTP 200 with matching operationId and non-null allowed is interpretable.')
        q.close('boundary','gw');q.call('gw','proxy','forward','request, bytes, traceId')
        q.raw('note over proxy: Course object-level 403 is preserved.\nGateway does NOT invent course success audit or course Service methods.\nend');q.finish()
        # S05: canonical audit fact matches TIMESTAMP(3), including replay/query boundaries.
        p={'ctl':'IdentityController' if d else 'AuthController','svc':'AuditApplicationService' if d else 'AuditService',
           'repo':'AuditRepository' if d else 'Store','fact':'AuditFact' if d else 'AuditLog'}
        q=Sequence('S05-'+mode+'-sequence',mode,p,'ctl','audit' if d else 'write','bearer, key, input' if d else 'actor, key, input')
        q.raw('note over ctl: authenticated actor + service key + audit:write; no client-supplied actor')
        q.open('ctl','svc','external','actor, system, roles, requestId, type, objectType, objectId, target, before, after' if d else 'actor, input, key')
        q.raw('note over svc,fact: whitelist → canonical JSON → payload hash;\n'+('AuditFact compact constructor truncates at to MILLIS.' if d else 'record truncates occurredAt to MILLIS; requestId maps CHAR(36).')+'\nPOST time, persisted TIMESTAMP(3), replay and [at,at] query agree.\ngroup REQUIRES_NEW first attempt')
        q.call('svc','repo','find' if d else 'one','requestId' if d else 'AuditLog.class, requestId predicate')
        q.raw('alt existing requestId')
        if d:q.call('svc','fact','requireReplay','proposed')
        else:q.call('svc','svc','same','existing, payload')
        q.raw('note over svc: same content → old id; different content → 400 conflict\nelse new requestId');q.call('svc','repo','append' if d else 'save','fact' if d else 'log')
        if not d:q.call('svc','repo','flush')
        q.raw('end\nend\nalt concurrent unique-key violation\nnote over svc: failed insert transaction rolled back BEFORE catch re-read\ngroup separate REQUIRES_NEW')
        q.call('svc','repo','find' if d else 'one','requestId' if d else 'AuditLog.class, requestId predicate')
        if d:q.call('svc','fact','requireReplay','proposed')
        else:q.call('svc','svc','same','existing, payload')
        q.raw('note over svc: missing existing → rethrow DB exception; existing content rechecked\nend\nelse committed first attempt\nnote over svc: canonical record is durable\nend\nnote over svc,ctl: 201 only after commit. Timeout retry uses original requestId; SQL failure is not 201.')
        end_use_case(q)
        # S06: current last-administrator policy is part of the same transaction.
        p={'ctl':'RoleController' if d else 'ManagementController','svc':'RoleApplicationService' if d else 'ManagementService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService','role':'Role',
           'repo':'AuthorizationRepository' if d else 'Store','audit':'AuditApplicationService' if d else 'AuditService'}
        if d:p.update({'integrity':'GrantIntegrityService','auditRepo':'AuditRepository'})
        if d:p['graph']='RoleHierarchy'
        q=Sequence('S06-'+mode+'-sequence',mode,p,'ctl','status' if d else handler(mode,'roleStatus'),'bearer, id, input' if d else 'actor, id, input')
        q.open('ctl','svc','status' if d else 'roleStatus','principal, id, enabled' if d else 'actor, id, status')
        q.raw('group same local transaction: business + integrity + version + SUCCESS audit')
        q.call('svc','repo' if d else 'auth','lockGrants' if d else 'lock')
        if d:
            q.call('svc','auth','require','principal, rbac:role:status, true');q.call('svc','repo','role','id')
            q.call('svc','auth','requireRoleManagement','actor, role');q.call('svc','role','changeStatus','enabled')
            q.raw('opt enabling');affected_grants(q);q.raw('end')
            q.call('svc','repo','saveRole','role');q.call('svc','integrity','requireAdministrator')
            q.raw('note over integrity: active identities + root department + role eligibility\n+ current graph/permissions → LastAdministratorPolicy.\nNo qualified effective SUPER_ADMIN → 409; outer transaction rolls back.')
            q.call('svc','repo','advanceVersion')
        else:
            q.raw('note over svc,auth: begin: rbac:role:status + root department\n→ captureUpper before mutation')
            q.call('svc','auth','protectRole','actor, role')
            q.raw('note over svc,role: managed Role.status = status; enable → changedUpper\nupper checks latent capability paths even across disabled nodes.\nfinish flushes → preserveAdministrator → increments GrantVersion\nGrantVersion Java Long id maps physical TINYINT, not BIGINT.')
        q.raw('alt audit persistence throws')
        q.open('svc','audit','local' if d else 'success','actor, system, ROLE_STATUS_CHANGED, target, SUCCESS, null' if d else 'actor, ROLE_STATUS, target')
        q.open('audit','auditRepo' if d else 'repo','append' if d else 'save','fact' if d else 'log')
        q.raw(('auditRepo' if d else 'repo')+' --> obj: persistence failure; rollback, no business success')
        q.close('auditRepo' if d else 'repo','audit');q.close('audit','svc')
        q.raw('else persisted in caller transaction')
        q.open('svc','audit','local' if d else 'success','actor, system, ROLE_STATUS_CHANGED, target, SUCCESS, null' if d else 'actor, ROLE_STATUS, target')
        q.call('audit','auditRepo' if d else 'repo','append' if d else 'save','fact' if d else 'log');q.close('audit','svc')
        q.raw('note over audit: DDD local derives actual actor identity-role snapshot before append.\nNo independent SUCCESS commit; timestamps have millisecond precision.\nend\nend\nnote over svc: Spring interceptor COMMIT after normal method return; failure blocks success response.\nnote over audit: denied is a separate REQUIRES_NEW DENIED event, not business SUCCESS.\nAOP method/log timing is neither audit_log nor proof of DB commit.')
        end_use_case(q)
    build_policy_detail()
    build_login_detail()
    build_simulation_detail()

def build_simulation_detail():
    # Shared-acceptance S06 target simulation is NOT UML-S06 local mutation/audit.
    p={'ctl':'AuthorizationController','rest':'RestSupport','auth':'AuthorizationApplicationService','directory':'DirectoryApplicationService','repo':'AuthorizationRepository'}
    q=Sequence('SIM-ddd-sequence','ddd',p,'ctl','explain','bearer, input')
    q.open('ctl','ctl','check','bearer, input, true');q.call('ctl','rest','principal','bearer')
    q.call('ctl','auth','require','principal, rbac:authz:invoke, false')
    q.raw('note over ctl: Validate UI permission vs API operationId input contract;\nchoose explicit target userId or actor.id().\nopt target differs from actor');q.call('ctl','directory','user','principal, targetUserId');q.raw('end')
    q.open('ctl','auth','unavailableReason','targetUserId, system');q.call('auth','repo','systemEnabled','system');q.call('auth','repo','identity','targetUserId')
    q.raw('alt missing target\nauth --> obj: 404 USER_NOT_FOUND; abort\nelse existing target\nnote over auth: non-ACTIVE → USER_DISABLED; otherwise disabled system → SYSTEM_DISABLED;\nactive user + enabled system → null\nend');q.close('auth','ctl','unavailable')
    q.raw('alt unavailable != null\nnote over ctl: empty effective; allowed=false; reason=unavailable;\nexplain trace=[ACTOR_AUTHENTICATED, unavailable]; fresh traceId.\nNo effective()/api() call for unavailable target.\nelse available target')
    q.call('ctl','auth','effective','targetUserId, system');q.raw('opt API decision');q.call('ctl','auth','api','targetUserId, system, operationId');q.raw('end\nnote over ctl: UI uses Effective.has; reason ALLOW or DENY;\nexplain trace records TOKEN_VALID, USER_SYSTEM_ACTIVE and requirement result\nend')
    q.raw('note over ctl: RestSupport.ok supplies HTTP envelope; target disabled is a DENY result,\nnot authentication failure of the requesting administrator.\nNo business mutation or SUCCESS audit occurs in this simulation.')
    q.close('ctl','ctl');q.finish()

def structured_calculation(q):
    q.raw('note over auth: calculate checks system enabled, loads roles/edges/permissions/grants')
    q.call('auth','model','where','Role.class, system predicate')
    q.raw('loop each valid direct root');q.call('auth','auth','walk','root, roles, edges, grants, path, effective, permissions, sources');q.raw('end')

def build_policy_detail():
    p={'auth':'AuthorizationApplicationService','reads':'AuthorizationReadTransaction','repo':'AuthorizationRepository','policy':'AuthorizationPolicy'}
    q=Sequence('S04-ddd-policy-sequence','ddd',p,'auth','api','userId, system, operationId')
    q.raw('alt actual outer transaction is read-only');q.open('auth','reads','fresh','query callback');q.call('reads','auth','stableApi','userId, system, operationId')
    q.raw('note over reads,repo: callback within REQUIRES_NEW READ_COMMITTED readOnly;\nfreshenRead clears JPA context per attempt; same bracket as below.')
    q.close('reads','auth');q.raw('else normal invocation');q.open('auth','auth','stableApi','userId, system, operationId')
    q.raw('loop at most 3 attempts');q.call('auth','repo','version');q.call('auth','repo','operation','system, operationId')
    q.raw('note over auth: unknown operation → 404 API_OPERATION_NOT_FOUND;\npublic/authenticated policy never bypasses target identity status.')
    q.call('auth','repo','identity','userId');q.raw('opt ACTIVE target');q.call('auth','auth','effective','userId, system');q.call('auth','repo','identity','userId')
    q.raw('opt target still ACTIVE');q.call('auth','policy','allows','operation, effective');q.raw('end\nend');q.call('auth','repo','version')
    q.raw('note over auth: unchanged version → return allowed; changed → retry\nend\nalt all attempts unstable\nauth --> obj: DomainException 503 AUTHORIZATION_UNSTABLE\nelse stable result\nnote over auth: inactive/missing target → allowed=false\nend')
    q.close('auth','auth');q.raw('end');q.finish()

def build_login_detail():
    p={'identity':'IdentityApplicationService','attempts':'LoginAttemptApplicationService','repo':'AuthorizationRepository','policy':'LoginAttemptPolicy','encoder':'CredentialEncoder','tokens':'TokenProvider','audit':'AuditApplicationService'}
    q=Sequence('LOGIN-ddd-sequence','ddd',p,'identity','login','username, password, system')
    q.open('identity','attempts','attempt','username, password, system');q.raw('group attempt @Transactional: locked persistent login state')
    q.call('attempts','repo','lockLogin','username');q.raw('alt no account\nnote over attempts: return null; no token\nelse account exists')
    q.call('attempts','policy','locked','lockedUntil, now');q.raw('opt ACTIVE and not locked');q.call('attempts','encoder','matches','password, storedHash');q.raw('end')
    q.call('attempts','policy','evaluate','failedCount, lockedUntil, active, matches, now')
    q.call('attempts','repo','saveLoginState','user.id(), result.failedCount(), result.lockedUntil()')
    q.raw('alt rejected');q.call('attempts','audit','local','user, system, LOGIN_DENIED, target, DENIED, INVALID_CREDENTIALS')
    q.raw('note over policy: fifth wrong credential locks for 900s;\nlocked attempt preserves count/until; expiry restarts count;\naccepted credentials reset count to zero.\nelse accepted')
    q.call('attempts','repo','systemEnabled','system');q.call('attempts','tokens','issue','subject, system, tokenVersion')
    q.call('attempts','audit','local','user, system, LOGIN, target, SUCCESS, null')
    q.raw('end\nend\nend\nnote over attempts,identity: attempt transaction COMMIT before returning null/token.\nDisabled system or persistence exception still rolls this transaction back.')
    q.close('attempts','identity');q.raw('alt null result\nidentity --> obj: DomainException 401 INVALID_CREDENTIALS\nelse token\nnote over identity: return token; HTTP layer owns response\nend');q.finish()
