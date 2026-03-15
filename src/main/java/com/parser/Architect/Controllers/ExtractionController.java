package com.parser.Architect.Controllers;

import com.parser.Architect.Dtos.Request.ExtractionRequest;
import com.parser.Architect.Services.ExtractionJsonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionJsonService extractionJsonService;

    /** PARSE SCOPE: Extract structured JSON from text using optimized schema (and optional RELAY). */
    @PostMapping("/extractJsonForText")
    public ResponseEntity<String> extractJsonData(@Valid @RequestBody ExtractionRequest extractionRequest) {
        String res=extractionJsonService.JsonFromText(extractionRequest);
        return ResponseEntity.ok(res);
    }
}
