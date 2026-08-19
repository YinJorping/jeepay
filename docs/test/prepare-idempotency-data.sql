-- ============================================================
-- 幂等性（Phase 3.2）测试数据
-- 用途：为 IDEM-01 ~ IDEM-04 四条用例预置订单/退款单
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-idempotency-data.sql
-- 复用凭证：MCH-TEST-001 / APP-TEST-001（均已启用，验签可用）
-- 全部 INSERT IGNORE 保证幂等；测试类 @BeforeEach 会显式 UPDATE 复位
-- ============================================================

-- ──── IDEM-01：重复统一下单（state=0 生成态，mchOrderNo 已存在） ────
-- count 去重只看 mch_no + mch_order_no，不创建新单
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-IDEM-PAY-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-IDEM-MCHNO-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '幂等-重复下单', '幂等-重复下单', 0,
    0, 0, 0, '', NOW(), NOW());

-- ──── IDEM-02：重复关单（state=1 支付中，关单成功后→6，再次关单应被拒） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-IDEM-PAY-002', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-IDEM-MCHNO-002',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '幂等-重复关单', '幂等-重复关单', 1,
    0, 0, 0, '', NOW(), NOW());

-- ──── IDEM-03：重复退款回调（支付单 state=2 + 退款单 state=1 在途，退款 30） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-IDEM-PAY-003', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-IDEM-MCHNO-003',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '幂等-重复退款回调', '幂等-重复退款回调', 2,
    0, 0, 0, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-IDEM-RO-003', 'TEST-IDEM-PAY-003', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-IDEM-REFNO-003', 'WX_NATIVE', 'wxpay', 100, 30, '幂等-重复退款回调', 1, NOW(), NOW());

-- ──── IDEM-04：重复退款下单（支付单 state=2 + 已成功退款单，mchRefundNo 已存在） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-IDEM-PAY-004', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-IDEM-MCHNO-004',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '幂等-重复退款下单', '幂等-重复退款下单', 2,
    0, 0, 0, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-IDEM-RO-004', 'TEST-IDEM-PAY-004', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-IDEM-REFNO-001', 'WX_NATIVE', 'wxpay', 100, 30, '幂等-重复退款下单', 2, NOW(), NOW());
