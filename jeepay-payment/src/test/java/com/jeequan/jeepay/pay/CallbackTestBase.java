package com.jeequan.jeepay.pay;

import com.jeequan.jeepay.pay.bootstrap.JeepayPayApplication;
import com.jeequan.jeepay.pay.channel.wxpay.WxpayChannelNoticeService;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import io.restassured.RestAssured;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 回调模块测试基类
 *
 * 和支付模块的关键区别：
 *   支付模块：直接发 HTTP 请求到运行中的 Docker 服务（不需要 @SpringBootTest）
 *   回调模块：需要 @SpringBootTest + @MockBean 来替换支付渠道服务，
 *            启动一个内嵌的 Spring Boot 实例进行测试
 */
@SpringBootTest(
        classes = JeepayPayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class CallbackTestBase {

    @LocalServerPort
    private int port;

    /** 把 Spring 容器里的微信回调服务换成假的，不连真实微信 */
    @MockBean
    protected WxpayChannelNoticeService wxpayChannelNoticeService;

    @BeforeEach
    void setUpBaseUri() {
        RestAssured.baseURI = "http://localhost:" + port;
    }

    // ──── 快捷 mock 方法 ────

    /**
     * 模拟渠道解析出一个有效订单号
     */
    protected void mockParseParams(String payOrderId) {
        MutablePair<String, Object> pair = new MutablePair<>();
        pair.setLeft(payOrderId);
        pair.setRight("mock params");

        when(wxpayChannelNoticeService.parseParams(
                any(HttpServletRequest.class),
                any(String.class),
                any()))
                .thenReturn(pair);
    }

    /**
     * 模拟渠道返回【支付成功】
     * 三个字段都不能为 null：channelState / responseEntity
     */
    protected void mockDoNoticeSuccess() {
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(ChannelRetMsg.ChannelState.CONFIRM_SUCCESS);
        ret.setResponseEntity(ResponseEntity.ok("success"));

        when(wxpayChannelNoticeService.doNotice(
                any(HttpServletRequest.class),
                any(),
                any(com.jeequan.jeepay.core.entity.PayOrder.class),
                any(MchAppConfigContext.class),
                any()))
                .thenReturn(ret);
    }

    /**
     * 模拟 parseParams 返回 null（解析异常）
     */
    protected void mockParseParamsNull() {
        when(wxpayChannelNoticeService.parseParams(
                any(HttpServletRequest.class),
                any(String.class),
                any()))
                .thenReturn(null);
    }

    /**
     * 模拟订单不存在
     */
    protected void mockDoNotifyOrderNotExists() {
        when(wxpayChannelNoticeService.doNotifyOrderNotExists(
                any(HttpServletRequest.class)))
                .thenReturn(ResponseEntity.ok("order not exists"));
    }

    /**
     * 模拟渠道返回【支付失败】
     */
    protected void mockDoNoticeFail() {
        ChannelRetMsg ret = new ChannelRetMsg();
        ret.setChannelState(ChannelRetMsg.ChannelState.CONFIRM_FAIL);
        ret.setResponseEntity(ResponseEntity.ok("fail"));

        when(wxpayChannelNoticeService.doNotice(
                any(HttpServletRequest.class),
                any(),
                any(com.jeequan.jeepay.core.entity.PayOrder.class),
                any(MchAppConfigContext.class),
                any()))
                .thenReturn(ret);
    }
}
