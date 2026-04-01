//package com.linkjb.aimed;
//
//import com.linkjb.aimed.assistant.Assistant;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.service.AiServices;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.test.context.SpringBootTest;
//
//@SpringBootTest
//public class AIServiceTest {
//    @Autowired
//    @Qualifier("onlineChatModel")
//    private ChatModel onlineChatModel;
//    @Test
//    public void testChat(){
//        Assistant assistant = AiServices.create(Assistant.class, onlineChatModel);
//        String answer = assistant.chat("你是谁");
//        System.out.println(answer);
//    }
//
//    @Autowired
//    private Assistant assistant;
//    @Test
//    public void testAssistant(){
//        String answer = assistant.chat("我是谁");
//        System.out.println(answer);
//    }
//}
