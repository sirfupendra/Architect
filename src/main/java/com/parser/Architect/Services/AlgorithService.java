package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.LlmModels;
import com.parser.Architect.Repositories.LlmModelsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class AlgorithService {
    private final LlmModelsRepository llmModelsRepository;
    private final LlmConnectionService llmConnectionService;
    public void refineJsonForBetterPerformance(ArchitectRequest architectRequest){
         try{
          LlmModels llmModels =llmModelsRepository.findByModelNameContaining(architectRequest.getLlmModel());
          log.info(String.valueOf(llmModels));
             LlmResponse response =llmConnectionService.sendRequest(llmModels.getModelUrl(),llmModels.getMyConnectionKey(),architectRequest.getDescription(),architectRequest.getLlmModel(),architectRequest.getJsonData());
           log.info(response.getChoices().toString());
         }
         catch (Exception e){
             log.error(e.getLocalizedMessage());
         }
    }
}
