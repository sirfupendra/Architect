package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.LlmRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.LlmModels;
import com.parser.Architect.Prompts.ParsePrompts;
import com.parser.Architect.Repositories.LlmModelsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmConnectionService {

    private final WebClient.Builder webClientBuilder;
    private final LlmModelsRepository llmModelsRepository;

    private LlmResponse post(String url, String key, String modelName, List<LlmRequest.Message> messages) {
        return webClientBuilder.baseUrl(url).build()
                .post()
                .header("Authorization", "Bearer " + key)
                .bodyValue(LlmRequest.builder()
                        .model(modelName)
                        .messages(messages)
                        .response_format(new LlmRequest.ResponseFormat("json_object"))
                        .build())
                .retrieve()
                .bodyToMono(LlmResponse.class)
                .block();
    }

    /** ARCHITECT: Generate initial JSON schema from task (and optional example). */
    public String generateSchema(String taskDescription, String modelName, JsonNode optionalExampleSchema) {
        LlmModels m = llmModelsRepository.findByModelNameContaining(modelName);
        String user = String.format(ParsePrompts.SCHEMA_GENERATOR_USER_TEMPLATE, taskDescription);
        if (optionalExampleSchema != null && !optionalExampleSchema.isEmpty()) {
            user += "\n\nExample/reference schema:\n" + optionalExampleSchema.toString();
        }
        LlmResponse r = post(m.getModelUrl(), m.getMyConnectionKey(), modelName,
                List.of(
                        new LlmRequest.Message("system", ParsePrompts.SCHEMA_GENERATOR_SYSTEM),
                        new LlmRequest.Message("user", user)
                ));
        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : "{}";
    }

    /** ARCHITECT: Generate synthetic test examples (input_text, ground_truth, challenge). */
    public String generateSyntheticData(String task, String schemaJson, String seedExamplesJson, String modelName) {
        LlmModels m = llmModelsRepository.findByModelNameContaining(modelName);
        String user = String.format(ParsePrompts.SYNTHETIC_DATA_USER_TEMPLATE, task, schemaJson, seedExamplesJson != null ? seedExamplesJson : "[]");
        LlmResponse r = post(m.getModelUrl(), m.getMyConnectionKey(), modelName,
                List.of(
                        new LlmRequest.Message("system", ParsePrompts.SYNTHETIC_DATA_SYSTEM),
                        new LlmRequest.Message("user", user)
                ));
        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : "[]";
    }

    /** ARCHITECT: Refine schema given failure analysis. */
    public String refineSchema(String task, String currentSchemaJson, String failureSummary, String modelName) {
        LlmModels m = llmModelsRepository.findByModelNameContaining(modelName);
        String user = String.format(ParsePrompts.REFINEMENT_USER_TEMPLATE, task, currentSchemaJson, failureSummary);
        LlmResponse r = post(m.getModelUrl(), m.getMyConnectionKey(), modelName,
                List.of(
                        new LlmRequest.Message("system", ParsePrompts.REFINEMENT_SYSTEM),
                        new LlmRequest.Message("user", user)
                ));
        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : currentSchemaJson;
    }

    /** RELAY: Generate mapping spec from original schema to optimized schema. */
    public String generateRelaySpec(String originalSchemaJson, String optimizedSchemaJson, String modelName) {
        LlmModels m = llmModelsRepository.findByModelNameContaining(modelName);
        String user = String.format(ParsePrompts.RELAY_USER_TEMPLATE, originalSchemaJson, optimizedSchemaJson);
        LlmResponse r = post(m.getModelUrl(), m.getMyConnectionKey(), modelName,
                List.of(
                        new LlmRequest.Message("system", ParsePrompts.RELAY_SYSTEM),
                        new LlmRequest.Message("user", user)
                ));
        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : "{}";
    }

    /** SCOPE: Extract JSON from text using schema. */
    public LlmResponse JsonFromText(String optimizedSchema, String text, String modelName) {
        LlmModels m = llmModelsRepository.findByModelNameContaining(modelName);
        String schemaForFormat = optimizedSchema;
        String user = String.format(ParsePrompts.SCOPE_EXTRACTOR_USER_TEMPLATE, optimizedSchema, schemaForFormat, text);
        return post(m.getModelUrl(), m.getMyConnectionKey(), modelName,
                List.of(
                        new LlmRequest.Message("system", ParsePrompts.SCOPE_EXTRACTOR_SYSTEM),
                        new LlmRequest.Message("user", user)
                ));
    }

    /** SCOPE: Retry with error feedback. */
    public String retryWithErrors(String previousOutput, List<String> errors, String optimizedSchema, String text, String modelName) {
        LlmModels m = llmModelsRepository.findByModelNameContaining(modelName);
        String user = String.format(ParsePrompts.SCOPE_RETRY_USER_TEMPLATE, previousOutput, String.join("\n", errors), optimizedSchema, text);
        LlmResponse r = post(m.getModelUrl(), m.getMyConnectionKey(), modelName,
                List.of(
                        new LlmRequest.Message("system", ParsePrompts.SCOPE_RETRY_SYSTEM),
                        new LlmRequest.Message("user", user)
                ));
        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : previousOutput;
    }
}
