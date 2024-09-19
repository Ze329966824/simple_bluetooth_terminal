package de.kai_morich.simple_bluetooth_terminal

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.kai_morich.simple_bluetooth_terminal.SerialService.SerialBinder
import java.util.ArrayDeque


class TerminalFragment : Fragment(), ServiceConnection, SerialListener {

    private lateinit var otaUpdateManager: OtaUpdateManager

    private enum class Connected { False, Pending, True }

    private var deviceAddress: String? = null
    private var service: SerialService? = null
    private lateinit var receiveText: TextView
    private lateinit var otaBtn: Button
    private lateinit var sendText: TextView
    private lateinit var hexWatcher: TextUtil.HexWatcher
    private var connected = Connected.False
    private var initialStart = true
    private var hexEnabled = false
    private var pendingNewline = false
    private var newline = TextUtil.newline_crlf

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        otaUpdateManager = OtaUpdateManager { command -> send(command) } // 将 send 方法作为回调传递
        deviceAddress = arguments?.getString("device")
    }

    override fun onStart() {
        super.onStart()
        if (service != null) service?.attach(this)
        else activity?.startService(Intent(activity, SerialService::class.java))
    }

    override fun onStop() {
        if (service != null && !activity?.isChangingConfigurations!!) service?.detach()
        super.onStop()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        activity.bindService(
            Intent(activity, SerialService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDetach() {
        try {
            activity?.unbindService(this)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText = view.findViewById(R.id.receive_text)
        otaBtn = view.findViewById(R.id.ota_btn)
        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText))
        receiveText.movementMethod = ScrollingMovementMethod.getInstance()

        sendText = view.findViewById(R.id.send_text)
        hexWatcher = TextUtil.HexWatcher(sendText)
        hexWatcher.enable(hexEnabled)
        sendText.addTextChangedListener(hexWatcher)
        sendText.hint = if (hexEnabled) "HEX mode" else ""

        val sendBtn: View = view.findViewById<View>(R.id.send_btn)
        // 设置 send 按钮点击事件：发送输入框中的内容
        // 设置 send 按钮点击事件：发送输入框中的内容
        sendBtn.setOnClickListener {
            val textToSend = sendText.text.toString()

            if (textToSend.isNotEmpty()) {
                if (hexEnabled) {
                    // HEX mode: 将十六进制字符串转换为 ByteArray
                    try {
                        val hexStr = textToSend.replace(" ", "")  // 去掉空格
                        if (hexStr.length % 2 != 0) {
                            Toast.makeText(context, "请输入完整的十六进制字符对", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val byteArray = hexStr.chunked(2)
                            .map { it.toInt(16).toByte() }
                            .toByteArray()

                        // 调用 send 方法发送字节数组
                        send(byteArray)
                    } catch (e: NumberFormatException) {
                        Toast.makeText(context, "无效的十六进制输入", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 普通模式：直接将字符串转换为字节数组并发送
                    send(textToSend.toByteArray())
                }
            } else {
                Toast.makeText(context, "请输入要发送的内容", Toast.LENGTH_SHORT).show()
            }
        }

// 设置 otaBtn 按钮点击事件：启动 OTA 更新流程
        otaBtn.setOnClickListener {
            // 启动 OTA 更新流程
            startOtaUpdate()
        }



        return view
    }

    // 启动 OTA 更新流程
    private fun startOtaUpdate() {
        otaUpdateManager.sendNextCommand() // 发送第一个 OTA 指令
    }



    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.hex).isChecked = hexEnabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification).isChecked =
                service?.areNotificationsEnabled() == true
        } else {
            menu.findItem(R.id.backgroundNotification).isChecked = true
            menu.findItem(R.id.backgroundNotification).isEnabled = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear -> {
                receiveText.text = ""
                return true
            }

            R.id.newline -> {
                val newlineNames = resources.getStringArray(R.array.newline_names)
                val newlineValues = resources.getStringArray(R.array.newline_values)
                val pos = newlineValues.indexOf(newline)
                AlertDialog.Builder(activity)
                    .setTitle("Newline")
                    .setSingleChoiceItems(newlineNames, pos) { dialog, which ->
                        newline = newlineValues[which]
                        dialog.dismiss()
                    }
                    .create()
                    .show()
                return true
            }

            R.id.hex -> {
                hexEnabled = !hexEnabled
                sendText.setText("")
                hexWatcher.enable(hexEnabled)
                sendText.hint = if (hexEnabled) "HEX mode" else ""
                item.isChecked = hexEnabled
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            status("connecting...")
            connected = Connected.Pending
            val socket = SerialSocket(requireActivity().applicationContext, device)
            service?.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service?.disconnect()
    }

    private fun status(str: String) {
        val spn = SpannableStringBuilder("$str\n")
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorStatusText)),
            0, spn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        receiveText.append(spn)
    }

    override fun onSerialConnect() {
        status("connected")
        connected = Connected.True
    }

    override fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
        // 处理收到的数据
        receive(datas)
    }

    override fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
    }
    private fun send(data: ByteArray) {
        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // 将要发送的数据转换为字符串，显示在界面上
            val msg = data.joinToString(" ") { String.format("%02X", it) }

            // 显示发送的数据
            val spn = SpannableStringBuilder("$msg\n")
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            receiveText.append(spn)

            // 发送字节数据到板子
            service?.write(data)

        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }


    // 处理收到的数据并传递给 OtaUpdateManager
    override fun onSerialRead(data: ByteArray) {
        otaUpdateManager.onReceiveAck(data) // 传递收到的数据
    }


    private fun receive(datas: ArrayDeque<ByteArray>) {
        val spn = SpannableStringBuilder()
        for (data in datas) {
            if (hexEnabled) {
                // 将收到的字节数组转换为十六进制字符串并追加到 TextView 中
                spn.append(TextUtil.toHexString(data)).append('\n')
            } else {
                // 将字节数组转换为字符串并处理换行符
                var msg = String(data)
                if (newline == TextUtil.newline_crlf && msg.isNotEmpty()) {
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf)
                    // 处理回车换行符
                    if (pendingNewline && msg[0] == '\n') {
                        if (spn.length >= 2) spn.delete(spn.length - 2, spn.length)
                        else {
                            val edt = receiveText.editableText
                            if (edt != null && edt.length >= 2) edt.delete(
                                edt.length - 2,
                                edt.length
                            )
                        }
                    }
                    pendingNewline = msg.last() == '\r'
                }
                spn.append(TextUtil.toCaretString(msg, newline.isNotEmpty()))
            }
        }
        receiveText.append(spn)
    }


    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as SerialBinder).service
        service?.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread { this.connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

}
