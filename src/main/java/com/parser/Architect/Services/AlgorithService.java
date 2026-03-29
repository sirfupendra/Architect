package com.parser.Architect.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Entites.SchemaDefinition;
import com.parser.Architect.Repositories.SchemaDefinitionRepository;
import com.parser.Architect.Util.LlmResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * ARCHITECT: Iterative schema optimization for LLM consumption.
 * 1) Generate initial schema from task + optional original schema.
 * 2) Loop: generate synthetic data -> evaluate with SCOPE -> refine schema on failures.
 * 3) RELAY: generate transformation spec (optimized -> original).
 * 4) Persist SchemaDefinition (original, optimized, relaySpec).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlgorithService {

    private static final int MAX_REFINEMENT_ITERATIONS = 5;
    private static final double ACCURACY_THRESHOLD = 0.85;

    private final LlmConnectionService llmConnectionService;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final ExtractionJsonService extractionJsonService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Runs ARCHITECT pipeline and returns the saved schema id for use in extraction. */
    public Long refineJsonForBetterPerformance(ArchitectRequest architectRequest) {
        try {
            String task = architectRequest.getDescription() != null ? architectRequest.getDescription() : "";
            String modelName = architectRequest.getLlmModel();
            String originalSchemaJson = "{}";

            try {
                if (architectRequest.getJsonData() != null) {
                    originalSchemaJson = objectMapper.writeValueAsString(architectRequest.getJsonData());
                }
            } catch (Exception e) {
                log.warn("Failed to serialize schema to JSON", e);
            }
            String seedJson = formatSeedData(architectRequest.getSeedDataSet());

            // Step 1: Generate initial optimized schema
            String rawSchema = llmConnectionService.generateSchema(task, modelName, architectRequest.getJsonData());
            String currentSchema = LlmResponseUtils.extractJson(rawSchema);
            if (!isValidJsonObject(currentSchema)) {
                currentSchema = originalSchemaJson;
            }

            log.info("ARCHITECT: initial schema generated, length={}", currentSchema.length());

            // Step 2: Iterative refinement with synthetic data and evaluation
            for (int iter = 0; iter < MAX_REFINEMENT_ITERATIONS; iter++) {
                String syntheticRaw = llmConnectionService.generateSyntheticData(task, currentSchema, seedJson, modelName);
                String syntheticJson = LlmResponseUtils.extractJson(syntheticRaw);
                List<SyntheticExample> examples = parseSyntheticExamples(syntheticJson);
                if (examples.isEmpty()) {
                    log.info("ARCHITECT: no synthetic examples at iter {}, stopping", iter);
                    break;
                }

                int correct = 0;
                List<String> failureLines = new ArrayList<>();
                for (SyntheticExample ex : examples) {
                    if ("INSUFFICIENT_SCHEMA".equalsIgnoreCase(ex.groundTruth)) continue;
                    String extracted = extractionJsonService.extractWithSchema(currentSchema, ex.inputText, modelName);
                    String normalizedExtracted = normalizeJson(extracted);
                    String normalizedExpected = normalizeJson(ex.groundTruth);
                    if (normalizedExtracted.equals(normalizedExpected)) {
                        correct++;
                    } else {
                        failureLines.add(String.format("input: %s | expected: %s | got: %s",
                                truncate(ex.inputText, 80), truncate(ex.groundTruth, 60), truncate(extracted, 60)));
                    }
                }
                int total = examples.size();
                double accuracy = total > 0 ? (double) correct / total : 1.0;
                log.info("ARCHITECT: iter {} accuracy={}/{} = {}", iter + 1, correct, total, accuracy);

                if (accuracy >= ACCURACY_THRESHOLD || failureLines.isEmpty()) {
                    break;
                }

                String failureSummary = String.join("\n", failureLines.subList(0, Math.min(15, failureLines.size())));
                String refinedRaw = llmConnectionService.refineSchema(task, currentSchema, failureSummary, modelName);
                String refined = LlmResponseUtils.extractJson(refinedRaw);
                if (isValidJsonObject(refined)) {
                    currentSchema = refined;
                }
            }

            // Step 3: RELAY - generate transformation spec (optimized -> original)
            String relaySpec = "{}";
            try {
                String relayRaw = llmConnectionService.generateRelaySpec(originalSchemaJson, currentSchema, modelName);
                relaySpec = LlmResponseUtils.extractJson(relayRaw);
                if (!relaySpec.startsWith("{")) relaySpec = "{}";
            } catch (Exception e) {
                log.warn("RELAY spec generation failed: {}", e.getMessage());
            }

            // Step 4: Persist
            SchemaDefinition schemaDefinition = SchemaDefinition.builder()
                    .originalSchema(originalSchemaJson)
                    .optimizedSchema(currentSchema)
                    .relayTransformSpec(relaySpec)
                    .modelName(modelName)
                    .build();
            schemaDefinition = schemaDefinitionRepository.save(schemaDefinition);
            log.info("ARCHITECT: saved SchemaDefinition id={}", schemaDefinition.getId());
            return schemaDefinition.getId();
        }
        catch (HttpClientErrorException e) {
            log.error("ARCHITECT failed: {}", e.getResponseBodyAsString(), e);
            throw new RuntimeException("Schema refinement failed", e);
        }
        catch (HttpServerErrorException e) {
            log.error("ARCHITECT failed: {}", e.getResponseBodyAsString(), e);
            throw new RuntimeException("Schema refinement failed", e);
        }
        catch(Exception e){
            log.error("ARCHITECT failed: {}", e.getMessage(), e);
            throw new RuntimeException("Schema refinement failed", e);
        }
    }

    private String formatSeedData(Object[] seedDataSet) {
        if (seedDataSet == null || seedDataSet.length == 0) return "[]";
        try {
            return objectMapper.writeValueAsString(seedDataSet);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<SyntheticExample> parseSyntheticExamples(String json) {
        List<SyntheticExample> list = new ArrayList<>();
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return list;
            for (JsonNode el : arr) {
                String input = el.has("input_text") ? el.get("input_text").asText() : "";
                String gt = el.has("ground_truth") ? el.get("ground_truth").toString() : "{}";
                if (el.get("ground_truth").isTextual()) gt = el.get("ground_truth").asText();
                list.add(new SyntheticExample(input, gt));
            }
        } catch (Exception e) {
            log.debug("Could not parse synthetic JSON: {}", e.getMessage());
        }
        return list;
    }

    private static boolean isValidJsonObject(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            JsonNode n = new ObjectMapper().readTree(s);
            return n.isObject();
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeJson(String s) {
        if (s == null) return "";
        try {
            return new ObjectMapper().readTree(s).toString();
        } catch (Exception e) {
            return s.trim();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private record SyntheticExample(String inputText, String groundTruth) {}
}
