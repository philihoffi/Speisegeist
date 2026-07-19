package com.philipphofmann.backend.service;

import tools.jackson.databind.JsonNode;
import com.philipphofmann.backend.exception.RecipeGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    @Value("${openrouter.model:openai/gpt-4o-mini}")
    private String model;

    @Value("${openrouter.max-tokens:2000}")
    private int maxTokens;

    @Override
    public String getModel() {
        return model;
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
            // Nur Provider zulassen, die response_format/json_schema wirklich unterstützen,
            // sonst routet OpenRouter ggf. an eine Instanz, die den Parameter ignoriert/ablehnt
            // (Folge: finish_reason=error). Siehe openrouter.ai/docs/features/structured-outputs
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
}
