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

    // 定义事件数据类
    data class ModelResponse(
        val type: String,
        val content: String? = null
    )

    data class ToolCall(
        val type: String,
        val name: String? = null,
        val arguments: String? = null,
        val toolId: String? = null,
        val output: String? = null
    )

    data class AgentEvent(
        val type: String,
        val data: Any
    )

    // 发送查询请求并返回事件流
    fun query(query: String, sessionId: String = "default", streaming: Boolean = true): Flow<AgentEvent> = flow {
        val channel = Channel<AgentEvent>()
        
        val requestBody = JSONObject().apply {
            put("query", query)
            put("session_id", sessionId)
            put("streaming", streaming)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/query")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Log.d("benyl", request.toString())

        val eventSource = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "model_response" -> {
                        val jsonData = JSONObject(data)
                        channel.trySend(AgentEvent("model_response", ModelResponse(
                            type = jsonData.getString("type"),
                            content = jsonData.optString("content")
                        )))
                    }
                    "tool_call" -> {
                        val jsonData = JSONObject(data)
                        channel.trySend(AgentEvent("tool_call", ToolCall(
                            type = jsonData.getString("type"),
                            name = jsonData.optString("name"),
                            arguments = jsonData.optString("arguments"),
                            toolId = jsonData.optString("tool_id"),
                            output = jsonData.optString("output")
                        )))
                    }
                    "error" -> {
                        val jsonData = JSONObject(data)
                        channel.trySend(AgentEvent("error", jsonData.getString("error")))
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                channel.trySend(AgentEvent("error", t?.message ?: "Unknown error"))
            }
        })

        try {
            channel.receiveAsFlow().collect { event ->
                emit(event)
            }
        } catch (e: Exception) {
            emit(AgentEvent("error", e.message ?: "Unknown error"))
        } finally {
            channel.close()
            eventSource.cancel()
        }
    }

    // 清空对话历史
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
}