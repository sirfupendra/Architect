package com.parser.Architect.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * SCOPE guardrails: validates extraction output against schema and source text.
 * Implements:
 *  (1) Missing attribute check
 *  (2) Grounding verification — with semantic date grounding (handles "20th June" -> "2024-06-20")
 *  (3) Rule compliance (pattern, enum, minLength, maxLength)
 *
 * NOTE: Errors are REPORTED for retry guidance — fields are NEVER removed from output.
 */
@Service
@Slf4j
public class SchemaGuardrailsService {

    private final ObjectMapper mapper = new ObjectMapper();

    // Common date formatters the LLM might produce
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    // Month name variants for semantic matching
    private static final List<String[]> MONTH_VARIANTS = List.of(
            new String[]{"january", "jan", "1"},
            new String[]{"february", "feb", "2"},
            new String[]{"march", "mar", "3"},
            new String[]{"april", "apr", "4"},
            new String[]{"may", "may", "5"},
            new String[]{"june", "jun", "6"},
            new String[]{"july", "jul", "7"},
            new String[]{"august", "aug", "8"},
            new String[]{"september", "sep", "sept", "9"},
            new String[]{"october", "oct", "10"},
            new String[]{"november", "nov", "11"},
            new String[]{"december", "dec", "12"}
    );

    /**
     * Validates extraction JSON against schema and source text.
     * Returns list of error messages — does NOT modify the extraction JSON.
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

            // 2 & 3: Grounding + rule compliance for all present fields
            validateNode(extraction, properties, "", sourceText, errors);

        } catch (Exception e) {
            errors.add("Invalid JSON or schema: " + e.getMessage());
        }
        return errors;
    }

    private void validateNode(JsonNode value, JsonNode schemaProps, String path,
                              String sourceText, List<String> errors) {
        if (schemaProps == null || !schemaProps.isObject()) return;

        schemaProps.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode fieldSchema = entry.getValue();
            String fieldPath = path.isEmpty() ? field : path + "." + field;
            JsonNode fieldValue = value.isObject() && value.has(field) ? value.get(field) : null;

            // Skip null/missing — missing check already handled above
            if (fieldValue == null || fieldValue.isNull()) return;

            // Nested object
            if (fieldSchema.has("properties") && fieldSchema.get("properties").isObject()) {
                validateNode(fieldValue, fieldSchema.get("properties"), fieldPath, sourceText, errors);
                return;
            }

            // Array of objects
            if (fieldSchema.has("items") && fieldValue.isArray()) {
                JsonNode itemsSchema = fieldSchema.get("items");
                if (itemsSchema.has("properties")) {
                    for (JsonNode item : fieldValue) {
                        validateNode(item, itemsSchema.get("properties"),
                                fieldPath + "[]", sourceText, errors);
                    }
                } else {
                    for (JsonNode item : fieldValue) {
                        checkLeafRules(fieldPath, item, itemsSchema, fieldSchema, sourceText, errors);
                    }
                }
                return;
            }

            checkLeafRules(fieldPath, fieldValue, fieldSchema, fieldSchema, sourceText, errors);
        });
    }

    private void checkLeafRules(String fieldPath, JsonNode value, JsonNode fieldSchema,
                                JsonNode parentSchema, String sourceText, List<String> errors) {
        String strValue = value.isTextual() ? value.asText() : value.toString();

        // ── Grounding check (string leaves only) ──────────────────────────────
        if (value.isTextual() && !strValue.isBlank() && sourceText != null && !sourceText.isBlank()) {
            if (!isGrounded(strValue, fieldSchema, sourceText)) {
                // Report as error for retry — DO NOT remove the field
                errors.add("Field '" + fieldPath + "' value not grounded in source text: \""
                        + truncate(strValue, 50) + "\"");
            }
        }

        // ── Type check ────────────────────────────────────────────────────────
        if (fieldSchema.has("type")) {
            String type = fieldSchema.get("type").asText();
            switch (type) {
                case "string":
                    if (!value.isTextual())
                        errors.add("Field '" + fieldPath + "' must be string");
                    break;
                case "integer":
                    if (!value.isInt() && !value.isLong())
                        errors.add("Field '" + fieldPath + "' must be integer");
                    break;
                case "number":
                    if (!value.isNumber())
                        errors.add("Field '" + fieldPath + "' must be number");
                    break;
                default:
                    break;
            }
        }

        // ── String-specific rule checks ───────────────────────────────────────
        if (value.isTextual()) {
            if (fieldSchema.has("minLength")) {
                int min = fieldSchema.get("minLength").asInt();
                if (strValue.length() < min)
                    errors.add("Field '" + fieldPath + "' length " + strValue.length()
                            + " < minLength " + min);
            }
            if (fieldSchema.has("maxLength")) {
                int max = fieldSchema.get("maxLength").asInt();
                if (strValue.length() > max)
                    errors.add("Field '" + fieldPath + "' length " + strValue.length()
                            + " > maxLength " + max);
            }
            if (fieldSchema.has("pattern")) {
                try {
                    String patternStr = fieldSchema.get("pattern").asText();
                    if (!Pattern.matches(patternStr, strValue))
                        errors.add("Field '" + fieldPath + "' does not match pattern: " + patternStr);
                } catch (PatternSyntaxException e) {
                    log.warn("Invalid pattern in schema for {}: {}", fieldPath, e.getMessage());
                }
            }
            if (fieldSchema.has("enum")) {
                boolean found = false;
                for (JsonNode e : fieldSchema.get("enum")) {
                    if (e.asText().equalsIgnoreCase(strValue)) {
                        found = true;
                        break;
                    }
                }
                if (!found) errors.add("Field '" + fieldPath + "' value \"" + strValue + "\" not in enum");
            }
        }
    }

    // ── Grounding logic ───────────────────────────────────────────────────────

    /**
     * Returns true if the extracted value is semantically present in the source text.
     * Handles:
     *  - Direct string match (normalized)
     *  - Date format conversion ("2024-06-20" grounded by "20th June", "June 20", "20/06" etc.)
     *  - Numeric values embedded in text
     */
    private boolean isGrounded(String extractedValue, JsonNode fieldSchema, String sourceText) {
        String normalizedSource = normalizeForMatch(sourceText);
        String normalizedValue = normalizeForMatch(extractedValue);

        // 1. Direct match — fastest path
        if (normalizedSource.contains(normalizedValue)) return true;

        // 2. Try semantic date matching
        if (looksLikeDate(extractedValue)) {
            return isDateGrounded(extractedValue, sourceText);
        }

        // 3. Numeric value — check if the number appears in text
        try {
            double numVal = Double.parseDouble(extractedValue);
            // Check various representations: "29.99", "29", "$29.99"
            String intStr = String.valueOf((long) numVal);
            if (normalizedSource.contains(intStr)) return true;
            if (normalizedSource.contains(extractedValue)) return true;
        } catch (NumberFormatException ignored) {}

        // 4. Check if schema field has allowed_date_formats — treat as date
        if (fieldSchema != null && fieldSchema.has("allowed_date_formats")) {
            return isDateGrounded(extractedValue, sourceText);
        }

        return false;
    }

    /**
     * Checks if an extracted date string is semantically grounded in the source text.
     * Handles conversions like "2024-06-20" being present as "20th June", "June 20", "20/6" etc.
     */
    private boolean isDateGrounded(String extractedDate, String sourceText) {
        if (extractedDate == null || extractedDate.isBlank()) return true;

        // Direct match first
        if (normalizeForMatch(sourceText).contains(normalizeForMatch(extractedDate))) return true;

        // Parse the date
        LocalDate date = tryParseDate(extractedDate);
        if (date == null) {
            // Can't parse — don't block, give benefit of doubt
            log.debug("Could not parse date value '{}' for grounding check", extractedDate);
            return true;
        }

        String lowerSource = sourceText.toLowerCase();
        int day = date.getDayOfMonth();
        int month = date.getMonthValue();
        String year = String.valueOf(date.getYear());

        // Get all month name variants for this month
        String[] monthVariants = MONTH_VARIANTS.get(month - 1);

        // Check if day appears in source
        boolean dayFound = lowerSource.contains(String.valueOf(day))
                || lowerSource.contains(day + "st")
                || lowerSource.contains(day + "nd")
                || lowerSource.contains(day + "rd")
                || lowerSource.contains(day + "th");

        // Check if month appears in source (by name or number)
        boolean monthFound = false;
        for (String variant : monthVariants) {
            if (lowerSource.contains(variant)) {
                monthFound = true;
                break;
            }
        }
        // Also check numeric month with separators
        if (!monthFound) {
            monthFound = lowerSource.contains("/" + month + "/")
                    || lowerSource.contains("-" + month + "-")
                    || lowerSource.contains("." + month + ".")
                    || lowerSource.contains(String.format("%02d", month));
        }

        // If both day and month are found → grounded
        if (dayFound && monthFound) return true;

        // If only year provided (no day/month in original text), just year match is enough
        if (lowerSource.contains(year) && !dayFound && !monthFound) return true;

        // Relative date expressions — if source has "today", "tomorrow", "next week" etc.
        // we can't validate the exact date, so give benefit of doubt
        if (containsRelativeDateExpression(lowerSource)) return true;

        return false;
    }

    private boolean looksLikeDate(String value) {
        if (value == null || value.length() < 6) return false;
        // Matches patterns like: 2024-06-20, 06/20/2024, 20-06-2024 etc.
        return value.matches(".*\\d{4}.*") && value.matches(".*[-/.].*");
    }

    private LocalDate tryParseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private boolean containsRelativeDateExpression(String lowerText) {
        return lowerText.contains("today") || lowerText.contains("tomorrow")
                || lowerText.contains("yesterday") || lowerText.contains("next week")
                || lowerText.contains("last week") || lowerText.contains("next month")
                || lowerText.contains("this week") || lowerText.contains("next monday")
                || lowerText.contains("next tuesday") || lowerText.contains("next wednesday")
                || lowerText.contains("next thursday") || lowerText.contains("next friday");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        // If no required list defined, treat all top-level fields as required
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