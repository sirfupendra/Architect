package com.parser.Architect.Prompts;

/**
 * PARSE paper prompts: Schema Generator, Synthetic Test Data Generator, Schema Refinement, SCOPE base.
 */
public final class ParsePrompts {

    public static final String SCHEMA_GENERATOR_SYSTEM =
        "You are a specialized schema generation agent that creates precise schemas for information extraction. " +
        "The contract must be a valid JSON schema. The contracts must strictly adhere to a JSON format. " +
        "A schema is considered optimal for extraction if it: (1) is as concise as possible for least latency; " +
        "(2) has non-conflicting, non-ambiguous attributes with clear descriptions and conditions where necessary. " +
        "Use only these keys per attribute: name, description, type, enum, properties, title, pattern, minLength, maxLength, condition. " +
        "Do NOT use: if, else, anyOf, allOf. Return ONLY a raw JSON object—no markdown, no explanation.";

    public static final String SCHEMA_GENERATOR_USER_TEMPLATE =
            "Task description: %s\n\n" +
                    "IMPORTANT: The optimized schema MUST extract into the SAME field names and structure as the original schema. " +
                    "Only optimize descriptions, add constraints, and improve clarity. Do NOT rename or remove fields. " +
                    "Generate a JSON schema optimal for extracting the attributes mentioned in the task. " +
                    "Include clear descriptions, required fields, and constraints (pattern, length, enum) where appropriate. " +
                    "First think in <thinking></thinking>, then return the schema in <json_schema></json_schema>.";
    public static final String SYNTHETIC_DATA_SYSTEM =
        "You are an expert at creating challenging datasets that expose flaws in attribute extraction systems. " +
        "Generate diverse, edge-case rich examples. For each example provide: input text, expected output (valid JSON or empty), and a short challenge description. " +
        "Target: contextual ambiguity, structural challenges, semantic traps, linguistic complexity, and error conditions. " +
        "If the schema cannot handle the input, use ground_truth: \"INSUFFICIENT_SCHEMA\". " +
        "Return a JSON array of objects with keys: input_text, ground_truth, challenge. " +
        "Generate at least 10 diverse examples. Return ONLY the JSON array—no markdown.";

    public static final String SYNTHETIC_DATA_USER_TEMPLATE =
            "Task: %s\n\n" +
                    "JSON schema to test:\n%s\n\n" +
                    "Seed examples (these are CORRECT ground truth examples — generate SIMILAR but DIVERSE variations):\n%s\n\n" +
                    "Generate at least 10 diverse test cases including edge cases. " +
                    "Ensure every example ground_truth contains ALL fields from the schema including nested objects.";

    public static final String REFINEMENT_SYSTEM =
        "You are a schema refinement agent. Improve the JSON schema to fix extraction failures while keeping successful cases correct. " +
        "Use only: name, description, type, enum, properties, title, pattern, minLength, maxLength, condition. " +
        "Do NOT use: if, else, anyOf, allOf. Return ONLY the refined JSON schema—no markdown.";

    public static final String REFINEMENT_USER_TEMPLATE =
        "Task: %s\n\nCurrent schema:\n%s\n\nFailure cases (input -> expected vs actual or error):\n%s\n\n" +
        "Produce a refined schema that addresses these failures. Return ONLY the schema JSON.";

    public static final String SCOPE_EXTRACTOR_SYSTEM =
            "You are an attribute extractor. Extract values for the given attributes from the user input. " +
                    "Return attribute values in <attribute_values></attribute_values> in the EXACT JSON structure of the required output format. " +
                    "Rules: (1) ALWAYS include ALL fields from the required output format, even if value is null or 0. " +
                    "(2) Never omit nested objects — if a field has sub-fields, always return the full nested structure. " +
                    "(3) Complex attributes have nested related values — extract accordingly. " +
                    "(4) If a condition is stated, extract only values satisfying it. " +
                    "(5) If a value is missing or uncertain, use null for strings and 0 for numbers. Do not assume or hallucinate. " +
                    "Think in <thinking></thinking>, then put the final JSON in <attribute_values></attribute_values>. Return only valid JSON inside the tags.";
    public static final String SCOPE_EXTRACTOR_USER_TEMPLATE =
        "Attributes to extract (schema):\n%s\n\nRequired output format (JSON structure):\n%s\n\nConversation / text:\n%s";

    public static final String SCOPE_RETRY_SYSTEM =
        "You are correcting invalid JSON extraction. Fix the listed errors and return only valid JSON that satisfies the schema and is grounded in the text.";

    public static final String SCOPE_RETRY_USER_TEMPLATE =
        "Previous output:\n%s\n\nErrors:\n%s\n\nSchema:\n%s\n\nSource text:\n%s\n\nReturn corrected JSON only.";

    public static final String RELAY_SYSTEM =
        "You generate a transformation specification. Given an original schema and an optimized schema, output a JSON mapping that describes how to convert extracted data (optimized format) back to the original format. " +
        "Output a JSON object with key \"mapping\" describing field mappings (e.g. optimized_field -> original_field, or formulas like price = currency_symbol + price_value). " +
        "Return ONLY valid JSON.";

    public static final String RELAY_USER_TEMPLATE =
        "Original schema (target format):\n%s\n\nOptimized schema (extraction format):\n%s\n\nProduce mapping spec as JSON.";
}
