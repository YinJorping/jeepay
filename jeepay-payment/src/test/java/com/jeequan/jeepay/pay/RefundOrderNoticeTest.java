package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.components.mq.model.PayOrderMchNotifyMQ;
import com.jeequan.jeepay.core.entity.RefundOrder;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款渠道回调测试（/api/refund/notify/{ifCode}）
 *
 * 复用支付回调模块的模式：@SpringBootTest + @MockBean mock 渠道回调服务，
 * 验证退款单状态机 ING(1) → SUCCESS(2) / FAIL(3)
 */
public class RefundOrderNoticeTest extends PaySpringTestBase {

    private static final String REFUND_ORDER_ID = "TEST-REF-RO-NOTICE-1";
    private static final String PAY_ORDER_ID = "TEST-REFUND-NOTICE-001";

    @BeforeEach
    void resetTestData() {
        // 重置退款单为在途(1)、notify_url 复位为空、清理通知记录：
        // 保证 3 条用例互相隔离（不改变原有用例语义——原数据 notify_url 本就是空串）
        jdbcTemplate.update(
                "UPDATE t_refund_order SET state=1, notify_url='' WHERE refund_order_id=?", REFUND_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM t_mch_notify_record WHERE order_id=?", REFUND_ORDER_ID);
        // 支付单退款字段归零，保证 CAS 守卫可重复触发
        jdbcTemplate.update(
                "UPDATE t_pay_order SET refund_amount=0, refund_state=0, refund_times=0, state=2 WHERE pay_order_id=?",
                PAY_ORDER_ID);
    }

    private void mockRefundParseParams(String refundOrderId) {
        MutablePair<String, Object> pair = new MutablePair<>();
        pair.setLeft(refundOrderId);
        pair.setRight("mock params");

        when(wxpayChannelRefundNoticeService.parseParams(
                any(HttpServletRequest.class), any(String.class), any()))
                .thenReturn(pair);
    }

    private ChannelRetMsg buildRetMsg(ChannelRetMsg.ChannelState state) {
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(state);
        ret.setChannelOrderId("mock-channel-refund-id");
        ret.setResponseEntity(ResponseEntity.ok("success"));
        return ret;
    }

    /**
     * 成功回调：在途退款单收到 CONFIRM_SUCCESS，状态 1(ING) → 2(SUCCESS)，支付单退款额累加
     */
    @Test
    void testRefundCallbackSuccess() {
        mockRefundParseParams(REFUND_ORDER_ID);
        when(wxpayChannelRefundNoticeService.doNotice(
                any(HttpServletRequest.class), any(), any(RefundOrder.class),
                any(MchAppConfigContext.class), any()))
                .thenReturn(buildRetMsg(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS));

        given()
                .when()
                .post("/api/refund/notify/wxpay/" + REFUND_ORDER_ID)
                .then()
                .statusCode(200);

        Integer refundState = jdbcTemplate.queryForObject(
                "SELECT state FROM t_refund_order WHERE refund_order_id=?",
                Integer.class, REFUND_ORDER_ID);
        assertEquals(2, refundState, "退款单状态应从 1(ING) 更新为 2(SUCCESS)");

        Long payOrderRefundAmount = jdbcTemplate.queryForObject(
                "SELECT refund_amount FROM t_pay_order WHERE pay_order_id=?",
                Long.class, PAY_ORDER_ID);
        assertEquals(30L, payOrderRefundAmount, "支付单 refund_amount 应累加 30");
    }

    /**
     * 失败回调：在途退款单收到 CONFIRM_FAIL，状态 1(ING) → 3(FAIL)，支付单退款额不变
     */
    @Test
    void testRefundCallbackFail() {
        mockRefundParseParams(REFUND_ORDER_ID);
        when(wxpayChannelRefundNoticeService.doNotice(
                any(HttpServletRequest.class), any(), any(RefundOrder.class),
                any(MchAppConfigContext.class), any()))
                .thenReturn(buildRetMsg(ChannelRetMsg.ChannelState.CONFIRM_FAIL));

        given()
                .when()
                .post("/api/refund/notify/wxpay/" + REFUND_ORDER_ID)
                .then()
                .statusCode(200);

        Integer refundState = jdbcTemplate.queryForObject(
                "SELECT state FROM t_refund_order WHERE refund_order_id=?",
                Integer.class, REFUND_ORDER_ID);
        assertEquals(3, refundState, "退款单状态应从 1(ING) 更新为 3(FAIL)");

        Long payOrderRefundAmount = jdbcTemplate.queryForObject(
                "SELECT refund_amount FROM t_pay_order WHERE pay_order_id=?",
                Long.class, PAY_ORDER_ID);
        assertEquals(0L, payOrderRefundAmount, "失败回调不应累加支付单退款额");
    }

    /**
     * 退款回调成功 → 生成退款商户通知记录（退款通知链路）
     *
     * 现有退款数据的退款单 notify_url 为空，RefundOrderProcessService:49 的闸门为假，
     * refundOrderNotify 从未真正执行通知逻辑（JaCoCo：指令 5/111 覆盖）。
     * 本用例给退款单补上非空 notify_url，点亮「退款回调 → 商户通知」这条尾巴。
     *
     * 风险理由：退款是资金动作，商户拿不到退款结果会导致双方资金状态对不上账。
     */
    @Test
    void testRefundCallbackSuccessNotifiesMerchant() {
        // 补非空 notify_url（现有数据该列为空，是通知链路被短路的原因）
        jdbcTemplate.update(
                "UPDATE t_refund_order SET notify_url=? WHERE refund_order_id=?",
                "http://merchant.example.com/notify/refund", REFUND_ORDER_ID);

        mockRefundParseParams(REFUND_ORDER_ID);
        when(wxpayChannelRefundNoticeService.doNotice(
                any(HttpServletRequest.class), any(), any(RefundOrder.class),
                any(MchAppConfigContext.class), any()))
                .thenReturn(buildRetMsg(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS));

        given()
                .when()
                .post("/api/refund/notify/wxpay/" + REFUND_ORDER_ID)
                .then()
                .statusCode(200);

        // 回归保护：退款单仍应正常流转
        Integer refundState = jdbcTemplate.queryForObject(
                "SELECT state FROM t_refund_order WHERE refund_order_id=?",
                Integer.class, REFUND_ORDER_ID);
        assertEquals(2, refundState, "退款单状态应从 1(ING) 更新为 2(SUCCESS)");

        // 核心断言：退款商户通知记录真实落库
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_mch_notify_record WHERE order_id=?",
                Integer.class, REFUND_ORDER_ID);
        assertEquals(1, count, "退款成功应生成 1 条商户通知记录");

        Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT state, order_id, order_type, notify_url FROM t_mch_notify_record WHERE order_id=?",
                REFUND_ORDER_ID);
        assertEquals((byte) 2, ((Number) record.get("order_type")).byteValue(), "order_type 应为 2(退款订单)");
        assertEquals(REFUND_ORDER_ID, record.get("order_id"), "order_id 应为退款单号");
        assertEquals((byte) 1, ((Number) record.get("state")).byteValue(), "通知记录初始状态应为 1(ING)");
        String notifyUrl = (String) record.get("notify_url");
        assertNotNull(notifyUrl, "通知地址不应为空");
        assertTrue(notifyUrl.contains("sign="), "通知 URL 应带 sign 签名参数，实际：" + notifyUrl);

        verify(mqSender, times(1)).send(any(PayOrderMchNotifyMQ.class));
    }
}
