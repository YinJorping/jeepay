package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.pay.bootstrap.JeepayPayApplication;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayChannelRefundNoticeService;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayRefundService;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 退款模块 Spring 集成测试基类
 *
 * 关键设计：把两个 @MockBean 统一声明在这里，让 RefundOrderSuccessTest / RefundOrderNoticeTest
 * 共享同一个 Spring 上下文。否则两个类各自声明不同 @MockBean，Spring 会起两个上下文，
 * 第二个上下文启动 RocketMQ listener 时因资源冲突而失败。
 */
@SpringBootTest(
        classes = JeepayPayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class RefundSpringTestBase {

    @LocalServerPort
    protected int port;

    /** 退款下单接口 mock：不连真实微信退款 */
    @MockBean
    protected WxpayRefundService wxpayRefundService;

    /** 退款回调接口 mock：不连真实微信回调 */
    @MockBean
    protected WxpayChannelRefundNoticeService wxpayChannelRefundNoticeService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpBaseUri() {
        // 退款下单接口走 JSON body，必须显式声明 Content-Type，否则服务端按表单解析导致 sign 读不到
        // 必须显式 setBaseUri：RestAssured 的 requestSpecification/baseURI 是 static，
        // 会被 PayApiTestBase（baseUri=9216）污染，导致打到 Docker 应用而不是本测试的随机端口
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setBaseUri("http://localhost:" + port)
                .setContentType(ContentType.JSON)
                .build();
    }
}
