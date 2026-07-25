package com.philipphofmann.backend.service;

import tools.jackson.databind.JsonNode;
import com.philipphofmann.backend.exception.RecipeGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Konkrete {@link OpenRouterService}-Implementierung: generischer HTTP-Zugriff auf
 * die OpenRouter Chat-Completions API. Baut den Request aus System-Prompt,
 * Nachrichten und optionalem Response-Format und liefert den rohen Text-Content.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterServiceImpl implements OpenRouterService {

    private final RestClient openRouterRestClient;

    /**
     * Header-loser Client zum Herunterladen der generierten Bild-Bytes von der
     * Provider-URL (kein OpenRouter-{@code Authorization}-Header, s. {@link #downloadImage}).
     */
    private final RestClient imageDownloadRestClient = RestClient.create();

    @Value("${openrouter.model:openai/gpt-4o-mini}")
    private String model;

    @Value("${openrouter.max-tokens:2000}")
    private int maxTokens;

    @Value("${openrouter.image-model:openai/dall-e-3}")
    private String imageModel;

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getImageModel() {
        return imageModel;
    }

    @Override
    public GeneratedImage generateImage(String prompt, String size, Integer n) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", imageModel);
        body.put("prompt", prompt);
        if (size != null && !size.isBlank()) {
            body.put("size", size);
        }
        if (n != null && n > 0) {
            body.put("n", n);
        }

        JsonNode response = openRouterRestClient.post()
                .uri("/images/generations")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new RecipeGenerationException("Leere Antwort von OpenRouter (Bildgenerierung)");
        }

        JsonNode topError = response.path("error");
        if (topError.isObject()) {
            log.warn("OpenRouter-Fehlerantwort (Bildgenerierung): {}", response);
            String message = topError.path("message").asText("unbekannter Fehler");
            throw new RecipeGenerationException("OpenRouter-Fehler bei der Bildgenerierung: " + message);
        }

        JsonNode data = response.path("data");
        if (data.isMissingNode() || !data.isArray() || data.isEmpty()) {
            throw new RecipeGenerationException("Keine Bilddaten von OpenRouter erhalten");
        }

        JsonNode image = data.get(0);

        // OpenRouter/image models return either a short-lived "url" or directly
        // base64-encoded "b64_json" (e.g. openai/gpt-image). Support both cases so
        // no model fails because of an unsupported response format.
        JsonNode url = image.path("url");
        if (!url.isMissingNode() && !url.asText().isBlank()) {
            log.debug("OpenRouter Bildgenerierung: model={}, size={}, url_length={}",
                    imageModel, size, url.asText().length());
            return downloadImage(url.asText());
        }

        JsonNode b64 = image.path("b64_json");
        if (!b64.isMissingNode() && !b64.asText().isBlank()) {
            byte[] bytes;
            try {
                bytes = java.util.Base64.getDecoder().decode(b64.asText());
            } catch (IllegalArgumentException e) {
                throw new RecipeGenerationException("Bild (b64_json) konnte nicht dekodiert werden", e);
            }
            if (bytes.length == 0) {
                throw new RecipeGenerationException("Bild (b64_json) ist leer");
            }
            // gpt-image returns PNG; other models may require adjustments.
            log.debug("OpenRouter Bildgenerierung: model={}, size={}, b64_bytes={}",
                    imageModel, size, bytes.length);
            return new GeneratedImage(bytes, MediaType.IMAGE_PNG_VALUE);
        }

        throw new RecipeGenerationException(
                "OpenRouter lieferte weder eine Bild-URL noch b64_json");
    }

    /**
     * Lädt die Bild-Bytes von der (kurzlebigen) Provider-URL herunter. Bewusst mit einem
     * eigenen, header-losen {@link RestClient}: die URL zeigt auf einen Drittanbieter-Storage,
     * dem der OpenRouter-{@code Authorization}-Header nicht mitgegeben werden darf.
     */
    private GeneratedImage downloadImage(String url) {
        try {
            ResponseEntity<byte[]> entity = imageDownloadRestClient.get()
                    .uri(java.net.URI.create(url))
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] bytes = entity.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new RecipeGenerationException("Heruntergeladenes Bild ist leer");
            }

            MediaType contentType = entity.getHeaders().getContentType();
            String mediaType = contentType != null ? contentType.toString() : MediaType.IMAGE_PNG_VALUE;

            log.debug("Bild heruntergeladen: bytes={}, contentType={}", bytes.length, mediaType);
            return new GeneratedImage(bytes, mediaType);
        } catch (RestClientException e) {
            throw new RecipeGenerationException("Bild konnte nicht heruntergeladen werden: " + e.getMessage(), e);
        }
    }

    @Override
    public String complete(String systemPrompt, List<Message> messages, Object responseFormat) {
        List<Map<String, Object>> payloadMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payloadMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (Message m : messages) {
            payloadMessages.add(Map.of("role", m.role(), "content", m.content()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", payloadMessages);
        body.put("max_tokens", maxTokens);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
            // Only allow providers that actually support response_format/json_schema,
            // otherwise OpenRouter may route to an instance that ignores/rejects the
            // parameter (resulting in finish_reason=error). See openrouter.ai/docs/features/structured-outputs
            body.put("provider", Map.of("require_parameters", true));
        }

        JsonNode response = openRouterRestClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new RecipeGenerationException("Leere Antwort von OpenRouter");
        }

        // OpenRouter meldet Provider-Fehler entweder top-level oder pro Choice als error-Objekt,
        // oft zusammen mit HTTP 200 und finish_reason=error.
        JsonNode choice = response.path("choices").get(0);
        JsonNode topError = response.path("error");
        JsonNode choiceError = choice != null ? choice.path("error") : null;
        String finishReason = choice != null ? choice.path("finish_reason").asText("") : "";

        if (topError.isObject() || (choiceError != null && choiceError.isObject()) || "error".equals(finishReason)) {
            log.warn("OpenRouter-Fehlerantwort: {}", response);
            JsonNode err = topError.isObject() ? topError : choiceError;
            String message = err != null ? err.path("message").asText("unbekannter Fehler") : "unbekannter Fehler";
            throw new RecipeGenerationException(
                    "OpenRouter/Provider-Fehler bei der Generierung (finish_reason=" + finishReason + "): " + message);
        }

        if (choice == null) {
            throw new RecipeGenerationException("Leere Antwort von OpenRouter");
        }

        String content = choice.path("message").path("content").asText();

        log.debug("OpenRouter finish_reason={}, completion_tokens={}, content_length={}",
                finishReason,
                response.path("usage").path("completion_tokens").asInt(-1),
                content.length());

        if ("length".equals(finishReason)) {
            throw new RecipeGenerationException(
                    "KI-Antwort wurde durch das Token-Limit abgeschnitten (max_tokens=" + maxTokens
                            + "). Erhöhe openrouter.max-tokens.");
        }
        return content;
    }

    @Override
    public com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo getKeyInfo() {
        try {
            JsonNode response = openRouterRestClient.get()
                    .uri("/auth/key")
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return new com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo(
                        null, null, null, false, null, null, "Leere Antwort von OpenRouter");
            }

            JsonNode data = response.path("data");
            String label = data.path("label").asText(null);
            Double usage = data.path("usage").isNull() ? null : data.path("usage").asDouble();
            Double limit = data.path("limit").isNull() ? null : data.path("limit").asDouble();
            boolean isFreeTier = data.path("is_free_tier").asBoolean(false);
            JsonNode rateLimit = data.path("rate_limit");
            Integer requests = rateLimit.isMissingNode() ? null : rateLimit.path("requests").asInt();
            String interval = rateLimit.isMissingNode() ? null : rateLimit.path("interval").asText(null);

            return new com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo(
                    label, usage, limit, isFreeTier, requests, interval, null);
        } catch (Exception e) {
            log.warn("OpenRouter Key-Info konnte nicht abgerufen werden: {}", e.getMessage());
            return new com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo(
                    null, null, null, false, null, null, e.getMessage());
        }
    }

    @Override
    public void streamComplete(String systemPrompt, List<Message> messages, Object responseFormat,
                               StreamEventHandler handler) throws IOException {
        List<Map<String, Object>> payloadMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payloadMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (Message m : messages) {
            payloadMessages.add(Map.of("role", m.role(), "content", m.content()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", payloadMessages);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
            body.put("provider", Map.of("require_parameters", true));
        }

        try {
            java.io.InputStream responseStream = openRouterRestClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(java.io.InputStream.class);

            if (responseStream == null) {
                handler.onError("Empty response from OpenRouter");
                return;
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(responseStream))) {
                String line;
                StringBuilder fullContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data: ")) {
                        continue;
                    }

                    String data = line.substring(6);
                    if (data.equals("[DONE]")) {
                        break;
                    }

                    JsonNode json;
                    try {
                        json = new tools.jackson.databind.ObjectMapper().readTree(data);
                    } catch (Exception e) {
                        log.debug("Failed to parse stream line", e);
                        continue;
                    }

                    JsonNode choice = json.path("choices").path(0);
                    JsonNode delta = choice.path("delta");
                    String content = delta.path("content").asText("");

                    if (!content.isEmpty()) {
                        fullContent.append(content);
                        handler.onToken(content);
                    }
                }
                handler.onComplete();
            }
        } catch (Exception e) {
            log.error("Streaming failed", e);
            handler.onError("Streaming error: " + e.getMessage());
        }
    }
}
