package de.kai_morich.simple_bluetooth_terminal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

class OtaUpdateManager(private val sendCallback: (ByteArray) -> Unit) {

    private val TAG = "PduBleManager"
    private var step = 0

    // 各步骤需要发送的数据
    private val otaUpdate = byteArrayOf(0x55.toByte(), 0x36.toByte(), 0xAA.toByte())
    private val otaStart = byteArrayOf(0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte())
    private val otaHeader = byteArrayOf(0xAA.toByte(), 0x02.toByte(), 0x10.toByte(), 0x00.toByte(), 0x7C.toByte(), 0x42.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF0.toByte(), 0xCA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte())
    private val otaEnd = byteArrayOf(0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte())

    fun onReceiveAck(response: ByteArray) {
        val ack = byteArrayOf(0xAA.toByte(), 0x03.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte())
        if (response.contentEquals(ack)) {
            step += 1
            sendNextCommand()
        } else if (response.contains(0x57.toByte())) {
            step = 1
            sendNextCommand()
        }
    }

    fun sendNextCommand() {
        when (step) {
            0 -> sendCommand(otaUpdate)
            1 -> sendCommand(otaStart)
            2 -> sendCommand(otaHeader)
            3 -> sendOtaData()
            4 -> sendCommand(otaEnd)
        }
    }

    private fun sendCommand(command: ByteArray) {
        Log.d(TAG, "发送指令: ${command.joinToString(" ") { String.format("%02X", it) }}")
        sendCallback(command) // 使用回调函数发送数据
    }

    private fun sendOtaData() {
        val data = "68656c6c6f".decodeHex() // 模拟的文件数据
        val dataLen: Short = data.size.toShort()
        val dataLenBytes = ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(dataLen)
            .array()

        val packet = ByteArray(1 + 1 + 2 + data.size + 4 + 1)
        var offset = 0
        packet[offset++] = 0xAA.toByte()
        packet[offset++] = 0x01.toByte()
        packet[offset++] = dataLenBytes[0]
        packet[offset++] = dataLenBytes[1]

        System.arraycopy(data, 0, packet, offset, data.size)
        offset += data.size
        packet[offset++] = 0x00.toByte()
        packet[offset++] = 0x00.toByte()
        packet[offset++] = 0x00.toByte()
        packet[offset++] = 0x00.toByte()
        packet[offset] = 0xBB.toByte()

        sendCommand(packet)
    }

    private fun String.decodeHex(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((this[i].toString().toInt(16) shl 4) + this[i + 1].toString().toInt(16)).toByte()
        }
        return data
    }
}
