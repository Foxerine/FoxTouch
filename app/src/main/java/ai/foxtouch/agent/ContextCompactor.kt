package ai.foxtouch.agent

/**
 * Handles context compaction when the conversation history grows too large.
 *
 * Adapted from Claude Code's compaction system for the FoxTouch phone agent.
 */
object ContextCompactor {

    /**
     * Estimate the token count for a list of messages.
     * Uses a rough heuristic: ~3 characters per token (compromise for mixed CJK/English).
     * Images count as ~1000 tokens each.
     */
    fun estimateTokens(messages: List<ChatMessage>): Int {
        var charCount = 0
        var imageCount = 0
        for (msg in messages) {
            charCount += msg.content?.length ?: 0
            msg.toolCalls?.forEach { call ->
                charCount += call.name.length + call.arguments.toString().length
            }
            if (msg.imageBase64 != null) imageCount++
        }
        return charCount / 3 + imageCount * 1000
    }

    /**
     * Format the conversation history into a readable text for the summarizer.
     */
    fun formatConversationForSummary(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            when (msg.role) {
                "system" -> {
                    if (msg.content?.startsWith("[TASK STATUS]") == true) {
                        sb.appendLine("[TASK STATUS]")
                        sb.appendLine(msg.content)
                    } else {
                        sb.appendLine("[SYSTEM PROMPT — omitted for brevity]")
                    }
                }
                "user" -> {
                    if (msg.imageBase64 != null) {
                        sb.appendLine("USER: ${msg.content ?: "(screenshot attached)"} [IMAGE]")
                    } else {
                        sb.appendLine("USER: ${msg.content ?: ""}")
                    }
                }
                "assistant" -> {
                    if (!msg.content.isNullOrBlank()) {
                        sb.appendLine("ASSISTANT: ${msg.content}")
                    }
                    msg.toolCalls?.forEach { call ->
                        val argsPreview = call.arguments.toString().take(200)
                        sb.appendLine("TOOL_CALL: ${call.name}($argsPreview)")
                    }
                }
                "tool" -> {
                    val resultPreview = msg.content?.take(300) ?: ""
                    sb.appendLine("TOOL_RESULT [${msg.toolName ?: "?"}]: $resultPreview")
                }
            }
        }
        return sb.toString()
    }

    /**
     * Default compact prompt. Used as fallback when no custom prompt is configured.
     * Adapted from Claude Code's conversation summarization prompt for the FoxTouch phone agent context.
     */
    val DEFAULT_COMPACT_PROMPT: String = """
Your task is to create a detailed summary of the conversation so far, paying close attention to the user's explicit requests and the agent's previous actions on the phone.
This summary should be thorough in capturing UI interactions, app states, navigation paths, and decisions that would be essential for continuing the task without losing context.

Before providing your final summary, wrap your analysis in <analysis> tags to organize your thoughts. In your analysis:

1. Chronologically analyze each message. For each section identify:
   - The user's explicit requests and intents
   - Apps opened, screens visited, UI elements interacted with
   - Key decisions and outcomes
   - Specific details like:
     - Package names and app names
     - Element IDs used for clicks
     - Coordinates used for taps
     - Text typed into fields
     - Navigation paths taken
   - Errors encountered and how they were resolved
   - User feedback (especially corrections or clarifications)
2. Double-check for completeness — every user message and tool action should be accounted for.

Your summary (after the analysis) should include the following sections:

1. **Primary Request and Intent**: Capture ALL of the user's explicit requests in detail.

2. **Actions Taken**: List all phone interactions performed — apps launched, screens navigated, elements clicked, text typed, etc. Include element IDs, coordinates, and package names where relevant.

3. **App States Observed**: Describe what was seen on screen at key points. Include relevant UI element details.

4. **Errors and Fixes**: List all errors encountered and how they were resolved. Include specific user feedback or corrections.

5. **All User Messages**: List ALL user messages verbatim (not tool results). These are critical for understanding changing intent.

6. **Pending Tasks**: Outline any tasks explicitly asked for but not yet completed. Include task IDs if tracked.

7. **Current Work**: Describe precisely what was being worked on immediately before this summary — which app, which screen, what action was in progress. Include element IDs and coordinates where applicable.

8. **Next Step**: The single next action to take, directly aligned with the most recent work. Include exact details (which element to click, what text to type, etc.). If the previous task was completed, only list next steps if explicitly requested by the user.

IMPORTANT:
- Do NOT include the full system prompt or tool definitions in your summary — they will be re-injected automatically.
- Focus on ACTIONABLE details the agent needs to continue work.
- Keep the summary concise but complete — aim for the minimum needed to resume without loss.
- Write the summary in the same language the user was communicating in.
""".trimIndent()

    /**
     * Build the continuation message that wraps the summary for the new context.
     */
    fun buildContinuationMessage(summary: String): String = """
This session is being continued from a previous conversation that ran out of context. The summary below covers the earlier portion of the conversation.

$summary

Continue the conversation from where it left off without asking the user any further questions. Resume directly — do not acknowledge the summary, do not recap what was happening. Pick up the last task as if the break never happened.
""".trimIndent()
}
