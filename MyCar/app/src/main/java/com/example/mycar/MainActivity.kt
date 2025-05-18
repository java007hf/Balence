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

class MainActivity : AppCompatActivity() {
    private var hasConnected = false

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

