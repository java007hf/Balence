package com.example.mycar

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object Command {
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    const val TEST = 0
    const val OPT_LR = 1
    const val SET_PID = 3
    private val listeners = ArrayList<OnRecv>()
    private val isRunning = AtomicBoolean(true)

    interface OnRecv {
        fun onRecvMsg(str: String)
    }

    fun initBluetooth(context: Context) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bluetoothAdapter.bondedDevices

        for (device in pairedDevices) {
            Log.d("Bluetooth", "接收数据 $device")
            if (device.name == "ESP32_BalanceCar") {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    bluetoothSocket?.connect()
                    outputStream = bluetoothSocket?.outputStream

                    // 启动接收线程
                    startReceiving()
                    break
                } catch (e: IOException) {
                    Log.e("Bluetooth", "连接失败", e)
                }
            }
        }
    }

    private fun startReceiving() {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(64)
            val inputStream = bluetoothSocket?.inputStream

            while (isRunning.get()) {
                try {
                    val bytes = inputStream?.read(buffer)
                    if (bytes != null && bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        Log.d("Bluetooth", "message = $message")
                        MainScope().launch {
                            listeners.forEach {
                                it.onRecvMsg(message)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e("Bluetooth", "接收数据失败", e)
                    break
                }
            }
        }
    }

    fun sendMsg(msg: String) {
        try {
            outputStream?.write(msg.toByteArray())
            outputStream?.flush() // 确保数据立即发送
        } catch (e: IOException) {
            Log.e("Bluetooth", "发送数据失败", e)
        }
    }
    
    fun addListener(lis: OnRecv) {
        listeners.add(lis)
    }
    
    fun removeListener(lis: OnRecv) {
        listeners.remove(lis)
    }
    
    fun disconnect() {
        isRunning.set(false)
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("Bluetooth", "断开连接失败", e)
        }
    }
}
