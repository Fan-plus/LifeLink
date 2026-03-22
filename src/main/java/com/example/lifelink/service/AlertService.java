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
        if (data.getHeartRate() != null && data.getHeartRate() > 120) {
            // Find guardians
            List<Relationship> relationships = relationshipRepository.findByElderlyId(data.getUserId());
            for (Relationship rel : relationships) {
                if (rel.getFamilyId() != null) {
                    userRepository.findById(rel.getFamilyId()).ifPresent(guardian -> {
                        String guardianPhone = guardian.getPhone();
                        if (guardianPhone != null) {
                            String aiContent = aiService.generateAlertContent("心率过高报警: " + data.getHeartRate());
                            smsProvider.send(guardianPhone, aiContent);
                            
                            // Send WeChat Notification if openId exists
                            if (guardian.getOpenId() != null) {
                                pushNotificationService.sendNotification(
                                    guardian.getOpenId(), 
                                    "老人健康报警", 
                                    aiContent
                                );
                            }
                        }
                    });
                }
            }
        }
    }
}
