package com.parser.Architect.Util;

/**
 * Extracts JSON from LLM responses that may be wrapped in XML tags or markdown.
 */
public final class LlmResponseUtils {

    public static String extractJson(String content) {
        if (content == null) return "{}";
        content = content.replaceAll("```json", "").replaceAll("```", "").trim();
        // Extract from <json_schema>...</json_schema> or <attribute_values>...</attribute_values>
        for (String tag : new String[] { "json_schema", "attribute_values" }) {
            String open = "<" + tag + ">";
            String close = "</" + tag + ">";
            int start = content.indexOf(open);
            int end = content.indexOf(close);
            if (start != -1 && end != -1 && end > start) {
                content = content.substring(start + open.length(), end).trim();
                break;
            }
        }
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        int arrStart = content.indexOf("[");
        int arrEnd = content.lastIndexOf("]");
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1);
        }
        if (arrStart != -1 && arrEnd != -1 && arrEnd > arrStart) {
            return content.substring(arrStart, arrEnd + 1);
        }
        return "{}";
    }
}
