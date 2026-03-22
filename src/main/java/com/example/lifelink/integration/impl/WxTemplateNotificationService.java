package com.example.lifelink.integration.impl;

import com.example.lifelink.integration.PushNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WxTemplateNotificationService implements PushNotificationService {

    @Value("${wechat.app-id}")
    private String appId;

    @Value("${wechat.app-secret}")
    private String appSecret;

    @Value("${wechat.template-id}")
    private String templateId;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Access Token 缓存 ──────────────────────────────────────────
    // 微信 access_token 有效期 7200秒（2小时），每天只能刷新2000次
    // 这里用内存缓存，距过期30分钟内才重新获取

    private String cachedAccessToken = null;
    private long tokenExpireTimeMs = 0; // 过期的毫秒时间戳

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        // 距过期还差30分钟(1800秒)以上，直接用缓存
        if (cachedAccessToken != null && now < tokenExpireTimeMs - 30 * 60 * 1000L) {
            return cachedAccessToken;
        }

        // 缓存为空或即将过期，重新获取
        log.info("Refreshing WeChat access_token...");
        String tokenUrl = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appId, appSecret);

        try {
            Map<String, Object> tokenRes = restTemplate.getForObject(tokenUrl, Map.class);
            if (tokenRes == null || tokenRes.containsKey("errcode")) {
                log.error("Failed to get access_token: {}", tokenRes);
                throw new RuntimeException("WeChat access_token 获取失败: " + tokenRes);
            }
            cachedAccessToken = (String) tokenRes.get("access_token");
            // 微信返回 expires_in 单位是秒（通常是7200）
            int expiresIn = ((Number) tokenRes.get("expires_in")).intValue();
            tokenExpireTimeMs = now + expiresIn * 1000L;
            log.info("access_token 刷新成功，有效期 {} 秒", expiresIn);
        } catch (Exception e) {
            log.error("获取微信 access_token 异常", e);
            throw new RuntimeException("获取微信 access_token 失败", e);
        }
        return cachedAccessToken;
    }
    // ──────────────────────────────────────────────────────────────

    @Override
    public void sendNotification(String openId, String title, String message) {
        // 1. 获取 access_token（走缓存，不会每次都请求微信服务器）
        String accessToken = getAccessToken();

        // 2. 发送模板消息
        String sendUrl = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + accessToken.trim();

        // 核心技术点：Spring 6.1+ 默认不设置 Content-Length，导致微信网关返回 412。必须手动计算。
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Spring-RestTemplate/LifeLink");

        // 构建消息体
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("touser", openId);
        bodyMap.put("template_id", templateId);
        bodyMap.put("url", ""); 

        Map<String, Object> data = new HashMap<>();
        data.put("first",    createValueMap(title));
        data.put("keyword1", createValueMap("健康异常"));
        data.put("keyword2", createValueMap(message));
        data.put("remark",   createValueMap("请尽快联系老人！"));
        bodyMap.put("data", data);

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonBody = mapper.writeValueAsString(bodyMap);
            byte[] bytes = jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            headers.setContentLength(bytes.length);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(jsonBody, headers);
            
            String result = restTemplate.postForObject(sendUrl, entity, String.class);
            if (result != null && result.contains("\"errcode\":0")) {
                log.info("WeChat Template Message sent successfully to {}", openId);
            } else {
                log.error("WeChat API error: {}", result);
            }
        } catch (Exception e) {
            log.error("Failed to send WeChat message: {}", e.getMessage());
            if (e instanceof org.springframework.web.client.HttpClientErrorException) {
                log.error("HTTP Error Body: {}", ((org.springframework.web.client.HttpClientErrorException)e).getResponseBodyAsString());
            }
        }
    }

    private Map<String, String> createValueMap(String value) {
        Map<String, String> map = new HashMap<>();
        map.put("value", value == null ? "" : value);
        return map;
    }
}
