package com.parser.Architect.Repositories;

import com.parser.Architect.Entites.LlmModels;
import org.springframework.data.repository.CrudRepository;


public interface LlmModelsRepository extends CrudRepository<LlmModels,Long> {

    LlmModels findByModelNameContaining(String modelName);
}
