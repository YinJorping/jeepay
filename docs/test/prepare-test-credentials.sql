-- ============================================================
-- 测试凭证初始化（Phase 4 补齐）
-- 用途：落盘 ISV / 商户 / 应用 三层测试凭证，供 CI 与「一键初始化」复用
-- 背景：此前这三条凭证只在 test/测试账号.md 里以文档形式记录，未落成 SQL，
--       本地靠手动 docker exec 插入，CI 无法复现（init.sql 不含测试凭证）
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-test-credentials.sql
-- 全部 INSERT IGNORE 保证幂等，可重复执行
-- ============================================================

-- ──── 服务商 ────
INSERT IGNORE INTO t_isv_info (isv_no, isv_name, isv_short_name, state, created_at, updated_at)
VALUES ('ISV-TEST-001', '测试服务商', '测试服务商', 1, NOW(), NOW());

-- ──── 商户（通过 isv_no 关联服务商） ────
INSERT IGNORE INTO t_mch_info (mch_no, mch_name, mch_short_name, type, isv_no, state, created_at, updated_at)
VALUES ('MCH-TEST-001', '测试商户', '测试商户', 1, 'ISV-TEST-001', 1, NOW(), NOW());

-- ──── 应用（通过 mch_no 关联商户，appSecret 用于 API 验签） ────
-- appSecret 与测试代码中的 APP_SECRET 常量保持一致：test_app_secret_abc123
INSERT IGNORE INTO t_mch_app (app_id, app_name, mch_no, state, app_secret, created_at, updated_at)
VALUES ('APP-TEST-001', '测试应用', 'MCH-TEST-001', 1, 'test_app_secret_abc123', NOW(), NOW());
