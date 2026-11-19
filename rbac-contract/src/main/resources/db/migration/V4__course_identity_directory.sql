-- Register the new identity provider queries and public course login.
START TRANSACTION;

DELETE m FROM sys_ui_api_mapping m
JOIN sys_api_operation a ON a.id=m.api_operation_id
JOIN sys_system s ON s.id=a.system_id AND s.code='course-project-management'
WHERE a.operation_id='lookupIdentityUser' AND a.path_template='/identity/users/{userId}';

DELETE a FROM sys_api_operation a
JOIN sys_system s ON s.id=a.system_id AND s.code='course-project-management'
WHERE a.operation_id='lookupIdentityUser' AND a.path_template='/identity/users/{userId}';

INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, required_role, access_policy)
SELECT id, 'listIdentityDepartments', 'GET', '/auth/departments', 'admin', 'AUTHENTICATED' FROM sys_system WHERE code='rbac-center';

INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, required_role, access_policy)
SELECT id, 'listIdentityDepartmentUsers', 'GET', '/auth/departments/{departmentId}/users', 'admin', 'AUTHENTICATED' FROM sys_system WHERE code='rbac-center';

INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, required_role, access_policy)
SELECT id, 'lookupIdentityUser', 'GET', '/auth/users/{userId}', 'admin', 'AUTHENTICATED' FROM sys_system WHERE code='rbac-center';

INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, access_policy)
SELECT id, 'courseLogin', 'POST', '/login', 'PUBLIC' FROM sys_system WHERE code='course-project-management';

UPDATE sys_grant_version SET version=version+1 WHERE id=1;

COMMIT;
