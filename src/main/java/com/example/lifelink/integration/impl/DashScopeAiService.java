package com.example.lifelink.integration.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.JsonUtils;
import com.example.lifelink.integration.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;

@Slf4j
@Service
public class DashScopeAiService implements AiService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Override
    public String generateAlertContent(String prompt) {
        try {
            Generation gen = new Generation();
            Message msg = Message.builder()
                    .role(Role.USER.getValue())
                    .content("请根据以下健康数据生成一条简短的家属报警短信内容，要求语气紧急且直接：" + prompt)
                    .build();
            GenerationParam param = GenerationParam.builder()
                    .model("qwen-turbo")
                    .messages(Collections.singletonList(msg))
                    .apiKey(apiKey)
                    .build();
            GenerationResult result = gen.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            log.error("Error calling DashScope AI", e);
            return "[紧急报警] 老人身体状况异常，请立即联系！"; // Fallback
        }
    }
}
