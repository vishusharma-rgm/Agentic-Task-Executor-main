package com.agent.executor.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the full lifecycle of one agent task.
 * Phase 3: stored in ConcurrentHashMap for REST polling.
 */
public class TaskResult {

    public enum Status {
        PENDING,    // Task received, not started
        RUNNING,    // Agent loop is executing
        DONE,       // All steps completed successfully
        FAILED      // Unrecoverable failure
    }

    private final String taskId;
    private final String originalPrompt;
    private Status status;
    private final List<StepLog> steps = new ArrayList<>();
    private String finalSummary;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public TaskResult(String taskId, String originalPrompt) {
        this.taskId = taskId;
        this.originalPrompt = originalPrompt;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // --- Mutators ---

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.DONE || status == Status.FAILED) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public void addStep(StepLog step) {
        this.steps.add(step);
    }

    public void setFinalSummary(String summary) {
        this.finalSummary = summary;
    }

    // --- Getters ---

    public String getTaskId()          { return taskId; }
    public String getOriginalPrompt()  { return originalPrompt; }
    public Status getStatus()          { return status; }
    public List<StepLog> getSteps()    { return steps; }
    public String getFinalSummary()    { return finalSummary; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getCompletedAt(){ return completedAt; }

    // ---- Inner class for step logs ----

    public static class StepLog {
        private final int stepNumber;
        private final String toolName;
        private final String toolInput;
        private String toolOutput;
        private boolean success;
        private int retryCount;

        public StepLog(int stepNumber, String toolName, String toolInput) {
            this.stepNumber = stepNumber;
            this.toolName = toolName;
            this.toolInput = toolInput;
        }

        public void complete(String output, boolean success, int retries) {
            this.toolOutput = output;
            this.success = success;
            this.retryCount = retries;
        }

        public int getStepNumber()  { return stepNumber; }
        public String getToolName() { return toolName; }
        public String getToolInput(){ return toolInput; }
        public String getToolOutput(){ return toolOutput; }
        public boolean isSuccess()  { return success; }
        public int getRetryCount()  { return retryCount; }
    }
}
