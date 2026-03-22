package com.example.lifelink.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class CheckUtil {
    
    @Value("${wechat.token}")
    private String token;

    public boolean checkSignature(String signature, String timestamp, String nonce) {
        String[] str = new String[]{token, timestamp, nonce};
        //排序
        Arrays.sort(str);
        //拼接字符串
        StringBuilder buffer = new StringBuilder();
        for (String s : str) {
            buffer.append(s);
        }
        //进行sha1加密
        String temp = SHA1.encode(buffer.toString());
        //与微信提供的signature进行匹对
        return signature != null && signature.equals(temp);
    }
}
