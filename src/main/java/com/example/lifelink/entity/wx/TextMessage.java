package com.example.lifelink.entity.wx;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TextMessage extends BaseMessage {
    private String Content;
}
