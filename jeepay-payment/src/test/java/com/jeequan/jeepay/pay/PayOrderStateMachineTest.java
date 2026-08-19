package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
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
 * 状态机完整性（Phase 3.1）—— 支付单 7 态状态机补齐
 *
 * SM-01 ~ SM-06 六条用例，补上模块循环阶段漏掉的转换：
 *   SM-01 INIT(0)→CLOSED(6) 关单成功（补 DB 断言）
 *   SM-02 ING(1)→CLOSED(6) 关单成功（mock 渠道确认）
 *   SM-03 SUCCESS(2)→REFUND(5) 全额退款（纯 SQL 转换，核心）
 *   SM-04 INIT(0) 收到成功回调 → 状态不变
 *   SM-05 REFUND(5) 收到成功回调 → 状态不变
 *   SM-06 SUCCESS(2) 关单被拒（业务校验拦截）
 */
public class PayOrderStateMachineTest extends PaySpringTestBase {

    private static final String APP_SECRET = "test_app_secret_abc123";

    @BeforeEach
    void resetTestData() {
        // 显式复位每一条订单到初始状态，保证用例可重复执行、互不依赖
        jdbcTemplate.update("UPDATE t_pay_order SET state=0 WHERE pay_order_id='TEST-SM-INIT-001'");
        jdbcTemplate.update("UPDATE t_pay_order SET state=1 WHERE pay_order_id='TEST-SM-ING-001'");
        jdbcTemplate.update("UPDATE t_pay_order SET state=2, refund_amount=0, refund_state=0, refund_times=0 WHERE pay_order_id='TEST-SM-FULLREFUND-001'");
        jdbcTemplate.update("DELETE FROM t_refund_order WHERE pay_order_id='TEST-SM-FULLREFUND-001'");
        jdbcTemplate.update("UPDATE t_pay_order SET state=5, refund_state=2 WHERE pay_order_id='TEST-SM-REFUND-001'");
        jdbcTemplate.update("UPDATE t_pay_order SET state=2 WHERE pay_order_id='TEST-SM-SUCCESS-001'");
    }

    /** 关单请求参数：基础凭证 + payOrderId + 重算签名 */
    private Map<String, Object> buildCloseParams(String payOrderId) {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("payOrderId", payOrderId);
        params.put("sign", SignUtils.getSign(params, APP_SECRET));
        return params;
    }

    /**
     * SM-01 生成态订单关单成功：INIT(0) → CLOSED(6)
     * 生成态关单走 updateInit2Close，无需渠道，直接改状态。
     */
    @Test
    void testInitOrderClose() {
        given()
                .body(buildCloseParams("TEST-SM-INIT-001"))
        .when()
                .post("/api/pay/close")
        .then()
                .body("code", equalTo(0));

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-SM-INIT-001'",
                Integer.class);
        assertEquals(6, state, "生成态订单关单后状态应从 0(INIT) 更新为 6(CLOSED)");
    }

    /**
     * SM-02 支付中订单关单成功：ING(1) → CLOSED(6)
     * ING 关单走 getBean("wxpayPayOrderCloseService") + 渠道确认，mock 渠道返回成功。
     */
    @Test
    void testIngOrderClose() throws Exception {
        when(wxpayPayOrderCloseService.close(any(PayOrder.class), any(MchAppConfigContext.class)))
                .thenReturn(ChannelRetMsg.confirmSuccess(null));

        given()
                .body(buildCloseParams("TEST-SM-ING-001"))
        .when()
                .post("/api/pay/close")
        .then()
                .body("code", equalTo(0));

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-SM-ING-001'",
                Integer.class);
        assertEquals(6, state, "支付中订单关单后状态应从 1(ING) 更新为 6(CLOSED)");
    }

    /**
     * SM-03 全额退款成功：SUCCESS(2) → REFUND(5)
     * 状态机里唯一纯 SQL 的转换（PayOrderMapper.xml updateRefundAmountAndCount），
     * 靠 MySQL SET 从左到右执行 + CASE WHEN refund_amount+current>=amount 联动：
     * 全额退款时 refund_state→2，state→5。
     */
    @Test
    void testFullRefundSuccess() throws Exception {
        // 1. mock 退款渠道成功
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS);
        ret.setChannelOrderId("mock-refund-id");
        when(wxpayRefundService.refund(any(), any(), any(), any())).thenReturn(ret);

        // 2. 全额退款参数：refundAmount = 订单 amount=100
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-SM-FULLREFUND-001");
        params.put("refundAmount", 100L);
        params.put("sign", SignUtils.getSign(params, APP_SECRET));

        // 3. 发起退款
        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(0));

        // 4. DB 断言：全额退款后支付单 state=5、refund_state=2、refund_amount=100
        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-SM-FULLREFUND-001'", Integer.class);
        Integer refundState = jdbcTemplate.queryForObject(
                "SELECT refund_state FROM t_pay_order WHERE pay_order_id='TEST-SM-FULLREFUND-001'", Integer.class);
        Long refundAmount = jdbcTemplate.queryForObject(
                "SELECT refund_amount FROM t_pay_order WHERE pay_order_id='TEST-SM-FULLREFUND-001'", Long.class);

        assertEquals(5, state, "全额退款后支付单状态应从 2(SUCCESS) 更新为 5(REFUND)");
        assertEquals(2, refundState, "全额退款后 refund_state 应为 2(全额退款)");
        assertEquals(100L, refundAmount, "全额退款后 refund_amount 应累加到 100");
    }

    /**
     * SM-04 生成态订单收到成功回调：状态保持 0 不变
     * doNotify 里只有 state==ING 才走 updateIng2Success，非 ING 态直接跳过更新；
     * confirmSuccess 只改内存对象（用于发商户通知），不落库。
     */
    @Test
    void testInitOrderReceiveSuccessCallback() {
        mockParseParams("TEST-SM-INIT-001");
        mockDoNoticeSuccess();

        given()
        .when()
                .post("/api/pay/notify/wxpay/TEST-SM-INIT-001")
        .then()
                .statusCode(200);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-SM-INIT-001'", Integer.class);
        assertEquals(0, state, "生成态订单收到成功回调，状态应保持 0(INIT) 不变");
    }

    /**
     * SM-05 已全额退款订单收到成功回调：状态保持 5 不变
     * 已退款订单不能因重复回调回到支付成功，否则退款白退、资金错误。
     */
    @Test
    void testRefundOrderReceiveSuccessCallback() {
        mockParseParams("TEST-SM-REFUND-001");
        mockDoNoticeSuccess();

        given()
        .when()
                .post("/api/pay/notify/wxpay/TEST-SM-REFUND-001")
        .then()
                .statusCode(200);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-SM-REFUND-001'", Integer.class);
        assertEquals(5, state, "已全额退款订单收到成功回调，状态应保持 5(REFUND) 不变");
    }

    /**
     * SM-06 支付成功订单关单被拒
     * CloseOrderController 第 70 行校验：只有 INIT/ING 可关单，SUCCESS 被业务校验拦截。
     */
    @Test
    void testSuccessOrderCloseRejected() {
        given()
                .body(buildCloseParams("TEST-SM-SUCCESS-001"))
        .when()
                .post("/api/pay/close")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("当前订单不可关闭"));

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-SM-SUCCESS-001'", Integer.class);
        assertEquals(2, state, "支付成功订单关单被拒，状态应保持 2(SUCCESS) 不变");
    }
}
