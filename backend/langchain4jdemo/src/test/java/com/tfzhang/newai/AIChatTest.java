package com.tfzhang.newai;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AIChatTest {
    @Resource
    private AIChat aiChat;

    @Test
    void testChat() {
        String prompt = "Hello, who are you?";
        String response = aiChat.chat(prompt);
        assertNotNull(response);
        System.out.println("AI response: " + response);
    }

    @Test
    void testChatWithMessage() {
        UserMessage userMessage = UserMessage.from(
                TextContent.from("描述图片"),
                ImageContent.from("https://66bond.com/tuchuang/default-cover.jpg")
        );
        aiChat.chatWithMessage(userMessage);
    }

    @Test
    void testChatWithSystem() {
        String message = "请帮我规划一条学习Java的路线";
        String response = aiChat.chatWithSystem(message);
        assertNotNull(response);
        System.out.println("AI response: " + response);
    }

    @Test
    void testChatWithMemory() {
        String message1 = "my name is tfzhang, hi";
        String response1 = aiChat.chatWithMemory(message1);
        assertNotNull(response1);
        System.out.println("AI response 1: " + response1);

        String message2 = "what's my name?";
        String response2 = aiChat.chatWithMemory(message2);
        assertNotNull(response2);
        System.out.println("AI response 2: " + response2);
    }

    @Test
    void testChatWithMemory2(){
        // First round
        String answer1 = aiChat.chatWithMemory("My name is Tom.");
        System.out.println("AI: " + answer1);

        // Second round
        String answer2 = aiChat.chatWithMemory("What is my name?");
        System.out.println("AI: " + answer2);
    }
}