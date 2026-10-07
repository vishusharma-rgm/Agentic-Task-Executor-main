package com.agent.executor.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/**
 * PHASE 2: File read/write tool.
 *
 * Input JSON format:
 *   {"action":"write","filename":"report.txt","content":"..."}
 *   {"action":"read","filename":"report.txt"}
 *   {"action":"list"}
 *
 * Security: all paths are resolved inside /workspace only.
 * Path traversal (../../etc) is blocked by resolve + normalize check.
 */
@Component
public class FileOpsTool {

    private static final Logger log = LoggerFactory.getLogger(FileOpsTool.class);

    @Value("${agent.workspace-dir}")
    private String workspaceDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final long MAX_FILE_SIZE_BYTES = 100_000; // 100 KB max read

    @Async
    public CompletableFuture<String> execute(String inputJson) {
        log.info("📁 FileOps: {}", inputJson);

        try {
            JsonNode input = objectMapper.readTree(inputJson);
            String action = input.path("action").asText("read").toLowerCase();

            return switch (action) {
                case "write" -> write(
                    input.path("filename").asText(),
                    input.path("content").asText("")
                );
                case "read" -> read(input.path("filename").asText());
                case "list" -> list();
                default -> CompletableFuture.completedFuture(
                    "Unknown action: " + action + ". Use: read, write, list"
                );
            };

        } catch (Exception e) {
            log.error("FileOps parse error: {}", e.getMessage());
            return CompletableFuture.completedFuture("FileOps error: " + e.getMessage());
        }
    }

    private CompletableFuture<String> write(String filename, String content) {
        try {
            Path target = resolveSecure(filename);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            log.info("FileOps wrote {} bytes to {}", content.length(), filename);
            return CompletableFuture.completedFuture(
                "Successfully wrote " + content.length() + " chars to " + filename
            );
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Write failed: " + e.getMessage());
        }
    }

    private CompletableFuture<String> read(String filename) {
        try {
            Path target = resolveSecure(filename);
            if (!Files.exists(target)) {
                return CompletableFuture.completedFuture("File not found: " + filename);
            }
            long size = Files.size(target);
            if (size > MAX_FILE_SIZE_BYTES) {
                return CompletableFuture.completedFuture(
                    "File too large to read (" + size + " bytes). Max: " + MAX_FILE_SIZE_BYTES
                );
            }
            String content = Files.readString(target);
            log.info("FileOps read {} bytes from {}", content.length(), filename);
            return CompletableFuture.completedFuture(content);
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Read failed: " + e.getMessage());
        }
    }

    private CompletableFuture<String> list() {
        try {
            Path workspace = Path.of(workspaceDir).toAbsolutePath();
            Files.createDirectories(workspace);

            StringBuilder sb = new StringBuilder("Files in workspace:\n");
            try (var stream = Files.list(workspace)) {
                stream.forEach(p -> sb.append("  - ").append(p.getFileName()).append("\n"));
            }
            return CompletableFuture.completedFuture(sb.toString());
        } catch (Exception e) {
            return CompletableFuture.completedFuture("List failed: " + e.getMessage());
        }
    }

    /**
     * Resolves a filename safely within the workspace directory.
     * Prevents path traversal like "../../etc/passwd".
     */
    private Path resolveSecure(String filename) throws SecurityException {
        Path workspace = Path.of(workspaceDir).toAbsolutePath().normalize();
        Path resolved = workspace.resolve(filename).normalize();

        if (!resolved.startsWith(workspace)) {
            throw new SecurityException(
                "Path traversal attempt blocked: " + filename
            );
        }
        return resolved;
    }
}
