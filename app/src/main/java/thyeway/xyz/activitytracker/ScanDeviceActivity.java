package thyeway.xyz.activitytracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * ScanDeviceActivity - main activity
 * Scans and displays a list of detected bluetooth le devices
 */
public class ScanDeviceActivity extends AppCompatActivity {

    // debug use
    private final String TAG = "ScanDeviceActivity";

    private BluetoothDevicesAdapter mLeBluetoothDevicesAdapter;
    private BluetoothAdapter mBluetoothAdapter;

    private SensorLoggingService mSensorLoggingService;

    private Handler mHandler;

    private boolean mBound = false;

    // how long to scan, in milliseconds
    private static final long SCAN_PERIOD = 1000000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // TODO: Check for permissions (bluetooth and location)
        // TODO: Check if bluetooth enabled
        // TODO: Check if bluetooth or location service is available

        mHandler = new Handler();

        // get the default bluetooth adapter for this device
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // initialize an adapter and link it to the listview to display list of bluetooth devices
        mLeBluetoothDevicesAdapter = new BluetoothDevicesAdapter(this, R.layout.bluetooth_device_info);
        ListView listView = (ListView) findViewById(R.id.listViewBLEDevices);
        listView.setAdapter(mLeBluetoothDevicesAdapter);

        // begin scanning devices
        scanDevices();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // bind to service
        bindService(new Intent(this, SensorLoggingService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mSensorLoggingService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent settings = new Intent(this, SettingsActivity.class);
                startActivity(settings);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Scan for bluetooth devices
     * first create a runnable to stop the scan after the pre-defined SCAN_PERIOD,
     * only then we start scanning for bluetooth devices
     */
    private void scanDevices() {
        // stops scanning after a pre-defined scan period
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mLeScanCallBack);
            }
        }, SCAN_PERIOD);

        // note: we can also scan for device that advertises certain services
        // we can do that using startLeScan(UUID[], callback);
        mBluetoothAdapter.startLeScan(mLeScanCallBack);
    }

    /**
     * Callback interface used to deliver LE scan results
     * Populates the list when a bluetooth device is detected
     */
    private LeScanCallback mLeScanCallBack = new LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeBluetoothDevicesAdapter.add(bluetoothDevice);
                }
            });
        }
    };

    /**
     * onClick event for track button
     * gets the selected devices and launches a service to track these devices in background
     *
     * @param view
     */
    public void track(View view) {
        // stop scanning first
        mBluetoothAdapter.stopLeScan(mLeScanCallBack);

        // pass the list of selected derive to the logging service to begin data logging
        mSensorLoggingService.track(mLeBluetoothDevicesAdapter.getSelected());
    }

    /**
     * Interface for monitoring the state of the logging service
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mSensorLoggingService = ((SensorLoggingService.LocalBinder) iBinder).getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSensorLoggingService = null;
            mBound = false;
        }
    };

    /**
     * Custom adapter class to display list of available bluetooth le devices
     */
    class BluetoothDevicesAdapter extends ArrayAdapter<BluetoothDevice> {

        // an array for available devices and selected devices
        private ArrayList<BluetoothDevice> mLeDevices;
        private ArrayList<BluetoothDevice> mLeDevicesSelected;
        private LayoutInflater mInflater;

        public BluetoothDevicesAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            this.mLeDevices = new ArrayList<BluetoothDevice>();
            this.mLeDevicesSelected = new ArrayList<BluetoothDevice>();
            this.mLeDevices.addAll(mLeDevices);
            mInflater = ScanDeviceActivity.this.getLayoutInflater();
        }

        private class ViewHolder {
            TextView deviceName;
            TextView deviceAddress;
            CheckBox selected;
            ImageButton deviceInfo;
        }

        @Override
        public void add(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        public ArrayList<BluetoothDevice> getSelected() {
            return mLeDevicesSelected;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.bluetooth_device_info, null);
                holder = new ViewHolder();
                holder.deviceAddress = (TextView) convertView.findViewById(R.id.textViewDeviceMACAddress);
                holder.deviceName = (TextView) convertView.findViewById(R.id.textViewDeviceName);
                holder.selected = (CheckBox) convertView.findViewById(R.id.checkBoxSelectDevice);
                holder.deviceInfo = (ImageButton) convertView.findViewById(R.id.imageButtonDeviceInfo);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final BluetoothDevice device = mLeDevices.get(position);

            // set device name and MAC address
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                holder.deviceName.setText(deviceName);
            } else {
                holder.deviceName.setText(R.string.error_unknown_device);
            }
            holder.deviceAddress.setText(device.getAddress());

            // add and remove from array when checkbox is checked or unchecked
            holder.selected.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mLeDevicesSelected.contains(device)) {
                        mLeDevicesSelected.remove(device);
                    } else {
                        mLeDevicesSelected.add(device);
                    }
                }
            });

            // starts the ViewDevice activity
            // pass the MAC address of the device to the activity
            holder.deviceInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getContext(), ViewDeviceActivity.class);
                    intent.putExtra("address", device.getAddress());
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }

}
