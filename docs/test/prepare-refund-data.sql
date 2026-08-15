-- ============================================================
-- 退款模块测试数据准备
-- 用途：为退款下单接口的校验用例准备支付订单/退款单数据
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-refund-data.sql
-- ============================================================
-- 复用 Phase 1 已有凭证：MCH-TEST-001 / APP-TEST-001（均已启用，验签可用）
-- 退款渠道：wxpay（有 WxpayRefundService bean，支持退款）
-- 所有 INSERT IGNORE 保证幂等，重复执行不报错
-- ============================================================

-- ──── 001：正常退款（SUCCESS 订单，未退过款） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-SUCCESS-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-SUCCESS-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    0, 0, 0, '', NOW(), NOW());

-- ──── 006：已退 30，再退 80 超（字段校验） ────
-- 支付订单 refund_amount=30 + 一笔成功退款单 30（字段和 SUM 一致）
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-PARTIAL-006', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-PARTIAL-006',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    1, 30, 1, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-006-1', 'TEST-REFUND-PARTIAL-006', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-006-1', 'WX_NATIVE', 'wxpay', 100, 30, '测试退款', 2, NOW(), NOW());

-- ──── 007：在途退款（存在 state=1 ING 的退款单） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-ING-007', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-ING-007',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    0, 0, 0, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-007-1', 'TEST-REFUND-ING-007', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-007-1', 'WX_NATIVE', 'wxpay', 100, 30, '测试退款', 1, NOW(), NOW());

-- ──── 008：SUM 兜底（退款订单表 SUM=100，字段故意写成 50 制造不一致） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-DIVERGE-008', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-DIVERGE-008',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    1, 50, 1, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-008-1', 'TEST-REFUND-DIVERGE-008', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-008-1', 'WX_NATIVE', 'wxpay', 100, 50, '测试退款', 2, NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-008-2', 'TEST-REFUND-DIVERGE-008', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-008-2', 'WX_NATIVE', 'wxpay', 100, 50, '测试退款', 2, NOW(), NOW());

-- ──── 009：SUM 兜底（退款订单表 SUM=70，字段故意写成 20 制造不一致） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-DIVERGE-009', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-DIVERGE-009',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    1, 20, 1, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-009-1', 'TEST-REFUND-DIVERGE-009', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-009-1', 'WX_NATIVE', 'wxpay', 100, 70, '测试退款', 2, NOW(), NOW());

-- ──── 010：mchRefundNo 重复（已存在一笔同 mchRefundNo 的退款单） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-DUP-010', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-DUP-010',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    0, 0, 0, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-010-1', 'TEST-REFUND-DUP-010', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-DUP-010', 'WX_NATIVE', 'wxpay', 100, 30, '测试退款', 2, NOW(), NOW());

-- ──── 013：非 SUCCESS 状态订单（支付中/失败/关闭/已退款，各一条） ────
-- state=1 支付中
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-STATE-ING-013', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-STATE-ING-013',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 1,
    0, 0, 0, '', NOW(), NOW());

-- state=3 支付失败
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-STATE-FAIL-013', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-STATE-FAIL-013',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 3,
    0, 0, 0, '', NOW(), NOW());

-- state=6 订单关闭
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-STATE-CLOSED-013', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-STATE-CLOSED-013',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 6,
    0, 0, 0, '', NOW(), NOW());

-- state=5 已退款（全额退完，refund_state=2）
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-STATE-REFUND-013', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-STATE-REFUND-013',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 5,
    2, 100, 1, '', NOW(), NOW());

-- ──── 回调测试专用：一笔在途退款单（避免与 007 的 TEST-REF-RO-007-1 共享数据造成污染） ────
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    if_code, way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state,
    refund_state, refund_amount, refund_times, notify_url, created_at, updated_at)
VALUES ('TEST-REFUND-NOTICE-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-REF-MCH-NOTICE-001',
    'wxpay', 'WX_NATIVE', 100, 0.006000, 0, '退款测试商品', '退款测试', 2,
    0, 0, 0, '', NOW(), NOW());

INSERT IGNORE INTO t_refund_order (refund_order_id, pay_order_id, mch_no, app_id, mch_name, mch_type,
    mch_refund_no, way_code, if_code, pay_amount, refund_amount, refund_reason, state, created_at, updated_at)
VALUES ('TEST-REF-RO-NOTICE-1', 'TEST-REFUND-NOTICE-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1,
    'TEST-REF-MCH-REF-NOTICE-1', 'WX_NATIVE', 'wxpay', 100, 30, '测试退款', 1, NOW(), NOW());
