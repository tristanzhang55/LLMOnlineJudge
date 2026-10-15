package com.tfzhang.linuxback.grading.ocr;

import com.tfzhang.linuxback.config.GradingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class QwenVlOcrClientTest {

    @Test
    void extractContent_readsOpenAiShape() {
        QwenVlOcrClient client = new QwenVlOcrClient(mock(RestClient.class), new GradingProperties());
        String text = client.extractContent("""
                {"choices":[{"message":{"content":"hello from image"}}]}
                """);
        assertEquals("hello from image", text);
    }
}
