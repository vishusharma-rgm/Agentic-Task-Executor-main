package com.agent.executor.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * PHASE 1: Web search via Tavily API.
 * PHASE 2: Made @Async for non-blocking execution.
 *
 * Sign up free at https://tavily.com — generous free tier.
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    @Value("${tavily.api.key}")
    private String tavilyKey;

    @Value("${tavily.api.url}")
    private String tavilyUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Phase 2: @Async makes this run on Spring's task thread pool.
     * The agent loop calls .get() to wait for the result.
     */
    @Async
    public CompletableFuture<String> search(String query) {
        log.info("🔍 WebSearch: \"{}\"", query);
        try {
            // Build Tavily request
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", tavilyKey);
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("max_results", 3);
            body.put("include_answer", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(
                objectMapper.writeValueAsString(body), headers
            );

            ResponseEntity<String> response = restTemplate.exchange(
                tavilyUrl, HttpMethod.POST, request, String.class
            );

            String result = extractTavilyResults(response.getBody());
            log.debug("WebSearch result: {}", result);
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("WebSearch failed: {}", e.getMessage());
            return CompletableFuture.completedFuture("Search failed: " + e.getMessage());
        }
    }

    private String extractTavilyResults(String responseBody) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);
        StringBuilder sb = new StringBuilder();

        // Tavily's direct answer (if available)
        String answer = json.path("answer").asText("");
        if (!answer.isBlank()) {
            sb.append("Summary: ").append(answer).append("\n\n");
        }

        // Individual results
        JsonNode results = json.path("results");
        int count = 0;
        for (JsonNode result : results) {
            if (count >= 3) break;
            sb.append("Source: ").append(result.path("url").asText()).append("\n");
            sb.append("Title: ").append(result.path("title").asText()).append("\n");
            sb.append("Snippet: ").append(result.path("content").asText("")).append("\n\n");
            count++;
        }

        return sb.toString().trim();
    }
}
