package com.parser.Architect.Consumer;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Entites.CompletedArchitectRequests;
import com.parser.Architect.Repositories.CompletedArchitectRequestsRepo;
import com.parser.Architect.Services.AlgorithService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.parser.Architect.Configurations.RabbitMqConfiguration.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ArchitectRequestConsumer {
    private final RabbitTemplate rabbitTemplate;
    private final AlgorithService algorithService;
    private final CompletedArchitectRequestsRepo completedArchitectRequestsRepo;

    private static final int MAX_RETRIES = 2;

    @RabbitListener(queues = MAIN_QUEUE)
    public void Consume(ArchitectRequest architectRequest){
        try{
            log.info("processing Architect Request", architectRequest);
            Long schemaId = algorithService.refineJsonForBetterPerformance(architectRequest);

            CompletedArchitectRequests completedArchitectRequests= CompletedArchitectRequests.builder().schemaId(schemaId).build();

            completedArchitectRequestsRepo.save(completedArchitectRequests);

        }
        catch (Exception e){
            int retryCount = 1;
            if (retryCount < MAX_RETRIES){
                // Send to retry queue
                retryCount++;
                rabbitTemplate.convertAndSend(
                        RETRY_EXCHANGE,
                        RETRY_ROUTING_KEY,
                        architectRequest
                );
            }
            else{
                rabbitTemplate.convertAndSend(MAIN_EXCHANGE,DLQ_ROUTING_KEY,architectRequest);
            }
        }


    }
}
