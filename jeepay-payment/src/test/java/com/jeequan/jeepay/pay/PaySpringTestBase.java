package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.pay.bootstrap.JeepayPayApplication;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayChannelNoticeService;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayChannelRefundNoticeService;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayPayOrderCloseService;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayRefundService;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 支付模块 Spring 集成测试统一基类
 *
 * 关键设计：所有 @SpringBootTest 用例共享这一个基类，把所有 @MockBean 统一声明在这里，
 * 保证全模块只有一个 Spring 上下文。否则每个类各声明不同 @MockBean，会生成多个上下文，
 * 后启动的上下文 RocketMQ listener 因资源冲突而启动失败（见工程问题日志）。
 */
@SpringBootTest(
        classes = JeepayPayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class PaySpringTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 支付渠道回调 mock：不连真实微信支付回调 */
    @MockBean
    protected WxpayChannelNoticeService wxpayChannelNoticeService;

    /** 退款下单渠道 mock：不连真实微信退款 */
    @MockBean
    protected WxpayRefundService wxpayRefundService;

    /** 退款渠道回调 mock：不连真实微信退款回调 */
    @MockBean
    protected WxpayChannelRefundNoticeService wxpayChannelRefundNoticeService;

    /** 关单渠道 mock：不连真实微信关单 */
    @MockBean
    protected WxpayPayOrderCloseService wxpayPayOrderCloseService;

    @BeforeEach
    void setUpBaseUri() {
        // 必须显式 setBaseUri + setContentType：
        // 1) RestAssured 的 requestSpecification 是 static，会被 PayApiTestBase（baseUri=9216）污染
        // 2) 退款下单等接口走 JSON body，缺 Content-Type 时服务端按表单解析导致 sign 读不到
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setBaseUri("http://localhost:" + port)
                .setContentType(ContentType.JSON)
                .build();
    }

    // ──── 支付回调 mock 快捷方法（供 SM-04/05 及回调模块复用） ────

    /** 模拟渠道解析出有效订单号 */
    protected void mockParseParams(String payOrderId) {
        MutablePair<String, Object> pair = new MutablePair<>();
        pair.setLeft(payOrderId);
        pair.setRight("mock params");
        when(wxpayChannelNoticeService.parseParams(
                any(HttpServletRequest.class), any(String.class), any()))
                .thenReturn(pair);
    }

    /** 模拟渠道返回【支付成功】 */
    protected void mockDoNoticeSuccess() {
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS);
        ret.setResponseEntity(ResponseEntity.ok("success"));
        when(wxpayChannelNoticeService.doNotice(
                any(HttpServletRequest.class), any(),
                any(com.jeequan.jeepay.core.entity.PayOrder.class),
                any(MchAppConfigContext.class), any()))
                .thenReturn(ret);
    }

    /** 模拟渠道返回【支付失败】 */
    protected void mockDoNoticeFail() {
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(ChannelRetMsg.ChannelState.CONFIRM_FAIL);
        ret.setResponseEntity(ResponseEntity.ok("fail"));
        when(wxpayChannelNoticeService.doNotice(
                any(HttpServletRequest.class), any(),
                any(com.jeequan.jeepay.core.entity.PayOrder.class),
                any(MchAppConfigContext.class), any()))
                .thenReturn(ret);
    }

    /** 模拟 parseParams 返回 null（解析异常） */
    protected void mockParseParamsNull() {
        when(wxpayChannelNoticeService.parseParams(
                any(HttpServletRequest.class), any(String.class), any()))
                .thenReturn(null);
    }

    /** 模拟订单不存在 */
    protected void mockDoNotifyOrderNotExists() {
        when(wxpayChannelNoticeService.doNotifyOrderNotExists(
                any(HttpServletRequest.class)))
                .thenReturn(ResponseEntity.ok("order not exists"));
    }
}
