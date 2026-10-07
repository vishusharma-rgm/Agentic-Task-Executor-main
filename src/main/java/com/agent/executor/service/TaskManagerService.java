package com.agent.executor.service;

import com.agent.executor.model.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHASE 3: In-memory task store.
 *
 * No database needed — ConcurrentHashMap is thread-safe and perfect
 * for a 20-day project. Tasks are gone on restart, which is fine.
 *
 * Future: replace with Redis or JPA if persistence needed.
 */
@Service
public class TaskManagerService {

    private static final Logger log = LoggerFactory.getLogger(TaskManagerService.class);

    // Thread-safe map: taskId → TaskResult
    private final ConcurrentHashMap<String, TaskResult> tasks = new ConcurrentHashMap<>();

    private final AgentLoopService agentLoop;

    public TaskManagerService(AgentLoopService agentLoop) {
        this.agentLoop = agentLoop;
    }

    /**
     * Creates a new task, starts the agent loop async, returns task ID immediately.
     */
    public TaskResult submitTask(String userPrompt) {
        String taskId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TaskResult task = new TaskResult(taskId, userPrompt);
        tasks.put(taskId, task);

        log.info("📋 Task {} submitted: \"{}\"", taskId, userPrompt);

        // Fire and forget — agent loop runs async, updates TaskResult in background
        agentLoop.runAgentLoop(userPrompt, task);

        return task;
    }

    /**
     * Returns task status for REST polling.
     */
    public TaskResult getTask(String taskId) {
        return tasks.get(taskId.toUpperCase());
    }

    /**
     * Returns count of all tasks (for health check).
     */
    public int getTaskCount() {
        return tasks.size();
    }
}
