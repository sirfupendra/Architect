package com.parser.Architect.Dtos.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExtractionRequest {
    @NotNull
    private String Text;

    @NotEmpty
    private Long schemaId;

}
