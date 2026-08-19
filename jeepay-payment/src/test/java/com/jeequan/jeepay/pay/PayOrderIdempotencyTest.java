package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.core.entity.PayOrder;
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
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等性（Phase 3.2）—— 同一操作重复执行，结果不变（状态/金额/记录数不变）
 *
 * IDEM-01 ~ IDEM-04 四条用例，补上下单/关单/退款回调三个幂等缺口：
 *   IDEM-01 重复统一下单   —— count(mchNo+mchOrderNo) 去重，不创建新单
 *   IDEM-02 重复关单       —— state 守卫拦截，不重复调渠道
 *   IDEM-03 重复退款回调   —— state=ING CAS 拦截，refund_amount 不重复累加（核心，资金安全）
 *   IDEM-04 重复退款下单   —— count(mchNo+mchRefundNo) 去重，不创建新单
 *
 * 支付回调幂等（CB-03/04/05）已在回调模块覆盖，本类不再重复。
 */
public class PayOrderIdempotencyTest extends PaySpringTestBase {

    private static final String APP_SECRET = "test_app_secret_abc123";
    private static final String REFUND_ORDER_ID = "TEST-IDEM-RO-003";
    private static final String PAY_ORDER_ID = "TEST-IDEM-PAY-003";

    @BeforeEach
    void resetTestData() {
        // IDEM-01：count 去重不写库，预置订单保持生成态（防御性复位）
        jdbcTemplate.update("UPDATE t_pay_order SET state=0 WHERE pay_order_id='TEST-IDEM-PAY-001'");
        // IDEM-02：关单成功后 state→6，复位回 1（支付中）
        jdbcTemplate.update("UPDATE t_pay_order SET state=1 WHERE pay_order_id='TEST-IDEM-PAY-002'");
        // IDEM-03：退款单复位在途(1)、支付单退款字段归零
        jdbcTemplate.update("UPDATE t_refund_order SET state=1 WHERE refund_order_id=?", REFUND_ORDER_ID);
        jdbcTemplate.update(
                "UPDATE t_pay_order SET refund_amount=0, refund_state=0, refund_times=0, state=2 WHERE pay_order_id=?",
                PAY_ORDER_ID);
        // IDEM-04：count 去重不写库，退款单保持成功态（防御性复位）
        jdbcTemplate.update("UPDATE t_refund_order SET state=2 WHERE refund_order_id='TEST-IDEM-RO-004'");
    }

    /**
     * IDEM-01 重复统一下单：同 mchNo+mchOrderNo 再次下单被拒
     * AbstractPayOrderController:117 count>0 → BizException「商户订单已存在」
     * 断言：code=9999 + t_pay_order 该 mchOrderNo 只有 1 条记录（不重复创建）
     */
    @Test
    void testDuplicateUnifiedOrder() {
        Map<String, Object> params = TestDataFactory.buildCompleteParams();
        params.put("mchOrderNo", "TEST-IDEM-MCHNO-001");
        params.put("sign", SignUtils.getSign(params, APP_SECRET));

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户订单[TEST-IDEM-MCHNO-001]已存在"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_pay_order WHERE mch_no='MCH-TEST-001' AND mch_order_no='TEST-IDEM-MCHNO-001'",
                Integer.class);
        assertEquals(1, count, "重复下单不应创建新订单，DB 应只有 1 条记录");
    }

    /**
     * IDEM-02 重复关单：ING 订单关单成功后再关被拒
     * CloseOrderController:70 只有 INIT/ING 可关单，CLOSED(6) 再次关单被业务校验拦截
     * 断言：第一次 code=0，第二次 code=9999 + state=6 + 渠道 close 只调 1 次
     */
    @Test
    void testDuplicateCloseOrder() throws Exception {
        when(wxpayPayOrderCloseService.close(any(PayOrder.class), any(MchAppConfigContext.class)))
                .thenReturn(ChannelRetMsg.confirmSuccess(null));

        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("payOrderId", "TEST-IDEM-PAY-002");
        params.put("sign", SignUtils.getSign(params, APP_SECRET));

        // 第一次关单：ING(1) → CLOSED(6)，code=0
        given()
                .body(params)
        .when()
                .post("/api/pay/close")
        .then()
                .body("code", equalTo(0));

        // 第二次关单：state=6 被守卫拦截
        given()
                .body(params)
        .when()
                .post("/api/pay/close")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("当前订单不可关闭"));

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id='TEST-IDEM-PAY-002'", Integer.class);
        assertEquals(6, state, "重复关单后状态应保持 6(CLOSED)");

        verify(wxpayPayOrderCloseService, times(1))
                .close(any(PayOrder.class), any(MchAppConfigContext.class));
    }

    /**
     * IDEM-03 重复退款回调（核心）：在途退款单收到两次 SUCCESS 回调
     * RefundOrderService.updateIng2Success CAS WHERE state=ING，第二次 0 行则 return false，
     * 不执行 updateRefundAmountAndCount → refund_amount 不重复累加。
     * 断言：refund_amount 保持 30（不是 60）+ 退款单 state=2
     */
    @Test
    void testDuplicateRefundCallback() {
        mockRefundParseParams(REFUND_ORDER_ID);
        when(wxpayChannelRefundNoticeService.doNotice(
                any(HttpServletRequest.class), any(), any(RefundOrder.class),
                any(MchAppConfigContext.class), any()))
                .thenReturn(buildRetMsg(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS));

        // 第一次回调：state 1(ING) → 2(SUCCESS)，refund_amount 累加 30
        given()
                .when()
                .post("/api/refund/notify/wxpay/" + REFUND_ORDER_ID);

        // 第二次回调（渠道重复通知）：CAS 拦截，refund_amount 不变
        given()
                .when()
                .post("/api/refund/notify/wxpay/" + REFUND_ORDER_ID);

        Integer refundState = jdbcTemplate.queryForObject(
                "SELECT state FROM t_refund_order WHERE refund_order_id=?",
                Integer.class, REFUND_ORDER_ID);
        assertEquals(2, refundState, "退款单状态应为 2(SUCCESS)");

        Long refundAmount = jdbcTemplate.queryForObject(
                "SELECT refund_amount FROM t_pay_order WHERE pay_order_id=?",
                Long.class, PAY_ORDER_ID);
        assertEquals(30L, refundAmount, "重复回调不应重复累加，refund_amount 应保持 30");
    }

    /**
     * IDEM-04 重复退款下单：同 mchRefundNo 再次退款被拒
     * RefundOrderController:120 count(mchNo+mchRefundNo)>0 → BizException「商户退款订单号已存在」
     * 断言：code=9999 + t_refund_order 该 mchRefundNo 只有 1 条记录（不重复创建）
     */
    @Test
    void testDuplicateRefundOrder() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-IDEM-PAY-004");
        params.put("mchRefundNo", "TEST-IDEM-REFNO-001");
        params.put("refundAmount", 30L);
        params.put("sign", SignUtils.getSign(params, APP_SECRET));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户退款订单号[TEST-IDEM-REFNO-001]已存在"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_refund_order WHERE mch_no='MCH-TEST-001' AND mch_refund_no='TEST-IDEM-REFNO-001'",
                Integer.class);
        assertEquals(1, count, "重复退款不应创建新退款单，DB 应只有 1 条记录");
    }

    /** 模拟退款渠道解析出有效退款单号 */
    private void mockRefundParseParams(String refundOrderId) {
        MutablePair<String, Object> pair = new MutablePair<>();
        pair.setLeft(refundOrderId);
        pair.setRight("mock params");

        when(wxpayChannelRefundNoticeService.parseParams(
                any(HttpServletRequest.class), any(String.class), any()))
                .thenReturn(pair);
    }

    /** 构造退款回调返回消息 */
    private ChannelRetMsg buildRetMsg(ChannelRetMsg.ChannelState state) {
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(state);
        ret.setChannelOrderId("mock-channel-refund-id");
        ret.setResponseEntity(ResponseEntity.ok("success"));
        return ret;
    }
}
