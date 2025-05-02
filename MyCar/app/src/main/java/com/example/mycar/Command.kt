package com.example.mycar

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Command {
    private fun IOScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scope = IOScope()
    const val TEST = 0
    const val OPT_LR = 1
    const val SET_PID = 3
    private val sender = UDPSender()
    private val receiver = UDPReceiver()
    private val listeners = ArrayList<OnRecvUDP>()

    init {
        scope.launch {
            receiver.startListening(8888, object : OnRecvUDP {
                override fun onRecvMsg(str: String) {
                    MainScope().launch {
                        listeners.forEach {
                            it.onRecvMsg(str)
                        }
                    }
                }
            })
        }
    }

    fun sendMsg(msg: String) {
        Log.d("benyl", "sendMsg===== $msg")
        // 创建发送器并发送消息
        scope.launch {
            sender.sendMessage(msg, "192.168.1.88", 5555)
        }
    }

    fun addListener(lis: OnRecvUDP) {
        listeners.add(lis)
    }

    fun removeListener(lis: OnRecvUDP) {
        listeners.remove(lis)
    }
}
