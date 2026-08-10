-- ============================================================
-- 支付模块测试数据准备
-- 用途：解除 5 条搁置用例中的 4 条（PAY-016/017/018/001）
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -proot jeepaydb < prepare-test-data.sql
-- ============================================================

-- ──── PAY-016：商户状态不可用 ────
-- 插入一个 state=0 的商户，及其专属应用
INSERT IGNORE INTO t_mch_info (mch_no, mch_name, mch_short_name, type, isv_no, state, created_at, updated_at)
VALUES ('MCH-DISABLED', '已禁用商户', '已禁用商户', 1, 'ISV-TEST-001', 0, NOW(), NOW());

INSERT IGNORE INTO t_mch_app (app_id, app_name, mch_no, state, app_secret, created_at, updated_at)
VALUES ('APP-FOR-DISABLED', '禁用商户的应用', 'MCH-DISABLED', 1, 'disabled_secret_999', NOW(), NOW());

-- ──── PAY-017：应用状态不可用 ────
-- 插入一个 state=0 的应用，属于 MCH-TEST-001
INSERT IGNORE INTO t_mch_app (app_id, app_name, mch_no, state, app_secret, created_at, updated_at)
VALUES ('APP-DISABLED', '已禁用应用', 'MCH-TEST-001', 0, 'disabled_app_secret_123', NOW(), NOW());

-- ──── PAY-018：appId与商户号不匹配 ────
-- 经分析：ApiController 第 89 行的检查是死代码（queryMchApp 已按 mchNo 过滤，app 必然属于该商户）
-- 标记为废弃，不再准备测试数据

-- ──── PAY-001：正常下单（微信扫码支付通道） ────
-- 注意：支付通道参数使用假数据，能通过前端校验但实际调用微信 API 时会失败
-- 因此 PAY-001 预期结果调整为通道级错误而非 code=0
INSERT IGNORE INTO t_mch_pay_passage (mch_no, app_id, if_code, way_code, rate, state, created_at, updated_at)
VALUES ('MCH-TEST-001', 'APP-TEST-001', 'wxpay', 'WX_NATIVE', 0.006000, 1, NOW(), NOW());

INSERT IGNORE INTO t_pay_interface_config (info_type, info_id, if_code, if_params, if_rate, state, created_at, updated_at)
VALUES (3, 'APP-TEST-001', 'wxpay', '{"mchId":"test","appId":"test","appSecret":"test","apiVersion":"V3","key":"test"}', 0.006000, 1, NOW(), NOW());

-- ──── 测试订单数据（方案2：SQL 造订单绕过支付通道） ────
-- state=0(订单生成)  用于测关闭订单
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-PAY-INIT-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-MCH-ORDER-INIT-001',
    'WX_NATIVE', 100, 0.006000, 0, '测试商品', '测试商品描述', 0, '', NOW(), NOW());

-- state=2(支付成功)  用于测查询订单
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-PAY-SUCCESS-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-MCH-ORDER-SUCCESS-001',
    'WX_NATIVE', 100, 0.006000, 0, '测试商品', '测试商品描述', 2, '', NOW(), NOW());
