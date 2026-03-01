package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.ExtractionRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.SchemaDefinition;
import com.parser.Architect.Repositories.SchemaDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionJsonService {
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final LlmConnectionService llmConnectionService;

    public String JsonFromText(ExtractionRequest extractionRequest) {
      int retry=1;
      int maxretry=3;
        SchemaDefinition schemaDefinition =
                schemaDefinitionRepository.findById(extractionRequest.getSchemaId())
                        .orElseThrow(() -> new RuntimeException("Schema not found"));

        String optimizedSchema = schemaDefinition.getOptimizedSchema();
        optimizedSchema=cleanAiResponse(optimizedSchema);
        String output = callLlm(optimizedSchema,
                extractionRequest.getText(),
                schemaDefinition.getModelName());
        output=cleanAiResponse(output);



        List<String> errors = calculateErrors(output, optimizedSchema);

        while (!errors.isEmpty() && retry<=maxretry) {

            output = llmConnectionService.retryWithErrors(
                    output,
                    errors,
                    optimizedSchema,
                    extractionRequest.getText(),
                    schemaDefinition.getModelName()
            );
            output=cleanAiResponse(output);


            errors = calculateErrors(output, optimizedSchema);
            retry++;
  log.info("left Errors = "+ errors);

        }

        return output;
    }

    private String callLlm(String optimizedSchema, String Text,  String modelName) {
     LlmResponse llmResponse=llmConnectionService.JsonFromText(optimizedSchema,Text,modelName);
     return llmResponse.getChoices().get(0).getMessage().getContent();
    }

    private List<String> calculateErrors(String llmResponse, String optimizedSchema) {
        List<String> errors = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseNode = mapper.readTree(llmResponse);
            JsonNode schemaNode = mapper.readTree(optimizedSchema);

            schemaNode.fieldNames().forEachRemaining(field -> {


                if (!responseNode.has(field)) {
                    errors.add("Missing field: " + field);
                    return;
                }

                JsonNode fieldSchema = schemaNode.get(field);
                JsonNode valueNode = responseNode.get(field);


                if (fieldSchema.has("type")) {
                    String expectedType = fieldSchema.get("type").asText();

                    switch (expectedType) {
                        case "string":
                            if (!valueNode.isTextual()) {
                                errors.add("Field " + field + " must be string");
                            }
                            break;

                        case "integer":
                            if (!valueNode.isInt()) {
                                errors.add("Field " + field + " must be integer");
                            }
                            break;

                        case "number":
                            if (!valueNode.isNumber()) {
                                errors.add("Field " + field + " must be number");
                            }
                            break;
                    }
                }
            });

        } catch (Exception e) {
            errors.add("Invalid JSON format");
        }

        return errors;
    }
    private String cleanAiResponse(String content) {
        if (content == null) return "{}";
        content.replaceAll("```json", "")
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
