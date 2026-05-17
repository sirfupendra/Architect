package com.parser.Architect.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;

/**
 * RELAY: Applies transformation from optimized-schema output back to original-schema format.
 *
 * Handles these mapping spec patterns:
 *
 * 1. Direct mapping:
 *    "origin": "origin"
 *
 * 2. Dot-notation nested reconstruction:
 *    "passengers.adults": "passengers.adults"
 *    "passengers.children": "passengers.children"
 *    → Reconstructs: { "passengers": { "adults": 2, "children": 0 } }
 *
 * 3. Concat mapping:
 *    "price": { "concat": ["currency_symbol", "price_value"] }
 *    → "price": "$29.99"
 *
 * 4. Nested object mapping (mapping is itself an object):
 *    "passengers": { "adults": "adults", "children": "children" }
 *    → Reconstructs nested object from flat optimized fields
 */
@Service
@Slf4j
public class RelayService {

    private final ObjectMapper mapper = new ObjectMapper();

    public String applyTransform(String optimizedOutputJson, String transformSpecJson) {
        try {
            JsonNode optimized = mapper.readTree(optimizedOutputJson);
            JsonNode spec = mapper.readTree(transformSpecJson);

            // Get the mapping node
            JsonNode mapping = spec.has("mapping") ? spec.get("mapping") : spec;
            if (!mapping.isObject()) {
                log.warn("RELAY spec has no valid 'mapping' object, returning optimized as-is");
                return optimizedOutputJson;
            }

            // Build result — start with empty object
            ObjectNode result = mapper.createObjectNode();

            Iterator<String> fieldNames = mapping.fieldNames();
            while (fieldNames.hasNext()) {
                String originalField = fieldNames.next();
                JsonNode rule = mapping.get(originalField);

                if (rule.isTextual()) {
                    // ── Pattern 1 & 2: Direct or dot-notation mapping ──────────
                    String optimizedPath = rule.asText();

                    if (originalField.contains(".")) {
                        // e.g. "passengers.adults" -> "passengers.adults"
                        // Extract value from optimized using dot path
                        JsonNode value = getByDotPath(optimized, optimizedPath);
                        if (value != null && !value.isNull()) {
                            // Set into result using dot path (builds nested object)
                            setByDotPath(result, originalField, value);
                        }
                    } else {
                        // Direct field — but source might be dot-path in optimized
                        JsonNode value = getByDotPath(optimized, optimizedPath);
                        if (value != null) {
                            result.set(originalField, value);
                        }
                    }

                } else if (rule.isObject() && rule.has("concat")) {
                    // ── Pattern 3: Concat mapping ─────────────────────────────
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode keyNode : rule.get("concat")) {
                        JsonNode v = getByDotPath(optimized, keyNode.asText());
                        if (v != null && !v.isNull()) {
                            sb.append(v.asText());
                        }
                    }
                    result.put(originalField, sb.toString());

                } else if (rule.isObject()) {
                    // ── Pattern 4: Nested object mapping ──────────────────────
                    // e.g. "passengers": { "adults": "adults", "children": "children" }
                    ObjectNode nestedResult = mapper.createObjectNode();
                    Iterator<String> nestedFields = rule.fieldNames();
                    while (nestedFields.hasNext()) {
                        String nestedOriginal = nestedFields.next();
                        String nestedOptimizedPath = rule.get(nestedOriginal).asText();
                        JsonNode value = getByDotPath(optimized, nestedOptimizedPath);
                        if (value != null && !value.isNull()) {
                            nestedResult.set(nestedOriginal, value);
                        } else {
                            // Put default based on type guess
                            nestedResult.putNull(nestedOriginal);
                        }
                    }
                    result.set(originalField, nestedResult);
                }
            }

            // ── Safety net: if mapping produced nothing useful, return optimized ──
            if (result.isEmpty()) {
                log.warn("RELAY produced empty result, returning optimized output as-is");
                return optimizedOutputJson;
            }

            // ── Ensure all original schema fields exist in result ──────────────
            // (fill nulls for any field in optimized not covered by mapping)
            ensureAllFieldsPresent(result, optimized, mapping);

            return mapper.writeValueAsString(result);

        } catch (Exception e) {
            log.error("RELAY transform failed: {}, returning optimized as-is", e.getMessage());
            return optimizedOutputJson;
        }
    }

    /**
     * Gets a value from a JsonNode using dot-notation path.
     * e.g. "passengers.adults" → node.get("passengers").get("adults")
     */
    private JsonNode getByDotPath(JsonNode node, String dotPath) {
        if (dotPath == null || dotPath.isBlank()) return null;
        String[] parts = dotPath.split("\\.");
        JsonNode current = node;
        for (String part : parts) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        return current;
    }

    /**
     * Sets a value into an ObjectNode using dot-notation path, creating nested objects as needed.
     * e.g. setByDotPath(result, "passengers.adults", IntNode(2))
     * → result = { "passengers": { "adults": 2 } }
     */
    private void setByDotPath(ObjectNode root, String dotPath, JsonNode value) {
        String[] parts = dotPath.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.has(part) || !current.get(part).isObject()) {
                // Create intermediate object node
                ObjectNode child = mapper.createObjectNode();
                current.set(part, child);
                current = child;
            } else {
                current = (ObjectNode) current.get(part);
            }
        }
        // Set the leaf value
        current.set(parts[parts.length - 1], value);
    }

    /**
     * Safety net: if a field exists in the optimized output but wasn't covered
     * by the mapping spec, copy it directly to result (avoids data loss).
     */
    private void ensureAllFieldsPresent(ObjectNode result, JsonNode optimized, JsonNode mapping) {
        optimized.fieldNames().forEachRemaining(field -> {
            // Only add if not already in result and not a nested field handled by dot-notation
            if (!result.has(field)) {
                // Check if this field is referenced anywhere in the mapping
                boolean referenced = isMappingReferenced(field, mapping);
                if (!referenced) {
                    result.set(field, optimized.get(field));
                    log.debug("RELAY: copied unmapped field '{}' directly to result", field);
                }
            }
        });
    }

    private boolean isMappingReferenced(String fieldName, JsonNode mapping) {
        Iterator<JsonNode> values = mapping.elements();
        while (values.hasNext()) {
            JsonNode rule = values.next();
            if (rule.isTextual() && rule.asText().startsWith(fieldName)) return true;
            if (rule.isObject() && rule.has("concat")) {
                for (JsonNode k : rule.get("concat")) {
                    if (k.asText().startsWith(fieldName)) return true;
                }
            }
        }
        return false;
    }
}