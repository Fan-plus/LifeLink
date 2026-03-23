package com.example.lifelink.service;

import com.example.lifelink.entity.HealthData;
import com.example.lifelink.entity.Relationship;
import com.example.lifelink.entity.User;
import com.example.lifelink.integration.AiService;
import com.example.lifelink.integration.PushNotificationService;
import com.example.lifelink.integration.SmsProvider;
import com.example.lifelink.repository.RelationshipRepository;
import com.example.lifelink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AlertService {
    @Autowired
    private SmsProvider smsProvider;

    @Autowired
    private AiService aiService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private RelationshipRepository relationshipRepository;

    @Autowired
    private UserRepository userRepository;

    public void checkHealth(HealthData data) {
        System.out.println("==================== 进入健康检查方法 checkHealth");
        System.out.println("当前心率：" + data.getHeartRate());
        System.out.println("当前用户ID：" + data.getUserId());

        if (data.getHeartRate() != null && data.getHeartRate() > 120) {
            System.out.println("==================== 心率超标，开始查找监护人");

            // Find guardians
            List<Relationship> relationships = relationshipRepository.findByElderlyId(data.getUserId());
            System.out.println("找到监护人数量：" + relationships.size());

            for (Relationship rel : relationships) {
                System.out.println("==================== 遍历监护人关系");
                System.out.println("elderlyId：" + rel.getElderlyId());
                System.out.println("familyId：" + rel.getFamilyId());

                if (rel.getFamilyId() != null) {
                    System.out.println("familyId 不为空，开始查询用户");

                    userRepository.findById(rel.getFamilyId()).ifPresentOrElse(guardian -> {
                        System.out.println("==================== 成功找到监护人！");
                        System.out.println("监护人ID：" + guardian.getId());
                        System.out.println("监护人电话：" + guardian.getPhone());
                        System.out.println("监护人openId：" + guardian.getOpenId());
                        String aiContent = aiService.generateAlertContent("心率过高报警: " + data.getHeartRate());
                        String guardianPhone = guardian.getPhone();
                        if (guardianPhone != null) {
                            System.out.println("==================== 开始发送短信");
                            //String aiContent = aiService.generateAlertContent("心率过高报警: " + data.getHeartRate());
                            smsProvider.send(guardianPhone, aiContent);
                            System.out.println("短信发送完成");
                        }
                        else {
                            System.out.println("监护人电话为空，不发短信");
                        }
                        // Send WeChat Notification if openId exists
                        if (guardian.getOpenId() != null) {
                            System.out.println("==================== 开始发送微信模板消息");
                            pushNotificationService.sendNotification(
                                    guardian.getOpenId(),
                                    "老人健康报警",
                                    aiContent
                            );
                            System.out.println("微信消息发送完成");
                        } else {
                            System.out.println("监护人 openId 为空，不发微信");
                        }
                    }, () -> {
                        // 这里是找不到用户时会执行的！
                        System.out.println("==================== ERROR：没有找到 familyId 对应的用户！");
                        System.out.println("找不到的ID是：" + rel.getFamilyId());
                    });
                } else {
                    System.out.println("familyId 为空，跳过");
                }
            }
        } else {
            System.out.println("心率正常，不报警");
        }
    }
}
