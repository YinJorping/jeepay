package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.core.entity.RefundOrder;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 退款渠道回调测试（/api/refund/notify/{ifCode}）
 *
 * 复用支付回调模块的模式：@SpringBootTest + @MockBean mock 渠道回调服务，
 * 验证退款单状态机 ING(1) → SUCCESS(2) / FAIL(3)
 */
public class RefundOrderNoticeTest extends RefundSpringTestBase {

    private static final String REFUND_ORDER_ID = "TEST-REF-RO-NOTICE-1";
    private static final String PAY_ORDER_ID = "TEST-REFUND-NOTICE-001";

    @BeforeEach
    void resetTestData() {
        // 重置退款单为在途(1)、支付单退款字段归零，保证 CAS 守卫可重复触发
        jdbcTemplate.update(
                "UPDATE t_refund_order SET state=1 WHERE refund_order_id=?", REFUND_ORDER_ID);
        jdbcTemplate.update(
                "UPDATE t_pay_order SET refund_amount=0, refund_state=0, refund_times=0, state=2 WHERE pay_order_id=?",
                PAY_ORDER_ID);
    }

    private void mockParseParams(String refundOrderId) {
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
        mockParseParams(REFUND_ORDER_ID);
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
        mockParseParams(REFUND_ORDER_ID);
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
}
