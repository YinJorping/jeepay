package com.jeequan.jeepay.pay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class RefundOrderTest extends PayApiTestBase {

    /**
     * 013：订单状态不是 SUCCESS 时发起退款，被拒
     * 覆盖 4 种非成功状态：支付中(1)/失败(3)/关闭(6)/已退款(5)
     * 对应源码 RefundOrderController.java:90 的 state != STATE_SUCCESS 校验
     */
    @ParameterizedTest
    @CsvSource({
            "TEST-REFUND-STATE-ING-013",
            "TEST-REFUND-STATE-FAIL-013",
            "TEST-REFUND-STATE-CLOSED-013",
            "TEST-REFUND-STATE-REFUND-013"
    })
    void testRefundOnNonSuccessState(String payOrderId) {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", payOrderId);
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("订单状态不正确， 无法完成退款"));
    }

    /**
     * 002：mchOrderNo 和 payOrderId 同时为空
     * buildRefundParams() 默认就不含这两个字段，直接触发双空校验（源码 L77）
     */
    @Test
    void testBothOrderNoEmpty() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("mchOrderNo 和 payOrderId不能同时为空"));
    }

    /**
     * 003：notifyUrl 协议非法（非 http/https）
     * 校验发生在查库之前（源码 L81），payOrderId 随便填即可通过双空校验
     */
    @Test
    void testInvalidNotifyUrl() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "P001");
        params.put("notifyUrl", "ftp://x.com");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("异步通知地址协议仅支持http:// 或 https:// !"));
    }

    /**
     * 004：payOrderId 查不到订单（源码 L85-88）
     */
    @Test
    void testPayOrderNotExist() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "NOT_EXIST");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("退款订单不存在"));
    }

    /**
     * 006：已退 30 再退 80，字段校验拦截超退（源码 L98）
     * 数据：pay_order.refund_amount=30，本次申请 80，30+80 > 100
     */
    @Test
    void testRefundExceedsFieldBalance() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-REFUND-PARTIAL-006");
        params.put("refundAmount", 80L);
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("申请金额超出订单可退款余额，请检查退款金额"));
    }

    /**
     * 007：存在在途退款单（state=ING）时再次退款被拒（源码 L102）
     */
    @Test
    void testRefundWhenInProgress() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-REFUND-ING-007");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("支付订单具有在途退款申请，请稍后再试"));
    }

    /**
     * 008：字段与 SUM 不一致时由 SQL 聚合兜底（源码 L107）
     * 数据：pay_order.refund_amount=50，但退款单表 SUM=100（两笔成功退款各 50）
     * 字段校验放行（50<100），SUM 校验发现已全额退款 → 拦截
     */
    @Test
    void testSumFullRefundGuard() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-REFUND-DIVERGE-008");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("退款单已完成全部订单退款，本次申请失败"));
    }

    /**
     * 009：字段与 SUM 不一致，SUM + 本次超过支付金额（源码 L112）
     * 数据：pay_order.refund_amount=20，退款单表 SUM=70，本次申请 40，70+40 > 100
     */
    @Test
    void testSumExceedsBalance() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-REFUND-DIVERGE-009");
        params.put("refundAmount", 40L);
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("申请金额超出订单可退款余额，请检查退款金额"));
    }

    /**
     * 010：mchRefundNo 重复（商户退款单号唯一校验，源码 L120）
     */
    @Test
    void testDuplicateMchRefundNo() {
        Map<String, Object> params = TestDataFactory.buildRefundParams();
        params.put("payOrderId", "TEST-REFUND-DUP-010");
        params.put("mchRefundNo", "TEST-REF-MCH-REF-DUP-010");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/refundOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户退款订单号[TEST-REF-MCH-REF-DUP-010]已存在"));
    }
}
