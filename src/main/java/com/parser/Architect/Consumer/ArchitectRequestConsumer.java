package com.parser.Architect.Consumer;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import com.parser.Architect.Entites.CompletedArchitectRequests;
import com.parser.Architect.Repositories.CompletedArchitectRequestsRepo;
import com.parser.Architect.Services.AlgorithService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
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
    @RabbitListener(queues = MAIN_QUEUE)
    public void Consume(ArchitectRequest architectRequest,
                        @Header(required = false, value = "x-retry-count") Integer retryCount) {
        try {
            log.info("processing Architect Request");
            Long schemaId = algorithService.refineJsonForBetterPerformance(architectRequest);
            completedArchitectRequestsRepo.save(
                    CompletedArchitectRequests.builder().schemaId(schemaId).build()
            );
        } catch (Exception e) {
            int count = retryCount == null ? 0 : retryCount;
            if (count < MAX_RETRIES) {
                rabbitTemplate.convertAndSend(RETRY_EXCHANGE, RETRY_ROUTING_KEY, architectRequest, msg -> {
                    msg.getMessageProperties().getHeaders().put("x-retry-count", count + 1);
                    return msg;
                });
            } else {
                rabbitTemplate.convertAndSend(MAIN_EXCHANGE, DLQ_ROUTING_KEY, architectRequest);
            }
        }
    }
}
