package ai.openclaw.app.host

import android.os.Build
import ai.openclaw.app.BuildConfig
import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.auth.OpenAICodexCredential
import ai.openclaw.app.auth.OpenAICodexOAuthApi
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.network.buildOpenAIHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class OpenAICodexAssistantReply(
  val text: String,
  val responseId: String?,
  val credential: OpenAICodexCredential,
)

interface LocalHostResponsesClient {
  suspend fun streamReply(
    role: String,
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
    onTextDelta: suspend (fullText: String) -> Unit,
    onToolEvent: suspend (LocalHostToolCallEvent) -> Unit,
  ): OpenAICodexAssistantReply
}

class OpenAICodexResponsesClient(
  private val prefs: SecurePrefs,
  private val json: Json,
  private val client: OkHttpClient = buildOpenAIHttpClient(),
  private val responsesUrl: String = defaultResponsesUrl,
  private val instructions: String = defaultInstructions,
  private val toolBridge: LocalHostToolBridge? = null,
) : LocalHostResponsesClient {
  companion object {
    private const val defaultResponsesUrl = "https://chatgpt.com/backend-api/codex/responses"
    private const val defaultModelId = "gpt-5.4"
    private const val defaultOriginator = "codex_cli_rs"
    private const val maxToolTurns = 8
    private val defaultInstructions =
      """
      You are Codex, based on GPT-5. You are running as a coding agent in the Codex CLI on a user's computer.

      ## General

      - When searching for text or files, prefer using `rg` or `rg --files` respectively because `rg` is much faster than alternatives like `grep`. (If the `rg` command is not found, then use alternatives.)

      ## Editing constraints

      - Default to ASCII when editing or creating files. Only introduce non-ASCII or other Unicode characters when there is a clear justification and the file already uses them.
      - Add succinct code comments that explain what is going on if code is not self-explanatory. You should not add comments like "Assigns the value to the variable", but a brief comment might be useful ahead of a complex code block that the user would otherwise have to spend time parsing out. Usage of these comments should be rare.
      - Try to use apply_patch for single file edits, but it is fine to explore other options to make the edit if it does not work well. Do not use apply_patch for changes that are auto-generated (i.e. generating package.json or running a lint or format command like gofmt) or when scripting is more efficient (such as search and replacing a string across a codebase).
      - You may be in a dirty git worktree.
          * NEVER revert existing changes you did not make unless explicitly requested, since these changes were made by the user.
          * If asked to make a commit or code edits and there are unrelated changes to your work or changes that you didn't make in those files, don't revert those changes.
          * If the changes are in files you've touched recently, you should read carefully and understand how you can work with the changes rather than reverting them.
          * If the changes are in unrelated files, just ignore them and don't revert them.
      - Do not amend a commit unless explicitly requested to do so.
      - While you are working, you might notice unexpected changes that you didn't make. If this happens, STOP IMMEDIATELY and ask the user how they would like to proceed.
      - **NEVER** use destructive commands like `git reset --hard` or `git checkout --` unless specifically requested or approved by the user.

      ## Plan tool

      When using the planning tool:
      - Skip using the planning tool for straightforward tasks (roughly the easiest 25%).
      - Do not make single-step plans.
      - When you made a plan, update it after having performed one of the sub-tasks that you shared on the plan.

      ## Special user requests

      - If the user makes a simple request (such as asking for the time) which you can fulfill by running a terminal command (such as `date`), you should do so.
      - If the user asks for a "review", default to a code review mindset: prioritise identifying bugs, risks, behavioural regressions, and missing tests. Findings must be the primary focus of the response - keep summaries or overviews brief and only after enumerating the issues. Present findings first (ordered by severity with file/line references), follow with open questions or assumptions, and offer a change-summary only as a secondary detail. If no findings are discovered, state that explicitly and mention any residual risks or testing gaps.

      ## Presenting your work and final message

      You are producing plain text that will later be styled by the CLI. Follow these rules exactly. Formatting should make results easy to scan, but not feel mechanical. Use judgment to decide how much structure adds value.

      - Default: be very concise; friendly coding teammate tone.
      - Ask only when needed; suggest ideas; mirror the user's style.
      - For substantial work, summarize clearly; follow final-answer formatting.
      - Skip heavy formatting for simple confirmations.
      - Don't dump large files you've written; reference paths only.
      - No "save/copy this file" - User is on the same machine.
      - Offer logical next steps (tests, commits, build) briefly; add verify steps if you couldn't do something.
      - For code changes:
        * Lead with a quick explanation of the change, and then give more details on the context covering where and why a change was made. Do not start this explanation with "summary", just jump right in.
        * If there are natural next steps the user may want to take, suggest them at the end of your response. Do not make suggestions if there are no natural next steps.
        * When suggesting multiple options, use numeric lists for the suggestions so the user can quickly respond with a single number.
      - The user does not command execution outputs. When asked to show the output of a command (e.g. `git show`), relay the important details in your answer or summarize the key lines so the user understands the result.

      ### Final answer structure and style guidelines

      - Plain text; CLI handles styling. Use structure only when it helps scanability.
      - Headers: optional; short Title Case (1-3 words) wrapped in **…**; no blank line before the first bullet; add only if they truly help.
      - Bullets: use - ; merge related points; keep to one line when possible; 4-6 per list ordered by importance; keep phrasing consistent.
      - Monospace: backticks for commands/paths/env vars/code ids and inline examples; use for literal keyword bullets; never combine with **.
      - Code samples or multi-line snippets should be wrapped in fenced code blocks; include an info string as often as possible.
      - Structure: group related bullets; order sections general -> specific -> supporting; for subsections, start with a bolded keyword bullet, then items; match complexity to the task.
      - Tone: collaborative, concise, factual; present tense, active voice; self-contained; no "above/below"; parallel wording.
      - Don'ts: no nested bullets/hierarchies; no ANSI codes; don't cram unrelated keywords; keep keyword lists short-wrap/reformat if long; avoid naming formatting styles in answers.
      - Adaptation: code explanations -> precise, structured with code refs; simple tasks -> lead with outcome; big changes -> logical walkthrough + rationale + next actions; casual one-offs -> plain sentences, no headers/bullets.
      - File References: When referencing files in your response, make sure to include the relevant start line and always follow the below rules:
        * Use inline code to make file paths clickable.
        * Each reference should have a stand alone path. Even if it's the same file.
        * Accepted: absolute, workspace-relative, a/ or b/ diff prefixes, or bare filename/suffix.
        * Line/column (1-based, optional): :line[:column] or #Lline[Ccolumn] (column defaults to 1).
        * Do not use URIs like file://, vscode://, or https://.
        * Do not provide range of lines
        * Examples: src/app.ts, src/app.ts:42, b/server/index.js#L10, C:\repo\project\main.rs:12:5
      """.trimIndent()
  }

  private val oauthApi = OpenAICodexOAuthApi(json = json, client = client)
  private val codexUserAgent by lazy {
    val release = Build.VERSION.RELEASE?.trim().orEmpty().ifEmpty { "unknown" }
    val arch = Build.SUPPORTED_ABIS?.firstOrNull()?.trim().orEmpty().ifEmpty { "unknown" }
    "$defaultOriginator/${BuildConfig.VERSION_NAME} (Android $release; $arch) OpenClaw/${BuildConfig.VERSION_NAME}"
  }

  override suspend fun streamReply(
    role: String,
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
    onTextDelta: suspend (fullText: String) -> Unit,
    onToolEvent: suspend (LocalHostToolCallEvent) -> Unit,
  ): OpenAICodexAssistantReply =
    withContext(Dispatchers.IO) {
      var credential = prefs.loadOpenAICodexCredential()
        ?: throw IllegalStateException("OpenAI Codex login required")
      if (credential.expires <= System.currentTimeMillis() + 30_000) {
        credential = oauthApi.refreshCredential(credential)
        prefs.saveOpenAICodexCredential(credential)
      }

      val accountId = oauthApi.extractAccountId(credential.access) ?: credential.accountId
      if (accountId.isBlank()) {
        throw IllegalStateException("OpenAI Codex credential is missing accountId")
      }

      val tools = toolBridge?.toolsForRole(role).orEmpty()
      val inputItems = buildConversationInput(messages).toMutableList()
      var responseId: String? = null
      var finalText: String? = null

      var turn = 0
      while (turn < maxToolTurns) {
        val result =
          requestResponseTurn(
            sessionId = sessionId,
            accountId = accountId,
            credential = credential,
            thinkingLevel = thinkingLevel,
            inputItems = inputItems,
            tools = tools,
            onTextDelta = onTextDelta,
          )
        responseId = result.responseId ?: responseId
        if (result.toolCalls.isEmpty()) {
          finalText = result.text?.trim()
          break
        }

        if (turn == maxToolTurns - 1) {
          throw IllegalStateException("OpenAI Codex exceeded the local-host tool turn limit")
        }

        result.toolCalls.forEach { toolCall ->
          onToolEvent(
            LocalHostToolCallEvent(
              toolCallId = toolCall.callId,
              name = toolCall.name,
              args = parseJsonObjectOrNull(toolCall.argumentsJson),
              phase = "start",
            ),
          )
          val executionResult =
            try {
              toolBridge?.executeToolCall(
                role = role,
                name = toolCall.name,
                argumentsJson = toolCall.argumentsJson,
              ) ?: LocalHostToolExecutionResult(
                outputText = buildToolErrorOutput("UNKNOWN_TOOL", "UNKNOWN_TOOL: ${toolCall.name}"),
              )
            } catch (err: Throwable) {
              LocalHostToolExecutionResult(
                outputText = buildToolErrorOutput("TOOL_FAILED", err.message ?: "tool execution failed"),
              )
            }
          onToolEvent(
            LocalHostToolCallEvent(
              toolCallId = toolCall.callId,
              name = toolCall.name,
              args = null,
              phase = "result",
            ),
          )
          inputItems += buildFunctionCallInput(toolCall)
          inputItems += buildFunctionCallOutputInput(toolCall.callId, executionResult.outputText)
          buildToolImageFollowUpInput(executionResult.imageInputs)?.let { inputItems += it }
        }
        turn += 1
      }

      val resolvedText = finalText?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("OpenAI Codex returned no final assistant text")
      val persistedCredential =
        if (credential.accountId == accountId && credential.email == oauthApi.extractEmail(credential.access)) {
          credential
        } else {
          credential.copy(
            accountId = accountId,
            email = oauthApi.extractEmail(credential.access) ?: credential.email,
          )
        }

      OpenAICodexAssistantReply(
        text = resolvedText,
        responseId = responseId,
        credential = persistedCredential,
      )
    }

  private suspend fun requestResponseTurn(
    sessionId: String,
    accountId: String,
    credential: OpenAICodexCredential,
    thinkingLevel: String,
    inputItems: List<JsonObject>,
    tools: List<LocalHostFunctionTool>,
    onTextDelta: suspend (fullText: String) -> Unit,
  ): CodexResponseTurn {
    val requestBody =
      buildRequestBody(
        sessionId = sessionId,
        inputItems = inputItems,
        thinkingLevel = thinkingLevel,
        tools = tools,
      ).toString()
        .toByteArray(Charsets.UTF_8)
        .toRequestBody("application/json".toMediaType())

    val request =
      Request.Builder()
        .url(responsesUrl)
        .post(requestBody)
        .header("Authorization", "Bearer ${credential.access}")
        .header("chatgpt-account-id", accountId)
        .header("originator", defaultOriginator)
        .header("User-Agent", codexUserAgent)
        .header("OpenAI-Beta", "responses=experimental")
        .header("accept", "text/event-stream")
        .header("x-client-request-id", sessionId)
        .header("session_id", sessionId)
        .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        val body = response.body?.string().orEmpty()
        throw IllegalStateException(
          parseCodexError(
            statusCode = response.code,
            body = body,
            responseMessage = response.message,
            requestId = response.header("x-request-id") ?: response.header("request-id"),
            contentType = response.header("content-type"),
          ),
        )
      }

      val reader = response.body?.charStream()?.buffered()
        ?: throw IllegalStateException("OpenAI Codex returned no response body")
      val eventLines = mutableListOf<String>()
      val fullText = StringBuilder()
      val toolCalls = LinkedHashMap<String, CodexFunctionCall>()
      var responseId: String? = null
      var finalText: String? = null

      fun consumeEvent(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return
        val root = json.parseToJsonElement(trimmed) as? JsonObject ?: return
        when (root["type"].asStringOrNull()) {
          "response.created" -> {
            responseId = root["response"].asObjectOrNull()?.get("id").asStringOrNull()
          }
          "response.output_text.delta" -> {
            val delta = root["delta"].asStringOrNull().orEmpty()
            if (delta.isNotEmpty()) {
              fullText.append(delta)
            }
          }
          "response.output_item.done" -> {
            val item = root["item"].asObjectOrNull() ?: return
            when (item["type"].asStringOrNull()) {
              "message" -> {
                val content = item["content"] as? JsonArray ?: return
                val text = extractMessageText(content)
                if (text.isNotBlank()) {
                  finalText = text
                }
              }
              "function_call" -> {
                extractFunctionCall(item)?.let { toolCalls[it.callId] = it }
              }
            }
          }
          "response.completed", "response.done", "response.incomplete" -> {
            responseId = root["response"].asObjectOrNull()?.get("id").asStringOrNull() ?: responseId
            val responseBody = root["response"].asObjectOrNull() ?: return
            val output = responseBody["output"] as? JsonArray ?: return
            val text = extractResponseOutputText(output)
            if (text.isNotBlank()) {
              finalText = text
            }
            extractFunctionCalls(output).forEach { toolCall ->
              toolCalls[toolCall.callId] = toolCall
            }
          }
          "response.failed" -> {
            val error = root["response"].asObjectOrNull()?.get("error").asObjectOrNull()
            val message = error?.get("message").asStringOrNull()
            throw IllegalStateException(
              formatCodexRequestFailure(
                message = message,
                detailSegments =
                  buildList {
                    error?.get("details").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                    error?.get("type").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("errorType=$it") }
                    error?.get("code").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("errorCode=$it") }
                  },
              ),
            )
          }
          "error" -> {
            val message = root["message"].asStringOrNull()
            throw IllegalStateException(
              formatCodexRequestFailure(
                message = message,
                detailSegments =
                  buildList {
                    root["type"].asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("type=$it") }
                    root["code"].asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("errorCode=$it") }
                  },
              ),
            )
          }
        }
      }

      while (true) {
        val line = reader.readLine() ?: break
        if (line.isBlank()) {
          if (eventLines.isNotEmpty()) {
            val previous = fullText.toString()
            consumeEvent(eventLines.joinToString(separator = "\n"))
            if (fullText.toString() != previous) {
              onTextDelta(fullText.toString())
            }
            eventLines.clear()
          }
          continue
        }
        if (line.startsWith("data:")) {
          eventLines += line.removePrefix("data:").trim()
        }
      }
      if (eventLines.isNotEmpty()) {
        val previous = fullText.toString()
        consumeEvent(eventLines.joinToString(separator = "\n"))
        if (fullText.toString() != previous) {
          onTextDelta(fullText.toString())
        }
      }

      val resolvedText = finalText?.takeIf { it.isNotBlank() } ?: fullText.toString().trim()
      return CodexResponseTurn(
        responseId = responseId,
        text = resolvedText.ifEmpty { null },
        toolCalls = toolCalls.values.toList(),
      )
    }
  }

  private fun buildRequestBody(
    sessionId: String,
    inputItems: List<JsonObject>,
    thinkingLevel: String,
    tools: List<LocalHostFunctionTool>,
  ): JsonObject {
    return buildJsonObject {
      put("model", JsonPrimitive(defaultModelId))
      put("store", JsonPrimitive(false))
      put("stream", JsonPrimitive(true))
      put("instructions", JsonPrimitive(instructions))
      put(
        "input",
        buildJsonArray {
          inputItems.forEach { add(it) }
        },
      )
      put(
        "tools",
        buildJsonArray {
          tools.forEach { add(it.asJsonObject()) }
        },
      )
      put("tool_choice", JsonPrimitive("auto"))
      put("parallel_tool_calls", JsonPrimitive(false))
      val reasoning = normalizeThinkingEffort(thinkingLevel)
      val include = buildJsonArray {
        if (reasoning != null) {
          add(JsonPrimitive("reasoning.encrypted_content"))
        }
      }
      put("include", include)
      put("prompt_cache_key", JsonPrimitive(sessionId))
      if (reasoning != null) {
        put(
          "reasoning",
          buildJsonObject {
            put("effort", JsonPrimitive(reasoning))
            put("summary", JsonPrimitive("auto"))
          },
        )
      }
    }
  }

  private fun buildConversationInput(messages: List<LocalHostMessage>): List<JsonObject> {
    return messages.mapIndexedNotNull { index, message ->
      when (message.role) {
        "user" ->
          buildJsonObject {
            put("type", JsonPrimitive("message"))
            put("role", JsonPrimitive("user"))
            put("content", buildUserContent(message.content))
          }
        "assistant" ->
          buildJsonObject {
            put("type", JsonPrimitive("message"))
            put("role", JsonPrimitive("assistant"))
            put(
              "content",
              buildJsonArray {
                val text =
                  message.content
                    .filter { it.type == "text" }
                    .joinToString(separator = "\n") { it.text.orEmpty() }
                    .trim()
                if (text.isNotEmpty()) {
                  add(
                    buildJsonObject {
                      put("type", JsonPrimitive("output_text"))
                      put("text", JsonPrimitive(text))
                      put("annotations", JsonArray(emptyList()))
                    },
                  )
                }
              },
            )
            put("status", JsonPrimitive("completed"))
            put("id", JsonPrimitive("msg_$index"))
          }
        else -> null
      }
    }
  }

  private fun buildFunctionCallInput(toolCall: CodexFunctionCall): JsonObject {
    return buildJsonObject {
      put("type", JsonPrimitive("function_call"))
      // Replaying function_call.id can be rejected when OpenAI hides the paired reasoning item.
      put("call_id", JsonPrimitive(toolCall.callId))
      put("name", JsonPrimitive(toolCall.name))
      put("arguments", JsonPrimitive(toolCall.argumentsJson))
    }
  }

  private fun buildFunctionCallOutputInput(
    callId: String,
    output: String,
  ): JsonObject =
    buildJsonObject {
      put("type", JsonPrimitive("function_call_output"))
      put("call_id", JsonPrimitive(callId))
      put("output", JsonPrimitive(output))
    }

  private fun buildToolImageFollowUpInput(
    imageInputs: List<LocalHostToolImageInput>,
  ): JsonObject? {
    if (imageInputs.isEmpty()) return null
    return buildJsonObject {
      put("type", JsonPrimitive("message"))
      put("role", JsonPrimitive("user"))
      put(
        "content",
        buildJsonArray {
          add(
            buildJsonObject {
              put("type", JsonPrimitive("input_text"))
              put(
                "text",
                JsonPrimitive(
                  if (imageInputs.size == 1) {
                    "Attached image from the phone tool result."
                  } else {
                    "Attached images from the phone tool result."
                  },
                ),
              )
            },
          )
          imageInputs.forEach { image ->
            add(
              buildJsonObject {
                put("type", JsonPrimitive("input_image"))
                put("detail", JsonPrimitive("auto"))
                put("image_url", JsonPrimitive("data:${image.mimeType};base64,${image.base64}"))
              },
            )
          }
        },
      )
    }
  }

  private fun buildToolErrorOutput(
    code: String,
    message: String,
  ): String =
    buildJsonObject {
      put("ok", JsonPrimitive(false))
      put(
        "error",
        buildJsonObject {
          put("code", JsonPrimitive(code))
          put("message", JsonPrimitive(message))
        },
      )
    }.toString()

  private fun extractFunctionCalls(output: JsonArray): List<CodexFunctionCall> {
    return output.mapNotNull { extractFunctionCall(it as? JsonObject ?: return@mapNotNull null) }
  }

  private fun extractFunctionCall(item: JsonObject): CodexFunctionCall? {
    if (item["type"].asStringOrNull() != "function_call") {
      return null
    }
    val callId = item["call_id"].asStringOrNull()?.trim().orEmpty()
    val name = item["name"].asStringOrNull()?.trim().orEmpty()
    if (callId.isEmpty() || name.isEmpty()) {
      return null
    }
    return CodexFunctionCall(
      callId = callId,
      itemId = item["id"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
      name = name,
      argumentsJson = item["arguments"].asStringOrNull()?.trim().orEmpty().ifEmpty { "{}" },
    )
  }

  private fun parseJsonObjectOrNull(raw: String): JsonObject? {
    return try {
      json.parseToJsonElement(raw).asObjectOrNull()
    } catch (_: Throwable) {
      null
    }
  }

  private data class CodexFunctionCall(
    val callId: String,
    val itemId: String?,
    val name: String,
    val argumentsJson: String,
  )

  private data class CodexResponseTurn(
    val responseId: String?,
    val text: String?,
    val toolCalls: List<CodexFunctionCall>,
  )

  private fun buildUserContent(content: List<ChatMessageContent>): JsonArray {
    return buildJsonArray {
      content.forEach { item ->
        if (item.type == "text") {
          val text = item.text?.trim().orEmpty()
          if (text.isNotEmpty()) {
            add(
              buildJsonObject {
                put("type", JsonPrimitive("input_text"))
                put("text", JsonPrimitive(text))
              },
            )
          }
          return@forEach
        }
        val mimeType = item.mimeType?.trim().orEmpty()
        val base64 = item.base64?.trim().orEmpty()
        if (mimeType.isNotEmpty() && base64.isNotEmpty()) {
          add(
            buildJsonObject {
              put("type", JsonPrimitive("input_image"))
              put("detail", JsonPrimitive("auto"))
              put("image_url", JsonPrimitive("data:$mimeType;base64,$base64"))
            },
          )
        }
      }
    }
  }

  private fun normalizeThinkingEffort(raw: String): String? {
    return when (raw.trim().lowercase()) {
      "low" -> "low"
      "medium" -> "medium"
      "high" -> "high"
      else -> null
    }
  }

  private fun extractResponseOutputText(output: JsonArray): String {
    return output.mapNotNull { item ->
      val message = item as? JsonObject ?: return@mapNotNull null
      if (message["type"].asStringOrNull() != "message") return@mapNotNull null
      extractMessageText(message["content"] as? JsonArray ?: return@mapNotNull null)
    }.joinToString(separator = "")
  }

  private fun extractMessageText(content: JsonArray): String {
    return content.mapNotNull { part ->
      val obj = part as? JsonObject ?: return@mapNotNull null
      when (obj["type"].asStringOrNull()) {
        "output_text" -> obj["text"].asStringOrNull()
        "refusal" -> obj["refusal"].asStringOrNull()
        else -> null
      }
    }.joinToString(separator = "")
  }

  private fun formatCodexRequestFailure(
    statusCode: Int? = null,
    message: String? = null,
    detailSegments: List<String> = emptyList(),
    metadataSegments: List<String> = emptyList(),
    bodyPreview: String? = null,
  ): String {
    val prefix =
      if (statusCode != null) {
        "OpenAI Codex request failed ($statusCode)"
      } else {
        "OpenAI Codex request failed"
      }
    val segments =
      buildList {
        message?.trim()?.takeIf { it.isNotEmpty() && !it.equals(prefix, ignoreCase = true) }?.let { add(it) }
        detailSegments.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.forEach { add(it) }
        metadataSegments.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.forEach { add(it) }
        bodyPreview?.trim()?.takeIf(String::isNotEmpty)?.let { add("body=$it") }
      }.distinct()
    return if (segments.isEmpty()) {
      prefix
    } else {
      "$prefix | ${segments.joinToString(separator = " | ")}"
    }
  }

  private fun parseCodexError(
    statusCode: Int,
    body: String,
    responseMessage: String?,
    requestId: String?,
    contentType: String?,
  ): String {
    val metadata =
      buildList {
        requestId?.trim()?.takeIf { it.isNotEmpty() }?.let { add("requestId=$it") }
        contentType?.trim()?.takeIf { it.isNotEmpty() }?.let { add("contentType=$it") }
        responseMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { add("message=$it") }
      }
    if (body.isBlank()) {
      return formatCodexRequestFailure(statusCode = statusCode, metadataSegments = metadata)
    }
    return try {
      val root = json.parseToJsonElement(body) as? JsonObject
      val error = root?.get("error").asObjectOrNull()
      val detailParts =
        buildList {
          error?.get("type").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("errorType=$it") }
          error?.get("code").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("errorCode=$it") }
          error?.get("param").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("param=$it") }
          root?.get("type").asStringOrNull()?.takeIf { it.isNotEmpty() }?.let { add("type=$it") }
        }
      val detailText = error?.get("details").asStringOrNull() ?: root?.get("details").asStringOrNull()
      val message = error?.get("message").asStringOrNull() ?: root?.get("message").asStringOrNull()
      formatCodexRequestFailure(
        statusCode = statusCode,
        message = message,
        detailSegments = listOfNotNull(detailText) + detailParts,
        metadataSegments = metadata,
        bodyPreview = if (message.isNullOrBlank() && detailText.isNullOrBlank() && detailParts.isEmpty()) body.take(240) else null,
      )
    } catch (_: Throwable) {
      formatCodexRequestFailure(
        statusCode = statusCode,
        detailSegments = if (statusCode == 429) listOf("Too Many Requests") else emptyList(),
        metadataSegments = metadata,
        bodyPreview = body.take(200),
      )
    }
  }
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonPrimitive -> content
    else -> null
  }
