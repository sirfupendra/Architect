package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Dtos.Request.LlmRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.LlmModels;
import com.parser.Architect.Enums.LLMMODELS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmConnectionService {

    private final WebClient.Builder webClientBuilder;

    public LlmResponse sendRequest(String url, String connectionKey, String description, String modelName, JsonNode jsonData) {

        List<LlmRequest.Message> messages = List.of(
                new LlmRequest.Message("system", "You are a JSON architect. Task: " + description),
                new LlmRequest.Message("user", "Analyze and refine this JSON: " + jsonData.toString())
        );

        LlmRequest llmRequest = LlmRequest.builder()
                .model(modelName)
                .messages(messages)
                .build();

        return webClientBuilder.baseUrl(url).build()
                .post()
                .header("Authorization", "Bearer " + connectionKey)
                .bodyValue(llmRequest)
                .retrieve()
                .bodyToMono(LlmResponse.class)
                .block();
    }
}
