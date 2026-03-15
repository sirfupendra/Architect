package com.parser.Architect.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * SCOPE guardrails: validates extraction output against schema and source text.
 * Implements (1) Missing attribute check, (2) Grounding verification, (3) Rule compliance (pattern, enum, minLength, maxLength).
 */
@Service
@Slf4j
public class SchemaGuardrailsService {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Validates extraction JSON against schema and source text. Returns list of error messages.
     */
    public List<String> validate(String extractionJson, String schemaJson, String sourceText) {
        List<String> errors = new ArrayList<>();
        try {
            JsonNode extraction = mapper.readTree(extractionJson);
            JsonNode schema = mapper.readTree(schemaJson);
            JsonNode properties = getSchemaProperties(schema);
            List<String> required = getRequiredFields(schema, properties);

            // 1. Missing attribute check
            for (String field : required) {
                if (!hasPath(extraction, field)) {
                    errors.add("Missing required field: " + field);
                }
            }

            // 2 & 3: for each present field, check grounding and rules
            validateNode(extraction, properties, "", sourceText, errors);
        } catch (Exception e) {
            errors.add("Invalid JSON or schema: " + e.getMessage());
        }
        return errors;
    }

    private void validateNode(JsonNode value, JsonNode schemaProps, String path, String sourceText, List<String> errors) {
        if (schemaProps == null || !schemaProps.isObject()) return;
        schemaProps.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode fieldSchema = entry.getValue();
            String fieldPath = path.isEmpty() ? field : path + "." + field;
            JsonNode fieldValue = value.isObject() && value.has(field) ? value.get(field) : null;

            if (fieldValue == null || fieldValue.isNull()) return;

            if (fieldSchema.has("properties") && fieldSchema.get("properties").isObject()) {
                validateNode(fieldValue, fieldSchema.get("properties"), fieldPath, sourceText, errors);
                return;
            }
            if (fieldSchema.has("items") && fieldValue.isArray()) {
                JsonNode itemsSchema = fieldSchema.get("items");
                if (itemsSchema.has("properties")) {
                    for (JsonNode item : fieldValue) {
                        validateNode(item, itemsSchema.get("properties"), fieldPath + "[]", sourceText, errors);
                    }
                } else {
                    for (JsonNode item : fieldValue) {
                        checkLeafRules(fieldPath, item, itemsSchema, sourceText, errors);
                    }
                }
                return;
            }

            checkLeafRules(fieldPath, fieldValue, fieldSchema, sourceText, errors);
        });
    }

    private void checkLeafRules(String fieldPath, JsonNode value, JsonNode fieldSchema, String sourceText, List<String> errors) {
        String strValue = value.isTextual() ? value.asText() : value.toString();

        // Grounding: value must appear in source text (for string leaves)
        if (value.isTextual() && !strValue.isBlank() && sourceText != null && !sourceText.isBlank()) {
            if (!normalizeForMatch(sourceText).contains(normalizeForMatch(strValue))) {
                errors.add("Field '" + fieldPath + "' value not grounded in source text: \"" + truncate(strValue, 50) + "\"");
            }
        }

        if (fieldSchema.has("type")) {
            String type = fieldSchema.get("type").asText();
            switch (type) {
                case "string":
                    if (!value.isTextual()) errors.add("Field '" + fieldPath + "' must be string");
                    break;
                case "integer":
                    if (!value.isInt() && !value.isLong()) errors.add("Field '" + fieldPath + "' must be integer");
                    break;
                case "number":
                    if (!value.isNumber()) errors.add("Field '" + fieldPath + "' must be number");
                    break;
                default:
                    break;
            }
        }

        if (value.isTextual()) {
            if (fieldSchema.has("minLength")) {
                int min = fieldSchema.get("minLength").asInt();
                if (strValue.length() < min) errors.add("Field '" + fieldPath + "' length < minLength " + min);
            }
            if (fieldSchema.has("maxLength")) {
                int max = fieldSchema.get("maxLength").asInt();
                if (strValue.length() > max) errors.add("Field '" + fieldPath + "' length > maxLength " + max);
            }
            if (fieldSchema.has("pattern")) {
                try {
                    String patternStr = fieldSchema.get("pattern").asText();
                    if (!Pattern.matches(patternStr, strValue)) {
                        errors.add("Field '" + fieldPath + "' does not match pattern: " + patternStr);
                    }
                } catch (PatternSyntaxException e) {
                    log.warn("Invalid pattern in schema for {}: {}", fieldPath, e.getMessage());
                }
            }
            if (fieldSchema.has("enum")) {
                JsonNode enumNode = fieldSchema.get("enum");
                boolean found = false;
                for (JsonNode e : enumNode) {
                    if (e.asText().equals(strValue)) { found = true; break; }
                }
                if (!found) errors.add("Field '" + fieldPath + "' value not in enum");
            }
        }
    }

    private static boolean hasPath(JsonNode node, String path) {
        if (path == null || path.isEmpty()) return true;
        String[] parts = path.split("\\.");
        JsonNode cur = node;
        for (String p : parts) {
            if (cur == null || !cur.isObject()) return false;
            cur = cur.get(p);
        }
        return cur != null && !cur.isNull();
    }

    private static JsonNode getSchemaProperties(JsonNode schema) {
        if (schema.has("properties")) return schema.get("properties");
        return schema;
    }

    private static List<String> getRequiredFields(JsonNode schema, JsonNode properties) {
        List<String> required = new ArrayList<>();
        if (schema.has("required") && schema.get("required").isArray()) {
            schema.get("required").forEach(r -> required.add(r.asText()));
        }
        if (required.isEmpty() && properties != null && properties.isObject()) {
            properties.fieldNames().forEachRemaining(required::add);
        }
        return required;
    }

    private static String normalizeForMatch(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
