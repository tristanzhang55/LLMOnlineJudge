package com.tfzhang.newai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;

import com.tfzhang.newai.guardrail.SafeInputGuardrail;
import reactor.core.publisher.Flux;

@InputGuardrails({SafeInputGuardrail.class})
public interface AiService {
    public String chat(String prompt);

    public String chatWithMessage(dev.langchain4j.data.message.UserMessage userMessage);

    @SystemMessage(fromResource = "system-prompt.txt")
    public String chatWithSystem(String message);

    public String chatWithMemory(String message);

    public String chatWithRag(String message);

    //streaming outpout
    @SystemMessage(fromResource = "system-prompt.txt")
    Flux<String> chatWithStreaming(@MemoryId int memoryId, @UserMessage String message);
}