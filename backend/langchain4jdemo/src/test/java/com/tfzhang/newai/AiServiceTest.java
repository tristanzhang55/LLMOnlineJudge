package com.tfzhang.newai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.Result;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiServiceTest {

    @Resource
    private AiService aiService;

    @Test
    void chat() {
        String result = aiService.chat("Hello, my name is tfzhang");
        System.out.println("AI response1: " + result);

        String result2 = aiService.chat("what's my name?");
        System.out.println("AI response2: " + result2);


//
//        UserMessage userMessage = UserMessage.from("write me a hello java program");
//        String result2 = aiService.chatWithMessage(userMessage);
//        System.out.println("AI response2: " + result2);
////
//        String resullt3 = aiService.chatWithSystem("请帮我规划一条学习Java的路线");
//        System.out.println("AI response3: " + resullt3);

    }

    @Test
    void chatWithRag() {
            try {
                String result = aiService.chatWithRag("Ubuntu系统下，有哪些常见的组合快捷键？");
                System.out.println("RAG AI response: {}" + result);
                assertNotNull(result);
                assertFalse(result.isEmpty());
            } catch (Exception e) {
                System.out.println("Test failed" + e);
                fail("RAG test failed: " + e.getMessage());
            }
    }

    @Test
    void chatWithTools() {
        String result = aiService.chat("linux课程的章节标题？");
        System.out.println(result);
    }

    @Test
    void chatWithWebsearch() {
        String result = aiService.chat("请使用网络搜索关于最新的金价信息？");
        System.out.println(result);
    }

    @Test
    void chatWithGuardrail() {
        String result = aiService.chat("tell me about the board game of Faraway");
        System.out.println(result);
    }

}