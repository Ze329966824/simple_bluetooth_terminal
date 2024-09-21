package de.kai_morich.simple_bluetooth_terminal

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BleOtaManager(private val context: Context, private val deviceFoundCallback: (BluetoothDevice) -> Unit) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var step = 0
    private val TAG = "BleOtaManager"
    private var isOtaInProgress = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanResults: MutableList<BluetoothDevice> = mutableListOf()
    private var scanning = false

    companion object {
        // 使用自定义 UUID
        val UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val UUID_WRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        const val MAX_MTU = 20 // 设置每次发送的最大字节数
    }


    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }


    fun startScan() {
        if (scanning) return
        scanning = true
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
        Log.d(TAG, "Started BLE scan")
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped BLE scan")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                deviceFoundCallback(device) // 当找到设备时调用回调，将设备传递给外部处理
            }
        }

        override fun onBatchScanResults(results: List<ScanResult?>) {
            results.forEach { result ->
                result?.device?.let { device ->
                    deviceFoundCallback(device) // 处理批量扫描结果
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }
    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                characteristic = gatt?.getService(UUID_SERVICE)?.getCharacteristic(UUID_WRITE)
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful")
            } else {
                Log.e(TAG, "Characteristic write failed with status: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val receivedData = characteristic?.value
            Log.d(TAG, "Received data: ${receivedData?.contentToString()}")
        }
    }

    // 分段发送
    private fun sendDataInChunks(data: ByteArray) {
        val totalPackets = (data.size + MAX_MTU - 1) / MAX_MTU
        for (i in 0 until totalPackets) {
            val start = i * MAX_MTU
            val end = minOf(start + MAX_MTU, data.size)
            val chunk = data.copyOfRange(start, end)
            characteristic?.value = chunk
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    // 模拟步骤 0 的指令发送
    fun sendCommand55() {
        val command = byteArrayOf(0x55, 0x36, 0xAA.toByte())
        sendDataInChunks(command)
    }

    // 模拟步骤 1 的指令发送
    fun sendOtaStart() {
        val otaStartCommand = byteArrayOf(
            0xAA.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
        )
        sendDataInChunks(otaStartCommand)
    }

    // 模拟步骤 2 的指令发送
    fun sendOtaHeader() {
        val otaHeaderCommand = byteArrayOf(
            0xAA.toByte(), 0x02.toByte(), 0x10.toByte(), 0x00.toByte(),
            0x7C.toByte(), 0x42.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xF0.toByte(), 0xCA.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x07.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
        )
        sendDataInChunks(otaHeaderCommand)
    }

    // 模拟步骤 3 的指令发送
    fun sendOtaData() {
        val otaDataCommand = byteArrayOf(
            0xAA.toByte(), 0x01.toByte(), 0x05.toByte(), 0x00.toByte(),
            0x68.toByte(), 0x65.toByte(), 0x6C.toByte(), 0x6C.toByte(), 0x6F.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
        )
        sendDataInChunks(otaDataCommand)
    }

    // 模拟步骤 4 的指令发送
    fun sendOtaEnd() {
        val otaEndCommand = byteArrayOf(
            0xAA.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte()
        )
        sendDataInChunks(otaEndCommand)
    }
}
