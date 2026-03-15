package com.parser.Architect.Dtos.Response;

import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@Data
public class LlmResponse {
    private List<Choice> choices;

    @Data
    public static class Choice {
        private Message message;
    }

    @Data
    public static class Message {
        private String content; // This is where the AI's answer lives
    }
}
