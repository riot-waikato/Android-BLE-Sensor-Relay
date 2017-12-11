package thyeway.xyz.activitytracker;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static thyeway.xyz.activitytracker.DatabaseContract.DatabaseEntry;

/**
 * Logging service
 * <p/>
 * Keep tracks of bluetooth device(s), relays sensor data to access points (Pi)
 * If the connection between the Pi and the phone goes down, store them in the
 * database locally, while waiting for the connection to be back up. As soon
 * as the connection is re-established, send all the locally stored data to
 * the Pi.
 */
public class SensorLoggingService extends Service {

    // debug use
    private final String TAG = "LoggingService";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private ArrayList<Sensor> mSensors;

    private IBinder mBinder;

    private DatabaseHelper dbHelper;

    private boolean inRange;
    private Runnable verify_connection;

    // how frequent to read data from sensor(s), in milliseconds
    private final int mDelay = 5000;

    private String HOST;      // TODO: automatically detect access point
    private int PORT;
    private final int VERIFY_CONNECTION_INTERVAL = 1000;
    private static int ONGOING_NOTIFICATION_ID = 10;

    @Override
    public void onCreate() {
        super.onCreate();

        mBinder = new LocalBinder();

        // TODO: Stop tracking from notification
        //Intent intent = new Intent(this, SensorLoggingService.class);
        //PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // we want to make sure that the tracking goes on and does not get destroyed when:
        // 1: device (phone) is low on memory
        // 2: user explicitly kills the application
        // to do this we need to use startForeground
        // TODO: Handle destroying service gracefully
        NotificationCompat.Builder notificationBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_info_outline_black_24px)
                        .setContentTitle(getText(R.string.app_name))
                        .setContentText("Bluetooth device tracking is ON!");

        Notification notification = notificationBuilder.build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);

        // initialize bluetooth manager
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothConnectionManager.");
                this.stopSelf();
            }
        }

        // initialize bluetooth adapter
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            this.stopSelf();
        }

        dbHelper = new DatabaseHelper(this);
        inRange = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        SensorLoggingService getService() {
            return SensorLoggingService.this;
        }
    }

    private void setStatus(int status_type, String status_message) {
        Intent intent = new Intent("service-status");
        intent.putExtra("status-message", status_message);
        intent.putExtra("status-type", status_type);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Process a list of devices to be tracked
     *
     * @param sensors list of sensors to be tracked
     */
    public void track(final ArrayList<Sensor> sensors) {

        Log.i(TAG, "Checking if network is connected to a valid access point");
        if (!NetworkUtil.isConnected(this)) {
            Log.i(TAG, "Nope, Connecting...");
            setStatus(Status.STATUS_WARNING, "Connecting to access point...");
            NetworkUtil.connectAccessPoint(this);
        } else {
            setStatus(Status.STATUS_WARNING, "Connected to access point");
        }

        // get the host and port to connect to from settings
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        HOST = preferences.getString(getResources().getString(R.string.preference_host), HOST);
        PORT = Integer.parseInt(preferences.getString(getResources().getString(R.string.preference_port), getResources().getString(R.string.default_port)));

        mSensors = new ArrayList<>(sensors);

        for (Sensor sensor : mSensors) {
            Log.i(TAG, "Tracking : " + sensor.device_mac);
            final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(sensor.device_mac);
//                mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
            device.connectGatt(getApplicationContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        }

        // TODO: Feature/handler to stop tracking
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        private Sensor getSensor(String device_mac) {
            for (Sensor sensor : mSensors) {
                if (sensor.device_mac.equalsIgnoreCase(device_mac)) {
                    return sensor;
                }
            }
            return null;
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setStatus(Status.STATUS_OK, gatt.getDevice().getAddress() + " connected");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnect: " + gatt.getDevice().getAddress());
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.i(TAG, "onServiceDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Sensor sensor = getSensor(gatt.getDevice().getAddress());

                List<BluetoothGattService> gattServices = gatt.getServices();
                for (BluetoothGattService gattService : gattServices) {
                    if (gattService.getUuid().toString().equalsIgnoreCase(UUIDs.LUX_SERVICE)) {

                        List<BluetoothGattCharacteristic> characteristics = gattService.getCharacteristics();
                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            for (String UUID : sensor.gatt_characteristics) {
                                if (characteristic.getUuid().toString().equalsIgnoreCase(UUID)) {
                                    sensor.addToQueue(characteristic);
                                    Log.i(TAG, "Add to queue: " + characteristic.getUuid().toString());
                                }
                            }
                        }
                    }
                }

                Runnable discoverService = new Runnable() {
                    @Override
                    public void run() {
                        Sensor sensor = getSensor(gatt.getDevice().getAddress());
                        if (sensor.readReady()) {
                            sensor.prepareReadQueue();
                            gatt.readCharacteristic(sensor.getQueueNext());
                        }
                    }
                };

                ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
                executorService.scheduleAtFixedRate(discoverService, 0, 1, TimeUnit.SECONDS);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Sensor sensor = getSensor(gatt.getDevice().getAddress());
                sensor.updateData(characteristic.getUuid().toString(), characteristic.getValue());

                BluetoothGattCharacteristic next = sensor.getQueueNext();
                if (next != null) {
                    gatt.readCharacteristic(next);
                } else {
                    if (Integer.parseInt(sensor.sequence_number) != 0) {
                        new RelayData().execute(sensor.createPacket(Long.toString(System.currentTimeMillis() / 1000)));
                    } else {
                        setStatus(Status.STATUS_OK, "ESP8266 Thing working...");
                        Log.i(TAG, "Dropping packet");
                    }
                    sensor.emptyData();
                }
            }
        }
    };

    /**
     * Spawn a process to check if the connection has been restored
     */
    private void verify_connection() {
        final Handler handler = new Handler();

        verify_connection = new Runnable() {
            @Override
            public void run() {
                VerifyConnection verify = new VerifyConnection(new TaskCallback() {
                    @Override
                    public void taskStatus(boolean status) {
                        if (status == false) {
                            // connection is still down, try again within a specified interval
                            handler.postDelayed(verify_connection, VERIFY_CONNECTION_INTERVAL);
                        } else {
                            // connection has been restored, spawn process to send all locally
                            // stored data back to the access point (Pi)
                            new ExportLocalDatabase().execute();
                        }
                    }
                });
                verify.execute();
            }
        };

        handler.postDelayed(verify_connection, VERIFY_CONNECTION_INTERVAL);
    }

    /**
     * Process to relay data to an access point (Pi)
     */
    private class RelayData extends AsyncTask<String, Void, Void> {

        // detect state change (connection dropped)
        private boolean stateChange = false;

        /**
         * store the sensor data locally in the database
         *
         * @param packet packet to store in database
         */
        private void storeInDatabase(String packet) {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();

                Log.i(TAG, "INTO DATABASE: " + packet);
                values.put(DatabaseEntry.PACKET_DATA, packet);

                db.insertWithOnConflict(DatabaseEntry.TABLE_PACKETS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                db.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                String data = params[0];

                if (inRange) {
                    // in range, send data to server
                    Socket socket = new Socket(HOST, PORT);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    Log.i(TAG, "SENDING DATA: " + data);
                    String message = "Sending data... \n" + data;
                    setStatus(thyeway.xyz.activitytracker.Status.STATUS_OK, message.substring(0, message.length()-1));
//
                    outputStream.writeBytes(data);
                    socket.close();
                } else {
                    // not in range, store to database
                    storeInDatabase(data);
                    String message = "Connection down, storing to database... \n" + data;
                    setStatus(thyeway.xyz.activitytracker.Status.STATUS_WARNING, message.substring(0, message.length()-1));

                }
            } catch (Exception e) {
                Log.i(TAG, "Connection is down");
                // connection went down
                inRange = false;
                storeInDatabase(params[0]);
                stateChange = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // if there was a state change (connection went down),
            // start waiting for connection to restore
            if (stateChange) {
                verify_connection();
            }
        }
    }

    /**
     * Process to verify if the connection to an access point has been restored
     */
    private class VerifyConnection extends AsyncTask<Void, Void, Void> {

        private TaskCallback listener;

        public VerifyConnection(TaskCallback listener) {
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Log.i(TAG, "Connecting to " + HOST + ":" + PORT);
                // try connecting
                Socket socket = new Socket(HOST, PORT);

                // the code below will only process if the connection is re-established
                Log.i(TAG, "Connection is back up!");
                inRange = true;
                socket.close();

                // sends callback notify that connection has been restored
                listener.taskStatus(inRange);

            } catch (Exception e) {
                // connection is still down
                Log.i(TAG, "Connection is still down ... ");

                inRange = false;
                // sends callback to notify that connection is still down
                listener.taskStatus(inRange);

                // recheck connection, do this here because it takes time for connection to be re-established
                if(!NetworkUtil.isConnected(getApplicationContext())) {
                    Log.i(TAG, "connection is down, trying to connect to access point");
                    NetworkUtil.connectAccessPoint(getApplicationContext());
                }
            }
            return null;
        }
    }

    /**
     * Process to relay locally stored data to an access point (Pi)
     */
    private class ExportLocalDatabase extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {

            SQLiteDatabase db = dbHelper.getReadableDatabase();
            // basically SELECT * FROM TABLE_PACKETS
            Cursor c = db.query(DatabaseEntry.TABLE_PACKETS, null, null, null, null, null, null);

            c.moveToFirst();
            try {
                // for each entry, sends it off
                while (c.moveToNext()) {
                    Socket socket = new Socket(HOST, PORT);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    String data = c.getString(0);
                    Log.i(TAG, "SENDING DATABASE DATA: " + data);
                    String message = "Sending database data... \n" + data;
                    setStatus(thyeway.xyz.activitytracker.Status.STATUS_OK, message.substring(0, message.length()-1));

                    outputStream.writeBytes(data);
                    socket.close();

                    // delete the entry from the database after sending
                    int numRows = db.delete(DatabaseEntry.TABLE_PACKETS, DatabaseEntry.PACKET_DATA + "=?", new String[]{data});
                    Log.i(TAG, "Deleted " + numRows + " rows.");

                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                c.close();
            }

            Log.i(TAG, "Sent all packets");

            return null;
        }
    }

}