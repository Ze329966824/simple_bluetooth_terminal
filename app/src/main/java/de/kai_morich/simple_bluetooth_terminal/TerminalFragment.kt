package de.kai_morich.simple_bluetooth_terminal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.clj.fastble.BleManager
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleMtuChangedCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleWriteCallback
import de.kai_morich.simple_bluetooth_terminal.OtaUpdateManager.TAG
import de.kai_morich.simple_bluetooth_terminal.OtaUpdateManager.sendOtaCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TerminalFragment : Fragment() {

    private lateinit var receiveText: TextView
    private lateinit var otaBtn: Button
    private var bleDevice: BleDevice? = null
    private var step = 0

    override fun onResume() {
        super.onResume()
        // 从上一个Fragment获取传递过来的设备地址
        val deviceAddress = arguments?.getString("device_address")

        // 连接设备
        connectDevice(deviceAddress)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText = view.findViewById(R.id.receive_text)
        receiveText.movementMethod = ScrollingMovementMethod()
        otaBtn = view.findViewById(R.id.ota_btn)


        // 设置OTA按钮的点击事件
        otaBtn.setOnClickListener {
            startOtaProcess() // 点击按钮触发OTA流程
        }

        return view
    }

    // 连接设备
    private fun connectDevice(address: String?) {
        if (address.isNullOrEmpty()) {
            receiveText.append("Device address is null or empty\n")
            return
        }

        BleManager.getInstance().connect(address, object : BleGattCallback() {
            override fun onStartConnect() {
                receiveText.append("Connecting to device...\n")
            }

            override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                receiveText.append("Connect failed: ${exception?.description}\n")
            }

            override fun onConnectSuccess(
                bleDevice: BleDevice?,
                gatt: BluetoothGatt?,
                status: Int
            ) {
                receiveText.append("Connected to device\n")
                this@TerminalFragment.bleDevice = bleDevice
                setNotification() // 设置通知
                setMaxMTU()
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice?,
                gatt: BluetoothGatt?,
                status: Int
            ) {
                receiveText.append("Disconnected from device\n")
            }
        })
    }

    private fun setMaxMTU() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val maxMTU = 512  // 直接尝试设置为最大值 512

            BleManager.getInstance()
                .setMtu(bleDevice, maxMTU, object : BleMtuChangedCallback() {
                    override fun onSetMTUFailure(exception: BleException) {
                        // 设置 MTU 失败，设备可能不支持 512，可以记录并处理
                        Log.e(TAG, "Failed to set MTU to $maxMTU: ${exception.description}")
                    }

                    override fun onMtuChanged(mtu: Int) {
                        // 设置 MTU 成功，记录当前设备实际支持的 MTU
                        Log.i(TAG, "MTU successfully set to $mtu")
                    }
                })
        } else {
            // 如果设备的 API 低于 21，不支持 MTU 调整
            Log.i(TAG, "MTU setting is not supported on devices below API 21.")
        }
    }

    private fun findMaxMTU() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var defaultMTU = 23
            val maxMTU = 512

            fun trySetMTU(currentMTU: Int) {
                BleManager.getInstance()
                    .setMtu(bleDevice, currentMTU, object : BleMtuChangedCallback() {
                        override fun onSetMTUFailure(exception: BleException) {
                            Log.i(TAG, "onSetMTUFailure for MTU: $currentMTU\n${exception.description}")
                            if (currentMTU > 23) {
                                Log.i(TAG, "Max MTU supported: ${currentMTU - 1}")
                            }
                        }

                        override fun onMtuChanged(mtu: Int) {
                            Log.i(TAG, "set: $currentMTU   onMtuChanged: $mtu")
                            if (mtu < maxMTU && mtu != defaultMTU) {
                                trySetMTU(mtu + 1)
                            } else {
                                Log.i(TAG, "Max MTU supported: $mtu")
                            }
                        }
                    })
            }

            trySetMTU(defaultMTU + 1)
        } else {
            Log.i(TAG, "MTU setting is not required for devices below API 21.")
        }
    }

    // 设置通知以接收数据
    private fun setNotification() {
        if (bleDevice == null) return

        BleManager.getInstance().notify(
            bleDevice,
            OtaUpdateManager.uuid_service,
            OtaUpdateManager.uuid_notify,
            object : BleNotifyCallback() {
                override fun onNotifySuccess() {
                    receiveText.append("Notification set successfully\n")
                }

                override fun onNotifyFailure(exception: BleException?) {
                    receiveText.append("Failed to set notification: ${exception?.description}\n")
                }

                override fun onCharacteristicChanged(data: ByteArray) {
                    receiveMessage(data)
                    // 根据收到的数据判断是否要进行下一步OTA操作
                    processOtaResponse(data)
                }
            })
    }

    // 接收消息并显示为绿色
    private fun receiveMessage(data: ByteArray) {
        val receivedMessage = byteArrayToHex(data)
        val spn = SpannableStringBuilder(receivedMessage)
        spn.setSpan(
            ForegroundColorSpan(Color.GREEN), 0, spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        receiveText.append(spn)
        receiveText.append("\n")
    }

    // 发送消息并显示为浅蓝色
    private fun sendMessage(command: ByteArray) {
//        val messageToSend = byteArrayToHex(command)
//        val spn = SpannableStringBuilder(messageToSend)
//        spn.setSpan(
//            ForegroundColorSpan(Color.parseColor("#ADD8E6")), 0, spn.length,
//            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE // 浅蓝色
//        )
//        receiveText.append(spn)
//        receiveText.append("\n")

        // 发送指令到设备
        BleManager.getInstance()
            .write(bleDevice, OtaUpdateManager.uuid_service, OtaUpdateManager.uuid_notify,
                command, object : BleWriteCallback() {
                    override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                        val spn = SpannableStringBuilder(byteArrayToHex(justWrite!!))
                        spn.setSpan(
                            ForegroundColorSpan(Color.YELLOW), 0, spn.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        receiveText.append("Write Success: ${spn}\n")
                    }

                    override fun onWriteFailure(exception: BleException?) {

                        receiveText.append("Write failed: ${exception?.description}\n")
                    }
                })
    }

    private fun startOtaProcess() {
        receiveText.append("\n")

        when (step) {
            0 -> {
                // Step 0: 发送 OTA_UPDATE 命令
                val otaUpdateCommand = byteArrayOf(0x55.toByte(), 0x36.toByte(), 0xAA.toByte())
                sendOtaCommand(bleDevice, otaUpdateCommand, receiveText)
                receiveText.append("Sent OTA_UPDATE command\n")
            }

            1 -> {
                // Step 1: 发送 OTA_START 命令
                val otaStartCommand = byteArrayOf(
                    0xAA.toByte(),
                    0x00.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xBB.toByte()
                )
                sendOtaCommand(bleDevice, otaStartCommand, receiveText)
                receiveText.append("Sent OTA_START command\n")
            }

            2 -> {
                // Step 2: 发送 OTA_HEADER 命令
                val otaHeaderCommand = byteArrayOf(
                    0xAA.toByte(),
                    0x02.toByte(),
                    0x10.toByte(),
                    0x00.toByte(),
                    0x7C.toByte(),
                    0x42.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xF0.toByte(),
                    0xCA.toByte(),
                    0xFF.toByte(),
                    0xFF.toByte(),
                    0x07.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xBB.toByte()
                )
                sendOtaCommand(bleDevice, otaHeaderCommand, receiveText)
                receiveText.append("Sent OTA_HEADER command\n")
            }

            3 -> {
                // Step 3: 发送自定义 OTA 数据包
                val otaDataCommand = byteArrayOf(
                    0xAA.toByte(),   // SOF
                    0x01.toByte(),   // Packet type
                    0x05.toByte(),   // Data length (低字节)
                    0x00.toByte(),   // Data length (高字节)
                    0x68.toByte(),   // 'h'
                    0x65.toByte(),   // 'e'
                    0x6C.toByte(),   // 'l'
                    0x6C.toByte(),   // 'l'
                    0x6F.toByte(),   // 'o'
                    0x00.toByte(),   // Padding
                    0x00.toByte(),   // Padding
                    0x00.toByte(),   // Padding
                    0x00.toByte(),   // Padding
                    0xBB.toByte()    // End byte
                )

                sendOtaCommand(bleDevice, otaDataCommand, receiveText)
                receiveText.append("Sent custom OTA data packet\n")

            }

            4 -> {
                // Step 4: 发送 OTA_END 命令
                val otaEndCommand = byteArrayOf(
                    0xAA.toByte(),
                    0x00.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xBB.toByte()
                )
                sendOtaCommand(bleDevice, otaEndCommand, receiveText)
                receiveText.append("Sent OTA_END command\n")
            }
        }
    }

    val ack = byteArrayOf(
        0xAA.toByte(),
        0x03.toByte(),
        0x01.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0xBB.toByte()
    )
    val nack = byteArrayOf(
        0xAA.toByte(),
        0x03.toByte(),
        0x01.toByte(),
        0x00.toByte(),
        0x01.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0xBB.toByte()
    )

    // 处理OTA流程响应，决定是否进入下一步
    private fun processOtaResponse(data: ByteArray) {
        when (step) {
            0 -> { // Step 0: 处理 OTA_UPDATE 的响应
                val expectedResponse = byteArrayOf(
                    0xFF.toByte(),
                    0xAA.toByte(),
                    0x57.toByte(),
                    0xFF.toByte(),
                    0xBB.toByte()
                )
                if (data.contentEquals(expectedResponse)) {
                    // 收到 FF AA 57 FF BB 的数据，进入下一步
                    step++ // 进入下一步
                    startOtaProcess() // 执行下一步 OTA 操作
                } else {
//                    receiveText.append("Unexpected response for OTA_UPDATE\n")
                }
            }

            1 -> { // Step 1: 处理 OTA_START 的 ACK
                if (data.contentEquals(ack)) {
                    step++ // 进入下一步
                    startOtaProcess() // 执行下一步 OTA 操作
                } else if (data.contentEquals(nack)) {
                    receiveText.append("Received OTA_START NACK\n")
                }
            }

            2 -> { // Step 2: 处理 OTA_HEADER 的 ACK
                if (data.contentEquals(ack)) {
                    step++ // 进入下一步
                    startOtaProcess() // 执行下一步 OTA 操作
                } else if (data.contentEquals(nack)) {
                    receiveText.append("Received OTA_HEADER NACK\n")
                }
            }

            3 -> { // Step 3: 处理 OTA_DATA 的 ACK
                if (data.contentEquals(ack)) {
                    step++ // 进入最后一步
                    startOtaProcess() // 执行下一步 OTA 操作
                } else if (data.contentEquals(nack)) {
                    receiveText.append("Received OTA_DATA NACK\n")
                }
            }

            4 -> { // Step 4: 处理 OTA_END 的 ACK（最后一步）
                val ack = byteArrayOf(
                    0xAA.toByte(),
                    0x03.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xBB.toByte()
                )
                if (data.contentEquals(ack)) {
                } else if (data.contentEquals(nack)) {
                    receiveText.append("Received OTA_END NACK\n")
                }
            }

            else -> {
                receiveText.append("Unknown step in OTA process\n")
            }
        }


    }


    // 发送OTA数据包
    private fun sendOtaData() {
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

        sendMessage(packet)
    }

    // 扩展函数：将16进制字符串转为字节数组
    private fun String.decodeHex(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] =
                ((this[i].toString().toInt(16) shl 4) + this[i + 1].toString().toInt(16)).toByte()
        }
        return data
    }

    // 将字节数组转换为16进制字符串
    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}
