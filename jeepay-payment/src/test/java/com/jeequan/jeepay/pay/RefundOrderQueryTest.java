package com.jeequan.jeepay.pay;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class RefundOrderQueryTest extends PaySpringTestBase {

    /**
     * 查询已存在的退款单（按 refundOrderId）
     * 数据：prepare-refund-data.sql 预置的 TEST-REF-RO-006-1（state=2 成功，金额 30）
     */
    @Test
    void testQueryByRefundOrderId() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("refundOrderId", "TEST-REF-RO-006-1");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/query")
        .then()
                .body("code", equalTo(0))
                .body("data.refundOrderId", equalTo("TEST-REF-RO-006-1"))
                .body("data.state", equalTo(2))
                .body("data.refundAmount", equalTo(30));
    }

    /**
     * 查询已存在的退款单（按 mchRefundNo）
     */
    @Test
    void testQueryByMchRefundNo() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("mchRefundNo", "TEST-REF-MCH-REF-006-1");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/query")
        .then()
                .body("code", equalTo(0))
                .body("data.mchRefundNo", equalTo("TEST-REF-MCH-REF-006-1"))
                .body("data.state", equalTo(2));
    }

    /**
     * mchRefundNo 和 refundOrderId 同时为空（源码 QueryRefundOrderController.java:56）
     */
    @Test
    void testQueryBothEmpty() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();

        given()
                .body(params)
        .when()
                .post("/api/refund/query")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("mchRefundNo 和 refundOrderId不能同时为空"));
    }

    /**
     * 查询不存在的退款单（源码 QueryRefundOrderController.java:61）
     */
    @Test
    void testQueryNotExist() {
        Map<String, Object> params = TestDataFactory.buildBaseMchAppParams();
        params.put("refundOrderId", "NOT-EXIST-REFUND-ID");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/refund/query")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("订单不存在"));
    }
}
