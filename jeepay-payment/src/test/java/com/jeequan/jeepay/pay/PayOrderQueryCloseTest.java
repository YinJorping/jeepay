package com.jeequan.jeepay.pay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class PayOrderQueryCloseTest extends PaySpringTestBase {

    /**
     * 关单用例会改变 TEST-PAY-INIT-001 的状态（0→6），
     * 重复执行前必须复位回 INIT，否则第二次跑会命中「当前订单不可关闭」。
     */
    @BeforeEach
    void resetCloseOrderData() {
        jdbcTemplate.update("UPDATE t_pay_order SET state=0 WHERE pay_order_id='TEST-PAY-INIT-001'");
    }

    /**
     * 查询已存在的订单：用 SQL 预插入的 state=SUCCESS 订单
     */
    @Test
    void testQueryOrder() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("payOrderId", "TEST-PAY-SUCCESS-001");
        // 加了字段，重新算签名
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/pay/query")
        .then()
                .body("code", equalTo(0))
                .body("data.payOrderId", equalTo("TEST-PAY-SUCCESS-001"))
                .body("data.state", equalTo(2));
    }

    /**
     * 关闭 INIT 状态的订单：用 SQL 预插入的 state=INIT 订单
     */
    @Test
    void testCloseOrder() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("payOrderId", "TEST-PAY-INIT-001");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/pay/close")
        .then()
                .body("code", equalTo(0));
    }

    /**
     * 查询不存在的订单
     */
    @Test
    void testQueryOrderNotFound() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("payOrderId", "NOT-EXIST-ORDER-ID");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/pay/query")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("订单不存在"));
    }
}
