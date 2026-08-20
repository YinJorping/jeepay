-- ============================================================
-- 认证模块测试数据准备
-- 用途：为商户端登录/鉴权用例准备 商户、用户、认证凭证 三表数据
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-auth-data.sql
-- 统一密码：Test@123456
-- BCrypt 哈希：$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C
-- 幂等策略：t_sys_user / t_mch_info 有主键或唯一键，INSERT IGNORE 即可去重；
--           t_sys_user_auth 仅自增主键，须先 DELETE 测试行再 INSERT（见下方说明）
-- ============================================================

-- ──── 商户：正常 + 禁用 ────
INSERT IGNORE INTO t_mch_info (mch_no, mch_name, mch_short_name, type, state, created_at, updated_at)
VALUES
('MCH-AUTH-001', '认证测试商户-正常', '认证正常', 1, 1, NOW(), NOW()),
('MCH-AUTH-002', '认证测试商户-禁用', '认证禁用', 1, 0, NOW(), NOW());

-- ──── 用户（6 个，sys_user_id 手动指定避免与自增冲突） ────
-- 900001 正常超管 → 登录成功用例（超管跳过菜单校验）
INSERT IGNORE INTO t_sys_user (sys_user_id, login_username, realname, telphone, is_admin, state, sys_type, belong_info_id, created_at, updated_at)
VALUES (900001, 'authadmin', '认证超管', '13900000001', 1, 1, 'MCH', 'MCH-AUTH-001', NOW(), NOW());

-- 900002 禁用用户（state=0）→ 用户禁用用例
INSERT IGNORE INTO t_sys_user (sys_user_id, login_username, realname, telphone, is_admin, state, sys_type, belong_info_id, created_at, updated_at)
VALUES (900002, 'authdisabled', '禁用用户', '13900000002', 1, 0, 'MCH', 'MCH-AUTH-001', NOW(), NOW());

-- 900003 禁用商户下的用户（belong MCH-AUTH-002）→ 商户禁用用例
INSERT IGNORE INTO t_sys_user (sys_user_id, login_username, realname, telphone, is_admin, state, sys_type, belong_info_id, created_at, updated_at)
VALUES (900003, 'authmchdisabled', '禁用商户用户', '13900000003', 1, 1, 'MCH', 'MCH-AUTH-002', NOW(), NOW());

-- 900004 商户不存在的用户（belong_info_id 指向不存在商户）→ 商户为空用例
INSERT IGNORE INTO t_sys_user (sys_user_id, login_username, realname, telphone, is_admin, state, sys_type, belong_info_id, created_at, updated_at)
VALUES (900004, 'authnomch', '无商户用户', '13900000004', 1, 1, 'MCH', 'MCH-NOT-EXIST', NOW(), NOW());

-- 900005 无菜单普通用户（is_admin=0 且未分配菜单）→ 无菜单权限用例
INSERT IGNORE INTO t_sys_user (sys_user_id, login_username, realname, telphone, is_admin, state, sys_type, belong_info_id, created_at, updated_at)
VALUES (900005, 'authnomenu', '无菜单用户', '13900000005', 0, 1, 'MCH', 'MCH-AUTH-001', NOW(), NOW());

-- 900006 手机号登录用户 → 手机号登录用例
INSERT IGNORE INTO t_sys_user (sys_user_id, login_username, realname, telphone, is_admin, state, sys_type, belong_info_id, created_at, updated_at)
VALUES (900006, 'authtel', '手机号用户', '13800138000', 1, 1, 'MCH', 'MCH-AUTH-001', NOW(), NOW());

-- ──── 认证凭证（密码统一 Test@123456，credential 为 BCrypt 哈希） ────
-- 900001~900005 用户名登录 identity_type=1；900006 手机号登录 identity_type=2
-- 注意：t_sys_user_auth 主键是自增 auth_id，没有 (identifier,identity_type,sys_type) 唯一键，
--       单纯 INSERT IGNORE 无法去重，重复执行会累积重复行，导致 selectByLogin 的 selectOne 抛
--       TooManyResultsException（一次会话中重复加载 seed 即复现）。故先按测试 user_id 清空再插入。
DELETE FROM t_sys_user_auth WHERE user_id IN (900001, 900002, 900003, 900004, 900005, 900006) AND sys_type = 'MCH';
INSERT IGNORE INTO t_sys_user_auth (user_id, identity_type, identifier, credential, salt, sys_type)
VALUES
(900001, 1, 'authadmin', '$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C', '', 'MCH'),
(900002, 1, 'authdisabled', '$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C', '', 'MCH'),
(900003, 1, 'authmchdisabled', '$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C', '', 'MCH'),
(900004, 1, 'authnomch', '$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C', '', 'MCH'),
(900005, 1, 'authnomenu', '$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C', '', 'MCH'),
(900006, 2, '13800138000', '$2a$10$sx54WiPjfwaJdra.RmUoVekzvr6TRDVQCrjWg65QBC0t1DArKJy/C', '', 'MCH');
