// 生成认证模块测试用例清单 xlsx
// 运行：node docs/test/gen-auth-excel.js
const ExcelJS = require('exceljs');
const path = require('path');

const headers = [
    '编号', '模块', '场景描述', '前置条件', '输入参数',
    '预期HTTP状态码', '预期code(body)', '预期msg', '预期数据状态(Redis/DB)',
    '优先级', '测试类型', '执行结果', '备注'
];

// 每条用例：严格按 headers 顺序
const rows = [
    ['AUTH-001', '认证', '获取图形验证码成功', '无', 'GET /api/anon/auth/vercode', '200', '0', 'SUCCESS', 'Redis 写入 img_code_{vercodeToken}（值=4位验证码，TTL=60s）；响应返回 imageBase64Data、vercodeToken、expireTime=60', '低', '正常路径', '', '验证码生成与缓存'],
    ['AUTH-002', '认证', '登录参数缺失：缺 ia', '无', 'POST /api/anon/auth/validate，只传 ip/vc/vt，缺 ia', '200', '11', '参数有误[参数ia必填]', '无变化', '中', '异常路径', '', 'R12 参数缺失'],
    ['AUTH-003', '认证', '登录参数缺失：缺 ip', '无', 'POST validate，只传 ia/vc/vt，缺 ip', '200', '11', '参数有误[参数ip必填]', '无变化', '中', '异常路径', '', 'R12 参数缺失'],
    ['AUTH-004', '认证', '登录参数缺失：缺 vc', '无', 'POST validate，只传 ia/ip/vt，缺 vc', '200', '11', '参数有误[参数vc必填]', '无变化', '中', '异常路径', '', 'R12 参数缺失'],
    ['AUTH-005', '认证', '登录参数缺失：缺 vt', '无', 'POST validate，只传 ia/ip/vc，缺 vt', '200', '11', '参数有误[参数vt必填]', '无变化', '中', '异常路径', '', 'R12 参数缺失'],
    ['AUTH-006', '认证', '验证码错误', '先调 vercode 拿到有效 vt；账号密码正确', 'ia/ip 正确，vc=错误验证码，vt=有效值', '200', '9999', '验证码有误！', '不生成 token；验证码缓存保留（校验失败未走到删除）', '高', '异常路径', '', 'R11 验证码是登录第一道防线'],
    ['AUTH-007', '认证', '验证码过期/无效 vt', '无（vt 无效或已过 60s）', 'vc 任意，vt=无效值', '200', '9999', '验证码有误！', '无', '中', '异常路径', '', 'R11（Redis 查不到 → 报验证码有误）'],
    ['AUTH-008', '认证', '验证码复用（一次性）', '该 vt 已成功登录过一次（验证码已删除）', '再次用同一 vt + 对应 vc 登录', '200', '9999', '验证码有误！', 'img_code_{vt} 已被删除', '低', '异常路径', '', 'R14 验证码一次性'],
    ['AUTH-009', '认证', '密码错误', '有效验证码；账号存在', 'ia=正确账号，ip=错误密码，vc/vt 有效', '200', '9999', '用户名/密码错误！', '不生成 token', '高', '异常路径', '', 'R6 BCrypt 比对失败 → BadCredentialsException'],
    ['AUTH-010', '认证', '用户不存在', '有效验证码', 'ia=不存在的账号，ip 任意', '200', '9999', '用户名/密码错误！', '无', '中', '异常路径', '', 'R7 与密码错误同文案，防账号枚举'],
    ['AUTH-011', '认证', '用户被禁用', '有效验证码；存在 state=禁用 的用户', 'ia=禁用用户，ip=正确密码', '200', '9999', '用户状态不可登录，请联系管理员！', '无', '高', '异常路径', '', 'R8 停用账号拦截'],
    ['AUTH-012', '认证', '商户被禁用', '有效验证码；用户属于 state=禁用 的商户', 'ia=禁用商户下用户，ip=正确密码', '200', '9999', '商户状态停用，请联系管理员！', '无', '高', '异常路径', '', 'R9 商户停用拦截'],
    ['AUTH-013', '认证', '商户不存在', '有效验证码；用户 belongInfoId 无对应 mch_info', '该用户登录', '200', '9999', '所属商户为空，请联系管理员！', '无', '中', '异常路径', '', '数据一致性（脏数据用户）'],
    ['AUTH-014', '认证', '非超管无菜单权限', '有效验证码；isAdmin=NO 且无菜单的用户', '该用户登录', '200', '9999', '当前用户未分配任何菜单权限，请联系管理员进行分配后再登录！', '无', '中', '异常路径', '', 'R10 无菜单权限拦截'],
    ['AUTH-015', '认证', '登录成功', '有效验证码；正常可登录用户（超管或已分配菜单）', 'ia/ip/vc/vt 全部正确', '200', '0', 'SUCCESS', 'Redis 生成 TOKEN_{sysUserId}_{uuid}（TTL=7200s）；data 返回 iToken；img_code_{vt} 被删除', '高', '正常路径', '', '登录主链路'],
    ['AUTH-016', '认证', '手机号登录成功', '有效验证码；存在 identityType=手机号 的用户', 'ia=手机号，ip=正确密码，vc/vt 有效', '200', '0', 'SUCCESS', '生成 TOKEN key', '低', '正常路径', '', 'R13 手机号走 TELPHONE 身份类型'],
    ['AUTH-017', '认证', '无 token 访问受保护接口', '无', 'GET /api/current/user，header 不带 iToken', '401', '-', 'Unauthorized', '无', '高', '安全', '', 'R1 认证底线'],
    ['AUTH-018', '认证', '篡改 token', '无（伪造/篡改一个 token）', '带篡改过的 iToken 访问受保护接口', '401', '-', 'Unauthorized', '无', '高', '安全', '', 'R2 HS512 签名校验拦篡改'],
    ['AUTH-019', '认证', '过期 token', '登录拿 token，再删除/过期 Redis 的 TOKEN key', '带该过期 token 访问受保护接口', '401', '-', 'Unauthorized', 'Redis key 被删除（filter 中 del）', '高', '安全', '', 'R4 过期 token 拦截'],
    ['AUTH-020', '认证', '有效 token 访问成功 + 续签', '已登录拿有效 token', '带有效 iToken 访问 /api/current/user', '200', '0', 'SUCCESS', 'Redis TOKEN key TTL 被续签回 7200s', '高', '正常路径', '', 'R4 滑动过期续签'],
    ['AUTH-021', '认证', '退出登录', '已登录', 'POST /api/current/logout，带有效 iToken', '200', '0', 'SUCCESS', 'Redis TOKEN_{uid}_{uuid} 被删除', '中', '正常路径', '', 'token 生命周期'],
    ['AUTH-022', '认证', '退出后 token 失效', '已退出（Redis key 已删）', '用已退出的 token 访问受保护接口', '401', '-', 'Unauthorized', '无', '中', '异常路径', '', 'token 生命周期闭环'],
];

const widths = [10, 8, 24, 30, 32, 12, 12, 32, 38, 8, 10, 10, 24];

const PRIORITY_FILL = { '高': 'FFF2CC', '中': 'FCE4D6', '低': 'E2EFDA' }; // 高=黄 中=橙 低=绿

async function main() {
    const wb = new ExcelJS.Workbook();
    const ws = wb.addWorksheet('认证模块');

    ws.columns = headers.map((h, i) => ({ header: h, width: widths[i] }));

    // 表头样式
    const headerRow = ws.getRow(1);
    headerRow.font = { bold: true };
    headerRow.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'DDEBF7' } };
    headerRow.alignment = { vertical: 'middle', horizontal: 'center' };

    // 数据行
    rows.forEach((r) => ws.addRow(r));

    // 样式：文本自动换行 + 垂直居中；优先级着色
    ws.eachRow((row, rowNumber) => {
        if (rowNumber === 1) return;
        row.alignment = { vertical: 'middle', wrapText: true };
        const priority = row.getCell(10).value; // 第 10 列 = 优先级
        const fill = PRIORITY_FILL[priority];
        if (fill) {
            row.getCell(10).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: fill } };
        }
    });

    // 冻结表头
    ws.views = [{ state: 'frozen', ySplit: 1 }];

    const outPath = path.join(__dirname, '认证模块.xlsx');
    await wb.xlsx.writeFile(outPath);
    console.log('已生成：' + outPath);
    console.log('用例数：' + rows.length);
}

main().catch((e) => { console.error(e); process.exit(1); });
