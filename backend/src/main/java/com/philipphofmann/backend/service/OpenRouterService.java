package com.philipphofmann.backend.service;

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

    /** Das konfigurierte Modell (z. B. für Provenienz-Metadaten). */
    String getModel();

    /** Eine Chat-Nachricht (role/content) im OpenRouter-Format. */
    record Message(String role, String content) {
        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}
