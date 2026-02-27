package com.parser.Architect.Enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LLMMODELS {
    CHATGPT("gpt-4o-mini"),
    GEMINI("gemini");
    String llmModel;

}
