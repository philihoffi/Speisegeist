package com.philipphofmann.backend.service;

import java.io.IOException;
import java.util.List;

/**
 * Schlanker, generischer Zugang zur OpenRouter Chat-Completions API:
 * System-Prompt, Nachrichten und optionales Response-Format rein, roher Text-Content
 * raus. Enthält bewusst keine anwendungsspezifische Logik (z. B. kein Rezept-Schema).
 */
public interface OpenRouterService {

    /**
     * Ruft eine Chat-Completion ab.
     *
     * @param systemPrompt   System-Prompt (role=system); {@code null}/leer lässt ihn weg
     * @param messages       weitere Nachrichten (typischerweise eine User-Nachricht)
     * @param responseFormat OpenRouter {@code response_format} (z. B. eine json_schema-Map)
     *                       oder {@code null} für eine unstrukturierte Antwort
     * @return der Text-Content der ersten Choice
     */
    String complete(String systemPrompt, List<Message> messages, Object responseFormat);

    /**
     * Ruft eine Chat-Completion mit Server-Sent Events (SSE) Streaming ab.
     * Die Callback wird für jedes Stream-Event aufgerufen.
     *
     * @param systemPrompt   System-Prompt (role=system)
     * @param messages       weitere Nachrichten
     * @param responseFormat OpenRouter {@code response_format} oder {@code null}
     * @param onToken        Callback für jeden Token/Event
     * @throws IOException if streaming fails
     */
    void streamComplete(String systemPrompt, List<Message> messages, Object responseFormat,
                        StreamEventHandler onToken) throws IOException;

    /**
     * Generiert ein Bild über die OpenRouter Images-API (OpenAI-kompatibel) und lädt
     * die Bild-Bytes gleich herunter, damit sie unabhängig von der kurzlebigen
     * Provider-URL persistiert werden können.
     *
     * @param prompt    Beschreibung des gewünschten Bildes
     * @param size      Zielgröße im Format "WIDTHxHEIGHT" (z. B. "1024x1024"),
     *                  oder {@code null} für die Modell-Standardgröße
     * @param n         Anzahl der zu erzeugenden Bilder (typischerweise 1)
     * @return die Roh-Bytes und der Content-Type des ersten Bildes
     */
    GeneratedImage generateImage(String prompt, String size, Integer n);

    /** The configured model (e.g. for provenance metadata). */
    String getModel();

    /**
     * Returns metadata about the configured API key (usage, limit, rate limit).
     * Returns a partial result with an error message if the call fails.
     */
    com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo getKeyInfo();

    /** Das konfigurierte Bildgenerierungs-Modell. */
    String getImageModel();

    /**
     * Ein fertig heruntergeladenes Bild.
     *
     * @param data      die Roh-Bytes des Bildes
     * @param mediaType der MIME-Typ (z. B. {@code image/png}); {@code null} falls unbekannt
     */
    record GeneratedImage(byte[] data, String mediaType) {
    }

    /** Eine Chat-Nachricht (role/content) im OpenRouter-Format. */
    record Message(String role, String content) {
        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /** Callback für Streaming-Events während der Rezept-Generierung. */
    interface StreamEventHandler {
        /**
         * Called when a token/partial content is received.
         *
         * @param token the token content
         * @throws IOException if handling fails
         */
        void onToken(String token) throws IOException;

        /**
         * Called when the stream is complete.
         */
        void onComplete();

        /**
         * Called when an error occurs during streaming.
         *
         * @param error the error message
         */
        void onError(String error);
    }
}
