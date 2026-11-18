-- Separate role status from the role edit capability without changing V1/V2.
START TRANSACTION;

UPDATE sys_grant_version SET version=version+1 WHERE id=1;

INSERT INTO sys_permission (system_id, code, name, kind, status)
SELECT id, 'rbac:role:status', '启停角色', 'API', 'ENABLED'
FROM sys_system WHERE code='rbac-center';

UPDATE sys_api_operation a
JOIN sys_system s ON s.id=a.system_id AND s.code='rbac-center'
JOIN sys_permission p ON p.system_id=s.id AND p.kind='API' AND p.code='rbac:role:status'
SET a.permission_id=p.id, a.version=a.version+1
WHERE a.operation_id='setRoleStatus';

-- Keep the existing built-in administrators authorized when they had the old capability.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, new_permission.id
FROM sys_role r
JOIN sys_system s ON s.id=r.system_id AND s.code='rbac-center'
JOIN sys_role_permission rp ON rp.role_id=r.id
JOIN sys_permission old_permission ON old_permission.id=rp.permission_id
    AND old_permission.system_id=s.id AND old_permission.kind='API'
    AND old_permission.code='rbac:role:edit' AND old_permission.status='ENABLED'
JOIN sys_permission new_permission ON new_permission.system_id=s.id
    AND new_permission.kind='API' AND new_permission.code='rbac:role:status'
WHERE r.builtin=TRUE AND r.code IN ('SUPER_ADMIN','RBAC_ADMIN');

COMMIT;
