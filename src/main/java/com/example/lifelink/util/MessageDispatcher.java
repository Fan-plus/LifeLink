package com.example.lifelink.util;

import com.example.lifelink.entity.wx.TextMessage;
import com.example.lifelink.service.UserService;
import com.example.lifelink.integration.PushNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.Map;

@Component
public class MessageDispatcher {

    @Autowired
    private UserService userService;

    @Autowired
    private PushNotificationService pushNotificationService;

    public String processMessage(Map<String, String> map) {
        String openid = map.get("FromUserName");
        String mpid = map.get("ToUserName");
        String msgType = map.get("MsgType");

        if (MessageUtil.REQ_MESSAGE_TYPE_TEXT.equals(msgType)) {
            String content = map.get("Content");
            
            // Handle binding invitation code
            if (content != null && content.length() == 6) {
                if (userService.bindWeChat(openid, content)) {
                    // 发送绑定成功的模板消息
                    sendBindingSuccessNotification(openid);
                    return createTextResponse(openid, mpid, "绑定成功！您现在可以接收老人的健康提醒了。");
                } else {
                    return createTextResponse(openid, mpid, "绑定失败，请检查邀请码是否正确或是否已过期。");
                }
            }

            return createTextResponse(openid, mpid, "欢迎使用 LifeLink！请输入老人的 6 位邀请码进行绑定。");
        }
        return null;
    }

    public String processEvent(Map<String, String> map) {
        String openid = map.get("FromUserName");
        String mpid = map.get("ToUserName");
        String eventType = map.get("Event");

        if (MessageUtil.EVENT_TYPE_SUBSCRIBE.equals(eventType)) {
            return createTextResponse(openid, mpid, "感谢关注 LifeLink！在这里您可以实时守护家人的健康。\n\n请输入老人的 6 位邀请码进行账号绑定。");
        }
        return null;
    }

    private String createTextResponse(String toUser, String fromUser, String content) {
        TextMessage txtmsg = new TextMessage();
        txtmsg.setToUserName(toUser);
        txtmsg.setFromUserName(fromUser);
        txtmsg.setCreateTime(new Date().getTime());
        txtmsg.setMsgType(MessageUtil.RESP_MESSAGE_TYPE_TEXT);
        txtmsg.setContent(content);
        return MessageUtil.textMessageToXml(txtmsg);
    }

    private void sendBindingSuccessNotification(String openId) {
        try {
            pushNotificationService.sendNotification(
                openId,
                "绑定成功通知",
                "恭喜您成功绑定LifeLink！现在您可以接收到家人的健康提醒了。"
            );
        } catch (Exception e) {
            // 记录错误但不影响绑定流程
            System.err.println("发送绑定成功通知失败: " + e.getMessage());
        }
    }
}
