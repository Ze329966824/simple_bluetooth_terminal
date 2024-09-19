package de.kai_morich.simple_bluetooth_terminal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
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
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleWriteCallback
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TerminalFragment : Fragment() {

    private lateinit var receiveText: TextView
    private lateinit var otaBtn: Button
    private var bleDevice: BleDevice? = null
    private var step = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText = view.findViewById(R.id.receive_text)
        receiveText.movementMethod = ScrollingMovementMethod()
        otaBtn = view.findViewById(R.id.ota_btn)

        // 从上一个Fragment获取传递过来的设备地址
        val deviceAddress = arguments?.getString("device")

        // 连接设备
        connectDevice(deviceAddress)

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

            override fun onConnectSuccess(bleDevice: BleDevice?, gatt: BluetoothGatt?, status: Int) {
                receiveText.append("Connected to device\n")
                this@TerminalFragment.bleDevice = bleDevice
                setNotification() // 设置通知
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
        val messageToSend = byteArrayToHex(command)
        val spn = SpannableStringBuilder(messageToSend)
        spn.setSpan(
            ForegroundColorSpan(Color.parseColor("#ADD8E6")), 0, spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE // 浅蓝色
        )
        receiveText.append(spn)
        receiveText.append("\n")

        // 发送指令到设备
        BleManager.getInstance().write(bleDevice, OtaUpdateManager.uuid_service, OtaUpdateManager.uuid_notify,
            command, object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    receiveText.append("Write Success: ${byteArrayToHex(justWrite!!)}\n")
                }

                override fun onWriteFailure(exception: BleException?) {
                    receiveText.append("Write failed: ${exception?.description}\n")
                }
            })
    }

    // 启动OTA流程
    private fun startOtaProcess() {
        when (step) {
            0 -> {
                val otaUpdateCommand = byteArrayOf(0x55.toByte(), 0x36.toByte(), 0xAA.toByte())
                sendMessage(otaUpdateCommand)
                step++
            }
            1 -> {
                val otaStartCommand = byteArrayOf(
                    0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0xBB.toByte()
                )
                sendMessage(otaStartCommand)
                step++
            }
            2 -> {
                val otaHeaderCommand = byteArrayOf(
                    0xAA.toByte(), 0x02.toByte(), 0x10.toByte(), 0x00.toByte(),
                    0x7C.toByte(), 0x42.toByte(), 0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                    0xF0.toByte(), 0xCA.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                    0x07.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
                )
                sendMessage(otaHeaderCommand)
                step++
            }
            3 -> sendOtaData()
            4 -> {
                val otaEndCommand = byteArrayOf(
                    0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
                    0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0xBB.toByte()
                )
                sendMessage(otaEndCommand)
            }
        }
    }

    // 处理OTA流程响应，决定是否进入下一步
    private fun processOtaResponse(data: ByteArray) {
        val ack = byteArrayOf(0xAA.toByte(), 0x03.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte())
        if (data.contentEquals(ack)) {
            step++
            startOtaProcess()
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
            data[i / 2] = ((this[i].toString().toInt(16) shl 4) + this[i + 1].toString().toInt(16)).toByte()
        }
        return data
    }

    // 将字节数组转换为16进制字符串
    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}
