package com.example.mycar

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import kotlinx.coroutines.*
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mycar.Command.sendMsg
import com.jaygoo.widget.OnRangeChangedListener
import com.jaygoo.widget.RangeSeekBar

class MainActivity : AppCompatActivity() {
    private var speed: Int = 0
    private var hasConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initUDP()
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

        val h_seekbar = findViewById<RangeSeekBar>(R.id.horizontal_seekbar)
        h_seekbar.setIndicatorTextDecimalFormat("0")
        h_seekbar.setProgress(0F)
        h_seekbar.setOnRangeChangedListener(object : OnRangeChangedListener {
            override fun onRangeChanged(
                view: RangeSeekBar?,
                leftValue: Float,
                rightValue: Float,
                isFromUser: Boolean
            ) {
                speed = leftValue.toInt()
            }

            override fun onStartTrackingTouch(view: RangeSeekBar?, isLeft: Boolean) {
            }

            override fun onStopTrackingTouch(view: RangeSeekBar?, isLeft: Boolean) {
            }

        })

        val frontBtn = findViewById<Button>(R.id.front_btn)
        frontBtn.setOnClickListener {
            val commandStr = "1"
            sendMsg(commandStr)
        }

        val backBtn = findViewById<Button>(R.id.back_btn)
        backBtn.setOnClickListener {
            val commandStr = "2"
            sendMsg(commandStr)
        }

        val leftBtn = findViewById<Button>(R.id.left_btn)
        leftBtn.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> turnLR(speed)
                else->stop()
            }
            true
        }

        val rightBtn = findViewById<Button>(R.id.right_btn)
        rightBtn.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> turnLR(-speed)
                else->stop()
            }
            true
        }
    }

    private fun initUDP() {
        Command.addListener(object : OnRecvUDP {
            override fun onRecvMsg(str: String) {
                onRecv(str)
            }
        })
    }

    private fun move(value: Int) {
        if (!hasConnected) {
            Toast.makeText(this, "未连接", Toast.LENGTH_SHORT).show()
//            return
        }

        val commandStr = Command.OPT_LR.toString() + " " + value + " " + value
        sendMsg(commandStr)
    }

    private fun turnLR(value: Int) {
        if (!hasConnected) {
            Toast.makeText(this, "未连接", Toast.LENGTH_SHORT).show()
//            return
        }

        val commandStr = Command.OPT_LR.toString() + " " + value + " " + (-value)
        sendMsg(commandStr)
    }

    private fun stop() {
    }

    private fun onRecv(msg: String) {
        Log.d("benyl", "recvMsg===== $msg")
        if (msg.equals("100 test ok")) {
            hasConnected = true
            Toast.makeText(this, "连接正常", Toast.LENGTH_SHORT).show()
        }
    }
}

