package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRQ;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRS;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 统一下单成功链路（Phase 2 遗留修复）
 *
 * 原 UnifiedOrderTest.testNormalOrder 直连 Docker，通道参数是假数据（mchId="test" 等），
 * 走到 paymentService.pay() 真正调微信 API 时会失败，永远拿不到 code=0。
 * 修复方式：mock 支付渠道 WxpayPaymentService，让 pay() 返回「等待支付」，
 * 从而验证下单成功链路（订单入库 + state INIT→ING + 返回 code=0）。
 *
 * 关键：@MockBean 会把整个 WxpayPaymentService 都 mock 掉，getIfCode()/isSupport()
 * 在 pay() 之前就会被调用，必须一并 stub，否则 ifCode 入库为 null、isSupport 抛「不支持该支付方式」。
 */
public class UnifiedOrderSuccessTest extends PaySpringTestBase {

    @Test
    void testUnifiedOrderSuccess() throws Exception {
        // 前置 stub：getIfCode() 返回 wxpay（否则 ifCode 入库 null）；isSupport() 返回 true（否则抛「不支持该支付方式」）
        when(wxpayPaymentService.getIfCode()).thenReturn(CS.IF_CODE.WXPAY);
        when(wxpayPaymentService.isSupport(any(String.class))).thenReturn(true);

        // 核心 mock：pay() 返回「等待支付」，让 processChannelMsg 把订单置 ING，返回 code=0
        UnifiedOrderRS rs = new UnifiedOrderRS();
        rs.setChannelRetMsg(ChannelRetMsg.waiting());
        when(wxpayPaymentService.pay(any(UnifiedOrderRQ.class), any(PayOrder.class), any(MchAppConfigContext.class)))
                .thenReturn(rs);

        Map<String, Object> params = TestDataFactory.buildCompleteParams();
        String mchOrderNo = (String) params.get("mchOrderNo");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(0))
                .body("msg", equalTo("SUCCESS"));

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE mch_no='MCH-TEST-001' AND mch_order_no=?",
                Integer.class, mchOrderNo);
        assertEquals(1, state, "下单成功链路应把订单置为 ING(1)");
    }

    /**
     * 同步成功：渠道在下单时直接返回 CONFIRM_SUCCESS
     *
     * 覆盖 INIT(0) → ING(1) → SUCCESS(2) 两步 CAS 链（PayOrderProcessService:101 updateInit2Ing
     * + :107 updateIng2SuccessOrFail），与回调路径的「单步 ING→SUCCESS」不是同一条。
     * 风险理由：条码/付款码支付被扫后渠道会同步返回成功，若这条链断，订单状态错误，
     * 且 confirmSuccess 下游（商户通知）不触发。
     */
    @Test
    void testUnifiedOrderSyncSuccess() throws Exception {
        when(wxpayPaymentService.getIfCode()).thenReturn(CS.IF_CODE.WXPAY);
        when(wxpayPaymentService.isSupport(any(String.class))).thenReturn(true);

        // 核心 mock：pay() 同步返回「明确成功」
        UnifiedOrderRS rs = new UnifiedOrderRS();
        rs.setChannelRetMsg(ChannelRetMsg.confirmSuccess("mock-channel-order-no"));
        when(wxpayPaymentService.pay(any(UnifiedOrderRQ.class), any(PayOrder.class), any(MchAppConfigContext.class)))
                .thenReturn(rs);

        // 带非空 notifyUrl 下单，用于断言 confirmSuccess 下游（商户通知）确实触发
        Map<String, Object> params = TestDataFactory.buildParamsWith(
                "notifyUrl", "http://merchant.example.com/notify/pay");
        String mchOrderNo = (String) params.get("mchOrderNo");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(0));

        String payOrderId = jdbcTemplate.queryForObject(
                "SELECT pay_order_id FROM t_pay_order WHERE mch_no='MCH-TEST-001' AND mch_order_no=?",
                String.class, mchOrderNo);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id=?", Integer.class, payOrderId);
        assertEquals(2, state, "同步成功后订单应为 SUCCESS(2)");

        // confirmSuccess 下游：应生成商户通知记录
        Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT state, order_id, order_type FROM t_mch_notify_record WHERE order_id=?", payOrderId);
        assertEquals((byte) 1, ((Number) record.get("order_type")).byteValue(), "order_type 应为 1(支付订单)");
        assertEquals(payOrderId, record.get("order_id"), "order_id 应为支付单号");
        assertEquals((byte) 1, ((Number) record.get("state")).byteValue(), "通知记录初始状态应为 1(ING)");
    }

    /**
     * 同步失败：渠道在下单时直接返回 CONFIRM_FAIL
     *
     * 覆盖 INIT(0) → ING(1) → FAIL(3)。
     * 风险理由：渠道同步返回失败时必须把订单正确置为失败态，否则失败订单会悬挂在支付中。
     */
    @Test
    void testUnifiedOrderSyncFail() throws Exception {
        when(wxpayPaymentService.getIfCode()).thenReturn(CS.IF_CODE.WXPAY);
        when(wxpayPaymentService.isSupport(any(String.class))).thenReturn(true);

        UnifiedOrderRS rs = new UnifiedOrderRS();
        rs.setChannelRetMsg(ChannelRetMsg.confirmFail("MOCK_ERR", "模拟渠道失败"));
        when(wxpayPaymentService.pay(any(UnifiedOrderRQ.class), any(PayOrder.class), any(MchAppConfigContext.class)))
                .thenReturn(rs);

        Map<String, Object> params = TestDataFactory.buildCompleteParams();
        String mchOrderNo = (String) params.get("mchOrderNo");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(0));

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE mch_no='MCH-TEST-001' AND mch_order_no=?",
                Integer.class, mchOrderNo);
        assertEquals(3, state, "同步失败后订单应为 FAIL(3)");
    }
}
