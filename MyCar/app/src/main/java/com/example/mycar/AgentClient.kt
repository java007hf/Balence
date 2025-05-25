package com.example.mycar

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class AgentClient(private val baseUrl: String = "http://localhost:8000") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 定义持久化的 SSE 连接和事件通道
    private var eventSource: EventSource? = null
    private val eventChannel = Channel<AgentEvent>(Channel.UNLIMITED)

    // 初始化时建立持续 SSE 监听（假设服务端支持 /listen 端点）
    init {
        val listenRequest = Request.Builder()
            .url("$baseUrl/sse")
            .build()

        eventSource = EventSources.createFactory(client).newEventSource(listenRequest, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d("benyl", "原始data内容: $data")
                // 解析 SSE 标准格式（event: type\ndata: payload）
                val lines = data.split("\n").filter { it.isNotBlank() }
                val parsedEvent = lines.firstOrNull { it.startsWith("event:") }?.substringAfter("event:")?.trim()
                val parsedData = lines.firstOrNull { it.startsWith("data:") }?.substringAfter("data:")?.trim() ?: data

                // 使用解析出的 event 类型（优先使用 type 参数，若为 null 则用 parsedEvent）
                val eventType = type ?: parsedEvent
                Log.d("benyl", "解析后的eventType: $eventType，解析后data: $parsedData")

                // 统一处理所有 SSE 事件，发送到事件通道
                when (eventType) {
                    "tool_call" -> {
                        val jsonData = JSONObject(parsedData)
                        eventChannel.trySend(AgentEvent("tool_call", ToolCall(
                            name = jsonData.optString("name"),
                            arguments = jsonData.optString("arguments"),
                        )))
                    }
                    "model_delta" -> {
                        val jsonData = JSONObject(parsedData)
                        eventChannel.trySend(AgentEvent("model_delta", jsonData.getString("content")))
                    }
                    "model_done" -> {
                        eventChannel.trySend(AgentEvent("model_done", Unit))
                    }
                    "tool_output" -> {
                        val jsonData = JSONObject(parsedData)
                        eventChannel.trySend(AgentEvent("tool_output", ToolOutput(
                            output = jsonData.getString("output")
                        )))
                    }
                    "final_output" -> {
                        val jsonData = JSONObject(parsedData)
                        eventChannel.trySend(AgentEvent("final_output", jsonData.getString("content")))
                    }
                    "error" -> {
                        val jsonData = JSONObject(parsedData)
                        eventChannel.trySend(AgentEvent("error", jsonData.getString("error")))
                    }
                    else -> {
                        handleMCPHostMessage(parsedData)
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                eventChannel.trySend(AgentEvent("error", t?.message ?: "Unknown error"))
            }
        })
    }

    // 发送查询请求（根据 main.py 的 QueryRequest 协议实现）
    suspend fun query(query: String, sessionId: String = "default", streaming: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            // 使用Gson等库构造JSON，避免手动拼接导致的格式错误
            val requestBody = JSONObject().apply {
                put("query", query)
                put("session_id", sessionId)
                put("streaming", streaming)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/query")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            
            // 记录422时的响应体，便于定位具体错误
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("AgentClient", "查询失败，状态码：${response.code}, 错误信息：$errorBody")
            }
            
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e("AgentClient", "查询请求失败: ${e.message}")
            false
        }
    }

    // 原 sendMCPHostMSG 功能迁移（示例）
    private fun handleMCPHostMessage(data: String) {
        // 这里实现原 sendMCPHostMSG 的逻辑（例如解析 data 并执行操作）
        Log.d("AgentClient", "Received MCP Host message: $data")
    }

    data class ToolCall(
        val name: String? = null,
        val arguments: String? = null,
    )

    data class ToolOutput(
        val output: String
    )

    data class AgentEvent(
        val type: String,  // 事件类型（如 "model_delta"、"tool_output"）
        val data: Any  // 具体数据（可能是 String、ToolCall、ToolOutput 等）
    )

    // 清空对话历史（未修改）
    suspend fun clearHistory(sessionId: String = "default"): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/clear_history?session_id=$sessionId")
                .post("".toRequestBody(null))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // 暴露事件流供外部订阅
    fun getEventFlow(): Flow<AgentEvent> = eventChannel.receiveAsFlow()
}