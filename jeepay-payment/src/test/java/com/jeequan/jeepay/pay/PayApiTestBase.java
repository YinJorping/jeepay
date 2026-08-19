package com.jeequan.jeepay.pay;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

/**
 * 支付网关测试基类
 * 封装公共配置，所有支付测试类都继承它
 */
public abstract class PayApiTestBase {

    // ──── 测试配置 ────
    protected static final String BASE_URI = "http://localhost:9216";
    protected static final String APP_SECRET = "test_app_secret_abc123";
    protected static final String MCH_NO = "MCH-TEST-001";
    protected static final String APP_ID = "APP-TEST-001";

    /**
     * 直连 Docker MySQL（无需 Spring 上下文），供直连测试做数据复位。
     * 直连测试（baseUri=9216）没有 @SpringBootTest 上下文，拿不到自动注入的 JdbcTemplate，
     * 只能自己 new 一个，连接参数与 config/application.yml 保持一致。
     */
    protected final JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new DriverManagerDataSource(
                    "jdbc:mysql://127.0.0.1:13306/jeepaydb?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf-8",
                    "root", "rootroot"));

    /**
     * 每个测试用例执行前自动跑一次，配好全局默认请求模板
     */
    @BeforeEach
    void setUp() {
        RequestSpecification spec = new RequestSpecBuilder()
                .setBaseUri(BASE_URI)       // 所有请求统一打给 localhost:9216
                .setContentType(ContentType.JSON)  // 请求体固定 JSON 格式
                .build();

        RestAssured.requestSpecification = spec;  // 设为全局，之后 given() 自动继承
    }

    /**
     * 快捷方法：根据参数算出 MD5 签名
     */
    protected String signParams(Map<String, Object> params) {
        return SignUtils.getSign(params, APP_SECRET);
    }
}
