package com.example.lifelink.service;

import com.example.lifelink.entity.Relationship;
import com.example.lifelink.entity.User;
import com.example.lifelink.repository.RelationshipRepository;
import com.example.lifelink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RelationshipRepository relationshipRepository;

    public User register(User user) {
        return userRepository.save(user);
    }

    public Optional<User> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getPassword().equals(password));
    }

    public void updateDeviceToken(Long userId, String token) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setDeviceToken(token);
            userRepository.save(user);
        });
    }

    public String generateBindingCode(Long elderlyId) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Relationship relationship = new Relationship();
        relationship.setElderlyId(elderlyId);
        relationship.setBindingCode(code);
        relationshipRepository.save(relationship);
        return code;
    }

    public boolean bindWeChat(String openId, String code) {
        return bind(openId, code, true);
    }

    public boolean bind(Long familyId, String code) {
        Optional<Relationship> optionalRelationship = relationshipRepository.findByBindingCode(code);
        if (optionalRelationship.isPresent()) {
            Relationship relationship = optionalRelationship.get();
            relationship.setFamilyId(familyId);
            relationship.setBindingCode(null);
            relationshipRepository.save(relationship);
            return true;
        }
        return false;
    }

    private boolean bind(String identifier, String code, boolean isWeChat) {
        Optional<Relationship> optionalRelationship = relationshipRepository.findByBindingCode(code);
        if (optionalRelationship.isPresent()) {
            Relationship relationship = optionalRelationship.get();
            User familyUser;
            if (isWeChat) {
                Optional<User> optionalUser = userRepository.findByUsername(identifier);
                if (optionalUser.isPresent()) {
                    familyUser = optionalUser.get();
                    // 确保openId被设置
                    if (familyUser.getOpenId() == null) {
                        familyUser.setOpenId(identifier);
                        userRepository.save(familyUser);
                    }
                } else {
                    familyUser = new User();
                    familyUser.setUsername(identifier);
                    familyUser.setOpenId(identifier);
                    familyUser.setRole(User.Role.FAMILY);
                    familyUser.setPassword("WECHAT_USER");
                    userRepository.save(familyUser);
                }
            } else {
                familyUser = userRepository.findById(Long.valueOf(identifier)).orElse(null);
            }

            if (familyUser != null) {
                relationship.setFamilyId(familyUser.getId());
                relationship.setBindingCode(null);
                relationshipRepository.save(relationship);
                return true;
            }
        }
        return false;
    }
}
