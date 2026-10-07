package com.agent.executor.service;

import com.agent.executor.model.TaskResult;
import com.agent.executor.model.TaskResult.Status;
import com.agent.executor.model.TaskResult.StepLog;
import com.agent.executor.model.ToolCall;
import com.agent.executor.tool.CodeExecTool;
import com.agent.executor.tool.FileOpsTool;
import com.agent.executor.tool.WebSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * THE CORE AGENT LOOP — This is the entire project in one class.
 *
 * Loop pattern:
 *   1. Send prompt to Claude → get tool call (JSON)
 *   2. Execute the tool → get result
 *   3. Add result to conversation history
 *   4. Repeat until Claude says "DONE" or max steps reached
 *
 * Phase 1: Basic loop with web_search only
 * Phase 2: @Async + all 3 tools + retry
 * Phase 3: Updates TaskResult for REST polling
 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    private final ClaudeApiService claudeApi;
    private final WebSearchTool webSearchTool;
    private final CodeExecTool codeExecTool;
    private final FileOpsTool fileOpsTool;

    @Value("${agent.max-steps}")
    private int maxSteps;

    @Value("${agent.max-retries}")
    private int maxRetries;

    public AgentLoopService(ClaudeApiService claudeApi,
                            WebSearchTool webSearchTool,
                            CodeExecTool codeExecTool,
                            FileOpsTool fileOpsTool) {
        this.claudeApi = claudeApi;
        this.webSearchTool = webSearchTool;
        this.codeExecTool = codeExecTool;
        this.fileOpsTool = fileOpsTool;
    }

    /**
     * PHASE 2: @Async — runs on Spring's thread pool, doesn't block the HTTP request.
     * The caller gets a TaskResult immediately; this method fills it in the background.
     */
    @Async
    public CompletableFuture<Void> runAgentLoop(String userPrompt, TaskResult taskResult) {
        log.info("▶ Starting agent loop for task: {}", taskResult.getTaskId());
        taskResult.setStatus(Status.RUNNING);

        // Conversation history — grows with each tool call + result
        List<Map<String, String>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "user", "content", userPrompt));

        try {
            for (int step = 1; step <= maxSteps; step++) {
                log.info("--- Step {}/{} ---", step, maxSteps);

                // Ask Claude what to do next
                ToolCall toolCall = claudeApi.getNextToolCall(conversation);
                log.info("Claude chose: {} | input: {}", toolCall.getTool(), toolCall.getInput());

                // If Claude says DONE, we're finished
                if ("DONE".equalsIgnoreCase(toolCall.getTool())) {
                    log.info("✅ Agent declared DONE: {}", toolCall.getInput());
                    taskResult.setFinalSummary(toolCall.getInput());
                    taskResult.setStatus(Status.DONE);
                    return CompletableFuture.completedFuture(null);
                }

                // Execute tool with retry logic (Phase 3)
                StepLog stepLog = new StepLog(step, toolCall.getTool(), toolCall.getInput());
                String toolResult = executeWithRetry(toolCall, stepLog);
                taskResult.addStep(stepLog);

                // Append tool result to conversation so Claude has context
                String assistantMessage = String.format(
                    "{\"thought\":\"%s\",\"tool\":\"%s\",\"input\":\"%s\"}",
                    toolCall.getThought(), toolCall.getTool(), escapeJson(toolCall.getInput())
                );
                conversation.add(Map.of("role", "assistant", "content", assistantMessage));

                String toolResultMessage = String.format(
                    "Tool result for %s:\n%s", toolCall.getTool(), toolResult
                );
                conversation.add(Map.of("role", "user", "content", toolResultMessage));
            }

            // Max steps reached without DONE
            log.warn("Max steps ({}) reached without completion", maxSteps);
            taskResult.setFinalSummary("Reached max steps limit. Partial results may be in workspace.");
            taskResult.setStatus(Status.DONE);

        } catch (Exception e) {
            log.error("Agent loop failed: {}", e.getMessage(), e);
            taskResult.setFinalSummary("Agent failed: " + e.getMessage());
            taskResult.setStatus(Status.FAILED);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * PHASE 3: Retry logic — try up to maxRetries times before giving up.
     */
    private String executeWithRetry(ToolCall toolCall, StepLog stepLog) {
        int attempts = 0;
        String lastError = "";

        while (attempts < maxRetries) {
            try {
                String result = dispatchTool(toolCall).get(); // blocking get within async thread
                stepLog.complete(result, true, attempts);
                return result;
            } catch (Exception e) {
                attempts++;
                lastError = e.getMessage();
                log.warn("Tool {} failed (attempt {}/{}): {}",
                    toolCall.getTool(), attempts, maxRetries, lastError);

                if (attempts < maxRetries) {
                    try {
                        // Simple backoff: 1s, 2s, 3s...
                        Thread.sleep(attempts * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        String failureMsg = "Tool failed after " + maxRetries + " attempts: " + lastError;
        stepLog.complete(failureMsg, false, attempts);
        log.error("Tool {} permanently failed after {} retries", toolCall.getTool(), maxRetries);
        return failureMsg;
    }

    /**
     * Routes tool call to the right tool implementation.
     */
    private CompletableFuture<String> dispatchTool(ToolCall toolCall) {
        return switch (toolCall.getTool().toLowerCase()) {
            case "web_search"  -> webSearchTool.search(toolCall.getInput());
            case "code_exec"   -> codeExecTool.execute(toolCall.getInput());
            case "file_ops"    -> fileOpsTool.execute(toolCall.getInput());
            default -> CompletableFuture.completedFuture(
                "Unknown tool: " + toolCall.getTool() +
                ". Available: web_search, code_exec, file_ops"
            );
        };
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
