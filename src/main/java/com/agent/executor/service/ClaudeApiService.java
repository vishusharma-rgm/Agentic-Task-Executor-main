package com.agent.executor.service;

import com.agent.executor.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Groq API version — FREE, no card needed.
 * Uses OpenAI-compatible format.
 * Model: llama3-70b-8192
 */
@Service
public class ClaudeApiService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are an AI agent with access to exactly 3 tools:
            
            1. web_search  — Search the internet for current information
               Input: a search query string
            
            2. code_exec   — Execute Python code in a sandboxed environment
               Input: Python code as a string
            
            3. file_ops    — Read or write files in the workspace
               Input: JSON like {"action":"write","filename":"out.txt","content":"..."}
                           or {"action":"read","filename":"out.txt"}
            
            RULES:
            - Break the user task into at most 3 sequential steps
            - Each response must call exactly ONE tool
            - Respond ONLY in this JSON format, no prose, no markdown:
            {
              "thought": "why I am calling this tool",
              "tool": "web_search",
              "input": "the input to the tool"
            }
            - When the task is complete, respond with:
            {
              "thought": "task is done",
              "tool": "DONE",
              "input": "brief summary of what was accomplished"
            }
            """;

    public ToolCall getNextToolCall(List<Map<String, String>> conversationHistory) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 1024);
            body.put("temperature", 0.2);

            ArrayNode messages = objectMapper.createArrayNode();

            // System prompt
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            messages.add(systemMsg);

            // Conversation history
            for (Map<String, String> msg : conversationHistory) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                msgNode.put("role", msg.get("role"));
                msgNode.put("content", msg.get("content"));
                messages.add(msgNode);
            }
            body.set("messages", messages);

            // Groq uses Bearer token (OpenAI style)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> request = new HttpEntity<>(
                objectMapper.writeValueAsString(body), headers
            );

            log.debug("Calling Groq API with {} messages", conversationHistory.size());

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, request, String.class
            );

            // Parse OpenAI-format response
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String text = responseJson
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

            log.debug("Groq response: {}", text);
            return parseToolCall(text);

        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new RuntimeException("Groq API error: " + e.getMessage(), e);
        }
    }

    private ToolCall parseToolCall(String text) throws Exception {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```json?\\s*", "").replace("```", "").trim();
        }
        JsonNode json = objectMapper.readTree(cleaned);
        ToolCall toolCall = new ToolCall();
        toolCall.setTool(json.path("tool").asText("DONE"));
        JsonNode inputNode = json.path("input");
if (inputNode.isObject()) {
    toolCall.setInput(inputNode.toString());
} else {
    toolCall.setInput(inputNode.asText(""));
}
        toolCall.setThought(json.path("thought").asText(""));
        return toolCall;
    }
}
