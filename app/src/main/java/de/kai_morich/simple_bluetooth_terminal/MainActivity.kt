package de.kai_morich.simple_bluetooth_terminal

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresApi

class MainActivity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {


     lateinit var bleOtaManager: BleOtaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportFragmentManager.addOnBackStackChangedListener(this)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, DevicesFragment(), "devices").commit()
        else onBackStackChanged()

        checkPer()
    }

    override fun onBackStackChanged() {
        supportActionBar!!.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


    @SuppressLint("WrongConstant")
    private fun checkPer() {
        val locationPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }

        if (!hasPermissions(locationPermission)) {
            requestPermissions(locationPermission, 1)
        } else {

            // 初始化 BLE 管理器
//            bleOtaManager = BleOtaManager(this)



            OtaUpdateManager.init(this@MainActivity)
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (this@MainActivity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
    fun navigateToTerminalFragment(address: String) {
        val fragment = TerminalFragment()
        val args = Bundle()
        args.putString("device_address", address)
        fragment.arguments = args

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }




    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                OtaUpdateManager.init(this@MainActivity)
//                bleOtaManager = BleOtaManager(this)

            } else {
                Log.e(OtaUpdateManager.TAG, "Permission denied!")
            }
        }
    }


}