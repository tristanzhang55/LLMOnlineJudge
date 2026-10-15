package com.tfzhang.newai;

import com.tfzhang.newai.tools.ListMyChapters;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServiceFactory {

    @Resource
    private ChatModel myQwenChatModel;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private McpToolProvider mcpToolProvider;

    @Resource
    private ChatModelListener chatModelListener;

    @Resource
    private StreamingChatModel qwenStreamingChatModel;

    @Bean
    public AiService aiService(ChatModel myQwenChatModel) {
//        return AiServices.create(AiService.class, qwchatModel);
        //ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(myQwenChatModel)
                .streamingChatModel(qwenStreamingChatModel) //streaming model
                .chatMemoryProvider(memoryId-> MessageWindowChatMemory.withMaxMessages(10)) //chat independently
                //.chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .tools(new ListMyChapters())
                .toolProvider(mcpToolProvider)
                .build();

        return aiService;
    }
}
