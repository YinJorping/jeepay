package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 退款下单 - 正常成功路径（001）
 *
 * 与 RefundOrderTest 的区别：
 *   RefundOrderTest 是 REST Assured 直连 Docker，测校验分支（code=9999）
 *   本类是 @SpringBootTest + @MockBean，把微信退款渠道 mock 成成功，测完整成功链路 + SQL 对账
 */
public class RefundOrderSuccessTest extends RefundSpringTestBase {

    private static final String APP_SECRET = "test_app_secret_abc123";
    private static final String PAY_ORDER_ID = "TEST-REFUND-SUCCESS-001";

    @BeforeEach
    void resetTestData() {
        // 重置数据，保证用例可重复执行（每次退款成功会写退款单 + 累加 refund_amount）
        jdbcTemplate.update(
                "UPDATE t_pay_order SET refund_amount=0, refund_state=0, refund_times=0, state=2 WHERE pay_order_id=?",
                PAY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM t_refund_order WHERE pay_order_id=?", PAY_ORDER_ID);
    }

    /**
     * 001：全部字段正确 + 渠道 mock 成功 → code=0，退款单入库，支付单退款金额累加
     * 核心断言：SQL 对账 —— 支付单字段 refund_amount 与 退款单表 SUM 一致
     */
    @Test
    void testRefundSuccess() throws Exception {
        // 1. mock 渠道返回「退款成功」
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS);
        ret.setChannelOrderId("mock-refund-id");
        when(wxpayRefundService.refund(any(), any(), any(), any())).thenReturn(ret);

        // 2. 构造请求参数并重算签名
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", PAY_ORDER_ID);
        params.put("sign", SignUtils.getSign(params, APP_SECRET));

        // 3. 发起退款，断言业务成功
        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(0))
                .body("msg", equalTo("SUCCESS"));

        // 4. SQL 对账：支付单字段与退款单表 SUM 一致
        Long payOrderRefundAmount = jdbcTemplate.queryForObject(
                "SELECT refund_amount FROM t_pay_order WHERE pay_order_id=?",
                Long.class, PAY_ORDER_ID);
        assertEquals(30L, payOrderRefundAmount, "支付单 refund_amount 应累加 30");

        Long sumRefundAmount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(SUM(refund_amount),0) FROM t_refund_order WHERE pay_order_id=? AND state=2",
                Long.class, PAY_ORDER_ID);
        assertEquals(30L, sumRefundAmount, "退款单表 SUM 应等于 30");

        // 对账核心：字段值 == SUM 值
        assertEquals(payOrderRefundAmount, sumRefundAmount, "字段 refund_amount 应与 SUM 一致");
    }
}
