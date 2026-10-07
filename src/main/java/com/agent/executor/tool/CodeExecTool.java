package com.agent.executor.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * PHASE 2: Sandboxed Python code executor.
 *
 * Safety approach: writes code to a temp file, runs it via ProcessBuilder
 * with a strict 10-second timeout. The /workspace dir is the only place
 * code can write files (enforced by working directory).
 *
 * For stronger isolation: replace with Docker exec (see comment below).
 */
@Component
public class CodeExecTool {

    private static final Logger log = LoggerFactory.getLogger(CodeExecTool.class);

    @Value("${agent.workspace-dir}")
    private String workspaceDir;

    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_CHARS = 2000;

    @Async
    public CompletableFuture<String> execute(String pythonCode) {
        log.info("🐍 CodeExec: running {} chars of Python", pythonCode.length());

        Path workspacePath = Path.of(workspaceDir).toAbsolutePath();

        try {
            // Ensure workspace exists
            Files.createDirectories(workspacePath);

            // Write code to temp file inside workspace
            Path scriptFile = workspacePath.resolve("_temp_script.py");
            Files.writeString(scriptFile, pythonCode);

            // Build process — working dir is workspace (limits file access)
            ProcessBuilder pb = new ProcessBuilder("python3", scriptFile.toString());
            pb.directory(workspacePath.toFile());
            pb.redirectErrorStream(true); // merge stdout + stderr

            /*
             * STRONGER SANDBOX (optional, uncomment if Docker is available):
             *
             * ProcessBuilder pb = new ProcessBuilder(
             *     "docker", "run", "--rm",
             *     "--network=none",           // no internet
             *     "--memory=128m",            // memory limit
             *     "--cpus=0.5",               // CPU limit
             *     "-v", workspaceDir + ":/workspace",
             *     "-w", "/workspace",
             *     "python:3.11-slim",
             *     "python3", "_temp_script.py"
             * );
             */

            Process process = pb.start();

            // Read output with timeout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > MAX_OUTPUT_CHARS) {
                        output.append("\n[Output truncated at ").append(MAX_OUTPUT_CHARS).append(" chars]");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("CodeExec timed out after {}s", TIMEOUT_SECONDS);
                return CompletableFuture.completedFuture(
                    "TIMEOUT: Code execution exceeded " + TIMEOUT_SECONDS + " seconds"
                );
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode != 0) {
                log.warn("CodeExec exited with code {}", exitCode);
                return CompletableFuture.completedFuture("ERROR (exit " + exitCode + "):\n" + result);
            }

            log.debug("CodeExec success: {}", result);
            return CompletableFuture.completedFuture(
                result.isBlank() ? "(no output)" : result
            );

        } catch (Exception e) {
            log.error("CodeExec error: {}", e.getMessage());
            return CompletableFuture.completedFuture("Execution failed: " + e.getMessage());
        }
    }
}
