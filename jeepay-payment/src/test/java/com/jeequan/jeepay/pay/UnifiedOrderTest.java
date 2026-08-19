package com.jeequan.jeepay.pay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class UnifiedOrderTest extends PayApiTestBase{

    // 正常路径已迁到 UnifiedOrderSuccessTest（mock 支付渠道），本类只保留参数校验/签名/商户状态用例
    // ──── 必填字段缺失（12条，参数化合并为一个方法） ────

    @ParameterizedTest
    @CsvSource({
            "mchOrderNo, 商户订单号不能为空",
            "wayCode, 支付方式不能为空",
            "amount, 支付金额不能为空",
            "currency, 货币代码不能为空",
            "subject, 商品标题不能为空",
            "body, 商品描述信息不能为空",
            "mchNo, 商户号不能为空",
            "appId, 商户应用ID不能为空",
            "version, 版本号不能为空",
            "signType, 签名类型不能为空",
            "sign, 签名值不能为空",
            "reqTime, 时间戳不能为空"
    })
    void testMissingField(String fieldName, String expectedMsg) {
        Map<String, Object> params = TestDataFactory.buildParamsWithout(fieldName);

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo(expectedMsg));
    }

    // ──── 签名验证异常 ────

    @Test
    void testMchNoNotFound() {
        Map<String, Object> params = TestDataFactory.buildParamsWith("mchNo", "INVALID-MCH");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户或商户应用不存在"));
    }

    // PAY-018：废弃 — ApiController 第 89 行检查被 queryMchApp 的 mchNo+appId 联合查询提前拦截，
    //           app 必然属于查询时的 mchNo，此分支 API 层不可达（与 PAY-014 同为死代码）

    @Test
    void testWrongSign() {
        Map<String, Object> params = TestDataFactory.buildParamsWith("sign", "WRONG_SIGN");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("验签失败"));
    }

    @Test
    void testMerchantDisabled() {
        // MCH-DISABLED 有专属应用 APP-FOR-DISABLED，需要同时改 mchNo 和 appId
        Map<String, Object> params = TestDataFactory.buildCompleteParams();
        params.put("mchNo", "MCH-DISABLED");
        params.put("appId", "APP-FOR-DISABLED");
        params.put("sign", signParams(params));

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户信息不存在或商户状态不可用"));
    }

    @Test
    void testAppDisabled() {
        Map<String, Object> params = TestDataFactory.buildParamsWith("appId", "APP-DISABLED");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户应用不存在或应用状态不可用"));
    }
}
