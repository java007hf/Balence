package com.example.mycar

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


class UDPSender {
    fun sendMessage(message: String, ip: String?, port: Int) {
        try {
            DatagramSocket().use { socket ->
                val data = message.toByteArray()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(ip),
                    port
                )
                socket.send(packet)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}