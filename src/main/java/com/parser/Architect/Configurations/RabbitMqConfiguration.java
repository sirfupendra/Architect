package com.parser.Architect.Configurations;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfiguration {

    public static final String MAIN_EXCHANGE = "main.exchange";
    public static final String RETRY_EXCHANGE = "retry.exchange";

    public static final String MAIN_QUEUE = "user.queue";
    public static final String RETRY_QUEUE = "user.retry.queue";
    public static final String DLQ = "user.dlq";

    public static final String ROUTING_KEY = "user.routing.key";
    public static final String RETRY_ROUTING_KEY = "retry.routing.key";
    public static final String DLQ_ROUTING_KEY = "dlq.routing.key";

    // ---------------- EXCHANGES ----------------

    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE); // was empty
    }

    // ---------------- MAIN QUEUE ----------------

    @Bean
    public Queue mainQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", RETRY_EXCHANGE);
        args.put("x-dead-letter-routing-key", RETRY_ROUTING_KEY);
        return new Queue(MAIN_QUEUE, true, false, false, args);
    }

    // ---------------- RETRY QUEUE ----------------

    @Bean
    public Queue retryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 5000);
        args.put("x-dead-letter-exchange", MAIN_EXCHANGE);
        args.put("x-dead-letter-routing-key", ROUTING_KEY);
        return new Queue(RETRY_QUEUE, true, false, false, args);
    }

    // ---------------- DLQ ----------------

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DLQ, true); // was: new (DLQ, true) — missing class name
    }

    // ---------------- BINDINGS ----------------

    @Bean
    public Binding mainBinding() {
        return BindingBuilder.bind(mainQueue())
                .to(mainExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding retryBinding() {
        return BindingBuilder.bind(retryQueue())
                .to(retryExchange())
                .with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(mainExchange())
                .with(DLQ_ROUTING_KEY);
    }
}