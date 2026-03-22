package com.example.lifelink.controller;

import com.example.lifelink.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/family")
public class FamilyController {
    @Autowired
    private UserService userService;

    @PostMapping("/bind-code")
    public ResponseEntity<String> generateCode(@RequestParam Long elderlyId) {
        return ResponseEntity.ok(userService.generateBindingCode(elderlyId));
    }

    @PostMapping("/bind")
    public ResponseEntity<String> bind(@RequestBody Map<String, Object> payload) {
        Long familyId = Long.valueOf(payload.get("familyId").toString());
        String code = payload.get("code").toString();
        if (userService.bind(familyId, code)) {
            return ResponseEntity.ok("Binding successful");
        }
        return ResponseEntity.badRequest().body("Invalid code");
    }
}
