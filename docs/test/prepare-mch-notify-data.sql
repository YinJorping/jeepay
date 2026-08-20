-- ============================================================
-- 回调 + 通知链路（Phase 3.3）测试数据
-- 用途：为 MCH-01 ~ MCH-04 四条用例预置「带非空 notify_url」的支付中订单
-- 关键：区别于现有回调/退款数据（notify_url 全空串，payOrderNotify 被短路），
--       这里通知地址非空，点亮「订单状态更新后 → 通知商户」这条对外尾巴
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-mch-notify-data.sql
-- 复用凭证：MCH-TEST-001 / APP-TEST-001（均已启用，appSecret 可用）
-- 全部 INSERT IGNORE 保证幂等；测试类 @BeforeEach 会显式复位 state 并清理 t_mch_notify_record
-- ============================================================

-- ──── MCH-01：成功回调 → 应生成 1 条通知记录（state=1 ING） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-MCH-NOTIFY-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-MCHN-MCHNO-001',
    'WX_NATIVE', 100, 0.006000, 0, '商户通知-成功', '商户通知-成功', 1, 'http://merchant.example.com/notify/pay', NOW(), NOW());

-- ──── MCH-02：重复成功回调 → 通知记录仍应只有 1 条 ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-MCH-NOTIFY-002', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-MCHN-MCHNO-002',
    'WX_NATIVE', 100, 0.006000, 0, '商户通知-重复', '商户通知-重复', 1, 'http://merchant.example.com/notify/pay', NOW(), NOW());

-- ──── MCH-03：失败回调 → 不应生成通知记录（带非空 notify_url 验证失败不通知） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-MCH-NOTIFY-003', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-MCHN-MCHNO-003',
    'WX_NATIVE', 100, 0.006000, 0, '商户通知-失败', '商户通知-失败', 1, 'http://merchant.example.com/notify/pay', NOW(), NOW());

-- ──── MCH-04：notify_url 为空，成功回调 → 订单 1→2 但不生成通知记录 ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-MCH-NOTIFY-004', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-MCHN-MCHNO-004',
    'WX_NATIVE', 100, 0.006000, 0, '商户通知-空地址', '商户通知-空地址', 1, '', NOW(), NOW());
