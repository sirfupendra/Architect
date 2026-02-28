package com.parser.Architect.Dtos.Request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ExtractionRequest {
    @NotEmpty
    private String Text;

    @NotEmpty
    private Long schemaId;

}
