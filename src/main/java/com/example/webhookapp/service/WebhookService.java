package com.example.webhookapp.service;

import com.example.webhookapp.model.GenerateWebhookResponse;
import com.example.webhookapp.model.RegistrationRequest;
import com.example.webhookapp.model.WebhookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class WebhookService implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private final RestTemplate restTemplate;
    
    @Value("${webhook.generate.url}")
    private String generateWebhookUrl;
    
    @Value("${registration.name}")
    private String name;
    
    @Value("${registration.regNo}")
    private String regNo;
    
    @Value("${registration.email}")
    private String email;

    public WebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(String... args) {
        processWebhook();
    }

    public void processWebhook() {
        try {
            // Step 1: Register and get webhook details
            RegistrationRequest registration = new RegistrationRequest();
            registration.setName(name);
            registration.setRegNo(regNo);
            registration.setEmail(email);
            
            HttpHeaders registrationHeaders = new HttpHeaders();
            registrationHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RegistrationRequest> registrationRequest = new HttpEntity<>(registration, registrationHeaders);
            
            logger.debug("Sending registration request: {}", registration);
            logger.debug("Request headers: {}", registrationHeaders);
            logger.debug("Request body: {}", registrationRequest.getBody());
            
            ResponseEntity<GenerateWebhookResponse> response = restTemplate.postForEntity(
                generateWebhookUrl, 
                registrationRequest, 
                GenerateWebhookResponse.class
            );
            
            logger.debug("Response status code: {}", response.getStatusCode());
            logger.debug("Response headers: {}", response.getHeaders());
            
            GenerateWebhookResponse webhookResponse = response.getBody();
            if (webhookResponse == null) {
                throw new RuntimeException("Invalid webhook response: response body is null");
            }
            logger.debug("Received webhook response: {}", webhookResponse);

            // Validate the response
            if (webhookResponse.getWebhook() == null || webhookResponse.getWebhook().isEmpty()) {
                throw new RuntimeException("Invalid webhook response: webhook URL is required");
            }
            if (webhookResponse.getAccessToken() == null || webhookResponse.getAccessToken().isEmpty()) {
                throw new RuntimeException("Invalid webhook response: access token is required");
            }
            if (webhookResponse.getData() == null) {
                throw new RuntimeException("Invalid webhook response: data is null");
            }
            if (webhookResponse.getData().getUsers() == null || webhookResponse.getData().getUsers().isEmpty()) {
                throw new RuntimeException("Invalid webhook response: no users found");
            }
            if (webhookResponse.getData().getN() < 0) {
                throw new RuntimeException("Invalid webhook response: n must be non-negative");
            }

            // Step 2: Process the data based on registration number
            List<Integer> result;
            if (isOddRegistration()) {
                logger.debug("Processing mutual followers for odd registration number");
                result = findMutualFollowers(webhookResponse.getData().getUsers());
            } else {
                logger.debug("Processing nth level followers for even registration number");
                result = findNthLevelFollowers(
                    webhookResponse.getData().getUsers(),
                    webhookResponse.getData().getFindId(),
                    webhookResponse.getData().getN()
                );
            }
            logger.debug("Processed result: {}", result);

            // Step 3: Send the response to webhook
            WebhookResponse finalResponse = new WebhookResponse(regNo, result);
            logger.debug("Sending final response to webhook: {}", finalResponse);

            sendWebhookResponse(
                webhookResponse.getWebhook(),
                finalResponse,
                webhookResponse.getAccessToken()
            );
        } catch (Exception e) {
            logger.error("Error processing webhook", e);
            throw new RuntimeException("Error processing webhook: " + e.getMessage(), e);
        }
    }

    private boolean isOddRegistration() {
        String lastTwoDigits = regNo.substring(regNo.length() - 2);
        int number = Integer.parseInt(lastTwoDigits);
        return number % 2 != 0;
    }

    private List<Integer> findMutualFollowers(List<GenerateWebhookResponse.User> users) {
        Set<List<Integer>> mutualPairs = new HashSet<>();
        Map<Integer, Set<Integer>> followsMap = new HashMap<>();

        // Build follows map
        for (GenerateWebhookResponse.User user : users) {
            followsMap.put(user.getId(), new HashSet<>(user.getFollows()));
        }

        // Find mutual follows
        for (GenerateWebhookResponse.User user : users) {
            int userId = user.getId();
            for (int followedId : user.getFollows()) {
                if (followsMap.containsKey(followedId) && 
                    followsMap.get(followedId).contains(userId)) {
                    List<Integer> pair = Arrays.asList(
                        Math.min(userId, followedId),
                        Math.max(userId, followedId)
                    );
                    mutualPairs.add(pair);
                }
            }
        }

        return new ArrayList<>(mutualPairs.stream()
            .sorted(Comparator.comparingInt(pair -> pair.get(0)))
            .flatMap(List::stream)
            .toList());
    }

    private List<Integer> findNthLevelFollowers(List<GenerateWebhookResponse.User> users, int findId, int n) {
        logger.debug("Finding {} level followers for user {}", n, findId);
        
        // If n is 0, return empty list as there are no nth level followers
        if (n == 0) {
            logger.debug("n is 0, returning empty list");
            return Collections.emptyList();
        }
        
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        
        // Build the graph
        for (GenerateWebhookResponse.User user : users) {
            graph.put(user.getId(), new HashSet<>(user.getFollows()));
        }
        logger.debug("Built graph: {}", graph);

        // BFS to find nth level followers
        Set<Integer> visited = new HashSet<>();
        Set<Integer> nthLevelFollowers = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(findId);
        visited.add(findId);
        
        int currentLevel = 0;
        while (!queue.isEmpty() && currentLevel < n) {
            int levelSize = queue.size();
            logger.debug("Processing level {}, queue size: {}", currentLevel, levelSize);
            
            for (int i = 0; i < levelSize; i++) {
                int currentUser = queue.poll();
                Set<Integer> followers = graph.getOrDefault(currentUser, Collections.emptySet());
                logger.debug("Processing user {} with followers {}", currentUser, followers);
                
                for (int follower : followers) {
                    if (!visited.contains(follower)) {
                        visited.add(follower);
                        if (currentLevel == n - 1) {
                            nthLevelFollowers.add(follower);
                            logger.debug("Added {} to nth level followers", follower);
                        } else {
                            queue.offer(follower);
                            logger.debug("Added {} to queue for next level", follower);
                        }
                    }
                }
            }
            currentLevel++;
        }

        List<Integer> result = nthLevelFollowers.stream()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        logger.debug("Final result: {}", result);
        return result;
    }

    @Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000))
    private void sendWebhookResponse(String webhookUrl, WebhookResponse response, String accessToken) {
        try {
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                throw new RuntimeException("Webhook URL is required");
            }

            // Extract base URL and path
            java.net.URL url = new java.net.URL(webhookUrl);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            if (url.getPort() != -1) {
                baseUrl += ":" + url.getPort();
            }
            String path = url.getPath();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Only add Authorization header if accessToken is not null
            if (accessToken != null && !accessToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + accessToken);
            }
            
            HttpEntity<WebhookResponse> request = new HttpEntity<>(response, headers);
            
            logger.debug("Sending webhook response to URL: {}", webhookUrl);
            logger.debug("Base URL: {}", baseUrl);
            logger.debug("Path: {}", path);
            logger.debug("Request headers: {}", headers);
            logger.debug("Request body: {}", response);
            
            // Add error handling for the response
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(baseUrl + path, request, String.class);
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                logger.error("Webhook response failed with status: {} and body: {}", 
                    responseEntity.getStatusCode(), responseEntity.getBody());
                throw new RuntimeException("Webhook response failed with status: " + responseEntity.getStatusCode());
            }
            logger.debug("Webhook response successful with status: {}", responseEntity.getStatusCode());
        } catch (Exception e) {
            logger.error("Error sending webhook response", e);
            throw new RuntimeException("Error sending webhook response: " + e.getMessage(), e);
        }
    }
} 