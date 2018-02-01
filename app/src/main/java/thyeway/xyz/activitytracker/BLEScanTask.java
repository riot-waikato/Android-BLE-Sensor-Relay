package thyeway.xyz.activitytracker;

import android.app.job.JobInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

/**
 * BLEScanTask is responsible for managing connections with and transferring data from paired
 * RIOT devices.
 *
 * It communicates with the main Activity using broadcasts. The reason it is not a bound service is
 * because this Bluetooth service is intended to run indefinitely in the background once started.
 */

public class BLEScanTask extends AsyncTask<Void, Void, Void> {

    private final String TAG = "BLEScanTask";
    private final int JOB_PERIOD_MS = 1000;
    private final int SCAN_PERIOD_MS = 1000;

    private BluetoothDeviceArrayAdapter mLeBluetoothDevicesArrayAdapter;
    private BluetoothAdapter mBluetoothAdapter;

    protected JobInfo jobInfo;

    // Bluetooth scan handler
    private Handler mScanHandler;

    // Setup job scheduling. Main activity must provide a reference to its context and a job ID.
    BLEScanTask(Context context, int jobID) {
        ComponentName bleComponentName = new ComponentName(context, BLEScanTask.class);

        // setup job info for a periodic job
        JobInfo.Builder builder = new JobInfo.Builder(jobID, bleComponentName);
        builder.setPeriodic(JOB_PERIOD_MS);
        jobInfo = builder.build();

        Log.d(TAG, "Created job.");

    }

    /**
     * Scan for bluetooth devices
     * first create a runnable to stop the scan after the pre-defined SCAN_PERIOD,
     * only then we start scanning for bluetooth devices
     */
    private void beginScan() {

        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        // stops scanning after a pre-defined scan period
        mScanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallBack);
            }
        }, SCAN_PERIOD_MS);

        // note: we can also scan for device that advertises certain services
        // we can do that using startLeScan(UUID[], callback);
        mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanCallBack);
    }

    /**
     * Sends discovered devices to the main Activity thread.
     */
    private ScanCallback mLeScanCallBack = new ScanCallback() {
        public void onScanResult(int callbackType, final ScanResult result) {
        }
    };

    @Override
    protected Void doInBackground(Void... voids) {
        return null;
    }
}
