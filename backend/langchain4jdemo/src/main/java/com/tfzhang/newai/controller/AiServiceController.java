package com.tfzhang.newai.controller;

import org.springframework.http.codec.ServerSentEvent;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import com.tfzhang.newai.AiService;


@RestController
@RequestMapping("/ai")
public class AiServiceController {

    @Resource
    private AiService aiService;

    @GetMapping("/chat")
    public Flux<ServerSentEvent<String>> chat(int memoryId, String message) {
        return aiService.chatWithStreaming(memoryId, message)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

}