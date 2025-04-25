package com.example.webhookapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {
    @JsonProperty("reg_no")
    private String regNo;
    
    @JsonProperty("outcome")
    private List<Integer> outcome;
} 