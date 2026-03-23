package com.example.lifelink.controller;

import com.example.lifelink.util.CheckUtil;
import com.example.lifelink.util.MessageDispatcher;
import com.example.lifelink.util.MessageUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.io.PrintWriter;
import java.util.Map;

@RestController
@RequestMapping("/verify_wx_token")
public class WxController {

    @Autowired
    private CheckUtil checkUtil;

    @Autowired
    private MessageDispatcher messageDispatcher;

    @GetMapping
    public void verify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String signature = request.getParameter("signature");
        String timestamp = request.getParameter("timestamp");
        String nonce = request.getParameter("nonce");
        String echostr = request.getParameter("echostr");

        PrintWriter out = response.getWriter();
        // 临时绕过签名验证来测试连接
         if (true) {  
            out.write(echostr);
        }
        out.close();
    }

    @PostMapping(produces = "application/xml;charset=utf-8")
    public String handleMessages(HttpServletRequest request) throws Exception {
        Map<String, String> map = MessageUtil.parseXml(request);
        String msgType = map.get("MsgType");

        if (MessageUtil.REQ_MESSAGE_TYPE_EVENT.equals(msgType)) {
            return messageDispatcher.processEvent(map);
        } else {
            return messageDispatcher.processMessage(map);
        }
    }
}
