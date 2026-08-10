package com.jeequan.jeepay.pay;

import java.util.HashMap;
import java.util.Map;

public class TestDataFactory {

    private static final String APP_SECRET = "test_app_secret_abc123";

    // 方法1：完整参数 + 正确签名
    public static Map<String, Object> buildCompleteParams() {
        return buildDefaultAndSign();
    }

    // 方法2：缺一个字段
    public static Map<String, Object> buildParamsWithout(String fieldToRemove) {
        Map<String, Object> params = buildCompleteParams();
        params.remove(fieldToRemove);
        if (!"sign".equals(fieldToRemove)) {
            String newSign = SignUtils.getSign(params, APP_SECRET);
            params.put("sign", newSign);
        }
        return params;
    }

    // 方法3：改一个字段的值
    public static Map<String, Object> buildParamsWith(String field, Object value) {
        Map<String, Object> params = buildCompleteParams();
        params.put(field, value);
        if (!"sign".equals(field)) {
            String newSign = SignUtils.getSign(params, APP_SECRET);
            params.put("sign", newSign);
        }
        return params;
    }

    // 方法4：精简参数（用于查询/关闭接口，不含 UnifiedOrder 专属字段）
    // QueryPayOrderRQ / ClosePayOrderRQ 只继承 AbstractMchAppRQ，不需要 wayCode/amount 等
    public static Map<String, Object> buildBaseMchAppParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("mchNo", "MCH-TEST-001");
        params.put("appId", "APP-TEST-001");
        params.put("version", "1.0");
        params.put("signType", "MD5");
        params.put("reqTime", String.valueOf(System.currentTimeMillis() / 1000));

        String sign = SignUtils.getSign(params, APP_SECRET);
        params.put("sign", sign);
        return params;
    }

    private static Map<String, Object> buildDefaultAndSign() {
        Map<String, Object> params = new HashMap<>();

        params.put("mchNo", "MCH-TEST-001");
        params.put("appId", "APP-TEST-001");
        params.put("mchOrderNo", "ORDER-" + System.currentTimeMillis());
        params.put("wayCode", "wx_native");
        params.put("amount", 100);
        params.put("currency", "cny");
        params.put("subject", "测试商品");
        params.put("body", "测试商品描述");
        params.put("version", "1.0");
        params.put("signType", "MD5");
        params.put("reqTime", String.valueOf(System.currentTimeMillis() / 1000));

        String sign = SignUtils.getSign(params, APP_SECRET);
        params.put("sign", sign);

        return params;
    }
}
