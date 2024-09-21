package de.kai_morich.simple_bluetooth_terminal

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.clj.fastble.BleManager
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.scan.BleScanRuleConfig

object OtaUpdateManager {
    val TAG = "PduBleManager"
    var uuid_service: String? = "0000ffe0-0000-1000-8000-00805f9b34fb"
    var uuid_notify: String? = "0000ffe1-0000-1000-8000-00805f9b34fb"

    fun init(context: Context) {
        BleManager.getInstance().init(context.applicationContext as Application)
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setSplitWriteNum(20)
            .setConnectOverTime(10000)
            .setOperateTimeout(5000)

        val scanRuleConfig = BleScanRuleConfig.Builder()
            .setScanTimeOut(5000)
            .build()
        BleManager.getInstance().initScanRule(scanRuleConfig)
    }

    fun startOtaProcess(bleDevice: BleDevice?, receiveText: TextView) {
        if (bleDevice == null) {
            receiveText.append("No device connected\n")
            return
        }

        // 发送 OTA 开始命令
        val otaStartCommand = byteArrayOf(0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0xBB.toByte())
        sendOtaCommand(bleDevice, otaStartCommand, receiveText)
    }

    fun sendOtaCommand(bleDevice: BleDevice?, command: ByteArray, receiveText: TextView) {
        val chunkSize = 20 // 每次发送 20 字节
        val totalChunks = (command.size + chunkSize - 1) / chunkSize // 计算总块数

        // 循环每个块，逐块发送
        for (i in 0 until totalChunks) {
            // 计算每块数据的开始和结束位置
            val start = i * chunkSize
            val end = minOf(start + chunkSize, command.size)

            // 复制这段数据
            val chunk = command.copyOfRange(start, end)

            // 发送每一块
            BleManager.getInstance().write(
                bleDevice,
                uuid_service,
                uuid_notify,
                chunk,
                false,  // 设置 split=false，因为我们手动切分
                object : BleWriteCallback() {
                    override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                        val commandText = justWrite?.joinToString(" ") { String.format("%02X", it) } ?: ""
                        val fullText = "$commandText\n"

                        // 创建一个 SpannableStringBuilder 来处理颜色
                        val spannable = SpannableStringBuilder(fullText)

                        // 设置 commandText 的颜色为黄色
                        val start = fullText.indexOf(commandText)
                        val end = start + commandText.length
                        spannable.setSpan(
                            ForegroundColorSpan(Color.YELLOW), // 设置黄色
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        // 将 SpannableStringBuilder 添加到 TextView 中
                        receiveText.append(spannable)
                    }

                    override fun onWriteFailure(exception: BleException?) {
                        receiveText.append("Write failed: ${exception?.description}\n")
                    }
                }
            )

            // 你可以根据需要添加延迟，避免 BLE 堆栈过载
            Thread.sleep(50) // 如果需要可以调节延迟时间
        }
    }

}
