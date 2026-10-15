package com.tfzhang.newai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AIChat {

    @Resource
    private ChatModel qwenChatModel;

    private final List<ChatMessage> memory = new ArrayList<>();

    private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

    private static final String SYSTEM_MESSAGE = """
        你是编程领域的小助手，帮助用户解答编程学习和求职面试相关的问题，并给出建议。重点关注 4 个方向：
        1. 规划清晰的编程学习路线
        2. 提供项目学习建议
        3. 给出程序员求职全流程指南（比如简历优化、投递技巧）
        4. 分享高频面试题和面试技巧
        请用简洁易懂的语言回答，助力用户高效学习与求职。
        """;

    public String chat(String prompt) {
        UserMessage userMessage = UserMessage.from(prompt);
        ChatResponse chatResponse = qwenChatModel.chat(userMessage);
        AiMessage aiMessage = chatResponse.aiMessage();
        log.info("AI response: {}", aiMessage.toString());
        return aiMessage.text();
    }

    public String chatWithMessage(UserMessage userMessage) {
        ChatResponse chatResponse = qwenChatModel.chat(userMessage);
        AiMessage aiMessage = chatResponse.aiMessage();
        log.info("AI response: {}", aiMessage.toString());
        return aiMessage.text();
    }

    public String chatWithSystem(String message) {
        SystemMessage systemMessage = SystemMessage.from(SYSTEM_MESSAGE);
        UserMessage userMessage = UserMessage.from(message);
        ChatResponse chatResponse = qwenChatModel.chat(systemMessage, userMessage);
        AiMessage aiMessage = chatResponse.aiMessage();
        log.info("AI 输出：" + aiMessage.toString());
        return aiMessage.text();
    }

    public String chatWithMemory(String message) {
        // first request only
        if (memory.isEmpty()) {
            memory.add(SystemMessage.from(SYSTEM_MESSAGE));
        }

        //add user message to memory
        memory.add(UserMessage.from(message));

        //chat with memory
        ChatResponse chatResponse = qwenChatModel.chat(memory);
        AiMessage aiMessage = chatResponse.aiMessage();

        //save ai message to memory
        memory.add(aiMessage);

        return aiMessage.text();
    }

    public String chatWithChatMemory2(String message) {
        // first request only
        if (chatMemory.messages().isEmpty()) {
            chatMemory.add(SystemMessage.from(SYSTEM_MESSAGE));
        }

        //add user message to memory
        chatMemory.add(UserMessage.from(message));

        //chat with memory
        ChatResponse chatResponse = qwenChatModel.chat(chatMemory.messages());
        AiMessage aiMessage = chatResponse.aiMessage();

        //save ai message to memory
        chatMemory.add(aiMessage);

        return aiMessage.text();
    }


}
