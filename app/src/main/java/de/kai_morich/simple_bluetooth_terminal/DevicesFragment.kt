package de.kai_morich.simple_bluetooth_terminal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.ListFragment
import com.clj.fastble.BleManager
import com.clj.fastble.data.BleDevice
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.exception.BleException

class DevicesFragment : ListFragment() {

    private lateinit var listAdapter: ArrayAdapter<BleDevice>
    private val listItems = mutableListOf<BleDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        listAdapter = object : ArrayAdapter<BleDevice>(requireContext(), 0, listItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val device = getItem(position)
                val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
                val text1 = view.findViewById<TextView>(R.id.text1)
                val text2 = view.findViewById<TextView>(R.id.text2)

                text1.text = device?.name ?: "Unknown Device"
                text2.text = device?.mac

                return view
            }
        }

        startScan()

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_devices, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.bt_refresh -> {
                startScan()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter(listAdapter)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val device = listItems[position]
        val args = Bundle().apply {
            putString("device_address", device.mac)
        }
        val terminalFragment = TerminalFragment()
        terminalFragment.arguments = args
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment, terminalFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun startScan() {
        listItems.clear()
        listAdapter.notifyDataSetChanged()

        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                Log.d("BleScan", "Scan started: $success")
            }


            override fun onScanFinished(scanResultList: List<BleDevice>) {
                Log.d("BleScan", "Scan finished")
            }

            override fun onScanning(bleDevice: BleDevice?) {
                bleDevice?.let {
                    if (!listItems.contains(it)) {
                        listItems.add(it)
                        listAdapter.notifyDataSetChanged()
                    }
                }
            }

        })
    }
}
