package de.kai_morich.simple_bluetooth_terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice

class DevicesFragment : Fragment() {

    private lateinit var listView: ListView
    private var devicesList: MutableList<BleDevice> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_devices, container, false)
        listView = view.findViewById(R.id.listView)

        startScan()

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedDevice = devicesList[position]
            val fragment = TerminalFragment()
            val args = Bundle()
            args.putString("device", selectedDevice.mac) // 将设备地址传递到 TerminalFragment
            fragment.arguments = args
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    private fun startScan() {
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanFinished(scanResultList: List<BleDevice>) {
                devicesList.clear()
                devicesList.addAll(scanResultList)
                // 更新列表适配器
            }

            override fun onScanStarted(success: Boolean) {


            }

            override fun onScanning(bleDevice: BleDevice?) {
                // 更新 UI 显示扫描中的设备
            }
        })
    }
}
