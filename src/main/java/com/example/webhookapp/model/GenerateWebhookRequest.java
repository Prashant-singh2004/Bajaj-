package com.example.webhookapp.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class GenerateWebhookRequest {
    private String name;
    private String regNo;
    private String email;
} 