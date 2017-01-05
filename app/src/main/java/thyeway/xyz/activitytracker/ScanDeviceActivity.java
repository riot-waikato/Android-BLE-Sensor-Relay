package thyeway.xyz.activitytracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

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

    // result codes
    private static final int REQUEST_ENABLE_BT = 1;

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
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // check that device supports Bluetooth
        if (mBluetoothAdapter == null) {

            Log.i(TAG, "No Bluetooth adapter could be found.");

            // display error message as toast.
            Context context = getApplicationContext();
            CharSequence message = getResources().getString(R.string.bluetooth_unavailable_message);
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, message, duration);
            toast.show();
        }

        // check that Bluetooth is enabled
        if (!mBluetoothAdapter.isEnabled()) {

            Log.i(TAG, "Bluetooth is disabled.");

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {

            // initialize an adapter and link it to the listview to display list of bluetooth devices
            mLeBluetoothDevicesAdapter = new BluetoothDevicesAdapter(this, R.layout.bluetooth_device_info);
            ListView listView = (ListView) findViewById(R.id.listViewBLEDevices);
            listView.setAdapter(mLeBluetoothDevicesAdapter);

            // begin scanning devices
            scanDevices();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // result from a request to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT) {

            if (resultCode == RESULT_OK) {

                Log.i(TAG, "User enabled Bluetooth.");

            } else {

                Log.i(TAG, "User refused to enable Bluetooth.");

                // Display error message as toast
                Context context = getApplicationContext();
                CharSequence message = getResources().getString(R.string.bluetooth_disabled_message);
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, message, duration);
                toast.show();
            }
        }
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
            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                    broadcastReceiver, new IntentFilter("service-status"));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSensorLoggingService = null;
            mBound = false;
        }
    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int type = intent.getIntExtra("status-type", 1);
            String message = intent.getStringExtra("status-message");

            TextView status = (TextView) findViewById(R.id.textViewStatus);

            if(status.getVisibility() != View.VISIBLE) {
                status.setVisibility(View.VISIBLE);
            }

            if(type == Status.STATUS_OK) {
                status.setBackgroundColor(getResources().getColor(R.color.status_ok));
            } else if(type == Status.STATUS_WARNING) {
                status.setBackgroundColor(getResources().getColor(R.color.status_warning));
            } else if(type == Status.STATUS_ERROR) {
                status.setBackgroundColor(getResources().getColor(R.color.status_error));
            }
            status.setText(message);
        }
    };

    /**
     * Custom adapter class to display list of available bluetooth le devices
     */
    class BluetoothDevicesAdapter extends ArrayAdapter<BluetoothDevice> {

        // an array for available devices and selected devices
        private ArrayList<Sensor> mLeDevices;       // TODO: RENAME
        private ArrayList<Sensor> mLeDevicesSelected;
        private LayoutInflater mInflater;

        public BluetoothDevicesAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            this.mLeDevices = new ArrayList<Sensor>();
            this.mLeDevicesSelected = new ArrayList<Sensor>();
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
            boolean exist = false;
            for(Sensor sensor : mLeDevices) {
                if(sensor.device_mac.equalsIgnoreCase(device.getAddress())) {
                    exist = true;
                    break;
                }
            }

            if (!exist) {
                device.connectGatt(getApplicationContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        }

        private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    List<BluetoothGattService> gattServices = gatt.getServices();

                    // for each service, get all its characteristics and add it to the queue
                    for (BluetoothGattService s : gattServices) {
                        Log.i(TAG, "SERVICE: " + s.getUuid());
                        SensorFactory sensorFactory = new SensorFactory();
                        Sensor newSensor = sensorFactory.getSensor(s.getUuid().toString(), gatt.getDevice().getName(), gatt.getDevice().getAddress());
                        if(newSensor != null) {
                            Log.i(TAG, "Valid sensor: " + newSensor.getClass().toString());
                            for(Sensor sensor : mLeDevices) {
                                if(sensor.device_mac.equalsIgnoreCase(newSensor.device_mac)) {
                                    return;
                                }
                            }
                            mLeDevices.add(newSensor);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    notifyDataSetChanged();
                                }
                            });
                            break;
                        }
                    }
                    gatt.disconnect();
                }

            }
        };

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        public ArrayList<Sensor> getSelected() {
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

            final Sensor sensor = mLeDevices.get(position);

            // set device name and MAC address
            final String deviceName = sensor.device_name;
            if (deviceName != null && deviceName.length() > 0) {
                holder.deviceName.setText(deviceName);
            } else {
                holder.deviceName.setText(R.string.error_unknown_device);
            }
            holder.deviceAddress.setText(sensor.device_mac);

            // add and remove from array when checkbox is checked or unchecked
            holder.selected.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mLeDevicesSelected.contains(sensor)) {
                        mLeDevicesSelected.remove(sensor);
                    } else {
                        mLeDevicesSelected.add(sensor);
                    }
                }
            });

            // starts the ViewDevice activity
            // pass the MAC address of the device to the activity
            holder.deviceInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getContext(), ViewDeviceActivity.class);
                    intent.putExtra("address", sensor.device_mac);
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }

}
