# Agentic Task Executor

A Spring Boot AI agent that takes a natural language task, calls Claude to plan it, and executes tools autonomously in a loop.

## How It Works

```
User Prompt
    ↓
Claude API ──→ "call web_search with query X"
    ↓
Execute Tool (web_search / code_exec / file_ops)
    ↓
Result fed back to Claude
    ↓
Claude API ──→ "call file_ops to save result"
    ↓
Execute Tool
    ↓
Claude API ──→ "DONE"
    ↓
Task Complete ✅
```

## Setup

### 1. Get API Keys

| Key | Where | Free Tier |
|-----|-------|-----------|
| Anthropic (Claude) | https://console.anthropic.com | Yes (pay-as-you-go) |
| Tavily (web search) | https://tavily.com | Yes (1000 searches/month) |

### 2. Configure Keys

Edit `src/main/resources/application.properties`:

```properties
anthropic.api.key=sk-ant-api03-YOUR_KEY_HERE
tavily.api.key=tvly-YOUR_KEY_HERE
```

### 3. Run

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Or run the JAR
java -jar target/agentic-task-executor-1.0.0.jar
```

Server starts at http://localhost:8080

---

## API Usage

### Submit a Task

```bash
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Research the top 3 AI frameworks in 2025 and save a comparison to a file"}'
```

Response:
```json
{
  "taskId": "A3F2B1C4",
  "status": "PENDING",
  "message": "Task started. Poll /status/A3F2B1C4 for updates."
}
```

### Poll Status

```bash
curl http://localhost:8080/status/A3F2B1C4
```

Response (while running):
```json
{
  "taskId": "A3F2B1C4",
  "status": "RUNNING",
  "stepsCompleted": 1,
  "steps": [
    {
      "step": 1,
      "tool": "web_search",
      "input": "top AI frameworks 2025",
      "success": true,
      "retries": 0,
      "outputPreview": "Summary: PyTorch, TensorFlow, and JAX dominate..."
    }
  ]
}
```

Response (when done):
```json
{
  "taskId": "A3F2B1C4",
  "status": "DONE",
  "stepsCompleted": 3,
  "finalSummary": "Researched top 3 AI frameworks and saved comparison.txt to workspace",
  "completedAt": "2025-01-15T14:32:10"
}
```

### Health Check

```bash
curl http://localhost:8080/health
```

---

## The 3 Tools

### 1. web_search
Searches the internet via Tavily API.
- Input: search query string
- Output: top 3 results with URLs and snippets

### 2. code_exec
Runs Python code in a sandboxed subprocess with 10s timeout.
- Input: Python code as a string
- Output: stdout/stderr (truncated at 2000 chars)
- Working directory: `/workspace` only (for security)

**Optional Docker isolation** (stronger sandbox):
Uncomment the Docker ProcessBuilder in `CodeExecTool.java`.
Requires Docker installed on the host.

### 3. file_ops
Reads and writes files in the `/workspace` directory.

Input formats:
```json
{"action": "write", "filename": "report.txt", "content": "..."}
{"action": "read", "filename": "report.txt"}
{"action": "list"}
```

---

## Phase-by-Phase Build Guide

### Phase 1 — Days 1–5: Get the loop working
- Start with just `ClaudeApiService` + `WebSearchTool`
- Hardcode a prompt, run in `main()`, print the output
- Don't worry about REST or async yet
- Goal: see Claude call web_search and get a result back

### Phase 2 — Days 6–12: Async + more tools
- Add `@Async` to all tool methods
- Add `CodeExecTool` and `FileOpsTool`
- Test each tool independently with unit tests
- Run all 3 tools in the agent loop

### Phase 3 — Days 13–17: REST API + reliability
- Add `AgentController` with `/execute` and `/status`
- Add retry logic (already in `AgentLoopService`)
- Test with curl commands
- Add logging to track the full loop

### Phase 4 — Days 18–20: Demo + polish
1. Run this exact command:
```bash
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Research the top 3 AI frameworks in 2025 and save a comparison to a file called ai_frameworks.txt"}'
```
2. Poll status every 5 seconds until DONE
3. Check `./workspace/ai_frameworks.txt` for the output
4. Record the terminal with OBS or any screen recorder

---

## Project Structure

```
agentic-task-executor/
├── pom.xml
├── workspace/                          ← agent writes files here
└── src/main/
    ├── resources/
    │   └── application.properties      ← API keys go here
    └── java/com/agent/executor/
        ├── AgenticTaskExecutorApplication.java
        ├── controller/
        │   └── AgentController.java    ← REST endpoints
        ├── service/
        │   ├── ClaudeApiService.java   ← Claude API calls
        │   ├── AgentLoopService.java   ← THE CORE LOOP
        │   └── TaskManagerService.java ← in-memory task store
        ├── tool/
        │   ├── WebSearchTool.java      ← Tavily search
        │   ├── CodeExecTool.java       ← Python subprocess
        │   └── FileOpsTool.java        ← read/write files
        ├── model/
        │   ├── TaskResult.java         ← task state + step logs
        │   └── ToolCall.java           ← Claude's tool call response
        └── config/
            └── AsyncConfig.java        ← thread pool config
```

## Scope Rules (Don't Break These)

- ✅ Exactly 3 tools: web_search, code_exec, file_ops
- ✅ Max 3 steps per task
- ✅ No database — ConcurrentHashMap only
- ✅ No UI, no auth, no multi-agent
- ❌ Don't add more tools until Phase 4 is done
- ❌ Don't add a database — it's scope creep

## Common Issues

**"API key not found"** → Check `application.properties`, no spaces around `=`

**"Python not found"** → Install Python 3: `sudo apt install python3` (Linux) or `brew install python3` (Mac)

**Tool returns timeout** → Code took >10s. Simplify the Python or increase `TIMEOUT_SECONDS` in `CodeExecTool`

**Claude returns invalid JSON** → Check system prompt in `ClaudeApiService`. The JSON format instruction is strict.
