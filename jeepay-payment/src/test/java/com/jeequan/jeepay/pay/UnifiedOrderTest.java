package com.jeequan.jeepay.pay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class UnifiedOrderTest extends PaySpringTestBase {

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

    /**
     * 商户应用不存在（appId 查无）→ 命中「商户或商户应用不存在」分支（ApiController:77）
     * 必须改 appId 而非 mchNo：缓存启用时 ConfigContextService 以 appId 为 key 缓存 mchAppConfigContext，
     * 若保留合法 APP-TEST-001 只改 mchNo，一旦该 appId 被其他用例预热缓存，会直接返回旧上下文，
     * 转而命中「参数appId与商户号不匹配」分支，断言随用例执行顺序漂移。用永不存在的 appId 保证确定性。
     */
    @Test
    void testMchAppNotExist() {
        Map<String, Object> params = TestDataFactory.buildParamsWith("appId", "INVALID-APP");

        given()
                .body(params)
        .when()
                .post("/api/pay/unifiedOrder")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户或商户应用不存在"));
    }

    // 说明：ApiController:89-91「参数appId与商户号不匹配」分支在无缓存路径（queryMchApp 联合查询）
    //       确实不可达（app 必属于查询 mchNo）；但缓存启用时，该分支可被「其他用例预热 appId 缓存」
    //       触发，属于缓存与直查路径的行为差异，已在工程问题日志记录。

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
