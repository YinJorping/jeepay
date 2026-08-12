package com.jeequan.jeepay.pay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回调模块测试
 */
public class ChannelCallbackTest extends CallbackTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 每个测试前重置数据，避免执行顺序污染 */
    @BeforeEach
    void resetTestData() {
        jdbcTemplate.update(
                "UPDATE t_pay_order SET state=1 WHERE pay_order_id='TEST-CALLBACK-ING-001'");
    }

    /**
     * CB-01 正常成功回调：支付中订单收到 CONFIRM_SUCCESS，状态从 1→2
     */
    @Test
    void testIngOrderPaySuccess() {
        mockParseParams("TEST-CALLBACK-ING-001");
        mockDoNoticeSuccess();

        String body = given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-ING-001")
                .then()
                .statusCode(200)
                .extract().body().asString();

        // FastJson 将字符串序列化为 JSON 格式，带引号
        assertEquals("\"success\"", body);

        // 验证 DB 状态已更新：1(ING) → 2(SUCCESS)
        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id = ?",
                Integer.class,
                "TEST-CALLBACK-ING-001"
        );
        assertEquals(2, state, "订单状态应从 1(ING) 更新为 2(SUCCESS)");
    }

    /**
     * CB-02 正常失败回调：支付中订单收到 CONFIRM_FAIL，状态从 1→3
     */
    @Test
    void testIngOrderPayFail() {
        mockParseParams("TEST-CALLBACK-ING-001");
        mockDoNoticeFail();

        String body = given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-ING-001")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertEquals("\"fail\"", body);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id = ?",
                Integer.class,
                "TEST-CALLBACK-ING-001"
        );
        assertEquals(3, state, "订单状态应从 1(ING) 更新为 3(FAIL)");
    }

    /**
     * CB-03 幂等性：已支付订单重复收到成功回调，状态保持 2 不变
     */
    @Test
    void testSuccessOrderRepeatCallback() {
        mockParseParams("TEST-CALLBACK-SUCCESS-001");
        mockDoNoticeSuccess();

        given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-SUCCESS-001")
                .then()
                .statusCode(200);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id = ?",
                Integer.class,
                "TEST-CALLBACK-SUCCESS-001"
        );
        assertEquals(2, state, "已成功的订单重复回调，状态应保持 2 不变");
    }

    /**
     * CB-04 失败订单收到成功回调：状态保持 3 不变（SQL WHERE state=1 守卫拦截）
     */
    @Test
    void testFailOrderReceiveSuccessCallback() {
        mockParseParams("TEST-CALLBACK-FAIL-001");
        mockDoNoticeSuccess();

        given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-FAIL-001")
                .then()
                .statusCode(200);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id = ?",
                Integer.class,
                "TEST-CALLBACK-FAIL-001"
        );
        assertEquals(3, state, "已失败的订单收到成功回调，状态应保持 3 不变");
    }

    /**
     * CB-05 已关闭订单收到成功回调：状态保持 6 不变
     */
    @Test
    void testClosedOrderReceiveSuccessCallback() {
        mockParseParams("TEST-CALLBACK-CLOSED-001");
        mockDoNoticeSuccess();

        given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-CLOSED-001")
                .then()
                .statusCode(200);

        Integer state = jdbcTemplate.queryForObject(
                "SELECT state FROM t_pay_order WHERE pay_order_id = ?",
                Integer.class,
                "TEST-CALLBACK-CLOSED-001"
        );
        assertEquals(6, state, "已关闭的订单收到成功回调，状态应保持 6 不变");
    }

    /**
     * CB-06 订单不存在：mock doNotifyOrderNotExists 返回 200
     */
    @Test
    void testOrderNotExists() {
        mockParseParams("NONEXIST-ORDER-ID");
        mockDoNotifyOrderNotExists();

        given()
                .when()
                .post("/api/pay/notify/wxpay/NONEXIST-ORDER-ID")
                .then()
                .statusCode(200);
    }

    /**
     * CB-07 参数解析失败：parseParams 返回 null → 400
     */
    @Test
    void testParseParamsNull() {
        mockParseParamsNull();

        String body = given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-ING-001")
                .then()
                .statusCode(400)
                .extract().body().asString();

        assertEquals("\"解析数据异常！\"", body);
    }

    /**
     * CB-08 订单号不匹配：URL 中的 ID 与解析出的 ID 不一致 → 400
     */
    @Test
    void testOrderIdMismatch() {
        mockParseParams("DIFFERENT-ID");

        String body = given()
                .when()
                .post("/api/pay/notify/wxpay/TEST-CALLBACK-ING-001")
                .then()
                .statusCode(400)
                .extract().body().asString();

        assertEquals("\"订单号不匹配！\"", body);
    }

    /**
     * CB-09 渠道接口不存在：未知的 ifCode → 400
     */
    @Test
    void testUnknownChannel() {
        String body = given()
                .when()
                .post("/api/pay/notify/unknown/TEST-CALLBACK-ING-001")
                .then()
                .statusCode(400)
                .extract().body().asString();

        assertEquals("\"[unknown] interface not exists\"", body);
    }
}
