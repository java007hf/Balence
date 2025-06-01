package com.example.mycar

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private var carInfo: TextView? = null
    private var kpEditText: EditText? = null
    private var kiEditText: EditText? = null
    private var kdEditText: EditText? = null
    private var kAEditText: EditText? = null
    private var submitBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_fragment_layout)

        carInfo = findViewById(R.id.textView)
        kpEditText = findViewById(R.id.kp)
        kiEditText = findViewById(R.id.ki)
        kdEditText = findViewById(R.id.kd)
        submitBtn = findViewById(R.id.submit)
        kAEditText = findViewById(R.id.Keep_Angle)

        Command.addListener(object : Command.OnRecv {
            override fun onRecvMsg(str: String) {
                carInfo?.setText(str)
            }
        })

        submitBtn?.setOnClickListener {
            val commandStr = Command.SET_PID.toString() + " " + kpEditText?.text + " " + kiEditText?.text + " " + kdEditText?.text + " " + kAEditText?.text
            Command.sendMsg(commandStr)
        }
    }
}