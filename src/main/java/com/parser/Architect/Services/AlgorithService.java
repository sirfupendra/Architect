package com.parser.Architect.Services;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Dtos.Response.LlmResponse;
import com.parser.Architect.Entites.LlmModels;
import com.parser.Architect.Entites.SchemaDefinition;
import com.parser.Architect.Repositories.LlmModelsRepository;
import com.parser.Architect.Repositories.SchemaDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class AlgorithService {
    private final LlmModelsRepository llmModelsRepository;
    private final LlmConnectionService llmConnectionService;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    public void refineJsonForBetterPerformance(ArchitectRequest architectRequest){
         try{
          LlmModels llmModels =llmModelsRepository.findByModelNameContaining(architectRequest.getLlmModel());
          log.info(String.valueOf(llmModels));
             LlmResponse response =llmConnectionService.sendRequest(llmModels.getModelUrl(),llmModels.getMyConnectionKey(),architectRequest.getDescription(),architectRequest.getLlmModel(),architectRequest.getJsonData());
           log.info("aiResponseForMe = "+response.toString());
           if(response!=null && !response.getChoices().isEmpty()){
               String rawOutput=response.getChoices().get(0).getMessage().getContent();
               log.info(rawOutput);
               String cleanedJson = cleanAiResponse(rawOutput);
               SchemaDefinition schemaDefinition=    SchemaDefinition.builder().optimizedSchema(cleanedJson).modelName(architectRequest.getLlmModel()).originalSchema(architectRequest.getJsonData().toString()).build();
               schemaDefinitionRepository.save(schemaDefinition);
           }

         }
         catch (Exception e){
             log.error(e.getLocalizedMessage());
         }
    }
    private String cleanAiResponse(String content) {
        if (content == null) return "{}";
         content.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1);
        }
        return "{}";
    }
}
