package com.example.mycar

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket


class UDPReceiver {
    fun startListening(port: Int, lis: OnRecvUDP) {
        try {
            DatagramSocket(port).use { socket ->
                while (true) {
                    val buffer = ByteArray(1024)
                    val packet =
                        DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message =
                        String(packet.data, 0, packet.length)
                    lis.onRecvMsg(message)
                    Log.d("benyl", "Received message: $message")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

interface OnRecvUDP {
    fun onRecvMsg(str: String)
}