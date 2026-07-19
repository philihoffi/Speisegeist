package com.philipphofmann.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient openRouterRestClient(
            @Value("${openrouter.api.base-url}") String baseUrl,
            @Value("${openrouter.api.key:}") String apiKey,
            @Value("${openrouter.api.timeout-seconds}") long timeoutSeconds) {

        if (apiKey == null || apiKey.isBlank() || apiKey.equals("sk-or-...")) {
            throw new IllegalStateException(
                    "OPENROUTER_API_KEY ist leer oder ein Platzhalter. "
                            + "Trage einen echten Key aus https://openrouter.ai/keys in die .env ein.");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
