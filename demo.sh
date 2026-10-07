#!/bin/bash
# ============================================================
# PHASE 4 DEMO SCRIPT — Agentic Task Executor
# 
# Run this after: mvn spring-boot:run
# ============================================================

BASE_URL="http://localhost:8080"
DEMO_PROMPT="Research the top 3 AI frameworks in 2025 and save a comparison to a file called ai_frameworks.txt"

echo "============================================"
echo "  Agentic Task Executor — Demo"
echo "============================================"
echo ""
echo "Task: $DEMO_PROMPT"
echo ""

# Step 1: Submit the task
echo "▶ Submitting task..."
RESPONSE=$(curl -s -X POST "$BASE_URL/execute" \
  -H "Content-Type: application/json" \
  -d "{\"prompt\": \"$DEMO_PROMPT\"}")

echo "Response: $RESPONSE"
echo ""

# Extract task ID (requires jq — install with: apt install jq)
TASK_ID=$(echo "$RESPONSE" | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)
echo "Task ID: $TASK_ID"
echo ""

# Step 2: Poll until done
echo "⏳ Polling status every 5 seconds..."
echo ""

for i in {1..30}; do
  sleep 5
  STATUS_RESPONSE=$(curl -s "$BASE_URL/status/$TASK_ID")
  STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  STEPS=$(echo "$STATUS_RESPONSE" | grep -o '"stepsCompleted":[0-9]*' | cut -d':' -f2)
  
  echo "[$i] Status: $STATUS | Steps completed: $STEPS"
  
  if [ "$STATUS" = "DONE" ] || [ "$STATUS" = "FAILED" ]; then
    echo ""
    echo "============================================"
    echo "  Final Status: $STATUS"
    echo "============================================"
    echo "$STATUS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$STATUS_RESPONSE"
    break
  fi
done

# Step 3: Check workspace
echo ""
echo "📁 Workspace contents:"
ls -la ./workspace/ 2>/dev/null || echo "  (workspace not found — check agent.workspace-dir in properties)"

echo ""
echo "📄 File output (if created):"
cat ./workspace/ai_frameworks.txt 2>/dev/null || echo "  (file not created yet or task still running)"
