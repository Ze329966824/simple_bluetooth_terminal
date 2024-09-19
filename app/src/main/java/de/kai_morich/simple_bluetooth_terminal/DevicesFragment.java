package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import java.util.ArrayList;
import java.util.Collections;

public class DevicesFragment extends ListFragment {

    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<BluetoothDevice> listItems = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> listAdapter;
    ActivityResultLauncher<String> requestBluetoothPermissionLauncherForRefresh;
    private Menu menu;
    private boolean permissionMissing;

    private static final String TAG = "PduBleManager";  // 用于日志调试

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // 检查设备是否支持蓝牙功能
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        // 初始化适配器
        listAdapter = new ArrayAdapter<BluetoothDevice>(getActivity(), 0, listItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                BluetoothDevice device = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);

                @SuppressLint("MissingPermission") String deviceName = device.getName();
                text1.setText(deviceName);
                text2.setText(device.getAddress());
                return view;
            }
        };

        // 请求权限
        requestBluetoothPermissionLauncherForRefresh = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> BluetoothUtil.onPermissionsResult(this, granted, this::refresh));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);

        // 设置列表头部
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("initializing...");
        ((TextView) getListView().getEmptyView()).setTextSize(18);

        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        this.menu = menu;
        inflater.inflate(R.menu.menu_devices, menu);

        if (permissionMissing)
            menu.findItem(R.id.bt_refresh).setVisible(true);

        if (bluetoothAdapter == null)
            menu.findItem(R.id.bt_settings).setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();  // 在 onResume 中刷新设备列表
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.bt_settings) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
            return true;
        } else if (id == R.id.bt_refresh) {
            if (BluetoothUtil.hasPermissions(this, requestBluetoothPermissionLauncherForRefresh))
                refresh();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("MissingPermission")
    void refresh() {
        Log.d(TAG, "Refreshing Bluetooth device list...");

        listItems.clear();  // 清空设备列表
        if (bluetoothAdapter != null) {
            // 检查是否为 Android 12 (API 31) 或更高版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionMissing = getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
                if (menu != null && menu.findItem(R.id.bt_refresh) != null)
                    menu.findItem(R.id.bt_refresh).setVisible(permissionMissing);
            } else {
                permissionMissing = false;  // Android 12 以下版本不需要 BLUETOOTH_CONNECT 权限
            }

            if (!permissionMissing) {
                if (!bluetoothAdapter.isEnabled()) {
                    Log.w(TAG, "Bluetooth is disabled.");
                } else {
                    // 获取已配对的设备
                    for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                        Log.d(TAG, "Found bonded device: " + device.getName() + " [" + device.getAddress() + "]");
                        if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE)
                            listItems.add(device);
                    }

                    // 按名称排序
                    Collections.sort(listItems, BluetoothUtil::compareTo);
                }
            }
        }

        if (bluetoothAdapter == null) {
            setEmptyText("<bluetooth not supported>");
        } else if (!bluetoothAdapter.isEnabled()) {
            setEmptyText("<bluetooth is disabled>");
        } else if (permissionMissing) {
            setEmptyText("<permission missing, use REFRESH>");
        } else {
            setEmptyText("<no bluetooth devices found>");
        }

        listAdapter.notifyDataSetChanged();  // 更新列表
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        BluetoothDevice device = listItems.get(position - 1);  // 获取点击的设备
        Bundle args = new Bundle();
        args.putString("device", device.getAddress());
        Fragment fragment = new TerminalFragment();
        fragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
    }
}
