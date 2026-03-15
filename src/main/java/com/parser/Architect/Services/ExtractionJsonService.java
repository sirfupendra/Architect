package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.ExtractionRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.SchemaDefinition;
import com.parser.Architect.Repositories.SchemaDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionJsonService {
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final LlmConnectionService llmConnectionService;
    private final SchemaGuardrailsService schemaGuardrailsService;
    private final RelayService relayService;

    /** SCOPE: extract from text using optimized schema, with guardrails and optional RELAY transform. */
    public String JsonFromText(ExtractionRequest extractionRequest) {
        int retry = 1;
        int maxRetry = 3;
        SchemaDefinition schemaDefinition =
                schemaDefinitionRepository.findById(extractionRequest.getSchemaId())
                        .orElseThrow(() -> new RuntimeException("Schema not found"));

        String optimizedSchema = cleanAiResponse(schemaDefinition.getOptimizedSchema());
        String sourceText = extractionRequest.getText();
        String output = callLlm(optimizedSchema, sourceText, schemaDefinition.getModelName());
        output = cleanAiResponse(output);

        List<String> errors = schemaGuardrailsService.validate(output, optimizedSchema, sourceText);

        while (!errors.isEmpty() && retry <= maxRetry) {
            output = llmConnectionService.retryWithErrors(
                    output, errors, optimizedSchema, sourceText, schemaDefinition.getModelName());
            output = cleanAiResponse(output);
            errors = schemaGuardrailsService.validate(output, optimizedSchema, sourceText);
            retry++;
            log.info("SCOPE retry {} - remaining errors: {}", retry - 1, errors);
        }

        if (schemaDefinition.getRelayTransformSpec() != null && !schemaDefinition.getRelayTransformSpec().isBlank()) {
            output = relayService.applyTransform(output, schemaDefinition.getRelayTransformSpec());
        }
        return output;
    }

    /** Used by ARCHITECT to evaluate a schema on synthetic examples (no RELAY). */
    public String extractWithSchema(String optimizedSchema, String text, String modelName) {
        String output = callLlm(optimizedSchema, text, modelName);
        output = cleanAiResponse(output);
        List<String> errors = schemaGuardrailsService.validate(output, optimizedSchema, text);
        int retries = 0;
        while (!errors.isEmpty() && retries < 2) {
            output = llmConnectionService.retryWithErrors(output, errors, optimizedSchema, text, modelName);
            output = cleanAiResponse(output);
            errors = schemaGuardrailsService.validate(output, optimizedSchema, text);
            retries++;
        }
        return output;
    }

    private String callLlm(String optimizedSchema, String text, String modelName) {
        LlmResponse llmResponse = llmConnectionService.JsonFromText(optimizedSchema, text, modelName);
        if (llmResponse == null || llmResponse.getChoices() == null || llmResponse.getChoices().isEmpty()) {
            return "{}";
        }
        String content = llmResponse.getChoices().get(0).getMessage().getContent();
        return content != null ? content : "{}";
    }

    private String cleanAiResponse(String content) {
        if (content == null) return "{}";
        content = content.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1);
        }
        return "{}";
    }
}
