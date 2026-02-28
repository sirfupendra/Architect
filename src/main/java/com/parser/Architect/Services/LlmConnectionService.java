package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Dtos.Request.LlmRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.LlmModels;
import com.parser.Architect.Enums.LLMMODELS;
import com.parser.Architect.Repositories.LlmModelsRepository;
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
    private final LlmModelsRepository llmModelsRepository;

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

    public LlmResponse JsonFromText(String optimizedSchema, String Text,String modelName){
        LlmModels llmModels =llmModelsRepository.findByModelNameContaining(modelName);
        List<LlmRequest.Message> messages = List.of(
                new LlmRequest.Message("system",
                        "You are a structured data extractor. Extract JSON strictly following this schema."),

                new LlmRequest.Message("user",
                        "Schema:\n" + optimizedSchema +
                                "\n\nText:\n" + Text +
                                "\n\nReturn only valid JSON.")
        );
        LlmRequest llmRequest = LlmRequest.builder()
                .model(modelName)
                .messages(messages)
                .build();
        return webClientBuilder.baseUrl(llmModels.getModelUrl()).build()
                .post()
                .header("Authorization", "Bearer " + llmModels.getMyConnectionKey())
                .bodyValue(llmRequest)
                .retrieve()
                .bodyToMono(LlmResponse.class)
                .block();
    }

    public  String retryWithErrors(String previousOutput,
                                   List<String> errors,
                                   String optimizedSchema,
                                   String text,
                                   String modelName) {
        LlmModels llmModels =llmModelsRepository.findByModelNameContaining(modelName);

        String errorMessage = String.join("\n", errors);

        List<LlmRequest.Message> messages = List.of(
                new LlmRequest.Message("system",
                        "You are correcting invalid JSON extraction."),

                new LlmRequest.Message("user",
                        "Previous output:\n" + previousOutput +
                                "\n\nErrors:\n" + errorMessage +
                                "\n\nSchema:\n" + optimizedSchema +
                                "\n\nText:\n" + text +
                                "\n\nReturn corrected JSON only.")
        );
        LlmRequest llmRequest = LlmRequest.builder()
                .model(modelName)
                .messages(messages)
                .build();
     LlmResponse llmResponse=webClientBuilder.baseUrl(llmModels.getModelUrl()).build()
             .post()
             .header("Authorization", "Bearer " + llmModels.getMyConnectionKey())
             .bodyValue(llmRequest)
             .retrieve()
             .bodyToMono(LlmResponse.class)
             .block();

     return llmResponse.getChoices().get(0).getMessage().getContent();

    }
}
