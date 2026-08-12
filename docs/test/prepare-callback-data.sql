-- ============================================================
-- 回调模块测试数据
-- 用途：为 9 条回调测试用例提供不同状态的订单
-- 执行方式：docker exec -i jeepay-mysql mysql -uroot -prootroot jeepaydb < prepare-callback-data.sql
-- ============================================================

-- state=1(ING) 用于测 CB-01 正常成功回调、CB-02 正常失败回调、CB-07 异常回调
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-CALLBACK-ING-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-CB-MCH-ORDER-ING-001',
    'WX_NATIVE', 100, 0.006000, 0, '回调测试商品-ING', '回调测试-支付中', 1, '', NOW(), NOW());

-- state=2(SUCCESS) 用于测 CB-03 重复通知幂等性
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, return_url, created_at, updated_at)
VALUES ('TEST-CALLBACK-SUCCESS-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-CB-MCH-ORDER-SUCCESS-001',
    'WX_NATIVE', 100, 0.006000, 0, '回调测试商品-SUCCESS', '回调测试-已支付', 2, '', 'https://shop.example.com/pay-result', NOW(), NOW());

-- state=3(FAIL) 用于测 CB-04 失败订单收到成功回调
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-CALLBACK-FAIL-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-CB-MCH-ORDER-FAIL-001',
    'WX_NATIVE', 100, 0.006000, 0, '回调测试商品-FAIL', '回调测试-已失败', 3, '', NOW(), NOW());

-- state=6(CLOSED) 用于测 CB-05 超时关闭后付款
INSERT IGNORE INTO t_pay_order (pay_order_id, mch_no, app_id, mch_name, mch_type, mch_order_no,
    way_code, amount, mch_fee_rate, mch_fee_amount, subject, body, state, notify_url, created_at, updated_at)
VALUES ('TEST-CALLBACK-CLOSED-001', 'MCH-TEST-001', 'APP-TEST-001', '测试商户', 1, 'TEST-CB-MCH-ORDER-CLOSED-001',
    'WX_NATIVE', 100, 0.006000, 0, '回调测试商品-CLOSED', '回调测试-已关闭', 6, '', NOW(), NOW());
