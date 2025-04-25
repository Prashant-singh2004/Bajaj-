package com.example.webhookapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateWebhookResponse {
    @JsonProperty("webhook")
    private String webhook;
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("data")
    private ResponseData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseData {
        @JsonProperty("users")
        private List<User> users;
        
        @JsonProperty("n")
        private int n;
        
        @JsonProperty("find_id")
        private int findId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        @JsonProperty("id")
        private int id;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("follows")
        private List<Integer> follows;
    }
} 