package de.kai_morich.simple_bluetooth_terminal
import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BleOtaManager(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var step = 0
    private val TAG = "BleOtaManager"
    private var isOtaInProgress = false

    companion object {
        val UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val UUID_WRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        const val MAX_MTU = 20 // 设置每次发送的最大字节数
    }

    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.")
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered.")
                characteristic = bluetoothGatt?.getService(UUID_SERVICE)?.getCharacteristic(UUID_WRITE)
                startOtaProcess() // 开始自动OTA流程
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful.")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                parseResponse(data) // 解析返回数据
            }
        }
    }

    private fun sendCommand(command: ByteArray) {
        if (characteristic == null || bluetoothGatt == null) return
        val chunkedData = command.asList().chunked(MAX_MTU) // 分段发送数据

        chunkedData.forEach { chunk ->
            val buffer = chunk.toByteArray()
            characteristic?.value = buffer
            bluetoothGatt?.writeCharacteristic(characteristic)
            Thread.sleep(100) // 每个包之间等待100毫秒，防止数据丢失
        }
    }

    private fun parseResponse(data: ByteArray) {
        Log.d(TAG, "Received response: ${byteArrayToHex(data)}")
        when (step) {
            0 -> { // 解析 OTA_UPDATE 的响应
                val expectedResponse = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x57.toByte(), 0xFF.toByte(), 0xBB.toByte())
                if (data.contentEquals(expectedResponse)) {
                    step++
                    sendOtaStart() // 进入 OTA_START
                }
            }
            1 -> { // 解析 OTA_START 的 ACK
                if (isAck(data)) {
                    step++
                    sendOtaHeader() // 进入 OTA_HEADER
                }
            }
            2 -> { // 解析 OTA_HEADER 的 ACK
                if (isAck(data)) {
                    step++
                    sendOtaData() // 进入 OTA_DATA
                }
            }
            3 -> { // 解析 OTA_DATA 的 ACK
                if (isAck(data)) {
                    step++
                    sendOtaEnd() // 进入 OTA_END
                }
            }
            4 -> { // 解析 OTA_END 的 ACK
                if (isAck(data)) {
                    Log.d(TAG, "OTA Process completed successfully!")
                    isOtaInProgress = false // OTA 完成
                }
            }
        }
    }

    private fun isAck(data: ByteArray): Boolean {
        val ack = byteArrayOf(0xAA.toByte(), 0x03.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte())
        return data.contentEquals(ack)
    }

    fun startOtaProcess() {
        if (isOtaInProgress) return // 避免重复开始
        isOtaInProgress = true
        step = 0
        val otaUpdateCommand = byteArrayOf(0x55.toByte(), 0x36.toByte(), 0xAA.toByte())
        sendCommand(otaUpdateCommand) // 发送 OTA_UPDATE
    }

    fun sendOtaStart() {
        val otaStartCommand = byteArrayOf(
            0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
        )
        sendCommand(otaStartCommand) // 发送 OTA_START
    }

    fun sendOtaHeader() {
        val otaHeaderCommand = byteArrayOf(
            0xAA.toByte(), 0x02.toByte(), 0x10.toByte(), 0x00.toByte(), 0x7C.toByte(), 0x42.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xF0.toByte(), 0xCA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xBB.toByte()
        )
        sendCommand(otaHeaderCommand) // 发送 OTA_HEADER
    }

    fun sendOtaData() {
        val data = "68656c6c6f".decodeHex() // 模拟的数据
        val dataLen: Short = data.size.toShort()

        val dataLenBytes = ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(dataLen)
            .array()

        val packet = ByteArray(1 + 1 + 2 + data.size + 4 + 1)
        var offset = 0

        packet[offset++] = 0xAA.toByte() // SOF
        packet[offset++] = 0x01.toByte() // Packet Type
        packet[offset++] = dataLenBytes[0]
        packet[offset++] = dataLenBytes[1]
        System.arraycopy(data, 0, packet, offset, data.size)
        offset += data.size
        packet[offset++] = 0x00.toByte() // 结束标志
        packet[offset++] = 0x00.toByte()
        packet[offset++] = 0x00.toByte()
        packet[offset++] = 0x00.toByte()
        packet[offset] = 0xBB.toByte()

        sendCommand(packet) // 发送 OTA_DATA
    }

    fun sendOtaEnd() {
        val otaEndCommand = byteArrayOf(
            0xAA.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
        )
        sendCommand(otaEndCommand) // 发送 OTA_END
    }

    fun close() {
        bluetoothGatt?.close()
    }

    private fun String.decodeHex(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((this[i].toString().toInt(16) shl 4) + this[i + 1].toString().toInt(16)).toByte()
        }
        return data
    }

    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}
