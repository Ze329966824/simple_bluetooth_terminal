package de.kai_morich.simple_bluetooth_terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice

class DevicesFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var refreshButton: Button
    private lateinit var listAdapter: ArrayAdapter<String>
    private val devicesList = ArrayList<BleDevice>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_devices, container, false)

        listView = view.findViewById(R.id.listView)
        refreshButton = view.findViewById(R.id.refresh_button)

        listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ArrayList<String>())
        listView.adapter = listAdapter

        refreshButton.setOnClickListener {
            startScan()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = devicesList[position]
            val address = selectedDevice.mac
            (activity as MainActivity).navigateToTerminalFragment(address)
        }

        // Initial scan
        startScan()

        return view
    }

    private fun startScan() {
        listAdapter.clear()
        devicesList.clear()
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                Toast.makeText(requireContext(), "Scan started", Toast.LENGTH_SHORT).show()
            }

            override fun onScanning(bleDevice: BleDevice) {
                devicesList.add(bleDevice)
                listAdapter.add("${bleDevice.name ?: "Unknown"} (${bleDevice.mac})")
            }

            override fun onScanFinished(scanResultList: List<BleDevice>) {
                Toast.makeText(requireContext(), "Scan finished", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
