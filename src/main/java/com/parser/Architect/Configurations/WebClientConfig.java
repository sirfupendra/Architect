package com.parser.Architect.Configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${openrouter.api.key}")
    private String openRouterApiKey;

    @Value("${openrouter.api.url}")
    private String openRouterApiUrl;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Pre-configured WebClient for OpenRouter — includes Authorization header
     * and base URL so callers don't need to set them manually.
     */
    @Bean
    public WebClient openRouterWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60))
                        .addHandlerLast(new WriteTimeoutHandler(10)))
                // disable connection pooling to avoid stale connections
                .disableRetry(false);

        return WebClient.builder()
                .baseUrl(openRouterApiUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + openRouterApiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("HTTP-Referer", "https://github.com/sirfupendra/Architect")
                .defaultHeader("X-Title", "Architect")
                .build();
    }
}