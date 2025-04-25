package com.example.webhookapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {
    @JsonProperty(" name ")
    private String name;
    
    @JsonProperty(" regNo ")
    private String regNo;
    
    @JsonProperty(" email ")
    private String email;
} 