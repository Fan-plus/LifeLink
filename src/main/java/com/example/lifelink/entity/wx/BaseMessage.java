package com.example.lifelink.entity.wx;

import lombok.Data;

@Data
public class BaseMessage {
    private String ToUserName;
    private String FromUserName;
    private long CreateTime;
    private String MsgType;
}
