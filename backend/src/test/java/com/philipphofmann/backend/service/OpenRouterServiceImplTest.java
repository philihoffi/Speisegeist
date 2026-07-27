package com.philipphofmann.backend.service;

import com.philipphofmann.backend.exception.RecipeGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Exercises the OpenRouter response parsing against a stubbed HTTP layer.
 * OpenRouter signals provider failures inconsistently (top-level error, per-choice
 * error, or finish_reason=error with HTTP 200) — each variant is covered here.
 */
class OpenRouterServiceImplTest {

    private OpenRouterServiceImpl service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openrouter.test/api/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenRouterServiceImpl(builder.build());
        ReflectionTestUtils.setField(service, "model", "openai/gpt-4o-mini");
        ReflectionTestUtils.setField(service, "maxTokens", 2000);
        ReflectionTestUtils.setField(service, "imageModel", "openai/dall-e-3");
    }

    private void respondWith(String json) {
        server.expect(requestTo("https://openrouter.test/api/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private String complete() {
        return service.complete("system", List.of(OpenRouterService.Message.user("hallo")), null);
    }

    // ---------- complete() ----------

    @Test
    void complete_returnsContentOfFirstChoice() {
        respondWith("""
                {"choices":[{"message":{"content":"Hallo Welt"},"finish_reason":"stop"}]}
                """);

        assertThat(complete()).isEqualTo("Hallo Welt");
        server.verify();
    }

    @Test
    void complete_sendsModelAndMaxTokensInBody() {
        server.expect(requestTo("https://openrouter.test/api/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-4o-mini"))
                .andExpect(jsonPath("$.max_tokens").value(2000))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        complete();
        server.verify();
    }

    @Test
    void complete_prependsSystemPromptAsFirstMessage() {
        server.expect(requestTo("https://openrouter.test/api/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        complete();
        server.verify();
    }

    @Test
    void complete_withResponseFormat_requiresProviderParameterSupport() {
        server.expect(requestTo("https://openrouter.test/api/v1/chat/completions"))
                .andExpect(jsonPath("$.provider.require_parameters").value(true))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        service.complete("system", List.of(OpenRouterService.Message.user("x")),
                java.util.Map.of("type", "json_schema"));
        server.verify();
    }

    @Test
    void complete_throwsOnTopLevelError() {
        respondWith("""
                {"error":{"message":"Rate limit exceeded"}}
                """);

        assertThatThrownBy(this::complete)
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void complete_throwsOnPerChoiceError() {
        respondWith("""
                {"choices":[{"error":{"message":"Provider ausgefallen"},"finish_reason":"error"}]}
                """);

        assertThatThrownBy(this::complete)
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("Provider ausgefallen");
    }

    @Test
    void complete_throwsWhenFinishReasonIsError() {
        respondWith("""
                {"choices":[{"message":{"content":""},"finish_reason":"error"}]}
                """);

        assertThatThrownBy(this::complete)
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("finish_reason=error");
    }

    @Test
    void complete_throwsWhenTruncatedByTokenLimit() {
        respondWith("""
                {"choices":[{"message":{"content":"halbes Rezept"},"finish_reason":"length"}]}
                """);

        assertThatThrownBy(this::complete)
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("Token-Limit");
    }

    // ---------- getKeyInfo() ----------

    @Test
    void getKeyInfo_mapsAllFields() {
        server.expect(requestTo("https://openrouter.test/api/v1/auth/key"))
                .andRespond(withSuccess("""
                        {"data":{"label":"mein-key","usage":1.5,"limit":10.0,
                         "is_free_tier":false,"rate_limit":{"requests":60,"interval":"10s"}}}
                        """, MediaType.APPLICATION_JSON));

        var info = service.getKeyInfo();

        assertThat(info.label()).isEqualTo("mein-key");
        assertThat(info.usageCredits()).isEqualTo(1.5);
        assertThat(info.limitCredits()).isEqualTo(10.0);
        assertThat(info.isFreeTier()).isFalse();
        assertThat(info.rateLimitRequests()).isEqualTo(60);
        assertThat(info.rateLimitInterval()).isEqualTo("10s");
        assertThat(info.error()).isNull();
    }

    @Test
    void getKeyInfo_returnsErrorFieldInsteadOfThrowing() {
        server.expect(requestTo("https://openrouter.test/api/v1/auth/key"))
                .andRespond(withServerError());

        var info = service.getKeyInfo();

        // The admin dashboard must still render when the key lookup fails.
        assertThat(info.error()).isNotNull();
        assertThat(info.label()).isNull();
    }

    @Test
    void getKeyInfo_handlesNullUsageAndLimit() {
        server.expect(requestTo("https://openrouter.test/api/v1/auth/key"))
                .andRespond(withSuccess("""
                        {"data":{"label":"k","usage":null,"limit":null,"is_free_tier":true}}
                        """, MediaType.APPLICATION_JSON));

        var info = service.getKeyInfo();

        assertThat(info.usageCredits()).isNull();
        assertThat(info.limitCredits()).isNull();
        assertThat(info.isFreeTier()).isTrue();
    }

    // ---------- generateImage() ----------

    @Test
    void generateImage_decodesBase64Payload() {
        String b64 = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4});
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}", MediaType.APPLICATION_JSON));

        var image = service.generateImage("ein Apfel", "1024x1024", "medium", 1);

        assertThat(image.data()).containsExactly(1, 2, 3, 4);
        assertThat(image.mediaType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
    }

    @Test
    void generateImage_throwsOnEmptyDataArray() {
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateImage("x", "1024x1024", "medium", 1))
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("Keine Bilddaten");
    }

    @Test
    void generateImage_throwsOnProviderError() {
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andRespond(withSuccess(
                        "{\"error\":{\"message\":\"content policy\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateImage("x", "1024x1024", "medium", 1))
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("content policy");
    }

    @Test
    void generateImage_throwsWhenNeitherUrlNorBase64Present() {
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"revised_prompt\":\"nur Text\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateImage("x", "1024x1024", "medium", 1))
                .isInstanceOf(RecipeGenerationException.class)
                .hasMessageContaining("weder eine Bild-URL noch b64_json");
    }

    @Test
    void generateImage_omitsSizeWhenBlank() {
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andExpect(jsonPath("$.size").doesNotExist())
                .andRespond(withSuccess(
                        "{\"data\":[{\"b64_json\":\"AQID\"}]}", MediaType.APPLICATION_JSON));

        service.generateImage("x", "  ", "medium", 1);
        server.verify();
    }

    @Test
    void generateImage_omitsQualityWhenBlank() {
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andExpect(jsonPath("$.quality").doesNotExist())
                .andRespond(withSuccess(
                        "{\"data\":[{\"b64_json\":\"AQID\"}]}", MediaType.APPLICATION_JSON));

        service.generateImage("x", "1024x1024", "  ", 1);
        server.verify();
    }

    @Test
    void generateImage_includesQualityWhenSet() {
        server.expect(requestTo("https://openrouter.test/api/v1/images"))
                .andExpect(jsonPath("$.quality").value("medium"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"b64_json\":\"AQID\"}]}", MediaType.APPLICATION_JSON));

        service.generateImage("x", "1024x1024", "medium", 1);
        server.verify();
    }

    // ---------- getters ----------

    @Test
    void getModel_andGetImageModel_returnConfiguredValues() {
        assertThat(service.getModel()).isEqualTo("openai/gpt-4o-mini");
        assertThat(service.getImageModel()).isEqualTo("openai/dall-e-3");
    }
}
