package com.tfzhang.linuxback.grading.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.config.GradingProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QwenVlOcrClient {

    private final RestClient restClient;
    private final GradingProperties gradingProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QwenVlOcrClient(RestClient restClient, GradingProperties gradingProperties) {
        this.restClient = restClient;
        this.gradingProperties = gradingProperties;
    }

    public String recognize(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        String mime = (mimeType == null || mimeType.isBlank()) ? "image/png" : mimeType;
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> imageUrl = Map.of("url", dataUrl);
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        Map<String, Object> textPart = Map.of(
                "type", "text",
                "text", "Extract all visible text from the image. Return plain text only.");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(imagePart, textPart));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("min_pixels", gradingProperties.getOcr().getMinPixels());
        extra.put("max_pixels", gradingProperties.getOcr().getMaxPixels());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", gradingProperties.getOcr().getModel());
        body.put("messages", List.of(message));
        body.put("extra_body", extra);

        String raw;
        try {
            raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("OCR request failed", e);
        }
        return extractContent(raw);
    }

    String extractContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? "" : content.asText("");
        } catch (Exception e) {
            throw new IllegalStateException("OCR response was not valid JSON", e);
        }
    }
}
