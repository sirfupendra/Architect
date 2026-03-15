package com.parser.Architect.Controllers;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Dtos.Response.ArchitectResponse;
import com.parser.Architect.Services.AlgorithService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AlgorithmController {

    private final AlgorithService algorithService;

    /**
     * PARSE ARCHITECT: Optimizes JSON schema for LLM extraction and persists it.
     * Returns schemaId to use in POST /extractJsonForText.
     */
    @PostMapping("/parseJson")
    public ResponseEntity<ArchitectResponse> refineJson(@Valid @RequestBody ArchitectRequest architectRequest) {
        log.info("ARCHITECT request for model {}", architectRequest.getLlmModel());
        Long schemaId = algorithService.refineJsonForBetterPerformance(architectRequest);
        return ResponseEntity.ok(ArchitectResponse.builder()
                .schemaId(schemaId)
                .message("Schema optimized and saved. Use schemaId for extraction.")
                .build());
    }
}
