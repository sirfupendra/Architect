package com.parser.Architect.Dtos.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

@Getter
@Setter
public class ArchitectRequest {
    @NotNull
    private JsonNode jsonData;

    private String Description;

    @NotEmpty
    private JsonNode[] seedDataSet;

    @NotNull
    private String LlmModel;

}
