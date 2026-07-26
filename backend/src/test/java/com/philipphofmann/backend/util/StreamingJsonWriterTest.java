package com.philipphofmann.backend.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamingJsonWriterTest {

    private ByteArrayOutputStream out;
    private StreamingJsonWriter writer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        objectMapper = new ObjectMapper();
        writer = new StreamingJsonWriter(out, objectMapper);
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void writeRecord_writesTypeAndDataAsJson() throws IOException {
        writer.writeRecord("step", Map.of("instruction", "Anbraten"));

        var node = objectMapper.readTree(output().strip());
        assertThat(node.path("type").asText()).isEqualTo("step");
        assertThat(node.path("data").path("instruction").asText()).isEqualTo("Anbraten");
    }

    @Test
    void writeRecord_terminatesLineWithNewline() throws IOException {
        writer.writeRecord("progress", Map.of("tokens", 5));

        assertThat(output()).endsWith("\n");
    }

    @Test
    void writeRecord_multipleRecordsProduceOneJsonObjectPerLine() throws IOException {
        writer.writeRecord("ingredient", Map.of("name", "Tofu"));
        writer.writeRecord("ingredient", Map.of("name", "Reis"));
        writer.writeRecord("complete", Map.of("id", "abc"));

        String[] lines = output().strip().split("\n");
        assertThat(lines).hasSize(3);
        for (String line : lines) {
            // Every line must parse standalone — that is the NDJSON contract.
            assertThat(objectMapper.readTree(line).path("type").asText()).isNotBlank();
        }
        assertThat(objectMapper.readTree(lines[2]).path("type").asText()).isEqualTo("complete");
    }

    @Test
    void writeError_usesErrorTypeAndMessageField() throws IOException {
        writer.writeError("Etwas ging schief");

        var node = objectMapper.readTree(output().strip());
        assertThat(node.path("type").asText()).isEqualTo("error");
        assertThat(node.path("data").path("message").asText()).isEqualTo("Etwas ging schief");
    }

    @Test
    void writeRecord_handlesNestedDataStructures() throws IOException {
        writer.writeRecord("complete", Map.of(
                "id", "r-1",
                "servings", 4,
                "tags", java.util.List.of("schnell", "vegan")));

        var data = objectMapper.readTree(output().strip()).path("data");
        assertThat(data.path("servings").asInt()).isEqualTo(4);
        assertThat(data.path("tags")).hasSize(2);
    }

    @Test
    void writeRecord_propagatesIOException() {
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("stream closed");
            }
        };
        StreamingJsonWriter failingWriter = new StreamingJsonWriter(failing, objectMapper);

        assertThatThrownBy(() -> failingWriter.writeRecord("step", Map.of("a", "b")))
                .isInstanceOf(IOException.class);
    }
}
