package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.components.mq.model.PayOrderMchNotifyMQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 回调 + 通知链路测试（Phase 3.3）
 *
 * 测的是「对外」这条尾巴：渠道回调 → 订单状态更新 → JeePay 通知商户 notifyUrl。
 * 现有回调/退款用例的 notify_url 全为空串，payOrderNotify 首行即 return，本链路从未被触发。
 * 这里通过预置非空 notify_url 点亮它，并 mock IMQSender 阻断真实 RocketMQ 外呼。
 */
public class PayOrderMchNotifyTest extends PaySpringTestBase {

    private static final String ID_SUCCESS = "TEST-MCH-NOTIFY-001";
    private static final String ID_REPEAT = "TEST-MCH-NOTIFY-002";
    private static final String ID_FAIL = "TEST-MCH-NOTIFY-003";
    private static final String ID_EMPTY = "TEST-MCH-NOTIFY-004";

    /** 每个用例前复位：订单回到支付中 + 清空商户通知记录，保证用例独立 */
    @BeforeEach
    void resetTestData() {
        jdbcTemplate.update(
                "UPDATE t_pay_order SET state=1 WHERE pay_order_id IN (?,?,?,?)",
                ID_SUCCESS, ID_REPEAT, ID_FAIL, ID_EMPTY);
        jdbcTemplate.update(
                "DELETE FROM t_mch_notify_record WHERE order_id IN (?,?,?,?)",
                ID_SUCCESS, ID_REPEAT, ID_FAIL, ID_EMPTY);
    }

    /** 发起一次成功回调 */
    private void doSuccessCallback(String payOrderId) {
        mockParseParams(payOrderId);
        mockDoNoticeSuccess();
        given()
                .when()
                .post("/api/pay/notify/wxpay/" + payOrderId)
                .then()
                .statusCode(200);
    }

    /** 发起一次失败回调 */
    private void doFailCallback(String payOrderId) {
        mockParseParams(payOrderId);
        mockDoNoticeFail();
        given()
                .when()
                .post("/api/pay/notify/wxpay/" + payOrderId)
                .then()
                .statusCode(200);
    }

    private int notifyRecordCount(String orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_mch_notify_record WHERE order_id = ?",
                Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private int orderState(String orderId) {
        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id = ?",
                Integer.class, orderId);
        return state == null ? -1 : state;
    }

    /**
     * MCH-01 成功回调（ING→SUCCESS）：应生成 1 条通知记录
     * 记录 state=1(ING)、orderId=支付单ID、orderType=1、notifyUrl 带 sign，且 MQ 发出 1 次。
     * 风险理由：成功必须触发商户通知，这是完整链路的闭环。
     */
    @Test
    void testSuccessCallbackNotifiesMerchant() {
        doSuccessCallback(ID_SUCCESS);

        assertEquals(2, orderState(ID_SUCCESS), "订单状态应从 1(ING) 更新为 2(SUCCESS)");
        assertEquals(1, notifyRecordCount(ID_SUCCESS), "成功回调应生成 1 条商户通知记录");

        Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT state, order_id, order_type, notify_url FROM t_mch_notify_record WHERE order_id = ?",
                ID_SUCCESS);
        assertEquals((byte) 1, ((Number) record.get("state")).byteValue(), "通知记录初始状态应为 1(ING)");
        assertEquals(ID_SUCCESS, record.get("order_id"));
        assertEquals((byte) 1, ((Number) record.get("order_type")).byteValue(), "orderType 应为 1(支付订单)");
        String notifyUrl = (String) record.get("notify_url");
        assertNotNull(notifyUrl);
        assertTrue(notifyUrl.contains("sign="), "通知 URL 应带 sign 签名参数，实际：" + notifyUrl);

        verify(mqSender, times(1)).send(any(PayOrderMchNotifyMQ.class));
    }

    /**
     * MCH-02 重复成功回调（同单发两次）：通知记录仍只有 1 条
     * 风险理由：幂等——重复回调不能重复通知商户，否则商户重复发货/入账。
     */
    @Test
    void testRepeatCallbackNotifiesOnlyOnce() {
        doSuccessCallback(ID_REPEAT);
        doSuccessCallback(ID_REPEAT);

        assertEquals(1, notifyRecordCount(ID_REPEAT), "重复成功回调不应重复生成通知记录");
        verify(mqSender, times(1)).send(any(PayOrderMchNotifyMQ.class));
    }

    /**
     * MCH-03 失败回调（ING→FAIL）：不应生成任何通知记录
     * 风险理由：支付失败不能通知商户「成功」，否则商户误判订单状态。
     */
    @Test
    void testFailCallbackNotNotifies() {
        doFailCallback(ID_FAIL);

        assertEquals(3, orderState(ID_FAIL), "订单状态应从 1(ING) 更新为 3(FAIL)");
        assertEquals(0, notifyRecordCount(ID_FAIL), "失败回调不应生成商户通知记录");
        verify(mqSender, never()).send(any(PayOrderMchNotifyMQ.class));
    }

    /**
     * MCH-04 notifyUrl 为空，成功回调：订单 1→2，但不生成通知记录
     * 风险理由：空地址边界——不应发起无意义的外呼。
     */
    @Test
    void testEmptyNotifyUrlNotNotifies() {
        doSuccessCallback(ID_EMPTY);

        assertEquals(2, orderState(ID_EMPTY), "订单状态应正常更新为 2(SUCCESS)");
        assertEquals(0, notifyRecordCount(ID_EMPTY), "notifyUrl 为空时不应生成商户通知记录");
        verify(mqSender, never()).send(any(PayOrderMchNotifyMQ.class));
    }
}
