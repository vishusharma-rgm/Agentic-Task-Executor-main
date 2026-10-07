package com.agent.executor.model;

/**
 * Represents a single tool call extracted from Claude's response.
 * Claude replies with: { "tool": "web_search", "input": "query string" }
 */
public class ToolCall {

    private String tool;    // "web_search" | "code_exec" | "file_ops"
    private String input;   // The argument to pass to the tool
    private String thought; // Claude's reasoning before calling the tool (optional)

    public ToolCall() {}

    public ToolCall(String tool, String input, String thought) {
        this.tool = tool;
        this.input = input;
        this.thought = thought;
    }

    public String getTool()    { return tool; }
    public String getInput()   { return input; }
    public String getThought() { return thought; }

    public void setTool(String tool)       { this.tool = tool; }
    public void setInput(String input)     { this.input = input; }
    public void setThought(String thought) { this.thought = thought; }

    @Override
    public String toString() {
        return "ToolCall{tool='" + tool + "', input='" + input + "'}";
    }
}
