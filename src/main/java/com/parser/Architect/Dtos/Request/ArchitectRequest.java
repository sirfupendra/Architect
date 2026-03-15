package com.parser.Architect.Dtos.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.databind.JsonNode;

@Getter
@Setter
public class ArchitectRequest {
    @NotNull
    private JsonNode jsonData;

    private String Description;

    /** Optional seed examples for synthetic data generation. Can be null or empty. */
    private JsonNode[] seedDataSet;

    @NotNull
    private String LlmModel;

}
