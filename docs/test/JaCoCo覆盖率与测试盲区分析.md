# JaCoCo 覆盖率与测试盲区分析

> 目标：用覆盖率结果反向评估测试范围、识别测试盲区，并区分「合理未覆盖」与「真实测试盲区」。不追求覆盖率数字。

---

## 一、真实覆盖率结果（2026-09-15 最终实测）

### 1.1 模块级：补测前后对比

| 指标 | payment 补测前（09-03） | payment 补测后（09-15） | merchant（未变化） |
|---|---|---|---|
| 指令覆盖 | 12.8%（3533/27584） | 15.0%（4146/27584） | 22.5%（1323/5884） |
| 分支覆盖 | 9.9%（207/2098） | 11.9%（249/2098） | 10.4%（44/422） |
| 行覆盖 | 12.4%（794/6407） | 14.3%（918/6407） | 23.8%（294/1233） |
| 方法覆盖 | 32.8%（281/856） | 34.7%（297/856） | 47.2%（101/214） |
| 分析类数 | 216 | 216 | 49 |

> ⚠️ **模块级数字存在波动，不能当作本轮补测的收益。** payment 的增量里大部分来自测试运行期间**后台 `@Scheduled` 定时任务**被触发，与补测无关：
> - `ChannelOrderReissueService`：指令覆盖 7 → 88（+3 方法）
> - `PayOrderExpiredTask` / `RefundOrderExpiredTask`：指令覆盖 7 → 18
>
> 所以本文只采信「按类 / 按方法」的定位结果。**这些百分比不是项目质量 KPI**，只用于回答「哪一行没跑到、为什么」。

### 1.2 方法级：本轮 3 条补测真实点亮的行

| 类.方法 | 指令 missed/cov | 分支 missed/cov | 行 missed/cov |
|---|---|---|---|
| `PayMchNotifyService.refundOrderNotify` | 106/5 → **16/95** | 3/1 → **1/3** | 28/2 → **7/23** |
| `PayMchNotifyService.createNotifyUrl(RefundOrder)` | 25/0 → **0/25** | — | 5/0 → **0/5** |
| `RefundOrderProcessService.handleRefundOrder4Channel` | 17/43 → **8/52** | 3/7 → **2/8** | 2/10 → **1/11** |
| `AbstractPayOrderController.processChannelMsg` | 43/30 → **27/46** | 12/6 → **10/8** | 9/8 → **6/11** |
| `AbstractPayOrderController.packageApiResByPayOrder` | 20/28 → **2/46** | 5/1 → **2/4** | 2/5 → **0/7** |

**两项「没有变化」，必须说清（否则会误判）：**
- `PayOrderProcessService.updateIngAndSuccessOrFailByCreatebyOrder`：20/28 → **无变化**。INIT→SUCCESS 的差异发生在**未被 JaCoCo 采集的 `jeepay-service`**（`PayOrderService.updateIng2SuccessOrFail`），payment 侧执行的**行是同一条**。
- `PayOrderProcessService.confirmSuccess`：0/20 → **无变化**（早已被回调路径覆盖）。

### 1.3 边界：CAS 的行为不是 JaCoCo 证明的

JaCoCo 只采集 `jeepay-payment` / `jeepay-merchant`。**CAS 的 SQL 位于 `jeepay-service`**（`PayOrderService:71-121`、`RefundOrderService:65-122`），该模块**未被采集**。

因此要分开讲：
- **JaCoCo 覆盖证据**：只能证明 payment 层「调用 CAS 的那一行」被执行（如 `ChannelNoticeController:237`、`RefundOrderProcessService:46`）。
- **测试结果 / 数据库断言证据**：CAS 的真实防护行为由以下用例的 DB 断言证明——
  - `PayOrderIdempotencyTest.testDuplicateRefundCallback`：两次回调后 `refund_amount` = 30（非 60）
  - `ChannelCallbackTest.testSuccessOrderRepeatCallback` / `testFailOrderReceiveSuccessCallback` / `testClosedOrderReceiveSuccessCallback`：重复回调后 state 保持 2 / 3 / 6
  - `PayOrderIdempotencyTest.testDuplicateCloseOrder`：重复关单 state 保持 6，渠道 close 仅调 1 次

**面试如实说：CAS 的防护由数据库断言证明，不是 JaCoCo。**

---

## 二、payment 与 merchant 的差异及原因

| 维度 | jeepay-payment | jeepay-merchant |
|---|---|---|
| 代码体量 | 6407 行、216 类（巨大） | 1233 行、49 类（小） |
| 主要构成 | 12+ 渠道实现 + 工具类 + DTO + 核心 Controller/Service | 认证链路 + 少量后台管理 |
| 行覆盖 | 14.3%（被渠道/工具/DTO 严重稀释） | 23.8%（相对集中） |
| 核心链路触达 | 部分核心入口方法级 100%（如 `UnifiedOrderController.unifiedOrder` 12/12），但类级差异大（`AbstractPayOrderController` 60.4%、`ChannelNoticeController` 35.7%） | 认证核心类较高（`AuthController` 100%、`JeeUserDetailsServiceImpl` 94.7%） |

**结论**：merchant 覆盖高于 payment，主因**不是**「认证测得更好」，而是「payment 模块被大量测试范围之外的代码（渠道/工具/DTO）稀释了」。但**不能反推「核心链路两者都触达充分」**——payment 里仍有类级行覆盖 35.7% 的核心入口。

---

## 三、为什么整体覆盖率不高

1. **12+ 支付渠道实现类从未被执行**：测试只 mock 了 `wxpay` 一条链路，alipay/ysfpay/plspay/xxpay/pppay 的 PaymentService/RefundService/NoticeService/Kit 全部 0 覆盖——这些占 payment 大量行数。
2. **被 mock 的 wxpay 真实实现**：`WxpayPaymentService`、`WxpayRefundService` 等真实渠道实现行覆盖仅 1%~2%，因为测试用 `@MockBean` 替换了它们（隔离外部依赖的必然代价）。
3. **工具类/加密/HTTP/二维码**：`WxpayKit`、`AlipayKit`、`YsfSignUtils`、`YsfHttpUtil`、`CodeImgUtil` 等全部 0 覆盖。
4. **DTO/RQ/RS 数据类**：`rqrs` 包大量 POJO（getter/setter）0 覆盖。
5. **非核心功能**：分账（division）、转账（transfer）、支付测试（paytest）、系统用户管理（sysuser）、WebSocket 等明确不在 5 模块测试范围。

---

## 四、核心业务区域覆盖明细（2026-09-15 补测后实测）

> 下表为**类级**数字。**核心入口的定义**：4 条核心链路（支付/回调/退款/认证）的 Controller 入口方法 + 其直接调用的流程 Service 方法。数据来源：各模块 `target/site/jacoco/jacoco.csv`。
> 说明：类级数字低于方法级，因为类里还包含大量异常分支与范围外方法。

### 支付

| 核心区域 | 核心类 | 行覆盖 | 分支覆盖 | 方法覆盖 | 风险判断 |
|---|---|---|---|---|---|
| 统一下单 | UnifiedOrderController | 79.2% | 50% | 100% | 低 |
| 统一下单处理 | AbstractPayOrderController | 60.4% | 44.2% | 100% | 低-中（同步成功/失败已补；SYS_ERROR/竞态兜底未覆盖） |
| 支付回调 | ChannelNoticeController | 35.7% | 32.8% | 50% | 中（doReturn 未测 + doNotify 部分分支） |
| 关单 | CloseOrderController | 71.4% | 68.8% | 100% | 低 |
| 查询 | QueryOrderController | 90% | 75% | 100% | 低 |
| 通用 API | ApiController | 92.6% | 75% | 100% | 低 |
| 订单处理 | PayOrderProcessService | 51.7% | 41.7% | 100% | 低-中（分账分支属范围外） |
| 商户通知 | PayMchNotifyService | 52.6% | 50% | 66.7% | 低（退款通知已补；transferOrderNotify 属范围外） |

### 退款

| 核心区域 | 核心类 | 行覆盖 | 分支覆盖 | 方法覆盖 | 风险判断 |
|---|---|---|---|---|---|
| 退款下单 | RefundOrderController | 78% | 56% | 100% | 低 |
| 退款处理 | RefundOrderProcessService | 92.9% | 80% | 100% | 低（退款通知调用已补） |
| 退款回调 | ChannelRefundNoticeController | 54.5% | 55% | 100% | 低（未覆盖集中在异常分支） |
| 退款查询 | QueryRefundOrderController | 100% | 100% | 100% | 无 |

### 认证

| 核心区域 | 核心类 | 行覆盖 | 分支覆盖 | 方法覆盖 | 风险判断 |
|---|---|---|---|---|---|
| 登录入口 | AuthController | 100% | 100% | 100% | 无 |
| 登录逻辑 | AuthService | 42% | 15% | 55.6% | 低（未覆盖集中在异常分支 + 冗余防御，见第五节） |
| 用户详情（5 层校验） | JeeUserDetailsServiceImpl | 94.7% | 91.7% | 100% | 无 |
| token 过滤 | JeeAuthenticationTokenFilter | 100% | 91.7% | 100% | 无 |
| 安全配置 | WebSecurityConfig | 100% | 50% | 100% | 无（未覆盖仅 1 个分支） |

---

## 五、合理未覆盖 vs 真实测试盲区

### A. 合理未覆盖（不补测）

| 类别 | 代表类 | 为什么合理 |
|---|---|---|
| 测试范围外渠道 | alipay/ysfpay/plspay/xxpay/pppay 全部实现 | 测试聚焦 wxpay 单链路，mock 隔离外部依赖 |
| 被 mock 的真实渠道 | WxpayPaymentService / WxpayRefundService 等 | @MockBean 替换后真实实现永不执行，这是设计使然 |
| 工具/加密/HTTP/二维码 | WxpayKit / AlipayKit / YsfHttpUtil / CodeImgUtil | 与核心业务链路无关，属底层基础设施 |
| DTO/数据类 | rqrs 包全部 POJO | getter/setter，无业务逻辑 |
| 非核心功能 | 分账/转账/支付测试/系统用户/WebSocket | 明确不在 5 模块测试范围 |
| 同步跳转 | ChannelNoticeController.doReturn | 不落库、不涉及资金/状态，风险低 |
| 认证缓存管理 | AuthService.refAuthentication / delAuthentication | 调用方全在 sysuser（系统用户管理）控制器，属明确排除范围 |

### B. 真实测试盲区 → 证据链与处置（2026-09-15 已完成）

| 优先级 | 盲区 | 证据链（数据 → 源码条件 → 未覆盖行 → 风险） | 处置 |
|---|---|---|---|
| P1 | 退款成功后商户通知链路 | 退款测试单 `notify_url` 为空（`prepare-refund-data.sql` 的 `t_refund_order` INSERT 无该列）→ `RefundOrderProcessService:49` 的 `isNotEmpty` 为假，`:50` 不被调用；即便进入，`PayMchNotifyService:108` 空值判断为真、`L109` 直接 return（JaCoCo：指令 5/111、行 2/30）→ 退款结果不送达商户 | ✅ **已补 1 条**（`RefundOrderNoticeTest.testRefundCallbackSuccessNotifiesMerchant`），断言 `t_mch_notify_record` 真实生成 |
| P1 | 支付下单时渠道**同步**返回成功/失败 | 现有测试只 mock `ChannelRetMsg.waiting()`，只走 `processChannelMsg:L347` 的 WAITING 分支 → `L336/L339/L344` 未执行（JaCoCo：行 8/17）→ 条码/付款码「下单即成功/失败」路径未验证 | ✅ **已补 2 条**（`UnifiedOrderSuccessTest.testUnifiedOrderSyncSuccess` / `testUnifiedOrderSyncFail`），断言 state=2 / state=3 |
| P2 | `AuthService.refAuthentication` / `delAuthentication` | 调用方全在 sysuser 控制器（`SysRoleController:185`、`SysUserController:210/217/259`） | ❌ 归入 **A 类范围外**，不补 |
| P2 | `AuthService:108/109`「当前商户状态不可用」 | 被更早的 `JeeUserDetailsServiceImpl:95-96` 拦截（`testMerchantDisabled` 已覆盖那条） | ❌ **不可达的冗余防御**，不补 |
| P2 | 支付回调竞态兜底 `ChannelNoticeController:L248-249` | 需真并发下 CAS 输掉竞争才可达；重复回调已由 `L232` 的 state 检查 + CB-03/04/05 覆盖 | ❌ 单线程测不到，不补 |

**为什么不再继续补其他覆盖**：
- 剩下的未覆盖集中在 **A 类范围外**（12+ 渠道实现、工具类、DTO、分账/转账/sysuser/WebSocket、同步跳转）——补它们只是抬数字，不产生业务风险价值。
- 其余是**异常分支 / 冗余防御 / 需真并发的竞态路径**——要么测不到，要么测了也不改结论。
- 本轮**只补了 3 条**，每条都对应一个真实业务风险，**不以提高覆盖率为目标**。

---

## 六、当前测试范围是否足以支撑项目定位

**足以支撑，但表述必须严谨。**

「业务测试深度实践型 SDET 项目」的定位核心是：**核心业务链路的深度测试**，而非「全量代码覆盖率」。当前 **payment 65 + merchant 22 = 87 条**用例已覆盖：

- 统一下单（16 条参数校验 + 签名 + 正常路径）
- 下单同步成功/失败（2 条，INIT→ING→SUCCESS/FAIL 两步 CAS 链）
- 支付回调（9 条，doNotify 状态机 + 幂等）
- 退款（19 条，金额校验 + 状态流转 + SQL 对账）
- 退款商户通知（1 条，退款结果送达商户）
- 认证（22 条，5 层登录拦截 + token 全生命周期）
- 状态机（6 条完整转换矩阵）+ 幂等（4 条）+ 支付通知链路（4 条）

**严谨表述**（不使用「核心入口行覆盖大多 79%~100%」这种没有清单支撑的说法）：

> 部分已识别的核心业务入口具有较高的类/方法级覆盖——例如 `AuthController` 类级行覆盖 100%、`JeeUserDetailsServiceImpl` 94.7%、`JeeAuthenticationTokenFilter` 100%、`UnifiedOrderController` 类级 79.2%；但**也存在明显偏低的核心入口**：`AbstractPayOrderController.unifiedOrder` 方法级行覆盖 32/78（41%，因类内含收银台/QR 等多种支付方式入口）、`ChannelNoticeController` 类级 35.7%、`AuthService` 类级 42%。**因此不能据此代表整个模块覆盖充分。**

剩余未覆盖属：**A 类范围外代码**（12+ 渠道实现 / 工具 / DTO / 分账·转账·sysuser·WebSocket / 同步跳转）＋**异常分支与冗余防御**＋**需真并发的竞态路径**，属深度补充，不是根基缺失。

---

## 七、后续测试建议

**2026-09-15 已完成：**

1. ✅ **P1 — 退款通知链路**：已补 1 条 → `RefundOrderNoticeTest.testRefundCallbackSuccessNotifiesMerchant`
2. ✅ **P1 — 支付下单同步成功/失败**：已补 2 条 → `UnifiedOrderSuccessTest.testUnifiedOrderSyncSuccess` / `testUnifiedOrderSyncFail`
3. ❌ **P2 — 认证缓存管理**：复核后确认调用方全在 sysuser（范围外），**不再补**

**仍不建议补的**（保持原判断）：

- `processChannelMsg` 的 SYS_ERROR / 补偿重发 MQ 分支（异常兜底）
- 支付回调竞态兜底 `ChannelNoticeController:L248-249`（需真并发才可达）
- 各类异常 catch 分支

> 以上均不涉及「提高覆盖率数字」，只针对核心业务真实风险点。

---

## 八、面试话术（封板版）

**先记录两个被废弃的说法（避免再用）：**

> ~~方法覆盖率 33%/47% 说明核心入口都摸到了。~~ ❌ 把「模块整体方法覆盖率」等同于「核心业务入口覆盖率」了。
>
> ~~核心入口行覆盖大多在 79%~100%。~~ ❌ 没给出「核心入口」的定义和清单，且实际存在 41% 的核心入口（`AbstractPayOrderController.unifiedOrder` 方法级 32/78）。

**封板版（每条都能被 JaCoCo 数据或数据库断言支撑，不会被追问击穿）：**

> 我不拿模块整体覆盖率说事。payment 15.0%、merchant 22.5% —— 这个数字会被 12 个渠道实现、加密工具类、几百个 DTO 稀释，而且会随**后台定时任务在测试期间是否触发**而波动（`ChannelOrderReissueService` 的指令覆盖在两次运行间就从 7 跳到 88）。所以我按类、按方法看。
>
> **第一步，用方法级数据证明核心入口真的摸到了** —— 不是笼统说「基本都覆盖了」，而是逐个给数：`AuthController` 类级行覆盖 100%、`JeeUserDetailsServiceImpl` 94.7%、`JeeAuthenticationTokenFilter` 100%、`UnifiedOrderController.unifiedOrder` 方法级 12/12。
>
> **第二步，用行级数据找真盲区，并且给出证据链。** 比如退款商户通知：我查测试数据发现 `t_refund_order` 的 INSERT 根本没有 `notify_url` 列 → 源码 `RefundOrderProcessService:49` 的 `isNotEmpty` 因此为假 → 通知方法进去了也当场 return，JaCoCo 显示这段指令只覆盖 5/111。这不是「覆盖率高不高」，是**一条已经对外承诺的链路从来没被执行过**。定位根因后补 1 条用例，指令覆盖 5/111 → 95/111。
>
> **第三步，该不补的坚决不补。** `AuthService` 里那句「当前商户状态不可用」完全没跑到，但查下来它被更早的 `JeeUserDetailsServiceImpl:95` 拦住了，是**不可达的冗余防御**，补它没意义。`AuthService.refAuthentication` 也没覆盖，但调用方全在系统用户管理模块，本来就在我测试范围外。**没跑到 ≠ 有风险，得看代码为什么没跑到。**
>
> **最后一个边界我会主动说清：CAS 的防护不是 JaCoCo 证明的。** CAS 的 SQL 在 `jeepay-service`，而 JaCoCo 只扫了 payment 和 merchant 两个模块。所以「重复回调不会重复累加退款金额」这个结论，我是用**数据库断言**证明的 —— 两次回调后 `refund_amount` 仍是 30 而不是 60。JaCoCo 只证明了 payment 层「调用 CAS 的那一行」被执行。
