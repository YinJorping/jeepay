package com.jeequan.jeepay.mch;

import com.jeequan.jeepay.core.constants.CS;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证模块——登录链路用例（AUTH-001 ~ AUTH-016）
 *
 * 覆盖：图形验证码生成、登录参数缺失、验证码错误/复用、密码错误/用户不存在、
 *       用户禁用、商户禁用、商户不存在、无菜单、登录成功、手机号登录。
 */
public class AuthLoginTest extends AuthSpringTestBase {

    /**
     * AUTH-001：获取图形验证码成功
     * 验证：返回 imageBase64Data/vercodeToken/expireTime=60，且 Redis 写入 4 位验证码。
     */
    @Test
    void testVercodeGenerate() {
        Response resp = given().get("/api/anon/auth/vercode");

        resp.then()
                .body("code", equalTo(0))
                .body("data.imageBase64Data", notNullValue())
                .body("data.vercodeToken", notNullValue())
                .body("data.expireTime", equalTo(60));

        String vt = resp.jsonPath().getString("data.vercodeToken");
        String code = stringRedisTemplate.opsForValue().get(CS.getCacheKeyImgCode(vt));
        assertNotNull(code, "验证码应写入 Redis img_code_{token}");
        assertEquals(4, code.length(), "验证码应为 4 位字符");
    }

    /**
     * AUTH-002 ~ AUTH-005：登录参数缺失
     * AbstractCtrl.getValStringRequired 对 ia/ip/vc/vt 逐一必填校验，缺哪个就报哪个。
     */
    @ParameterizedTest
    @CsvSource({
            "ia, 参数有误[参数ia必填]",
            "ip, 参数有误[参数ip必填]",
            "vc, 参数有误[参数vc必填]",
            "vt, 参数有误[参数vt必填]"
    })
    void testMissingParam(String field, String expectedMsg) {
        Map<String, Object> params = buildLoginParams(NORMAL_ACCOUNT, TEST_PASSWORD, TEST_VERCODE, seedVercode(TEST_VERCODE));
        params.remove(field);

        given()
                .body(params)
        .when()
                .post("/api/anon/auth/validate")
        .then()
                .body("code", equalTo(11))
                .body("msg", equalTo(expectedMsg));
    }

    /**
     * AUTH-006：验证码错误
     * 验证码校验是登录第一道防线，在 auth() 之前执行，失败不会走到删验证码那一步。
     */
    @Test
    void testWrongVercode() {
        String vt = seedVercode("5678");

        given()
                .body(buildLoginParams(NORMAL_ACCOUNT, TEST_PASSWORD, "wrong", vt))
        .when()
                .post("/api/anon/auth/validate")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("验证码有误！"));

        // 校验失败未走到删除，验证码缓存应保留
        assertNotNull(stringRedisTemplate.opsForValue().get(CS.getCacheKeyImgCode(vt)));
    }

    /**
     * AUTH-007：验证码 token 无效/过期
     * Redis 查不到 img_code_{vt} → cacheCode 为空 → 同样报"验证码有误"。
     */
    @Test
    void testInvalidVercodeToken() {
        given()
                .body(buildLoginParams(NORMAL_ACCOUNT, TEST_PASSWORD, TEST_VERCODE, "invalid-vt"))
        .when()
                .post("/api/anon/auth/validate")
        .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("验证码有误！"));
    }

    /**
     * AUTH-008：验证码复用（一次性）
     * 登录成功后验证码被删除，再次用同一 vt 登录应被拒。
     */
    @Test
    void testVercodeReuse() {
        String vt = seedVercode(TEST_VERCODE);

        // 第一次登录成功
        doLogin(NORMAL_ACCOUNT, TEST_PASSWORD, TEST_VERCODE, vt)
                .then().body("code", equalTo(0));

        // 验证码已被删除
        assertNull(stringRedisTemplate.opsForValue().get(CS.getCacheKeyImgCode(vt)));

        // 第二次复用同一 vt → 验证码有误
        doLogin(NORMAL_ACCOUNT, TEST_PASSWORD, TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("验证码有误！"));
    }

    /**
     * AUTH-009：密码错误
     * BCrypt 比对失败 → BadCredentialsException → "用户名/密码错误！"
     */
    @Test
    void testWrongPassword() {
        String vt = seedVercode(TEST_VERCODE);

        doLogin(NORMAL_ACCOUNT, "WrongPass123", TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("用户名/密码错误！"));
    }

    /**
     * AUTH-010：用户不存在
     * 与密码错误同文案，防止通过错误信息差异枚举账号。
     */
    @Test
    void testUserNotExist() {
        String vt = seedVercode(TEST_VERCODE);

        doLogin("no_such_user", TEST_PASSWORD, TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("用户名/密码错误！"));
    }

    /**
     * AUTH-011：用户被禁用（state=0）
     */
    @Test
    void testUserDisabled() {
        String vt = seedVercode(TEST_VERCODE);

        doLogin("authdisabled", TEST_PASSWORD, TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("用户状态不可登录，请联系管理员！"));
    }

    /**
     * AUTH-012：商户被禁用（belong 的 mch_info.state=0）
     */
    @Test
    void testMerchantDisabled() {
        String vt = seedVercode(TEST_VERCODE);

        doLogin("authmchdisabled", TEST_PASSWORD, TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("商户状态停用，请联系管理员！"));
    }

    /**
     * AUTH-013：商户不存在（belongInfoId 无对应 mch_info，脏数据用户）
     */
    @Test
    void testMerchantNotExist() {
        String vt = seedVercode(TEST_VERCODE);

        doLogin("authnomch", TEST_PASSWORD, TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("所属商户为空，请联系管理员！"));
    }

    /**
     * AUTH-014：非超管且无菜单权限
     * 密码校验通过后，AuthService.auth 会再查 userHasLeftMenu，无菜单则拦截。
     */
    @Test
    void testNoMenuPermission() {
        String vt = seedVercode(TEST_VERCODE);

        doLogin("authnomenu", TEST_PASSWORD, TEST_VERCODE, vt)
                .then()
                .body("code", equalTo(9999))
                .body("msg", equalTo("当前用户未分配任何菜单权限，请联系管理员进行分配后再登录！"));
    }

    /**
     * AUTH-015：登录成功（主链路）
     * 验证：返回 iToken、Redis 生成 TOKEN_{uid}_{uuid}（TTL≈7200s）、验证码被删除。
     */
    @Test
    void testLoginSuccess() {
        String vt = seedVercode(TEST_VERCODE);

        Response resp = doLogin(NORMAL_ACCOUNT, TEST_PASSWORD, TEST_VERCODE, vt);
        resp.then().body("code", equalTo(0));

        String token = resp.jsonPath().getString("data.iToken");
        assertNotNull(token, "登录成功应返回 iToken");

        // 通过 JWT 解析出 Redis 缓存 key，验证 token 已落地
        String cacheKey = parseCacheKey(token);
        assertNotNull(cacheKey);
        assertNotNull(stringRedisTemplate.opsForValue().get(cacheKey), "token 缓存应写入 Redis");

        // Redis TTL 以秒为单位，读取瞬间可能已流逝不到 1 秒，等值断言会偶发 7199，用区间断言
        long ttl = stringRedisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= CS.TOKEN_TIME, "token 初始 TTL 应接近 7200s，实际=" + ttl);

        // 验证码一次性：登录成功后已删除
        assertNull(stringRedisTemplate.opsForValue().get(CS.getCacheKeyImgCode(vt)));
    }

    /**
     * AUTH-016：手机号登录成功
     * ia=手机号 → RegKit.isMobile 命中 → identityType=TELPHONE(2) 走手机号认证。
     */
    @Test
    void testPhoneLoginSuccess() {
        String vt = seedVercode(TEST_VERCODE);

        Response resp = doLogin("13800138000", TEST_PASSWORD, TEST_VERCODE, vt);
        resp.then().body("code", equalTo(0));

        assertNotNull(resp.jsonPath().getString("data.iToken"), "手机号登录成功应返回 iToken");
    }
}
