package com.example.webhookapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    private static final Logger logger = LoggerFactory.getLogger(RestTemplateConfig.class);

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        
        RestTemplate restTemplate = new RestTemplate(
            new BufferingClientHttpRequestFactory(factory)
        );
        
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
                if (response.getStatusCode().is5xxServerError()) {
                    logger.error("Server error: {} {}", response.getStatusCode(), new String(getResponseBody(response)));
                } else if (response.getStatusCode().is4xxClientError()) {
                    logger.error("Client error: {} {}", response.getStatusCode(), new String(getResponseBody(response)));
                }
                super.handleError(response);
            }
        });
        
        return restTemplate;
    }
} 