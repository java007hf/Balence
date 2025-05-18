package com.example.mycar

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mycar.Command.sendMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private var hasConnected = false
    private val agentClient = AgentClient("http://192.168.3.102:8000")
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initASR()
        initView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        val openSettings = findViewById<Button>(R.id.opensetting)
        openSettings.setOnClickListener {
            val intent = Intent()
            intent.setClass(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        val testBtn = findViewById<Button>(R.id.button)
        testBtn.setOnClickListener {
            sendMsg(Command.TEST.toString())
        }

        val frontBtn = findViewById<Button>(R.id.front_btn)
        frontBtn.setOnClickListener {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        agentClient.query("前进").collect { event ->
                            withContext(Dispatchers.Main) {
                                when (event.type) {
                                    "model_response" -> {
                                        val response = event.data as AgentClient.ModelResponse
                                        Toast.makeText(this@MainActivity, "收到响应: ${response.content}", Toast.LENGTH_SHORT).show()
                                    }
                                    "tool_call" -> {
                                        val toolCall = event.data as AgentClient.ToolCall
                                        Toast.makeText(this@MainActivity, "工具调用: ${toolCall.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    "error" -> {
                                        val error = event.data as String
                                        Toast.makeText(this@MainActivity, "错误: $error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun initASR() {
    }

    private fun onRecv(msg: String) {
        Log.d("benyl", "recvMsg===== $msg")
        if (msg.equals("100 test ok")) {
            hasConnected = true
            Toast.makeText(this, "连接正常", Toast.LENGTH_SHORT).show()
        }
    }
}

