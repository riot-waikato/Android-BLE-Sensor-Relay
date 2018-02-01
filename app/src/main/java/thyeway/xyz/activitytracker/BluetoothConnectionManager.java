package thyeway.xyz.activitytracker;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * Bluetooth Manager for the RIOT app. Not called "BluetoothManager" mostly because that class
 * already exists in Android.
 *
 * Uses the Nordic Semiconductor Android-Scanner-Compat-Library to handle different versions of
 * the Android API for Bluetooth LE as the API has changed several times (e.g. API 21 and 23).
 */
public class BluetoothConnectionManager {

    public final String TAG = "BluetoothManager";

    // The BluetoothAdapter state most recently seen by the broadcast receiver;
    private BluetoothAdapter mAdapter;
    private int mAdapterState;

    // The state when Bluetooth is not supported
    public static final int UNDEFINED_ADAPTER_STATE = -1;

    private Set<BluetoothDevice> pairedDevices;

    private Context mContext;
    private Handler mHandler;

    // Parameters for LE scans
    private static final long SCAN_PERIOD = 10000;
    private boolean mScanning;

    // Allows automatic updating of the UI as devices are found.
    private BluetoothDeviceArrayAdapter mArrayAdapter;

    /**
     * Initializes variables and registers a state change broadcast receiver.
     * @param context
     */
    public BluetoothConnectionManager(Context context, BluetoothDeviceArrayAdapter arrayAdapter) {

        mContext = context;
        mHandler = new Handler();
        mArrayAdapter = arrayAdapter;

        // get the default bluetooth adapter for this device
        BluetoothManager bluetoothManager = (BluetoothManager)mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mAdapter = bluetoothManager.getAdapter();

        if (isSupported()) {
            mAdapterState = mAdapter.getState();
        } else {
            mAdapterState = UNDEFINED_ADAPTER_STATE;
        }

        // register for broadcasts about Bluetooth adapter state changes
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        // register for broadcasts about Bluetooth devices found
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Performs cleanup operations for this object. Should be called in the main Activity.
     */
    public void close() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    // receives broadcasts about state changes in the Bluetooth Adapter and devices found during
    // scans
    public final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                mAdapterState = state;

                Log.i(TAG, "Bluetooth adapter state changed: " + state);

            } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();

                Log.i(TAG, "Discovered device: (" + deviceName + ", " + deviceHardwareAddress + ")");
            }
        }
    };

    /**
     * Gets the current Bluetooth adapter state. The return value is equal to a
     * BluetoothAdapter.STATE_* value.
     * @return An integer representing the state of the Bluetooth adapter.
     */
    public int getAdapterState() {
        return mAdapterState;
    }

    public void setAdapterState(int state) {
        mAdapterState = state;
    }

    public BluetoothAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Updates the list of already known devices.
     */
    public void updatePairedDevices() {
        pairedDevices = mAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, "Found paired device: (" +
                        device.getName() +
                        ", " +
                        device.getAddress() +
                        ")");
            }
        } else {
            Log.d(TAG, "Did not find any paired devices.");
        }
    }

    /**
     * Determines if Bluetooth is supported on the device.
     * @return True if Bluetooth is supported.
     */
    public boolean isSupported() {
        return mAdapter != null;
    }

    public boolean isEnabled() {
        return mAdapter.isEnabled();
    }

    /**
     * Changes scanning status to match the boolean argument. If scanning begins, it will continue
     * for SCAN_PERIOD ms.
     * @param enable
     */
    public void scanLeDevice(final boolean enable) {

        if (enable) {
            // Automatically stops scan after SCAN_PERIOD ms.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    stopLeScan();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            startLeScan();

        } else {
            mScanning = false;
            stopLeScan();
        }
    }

    /**
     * Starts a Bluetooth LE scan using the most up-to-date function possible on the device.
     */
    private void startLeScan() {
        Log.i(TAG, "Beginning Bluetooth LE scan.");
        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false)
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        // TODO: Add filters for RIOT UUIDs.
        scanner.startScan(filters, settings, new LollipopScanCallback());
    }

    /**
     * Stops a Bluetooth LE scan using the most up-to-date function possible on the device.
     */
    private void stopLeScan() {
        Log.i(TAG, "Stopping Bluetooth LE scan.");
        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.stopScan(new LollipopScanCallback());
    }

    /**
     * Callback functions for handling devices found during LE scans.
     */
    class LollipopScanCallback extends ScanCallback {
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(TAG, "Found "
                    + results.size()
                    + " Bluetooth devices.");
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Could not start Le scan. Error code = "
                    + errorCode);
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mArrayAdapter.add(result.getDevice());
        }
    }

    /**
     * Starts a connection to the GATT server on a Bluetooth device. Connection is established when
     * the GattCallback is triggered.
     *
     * The connectGatt function has been changed multiple times between API levels 18 to 26, so
     * the best one available given the device's API level is called.
     * @param device
     */
    public void connectGatt(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothGatt gatt = device.connectGatt(mContext, false, new GattCallback(), BluetoothDevice.TRANSPORT_LE);
        } else {
            BluetoothGatt gatt = device.connectGatt(mContext, false, new GattCallback());
        }
    }

    class GattCallback extends BluetoothGattCallback {
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

                        // Check that the given context can be cast to an Activity so that
                        // runOnUiThread can be called.
                        if (mContext instanceof Activity) {
                            Activity activity = (Activity)mContext;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    notifyDataSetChanged();
                                }
                            });
                        }
                        break;
                    }
                }
                gatt.disconnect();
            }

        }
    }
}
