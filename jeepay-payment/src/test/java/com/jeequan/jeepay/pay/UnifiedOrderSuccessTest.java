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
}
