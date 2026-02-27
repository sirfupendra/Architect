package com.parser.Architect.Controllers;



import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Services.AlgorithService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;


@RestController
@Slf4j
@RequiredArgsConstructor
public class AlgorithmController {
  private final AlgorithService algorithService;


    @PostMapping("/parseJson")
    public void refineJson(@RequestBody ArchitectRequest architectRequest){
        log.info(String.valueOf(architectRequest.getJsonData().getNodeType()));
        algorithService.refineJsonForBetterPerformance(architectRequest);
    }

}
