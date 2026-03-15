package com.parser.Architect.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;

/**
 * RELAY: Applies transformation from optimized-schema output back to original-schema format.
 * Transform spec is JSON: for each original field, either a string (direct key from optimized)
 * or an object with "concat" array of keys (values joined as strings).
 */
@Service
@Slf4j
public class RelayService {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Applies the stored transformation spec to convert optimized extraction output to original format.
     * Spec format: { "originalField": "optimizedKey" } or { "originalField": { "concat": ["key1", "key2"] } }
     */
    public String applyTransform(String optimizedOutputJson, String transformSpecJson) {
        try {
            JsonNode optimized = mapper.readTree(optimizedOutputJson);
            JsonNode spec = mapper.readTree(transformSpecJson);
            ObjectNode result = mapper.createObjectNode();

            JsonNode mapping = spec.has("mapping") ? spec.get("mapping") : spec;
            if (!mapping.isObject()) {
                log.warn("RELAY spec has no 'mapping' object, returning optimized as-is");
                return optimizedOutputJson;
            }

            Iterator<String> fieldNames = mapping.fieldNames();
            while (fieldNames.hasNext()) {
                String originalField = fieldNames.next();
                JsonNode rule = mapping.get(originalField);
                if (rule.isTextual()) {
                    String optKey = rule.asText();
                    if (optimized.has(optKey)) {
                        result.set(originalField, optimized.get(optKey));
                    }
                } else if (rule.isObject() && rule.has("concat")) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode keyNode : rule.get("concat")) {
                        String k = keyNode.asText();
                        if (optimized.has(k)) {
                            JsonNode v = optimized.get(k);
                            if (v.isNumber()) sb.append(v.asText());
                            else sb.append(v.asText());
                        }
                    }
                    result.put(originalField, sb.toString());
                }
            }
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("RELAY transform failed: {}", e.getMessage());
            return optimizedOutputJson;
        }
    }
}
