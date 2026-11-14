-- RBAC1 EMPTY DEVELOPMENT DATABASE ONLY. Never baseline/migrate an existing production database.
SET time_zone = '+00:00';

CREATE TABLE sys_system (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK(status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_department (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT NULL,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  path VARCHAR(1024) NOT NULL,
  status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(parent_id) REFERENCES sys_department(id),
  CHECK(status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  subject VARCHAR(128) NOT NULL UNIQUE,
  username VARCHAR(128) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(128) NOT NULL,
  no VARCHAR(64) NULL,
  phone VARCHAR(32) NULL,
  department_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  token_version BIGINT NOT NULL DEFAULT 0,
  must_change_password BOOLEAN NOT NULL DEFAULT true,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMP(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY(department_id) REFERENCES sys_department(id),
  CHECK(status IN ('ACTIVE','DISABLED','DELETED')),
  CHECK(token_version>=0 AND failed_login_count>=0 AND version>=0),
  INDEX ix_user_department_status(department_id,status,id),
  INDEX ix_user_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci AUTO_INCREMENT=10000;

CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  system_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL,
  builtin BOOLEAN NOT NULL DEFAULT false,
  identity_role VARCHAR(16) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(system_id) REFERENCES sys_system(id),
  UNIQUE(system_id,code),
  CHECK(status IN ('ENABLED','DISABLED')),
  CHECK(identity_role IS NULL OR identity_role IN ('student','teacher','admin'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  system_id BIGINT NOT NULL,
  code VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  kind VARCHAR(8) NOT NULL,
  status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(system_id) REFERENCES sys_system(id),
  UNIQUE(system_id,kind,code),
  CHECK(kind IN ('UI','API')),
  CHECK(status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  FOREIGN KEY(role_id) REFERENCES sys_role(id),
  FOREIGN KEY(permission_id) REFERENCES sys_permission(id),
  UNIQUE(role_id,permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  assigned_by BIGINT NULL,
  assigned_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(role_id) REFERENCES sys_role(id),
  FOREIGN KEY(assigned_by) REFERENCES sys_user(id),
  UNIQUE(user_id,role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_department_allowed_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  department_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  FOREIGN KEY(department_id) REFERENCES sys_department(id),
  FOREIGN KEY(role_id) REFERENCES sys_role(id),
  UNIQUE(department_id,role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_hierarchy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  senior_role_id BIGINT NOT NULL,
  junior_role_id BIGINT NOT NULL,
  FOREIGN KEY(senior_role_id) REFERENCES sys_role(id),
  FOREIGN KEY(junior_role_id) REFERENCES sys_role(id),
  UNIQUE(senior_role_id,junior_role_id),
  CHECK(senior_role_id<>junior_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_ui_resource (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  system_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  type VARCHAR(16) NOT NULL,
  code VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  route VARCHAR(255) NULL,
  component VARCHAR(64) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL,
  permission_id BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(system_id) REFERENCES sys_system(id),
  FOREIGN KEY(parent_id) REFERENCES sys_ui_resource(id),
  FOREIGN KEY(permission_id) REFERENCES sys_permission(id),
  UNIQUE(system_id,code),
  CHECK(type IN ('MENU','BUTTON')),
  CHECK(status IN ('ENABLED','DISABLED')),
  INDEX ix_ui_tree(system_id,parent_id,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_api_operation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  system_id BIGINT NOT NULL,
  operation_id VARCHAR(128) NOT NULL,
  method VARCHAR(16) NOT NULL,
  path_template VARCHAR(500) NOT NULL,
  permission_id BIGINT NULL,
  required_role VARCHAR(64) NULL,
  access_policy VARCHAR(32) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(system_id) REFERENCES sys_system(id),
  FOREIGN KEY(permission_id) REFERENCES sys_permission(id),
  UNIQUE(system_id,operation_id),
  UNIQUE(system_id,method,path_template),
  CHECK(access_policy IN ('PUBLIC','AUTHENTICATED','PROTECTED')),
  CHECK(method IN ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')),
  CHECK(access_policy<>'PROTECTED' OR permission_id IS NOT NULL OR required_role IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_ui_api_mapping (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ui_resource_id BIGINT NOT NULL,
  api_operation_id BIGINT NOT NULL,
  FOREIGN KEY(ui_resource_id) REFERENCES sys_ui_resource(id),
  FOREIGN KEY(api_operation_id) REFERENCES sys_api_operation(id),
  UNIQUE(ui_resource_id,api_operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_token_revocation (
  jti VARCHAR(128) PRIMARY KEY,
  subject VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_revocation_expiry(expires_at),
  INDEX ix_revocation_subject(subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_grant_version (
  id TINYINT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK(id=1),
  CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id CHAR(36) NOT NULL UNIQUE,
  source_system_code VARCHAR(64) NOT NULL,
  origin VARCHAR(16) NOT NULL,
  actor_id BIGINT NULL,
  actor_subject VARCHAR(128) NULL,
  actor_name VARCHAR(128) NULL,
  actor_roles_json JSON NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  object_type VARCHAR(64) NULL,
  object_id VARCHAR(128) NULL,
  target VARCHAR(500) NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  result VARCHAR(16) NOT NULL,
  reason VARCHAR(255) NULL,
  trace_id VARCHAR(64) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CHECK(origin IN ('COURSE','INTERNAL')),
  CHECK(result IN ('SUCCESS','DENIED','FAILURE')),
  INDEX ix_audit_system_time(source_system_code,occurred_at,id),
  INDEX ix_audit_system_actor(source_system_code,actor_id,occurred_at,id),
  INDEX ix_audit_system_object(source_system_code,object_type,object_id,occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
