package com.agent.executor.controller;

import com.agent.executor.model.TaskResult;
import com.agent.executor.service.TaskManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PHASE 3: REST API endpoints.
 *
 * POST /execute         → submit a task, returns task ID
 * GET  /status/{id}     → poll task progress
 * GET  /health          → sanity check
 */
@RestController
@RequestMapping("/")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final TaskManagerService taskManager;

    public AgentController(TaskManagerService taskManager) {
        this.taskManager = taskManager;
    }

    /**
     * Submit a task.
     *
     * Request:  POST /execute
     *           Content-Type: application/json
     *           {"prompt": "Research top 3 AI frameworks in 2025 and save a comparison file"}
     *
     * Response: {"taskId": "A3F2B1C4", "status": "PENDING", "message": "Task started"}
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeTask(@RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Request body must contain 'prompt' field"));
        }

        log.info("POST /execute — prompt: \"{}\"", prompt);

        TaskResult task = taskManager.submitTask(prompt);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.getTaskId());
        response.put("status", task.getStatus().toString());
        response.put("message", "Task started. Poll /status/" + task.getTaskId() + " for updates.");
        response.put("createdAt", task.getCreatedAt().toString());

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Poll task status.
     *
     * GET /status/A3F2B1C4
     *
     * Response includes: status, steps executed, final summary (when done)
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String taskId) {
        log.debug("GET /status/{}", taskId);

        TaskResult task = taskManager.getTask(taskId);

        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Task not found: " + taskId));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.getTaskId());
        response.put("status", task.getStatus().toString());
        response.put("prompt", task.getOriginalPrompt());
        response.put("stepsCompleted", task.getSteps().size());
        response.put("createdAt", task.getCreatedAt().toString());

        if (task.getCompletedAt() != null) {
            response.put("completedAt", task.getCompletedAt().toString());
        }

        // Include step details
        if (!task.getSteps().isEmpty()) {
            var stepDetails = task.getSteps().stream().map(step -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("step", step.getStepNumber());
                s.put("tool", step.getToolName());
                s.put("input", step.getToolInput());
                s.put("success", step.isSuccess());
                s.put("retries", step.getRetryCount());
                // Only include output snippet to keep response small
                String output = step.getToolOutput();
                s.put("outputPreview", output != null
                    ? output.substring(0, Math.min(200, output.length())) + (output.length() > 200 ? "..." : "")
                    : null);
                return s;
            }).toList();
            response.put("steps", stepDetails);
        }

        if (task.getFinalSummary() != null) {
            response.put("finalSummary", task.getFinalSummary());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Health check — useful to verify the app started correctly.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Agentic Task Executor",
            "totalTasks", taskManager.getTaskCount(),
            "endpoints", Map.of(
                "submit", "POST /execute {\"prompt\": \"your task here\"}",
                "poll",   "GET /status/{taskId}"
            )
        ));
    }
}
