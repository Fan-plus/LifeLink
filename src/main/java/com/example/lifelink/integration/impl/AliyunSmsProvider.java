package com.example.lifelink.integration.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.lifelink.integration.SmsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AliyunSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsProvider.class);

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name}")
    private String signName;

    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    @Override
    public void send(String phone, String content) {
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "dysmsapi.aliyuncs.com";
            Client client = new Client(config);

            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"content\":\"" + content + "\"}"); // 需根据实际模板调整
            
            SendSmsResponse response = client.sendSmsWithOptions(sendSmsRequest, new RuntimeOptions());
            if (!"OK".equals(response.body.code)) {
                log.error("Failed to send SMS: {}", response.body.message);
            } else {
                log.info("Successfully sent SMS to {}", phone);
            }
        } catch (Exception e) {
            log.error("Error sending Aliyun SMS", e);
        }
    }
}
