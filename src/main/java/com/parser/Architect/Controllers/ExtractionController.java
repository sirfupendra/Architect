package com.parser.Architect.Controllers;

import com.parser.Architect.Dtos.Request.ExtractionRequest;
import com.parser.Architect.Services.ExtractionJsonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExtractionController {
   private final ExtractionJsonService extractionJsonService;
    @PostMapping("/extractJsonForText")
    public ResponseEntity extractJsonData(@RequestBody ExtractionRequest extractionRequest){
        String res=extractionJsonService.JsonFromText(extractionRequest);
        return ResponseEntity.status(200).body(res);
    }
}
