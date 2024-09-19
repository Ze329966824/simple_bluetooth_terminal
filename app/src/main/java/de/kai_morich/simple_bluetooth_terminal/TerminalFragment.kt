package de.kai_morich.simple_bluetooth_terminal

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException

class TerminalFragment : Fragment() {

    private lateinit var receiveText: TextView
    private lateinit var otaBtn: Button
    private var deviceAddress: String? = null
    private var bleDevice: BleDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从 arguments 中获取设备地址
        deviceAddress = arguments?.getString("device_address")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText = view.findViewById(R.id.receive_text)
        otaBtn = view.findViewById(R.id.ota_btn)

        // 连接蓝牙设备
        connectDevice()

        // 设置 OTA 按钮点击事件
        otaBtn.setOnClickListener {
            startOtaUpdate()
        }

        return view
    }

    private fun connectDevice() {
        if (deviceAddress == null) {
            Toast.makeText(context, "No device address provided", Toast.LENGTH_SHORT).show()
            return
        }

        receiveText.append("Connecting...\n")

        // 使用 BleManager 连接设备
        BleManager.getInstance().connect(deviceAddress, object : BleGattCallback() {
            override fun onStartConnect() {
                receiveText.append("Start connecting...\n")
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                receiveText.append("Connected\n")
                this@TerminalFragment.bleDevice = bleDevice

                // 开始接收数据
                startNotify()
            }

            override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                receiveText.append("Connection failed: ${exception?.description}\n")
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice?,
                gatt: BluetoothGatt?,
                status: Int
            ) {
                receiveText.append("Disconnected\n")
            }
        })
    }
    var uuid_service: String? = "0000ffe0-0000-1000-8000-00805f9b34fb"
    var uuid_notify: String? = "0000ffe1-0000-1000-8000-00805f9b34fb"
    private fun startNotify() {
        BleManager.getInstance().notify(
            bleDevice,
            uuid_service, // Example: UUID for Battery Service
            uuid_notify, // Example: UUID for Battery Level Characteristic
            object : BleNotifyCallback() {
                override fun onNotifySuccess() {
                    receiveText.append("Notify started\n")
                }

                override fun onNotifyFailure(exception: BleException?) {
                    receiveText.append("Notify failed: ${exception?.description}\n")
                }

                override fun onCharacteristicChanged(data: ByteArray?) {
                    val receivedData = data?.joinToString(" ") { byte -> String.format("%02X", byte) }
                    receiveText.append("Received: $receivedData\n")
                }
            })
    }

    private fun startOtaUpdate() {
        // 开始 OTA 更新逻辑，使用 OtaUpdateManager
        OtaUpdateManager.startOtaProcess(bleDevice, receiveText)
    }
}
