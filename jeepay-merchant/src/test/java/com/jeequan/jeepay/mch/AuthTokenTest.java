package com.jeequan.jeepay.mch;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证模块——token 鉴权链路用例（AUTH-017 ~ AUTH-022）
 *
 * 覆盖：无 token、篡改 token、过期 token、有效 token 访问 + 滑动续签、退出、退出后失效。
 *
 * 鉴权失败统一由 JeeAuthenticationEntryPoint 返回 HTTP 401（sendError），
 * 与登录业务失败（HTTP 200 + code=9999）是两套不同通道。
 */
public class AuthTokenTest extends AuthSpringTestBase {

    /**
     * AUTH-017：无 token 访问受保护接口
     * JeeAuthenticationTokenFilter 读不到 iToken → 放行 → 无认证 → 401。
     */
    @Test
    void testAccessWithoutToken() {
        given()
                .get("/api/current/user")
        .then()
                .statusCode(401);
    }

    /**
     * AUTH-018：篡改 token
     * 修改 JWT payload 首字符但不重新签名 → HS512 签名校验失败 → JWTUtils.parseToken 返回 null → 401。
     */
    @Test
    void testTamperedToken() {
        String token = loginAndGetToken(NORMAL_ACCOUNT, TEST_PASSWORD);

        // 篡改 payload 段（中间段）首字符，破坏签名一致性
        String[] parts = token.split("\\.");
        String payload = parts[1];
        String tamperedPayload = (payload.charAt(0) == 'e' ? 'f' : 'e') + payload.substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        given()
                .header("iToken", tampered)
                .get("/api/current/user")
        .then()
                .statusCode(401);
    }

    /**
     * AUTH-019：过期 token（Redis 中 token 缓存已不存在）
     * filter 能解析 JWT（签名有效），但 Redis getObject 返回 null → 删 key → 放行 → 401。
     */
    @Test
    void testExpiredToken() {
        String token = loginAndGetToken(NORMAL_ACCOUNT, TEST_PASSWORD);
        String cacheKey = parseCacheKey(token);

        // 模拟 token 过期：直接删除 Redis 里的缓存数据
        stringRedisTemplate.delete(cacheKey);

        given()
                .header("iToken", token)
                .get("/api/current/user")
        .then()
                .statusCode(401);
    }

    /**
     * AUTH-020：有效 token 访问成功 + 滑动续签
     * filter 每次命中都会 RedisUtil.expire(cacheKey, 7200)，把 TTL 续签回 2 小时。
     */
    @Test
    void testValidTokenAndRenewal() {
        String token = loginAndGetToken(NORMAL_ACCOUNT, TEST_PASSWORD);
        String cacheKey = parseCacheKey(token);
        assertNotNull(cacheKey);

        // 手动把 TTL 压到 100s，再访问接口，观察 filter 是否续签回 7200
        stringRedisTemplate.expire(cacheKey, 100, TimeUnit.SECONDS);

        given()
                .header("iToken", token)
                .get("/api/current/user")
        .then()
                .statusCode(200)
                .body("code", equalTo(0));

        long ttlAfter = stringRedisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttlAfter > 100, "访问后 TTL 应被续签回接近 7200，实际=" + ttlAfter);
    }

    /**
     * AUTH-021：退出登录
     * ITokenService.removeIToken 删除 Redis 里的 token 缓存。
     */
    @Test
    void testLogout() {
        String token = loginAndGetToken(NORMAL_ACCOUNT, TEST_PASSWORD);
        String cacheKey = parseCacheKey(token);

        given()
                .header("iToken", token)
                .post("/api/current/logout")
        .then()
                .statusCode(200)
                .body("code", equalTo(0));

        assertNull(stringRedisTemplate.opsForValue().get(cacheKey), "退出后 token 缓存应被删除");
    }

    /**
     * AUTH-022：退出后 token 失效
     * 用已退出的 token 再访问受保护接口 → 401，闭环验证 token 生命周期。
     */
    @Test
    void testTokenInvalidAfterLogout() {
        String token = loginAndGetToken(NORMAL_ACCOUNT, TEST_PASSWORD);

        // 先退出
        given()
                .header("iToken", token)
                .post("/api/current/logout")
        .then()
                .statusCode(200);

        // 再用同一 token 访问 → 401
        given()
                .header("iToken", token)
                .get("/api/current/user")
        .then()
                .statusCode(401);
    }
}
