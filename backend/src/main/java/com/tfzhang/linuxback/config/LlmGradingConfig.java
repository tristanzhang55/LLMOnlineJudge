package com.tfzhang.linuxback.config;

import com.tfzhang.linuxback.grading.ocr.QwenVlOcrClient;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.grading.scorer", havingValue = "llm")
public class LlmGradingConfig {

    @Bean
    public ChatModel gradingChatModel(GradingProperties gradingProperties) {
        String apiKey = gradingProperties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY / app.grading.llm.api-key is required when app.grading.scorer=llm");
        }
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(gradingProperties.getLlm().getModel())
                .build();
    }

    @Bean
    public QwenVlOcrClient qwenVlOcrClient(GradingProperties gradingProperties) {
        String apiKey = gradingProperties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY / app.grading.llm.api-key is required when app.grading.scorer=llm");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = gradingProperties.getOcr().getTimeoutMs();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeout, 10_000)));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        RestClient restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(gradingProperties.getOcr().getBaseUrl()))
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        return new QwenVlOcrClient(restClient, gradingProperties);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
