package com.jeequan.jeepay.pay;

import java.security.MessageDigest;
import java.util.*;

public class SignUtils {

    /**
     * 计算签名：与 JeepayKit.getSign() 算法一致
     * 1. 参数拼成 key=value&  2. 字母排序  3. 末尾加 key=appSecret  4. MD5 转大写
     */
    public static String getSign(Map<String, Object> params, String appSecret) {
        ArrayList<String> list = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null && !"".equals(entry.getValue().toString())
                    && !"sign".equals(entry.getKey())) {  // 跟服务端一样，跳过 sign 字段
                list.add(entry.getKey() + "=" + entry.getValue() + "&");
            }
        }

        String[] arr = list.toArray(new String[0]);
        Arrays.sort(arr, String.CASE_INSENSITIVE_ORDER);

        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            sb.append(s);
        }
        sb.append("key=").append(appSecret);

        return md5(sb.toString()).toUpperCase();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
