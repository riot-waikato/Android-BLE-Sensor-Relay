package thyeway.xyz.activitytracker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
 * MainActivity - main activity
 * Scans and displays a list of detected bluetooth le devices
 */
public class MainActivity extends AppCompatActivity {

    // debug use
    private final String TAG = MainActivity.class.getSimpleName();

    BluetoothConnectionManager mBluetooth;

    // interface between UI and array of Bluetooth devices
    private BluetoothDeviceArrayAdapter mLeBluetoothDeviceArrayAdapter;

    private SensorLoggingService mSensorLoggingService;

    private Handler mHandler;

    private boolean mBound = false;

    // how long to scan, in milliseconds
    private static final long SCAN_PERIOD = 1000000;

    // result codes
    public static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_COARSE_LOCATION = 2;

    // scheduled jobs
    BLEScanTask bleJob;
    final int BLE_JOB_ID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // TODO: Check for permissions (bluetooth and location)
        // TODO: Check if bluetooth or location service is available

        mHandler = new Handler();

        mLeBluetoothDeviceArrayAdapter = new BluetoothDeviceArrayAdapter(this, R.id.listViewBLEDevices);
        checkLocationPermission();

        initializeBluetooth();

        if (mBluetooth.isSupported()) {

            mBluetooth.updatePairedDevices();
        }
    }

    protected void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_COARSE_LOCATION);
        } else {
            Log.d(TAG,"Have coarse location permission.");
        }
    }

    /**
     * 1. Initializes the Bluetooth adapter.
     * 2. Checks that Bluetooth is supported on the device (error toast if not).
     * 3. Checks Bluetooth is enabled (prompts to enable if not).
     * 4. Registers BroadcastReceiver to listen for Bluetooth adapter state changes.
     */
    protected void initializeBluetooth() {

        mBluetooth = new BluetoothConnectionManager(this, mLeBluetoothDeviceArrayAdapter);

        // if adapter is null then Bluetooth is not supported on this device
        if (mBluetooth.isSupported()) {

            // check Bluetooth is enabled
            Log.d(TAG, "Bluetooth is supported.");

            if (mBluetooth.isEnabled()) {

            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

                // initialize an adapter for the list of Bluetooth devices
                // this can be done even if Bluetooth is not yet enabled
                mLeBluetoothDeviceArrayAdapter = new BluetoothDeviceArrayAdapter(this, R.layout.bluetooth_device_info);
                ListView listView = (ListView) findViewById(R.id.listViewBLEDevices);
                listView.setAdapter(mLeBluetoothDeviceArrayAdapter);
            }

        } else {

            // device does not support Bluetooth
            Log.e(TAG, "Bluetooth is not supported on this device..");

            // display error message as toast.
            Context context = getApplicationContext();
            CharSequence message = getResources().getString(R.string.bluetooth_unavailable_message);
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, message, duration);
            toast.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Coarse location permission granted.");
                } else {
                    Log.i(TAG, "Coarse location permission refused.");
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // request to enable bluetooth
        if (requestCode == REQUEST_ENABLE_BT) {

            if (resultCode == RESULT_OK) {

                Log.i(TAG, "User enabled Bluetooth.");

            } else {

                Log.e(TAG, "User refused to enable Bluetooth.");

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
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mSensorLoggingService = null;

        mBluetooth.close(this);
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
     * Callback interface used to deliver LE scan results
     * Populates the list when a bluetooth device is detected
     */
    private ScanCallback mLeScanCallBack = new ScanCallback() {
        public void onScanResult(int callbackType, final ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeBluetoothDeviceArrayAdapter.add(result.getDevice());
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
        mBluetooth.scanLeDevice(false);

        // pass the list of selected derive to the logging service to begin data logging
        mSensorLoggingService.track(mLeBluetoothDeviceArrayAdapter.getSelected());
    }

    /**
     * On-click event for the SCAN button.
     * Begins a BLE scan for devices.
     * @param view
     */
    public void onClickScanButton(View view) {
        mBluetooth.scanLeDevice(true);
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


}
