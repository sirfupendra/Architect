package com.parser.Architect.Producer;

import com.parser.Architect.Dtos.Request.ArchitectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.parser.Architect.Configurations.RabbitMqConfiguration.*;

@Component
@RequiredArgsConstructor
public class ArchitectRequestEvent {

    private final RabbitTemplate rabbitTemplate;

    public void send(ArchitectRequest architectRequest) {
        rabbitTemplate.convertAndSend(MAIN_EXCHANGE, ROUTING_KEY, architectRequest);
    }
}
