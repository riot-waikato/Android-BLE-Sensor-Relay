package thyeway.xyz.activitytracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.Set;

/**
 * Bluetooth Manager for the RIOT app. Not called "BluetoothManager" mostly because that class
 * already exists in Android.
 */
public class BluetoothConnectionManager {

    public final String TAG = "BluetoothManager";

    // The BluetoothAdapter state most recently seen by the broadcast receiver;
    private BluetoothAdapter mAdapter;
    private int mAdapterState;

    public static final int UNDEFINED_ADAPTER_STATE = -1;

    private Set<BluetoothDevice> pairedDevices;

    /**
     * Initializes variables and registers a state change broadcast receiver.
     * @param context
     */
    public BluetoothConnectionManager(Context context) {
        // get the default bluetooth adapter for this device
        BluetoothManager bluetoothManager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        mAdapter = bluetoothManager.getAdapter();

        if (isSupported()) {
            mAdapterState = mAdapter.getState();
        } else {
            mAdapterState = UNDEFINED_ADAPTER_STATE;
        }

        // register for broadcasts about Bluetooth adapter state changes
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);

        // register for broadcasts about Bluetooth devices found
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Performs cleanup operations for this object. Should be called in the main Activity.
     * @param context
     */
    public void close(Context context) {
        context.unregisterReceiver(mBroadcastReceiver);
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
}
