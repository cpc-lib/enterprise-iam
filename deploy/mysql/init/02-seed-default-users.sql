-- ==============================================================================
-- Enterprise IAM Platform 预置种子账号与租户初始化脚本
-- 1. 超级租户管理员 (Super Tenant Admin)
-- 2. 普通租户管理员 (Regular Tenant Admin)
-- 3. 普通租户业务账号 (Regular Tenant User)
-- 默认初始密码均为: Admin@123456
-- ==============================================================================

-- -----------------------------------------------------------------------------
-- 1. 租户初始化 (iam_identity.iam_tenant)
-- -----------------------------------------------------------------------------
INSERT INTO iam_identity.iam_tenant (id, tenant_code, tenant_name, status, delete_marker, version)
VALUES
(10001, 'system', '系统运营平台', 'ACTIVE', 0, 0),
(20001, 'tenant-corp', '企业协同示范租户', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
tenant_name = VALUES(tenant_name),
status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 2. 用户主数据 (iam_identity.iam_user)
-- -----------------------------------------------------------------------------
INSERT INTO iam_identity.iam_user (id, tenant_id, username, display_name, email, phone, status, delete_marker, version)
VALUES
-- 超级租户管理员
(100001, 10001, 'superadmin', '超级租户管理员', 'superadmin@enterprise.com', '13800000001', 'ACTIVE', 0, 0),
-- 普通租户管理员
(200001, 20001, 'tenant_admin', '企业租户管理员', 'admin@corp.com', '13800000002', 'ACTIVE', 0, 0),
-- 普通租户业务账号
(200002, 20001, 'staff_user', '普通业务专员', 'staff@corp.com', '13800000003', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
display_name = VALUES(display_name),
email = VALUES(email),
phone = VALUES(phone),
status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 3. 登录凭证身份映射 (iam_identity.iam_user_identity)
-- 支持 USERNAME / EMAIL / PHONE 三合一登录
-- -----------------------------------------------------------------------------
INSERT INTO iam_identity.iam_user_identity (id, tenant_id, user_id, identity_type, identity_key, credential_ref, status, delete_marker, version)
VALUES
-- superadmin
(1000001, 10001, 100001, 'USERNAME', 'superadmin', 100001, 'ACTIVE', 0, 0),
(1000002, 10001, 100001, 'EMAIL', 'superadmin@enterprise.com', 100001, 'ACTIVE', 0, 0),
(1000003, 10001, 100001, 'PHONE', '13800000001', 100001, 'ACTIVE', 0, 0),
-- tenant_admin
(2000001, 20001, 200001, 'USERNAME', 'tenant_admin', 200001, 'ACTIVE', 0, 0),
(2000002, 20001, 200001, 'EMAIL', 'admin@corp.com', 200001, 'ACTIVE', 0, 0),
(2000003, 20001, 200001, 'PHONE', '13800000002', 200001, 'ACTIVE', 0, 0),
-- staff_user
(2000004, 20001, 200002, 'USERNAME', 'staff_user', 200002, 'ACTIVE', 0, 0),
(2000005, 20001, 200002, 'EMAIL', 'staff@corp.com', 200002, 'ACTIVE', 0, 0),
(2000006, 20001, 200002, 'PHONE', '13800000003', 200002, 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 4. 角色定义 (iam_identity.iam_role)
-- -----------------------------------------------------------------------------
INSERT INTO iam_identity.iam_role (id, tenant_id, role_code, role_name, description, priority, status, delete_marker, version)
VALUES
(2001, 20001, 'TENANT_ADMIN', '租户管理员', '企业协同租户内部最高治理权限与管理角色', 100, 'ACTIVE', 0, 0),
(2002, 20001, 'REGULAR_USER', '普通员工角色', '租户内部基础日常业务流转角色', 10, 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
role_name = VALUES(role_name),
status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 5. 用户与角色绑定 (iam_identity.iam_user_role)
-- -----------------------------------------------------------------------------
INSERT INTO iam_identity.iam_user_role (id, tenant_id, user_id, role_id, status, active_slot, start_time, expire_time, version)
VALUES
(20001, 20001, 200001, 2001, 'ACTIVE', 1, NOW(3), NULL, 0),
(20002, 20001, 200002, 2002, 'ACTIVE', 1, NOW(3), NULL, 0)
ON DUPLICATE KEY UPDATE
status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 6. 认证密码凭证 (iam_auth.iam_password_credential)
-- 初始密码 Admin@123456 (Argon2id PHC)
-- -----------------------------------------------------------------------------
INSERT INTO iam_auth.iam_password_credential (id, tenant_id, user_id, password_phc, password_version, status, changed_at, version)
VALUES
(100001, 10001, 100001, '$argon2id$v=19$m=19456,t=2,p=1$Lg3tXUdTVmPdH9XQPAz/XA$POh2PLGl/CuWcWsu3+9QFiUoWJ1ZL8HBvQ0nbARzO9k', 1, 'ACTIVE', NOW(3), 0),
(200001, 20001, 200001, '$argon2id$v=19$m=19456,t=2,p=1$Lg3tXUdTVmPdH9XQPAz/XA$POh2PLGl/CuWcWsu3+9QFiUoWJ1ZL8HBvQ0nbARzO9k', 1, 'ACTIVE', NOW(3), 0),
(200002, 20001, 200002, '$argon2id$v=19$m=19456,t=2,p=1$Lg3tXUdTVmPdH9XQPAz/XA$POh2PLGl/CuWcWsu3+9QFiUoWJ1ZL8HBvQ0nbARzO9k', 1, 'ACTIVE', NOW(3), 0)
ON DUPLICATE KEY UPDATE
password_phc = VALUES(password_phc),
status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 7. 用户安全状态 (iam_auth.iam_user_security_state)
-- -----------------------------------------------------------------------------
INSERT INTO iam_auth.iam_user_security_state (id, tenant_id, user_id, token_version, password_version, login_failure_count, failure_window_start_at, lock_until, last_password_change_at, version)
VALUES
(100001, 10001, 100001, 1, 1, 0, NULL, NULL, NOW(3), 0),
(200001, 20001, 200001, 1, 1, 0, NULL, NULL, NOW(3), 0),
(200002, 20001, 200002, 1, 1, 0, NULL, NULL, NOW(3), 0)
ON DUPLICATE KEY UPDATE
login_failure_count = 0,
lock_until = NULL;

-- -----------------------------------------------------------------------------
-- 8. 确保 iam_auth 视图与跨库一致性
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW iam_auth.iam_tenant AS SELECT * FROM iam_identity.iam_tenant;
CREATE OR REPLACE VIEW iam_auth.iam_user AS SELECT * FROM iam_identity.iam_user;
CREATE OR REPLACE VIEW iam_auth.iam_user_identity AS SELECT * FROM iam_identity.iam_user_identity;
