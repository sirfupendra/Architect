package com.parser.Architect.Dtos.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// LlmRequest.java
@Data
@Builder
public class LlmRequest {
    private String model;
    private List<Message> messages;
    private ResponseFormat response_format; // Added this

    @Data
    @AllArgsConstructor
    public static class ResponseFormat {
        private String type; // usually "json_object"
    }// Changed from String[] to List<Message>

    @Data
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
