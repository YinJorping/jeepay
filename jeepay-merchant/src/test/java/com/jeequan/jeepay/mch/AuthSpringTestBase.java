package com.jeequan.jeepay.mch;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.lang.UUID;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.jwt.JWTPayload;
import com.jeequan.jeepay.core.jwt.JWTUtils;
import com.jeequan.jeepay.mch.bootstrap.JeepayMchApplication;
import com.jeequan.jeepay.mch.config.SystemYmlConfig;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

/**
 * 认证模块 Spring 集成测试基类
 *
 * 与支付/回调/退款模块的差异：认证模块不需要 mock 任何外部渠道（JWT + BCrypt + Redis 全真环境），
 * 因此只有 @SpringBootTest 没有 @MockBean。Spring 上下文在 AuthLoginTest / AuthTokenTest 间共享。
 *
 * 两个关键前置决策：
 * 1. 登录参数 ia/ip/vc/vt 必须走 JSON body：AbstractCtrl.getVal 读的是 getReqParamJSON()，
 *    而 RequestKitBean.isConvertJSON() 只有在 Content-Type=application/json 且非 GET 时才解析 body。
 * 2. 验证码直接种进 Redis：图片验证码是 4 位字符藏在 base64 图片里，测试无法 OCR；
 *    直接向 Redis 写入已知 code（img_code_{token}）绕过图片识别障碍，聚焦登录逻辑本身。
 */
@SpringBootTest(
        classes = JeepayMchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class AuthSpringTestBase {

    /** 测试统一密码 Test@123456 的 BCrypt 哈希，与 prepare-auth-data.sql 保持一致 */
    protected static final String TEST_PASSWORD = "Test@123456";

    /** 登录成功用户（超管 authadmin，is_admin=1 跳过菜单校验） */
    protected static final String NORMAL_ACCOUNT = "authadmin";

    /** 测试用的固定验证码值 */
    protected static final String TEST_VERCODE = "1234";

    @LocalServerPort
    protected int port;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected SystemYmlConfig systemYmlConfig;

    @BeforeEach
    void setUpBaseUri() {
        // 显式 setBaseUri + ContentType：RestAssured 的 requestSpecification/baseURI 是 static，
        // 必须每个用例重置，否则会残留上一个类的端口配置。ContentType.JSON 是登录参数能读到的前提。
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setBaseUri("http://localhost:" + port)
                .setContentType(ContentType.JSON)
                .build();
    }

    /**
     * 构造登录请求参数。ia/ip/vc/vt 四个字段都必须 base64 编码，
     * 对应 AuthController.validate 里的 Base64.decodeStr。
     */
    protected Map<String, Object> buildLoginParams(String account, String password, String vcode, String vcodeToken) {
        Map<String, Object> params = new HashMap<>();
        params.put("ia", Base64.encode(account));
        params.put("ip", Base64.encode(password));
        params.put("vc", Base64.encode(vcode));
        params.put("vt", Base64.encode(vcodeToken));
        return params;
    }

    /**
     * 向 Redis 种入一个已知验证码，返回验证码 token。
     * key 格式 img_code_{token}（CS.getCacheKeyImgCode），TTL 60s（CS.VERCODE_CACHE_TIME）。
     */
    protected String seedVercode(String code) {
        String token = "AUTH-TEST-" + UUID.fastUUID();
        stringRedisTemplate.opsForValue().set(CS.getCacheKeyImgCode(token), code, CS.VERCODE_CACHE_TIME, TimeUnit.SECONDS);
        return token;
    }

    /** 发起登录请求，返回原始 Response 供断言 */
    protected Response doLogin(String account, String password, String vcode, String vcodeToken) {
        return given()
                .body(buildLoginParams(account, password, vcode, vcodeToken))
                .post("/api/anon/auth/validate");
    }

    /** 登录并直接返回 accessToken（iToken） */
    protected String loginAndGetToken(String account, String password) {
        String vt = seedVercode(TEST_VERCODE);
        Response resp = doLogin(account, password, TEST_VERCODE, vt);
        return resp.jsonPath().getString("data.iToken");
    }

    /** 从 JWT 中解析出 Redis 里的 token 缓存 key（TOKEN_{sysUserId}_{uuid}） */
    protected String parseCacheKey(String token) {
        JWTPayload payload = JWTUtils.parseToken(token, systemYmlConfig.getJwtSecret());
        return payload == null ? null : payload.getCacheKey();
    }
}
