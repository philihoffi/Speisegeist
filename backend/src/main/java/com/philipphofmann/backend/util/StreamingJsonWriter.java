package com.philipphofmann.backend.util;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Utility for writing newline-delimited JSON (NDJSON) to an output stream.
 * Each record is written as a complete JSON object followed by a newline.
 */
@Slf4j
@RequiredArgsConstructor
public class StreamingJsonWriter {

    private final OutputStream outputStream;
    private final ObjectMapper objectMapper;

    /**
     * Writes a single NDJSON record to the stream.
     *
     * @param type the record type (e.g. "ingredients", "step", "complete")
     * @param data the data payload
     * @throws IOException if writing fails
     */
    public void writeRecord(String type, Object data) throws IOException {
        Map<String, Object> record = Map.of(
                "type", type,
                "data", data
        );
        String json = objectMapper.writeValueAsString(record);
        outputStream.write(json.getBytes());
        outputStream.write('\n');
        outputStream.flush();
    }

    /**
     * Writes an error record to the stream.
     *
     * @param errorMessage the error message
     * @throws IOException if writing fails
     */
    public void writeError(String errorMessage) throws IOException {
        writeRecord("error", Map.of("message", errorMessage));
    }
}
