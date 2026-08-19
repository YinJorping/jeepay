-- ============================================================
-- 状态机完整性（Phase 3.1）测试数据
-- 用途：为 SM-01 ~ SM-06 六条用例预置不同状态的支付订单
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-state-machine-data.sql
-- 复用凭证：MCH-TEST-001 / APP-TEST-001（均已启用，验签可用）
-- 全部 INSERT IGNORE 保证幂等；测试类 @BeforeEach 会显式 UPDATE 复位（不复用 INSERT IGNORE 的复位语义）
-- ============================================================

-- ──── SM-01 / SM-04：生成态订单（state=0） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-SM-INIT-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-SM-MCH-INIT-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '状态机-生成态', '状态机-生成态', 0,
    0, 0, 0, '', NOW(), NOW());

-- ──── SM-02：支付中订单（state=1） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-SM-ING-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-SM-MCH-ING-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '状态机-支付中', '状态机-支付中', 1,
    0, 0, 0, '', NOW(), NOW());

-- ──── SM-03：全额退款订单（state=2，amount=100，未退过款） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-SM-FULLREFUND-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-SM-MCH-FULLREFUND-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '状态机-全额退款', '状态机-全额退款', 2,
    0, 0, 0, '', NOW(), NOW());

-- ──── SM-05：已全额退款订单（state=5，refund_state=2） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-SM-REFUND-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-SM-MCH-REFUND-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '状态机-已退款', '状态机-已退款', 5,
    2, 100, 1, '', NOW(), NOW());

-- ──── SM-06：支付成功订单（state=2），用于关单被拒 ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-SM-SUCCESS-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-SM-MCH-SUCCESS-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '状态机-已支付', '状态机-已支付', 2,
    0, 0, 0, '', NOW(), NOW());
