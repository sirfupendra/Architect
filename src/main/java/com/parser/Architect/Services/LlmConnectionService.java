package com.parser.Architect.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parser.Architect.Dtos.Request.LlmRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Prompts.ParsePrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LlmConnectionService {

    private final WebClient openRouterWebClient;

    @Value("${openrouter.default.model}")
    private String defaultModel;

    /**
     * Core POST to OpenRouter /chat/completions.
     * modelName: pass null or empty to use the default free model from properties.
     */
    private LlmResponse post(String modelName, List<LlmRequest.Message> messages) {
        String model = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;
        try {
            return openRouterWebClient
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(
                            LlmRequest.builder()
                                    .model(model)
                                    .messages(messages)
                                    .build()
                    )
                    .retrieve()
                    .bodyToMono(LlmResponse.class)
                    .block();

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("OpenRouter ERROR STATUS: {}", e.getStatusCode());
            log.error("OpenRouter ERROR BODY: {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    /** ARCHITECT: Generate initial JSON schema from task (and optional example). */
    public String generateSchema(String taskDescription, String modelName, Object optionalExampleSchema) {
        try {
            String user = String.format(ParsePrompts.SCHEMA_GENERATOR_USER_TEMPLATE, taskDescription);

            if (optionalExampleSchema != null) {
                ObjectMapper mapper = new ObjectMapper();
                user += "\n\nExample/reference schema:\n" +
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(optionalExampleSchema);
            }

            LlmResponse r = post(modelName, List.of(
                    new LlmRequest.Message("system", ParsePrompts.SCHEMA_GENERATOR_SYSTEM),
                    new LlmRequest.Message("user", user)
            ));

            if (r != null && r.getChoices() != null && !r.getChoices().isEmpty()) {
                return r.getChoices().get(0).getMessage().getContent();
            }

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("LLM call failed — Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unexpected error in generateSchema: {}", e.getMessage(), e);
        }

        return "{}";
    }

    /** ARCHITECT: Generate synthetic test examples (input_text, ground_truth, challenge). */
    public String generateSyntheticData(String task, String schemaJson, String seedExamplesJson, String modelName) {
        String user = String.format(ParsePrompts.SYNTHETIC_DATA_USER_TEMPLATE, task, schemaJson,
                seedExamplesJson != null ? seedExamplesJson : "[]");

        LlmResponse r = post(modelName, List.of(
                new LlmRequest.Message("system", ParsePrompts.SYNTHETIC_DATA_SYSTEM),
                new LlmRequest.Message("user", user)
        ));

        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : "[]";
    }

    /** ARCHITECT: Refine schema given failure analysis. */
    public String refineSchema(String task, String currentSchemaJson, String failureSummary, String modelName) {
        String user = String.format(ParsePrompts.REFINEMENT_USER_TEMPLATE, task, currentSchemaJson, failureSummary);

        LlmResponse r = post(modelName, List.of(
                new LlmRequest.Message("system", ParsePrompts.REFINEMENT_SYSTEM),
                new LlmRequest.Message("user", user)
        ));

        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : currentSchemaJson;
    }

    /** RELAY: Generate mapping spec from original schema to optimized schema. */
    public String generateRelaySpec(String originalSchemaJson, String optimizedSchemaJson, String modelName) {
        String user = String.format(ParsePrompts.RELAY_USER_TEMPLATE, originalSchemaJson, optimizedSchemaJson);

        LlmResponse r = post(modelName, List.of(
                new LlmRequest.Message("system", ParsePrompts.RELAY_SYSTEM),
                new LlmRequest.Message("user", user)
        ));

        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : "{}";
    }

    /** SCOPE: Extract JSON from text using schema. */
    public LlmResponse JsonFromText(String optimizedSchema, String text, String modelName) {
        String user = String.format(ParsePrompts.SCOPE_EXTRACTOR_USER_TEMPLATE, optimizedSchema, optimizedSchema, text);

        return post(modelName, List.of(
                new LlmRequest.Message("system", ParsePrompts.SCOPE_EXTRACTOR_SYSTEM),
                new LlmRequest.Message("user", user)
        ));
    }

    /** SCOPE: Retry with error feedback. */
    public String retryWithErrors(String previousOutput, List<String> errors, String optimizedSchema, String text, String modelName) {
        String user = String.format(ParsePrompts.SCOPE_RETRY_USER_TEMPLATE, previousOutput,
                String.join("\n", errors), optimizedSchema, text);

        LlmResponse r = post(modelName, List.of(
                new LlmRequest.Message("system", ParsePrompts.SCOPE_RETRY_SYSTEM),
                new LlmRequest.Message("user", user)
        ));

        return r != null && r.getChoices() != null && !r.getChoices().isEmpty()
                ? r.getChoices().get(0).getMessage().getContent() : previousOutput;
    }
}