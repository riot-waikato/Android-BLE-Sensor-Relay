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
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static thyeway.xyz.activitytracker.DatabaseContract.DatabaseEntry;

/**
 * Logging service
 *
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
    private BluetoothGatt mBluetoothGatt;

    // maintain a queue of GATT characteristics to be read
    private Queue<BluetoothGattCharacteristic> mCharacteristicsQueue;
    // maintain a queue of devices to be read
    private Queue<String> mDeviceQueue;

    // keep track of current service being processed
    private String mCurrentServiceUUID;

    // sensor data currenly being read
    private ArrayList<Byte> mData;
    private String mCurrentTime;

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
                Log.e(TAG, "Unable to initialize BluetoothManager.");
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
    public void onDestroy() {
        super.onDestroy();
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
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

    /**
     * Process a list of devices to be tracked
     * @param devices list of device to be tracked
     */
    public void track(final ArrayList<BluetoothDevice> devices) {

        // get the host and port to connect to from settings
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        HOST = preferences.getString(getResources().getString(R.string.preference_host), HOST);
        PORT = Integer.parseInt(preferences.getString(getResources().getString(R.string.preference_port), ""));

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    mCharacteristicsQueue = new LinkedList<>();
                    mDeviceQueue = new LinkedList<>();

                    // add devices to queue
                    for (BluetoothDevice device : devices) {
                        mDeviceQueue.add(device.getAddress());
                    }

                    // connect to the devices in the queue and read data from the device
                    if (mDeviceQueue.size() != 0) {
                        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceQueue.poll());
                        if (device != null) {
                            Log.i(TAG, "Processing: " + device.getAddress() + " " + device.getName());
                            mCurrentTime = Long.toString(System.currentTimeMillis()/1000);
                            mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    handler.postDelayed(this, mDelay);
                }

            }
        };

        // run this repeatedly, at an interval
        handler.postDelayed(runnable, mDelay);

        // TODO: Feature/handler to stop tracking
    }

    /**
     * Handle callbacks from connectGatt
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // once the device is connected, discover what services does the device has
            // which will trigger onServicesDiscovered
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // if the device gets disconnected, move on to the next device in the queue
                if (mDeviceQueue.peek() != null) {
                    // gracefully close the bluetooth connection
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;

                    final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceQueue.poll());

                    // process the next device
                    if (device != null) {
                        Log.i(TAG, "now processing: " + device.getAddress() + " " + device.getName());
                        mCurrentTime = Long.toString(System.currentTimeMillis()/1000);
                        mCharacteristicsQueue = new LinkedList<>();
                        mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                    }
                } else {
                    // all the device in the queue have been processed, close the bluetooth connection
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    Log.i(TAG, "Disconnected");
                }
                Log.i(TAG, "Disconnected, terminating...");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // upon successfully discovering services, get the characteristics of each services,
            // each characteristic that has property READ is then added to a queue
            // note: this is not a standard implementation, BLE supports Read, Write, Notify and Indicate operation,
            //       for this project we only consider the READ operation. Can consider changing to NOTIFY operation
            //       to provide a way for the server to push data to the client, instead of the client reading from
            //       the server.
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();

                // for each service, get all its characteristics and add it to the queue
                for (BluetoothGattService s : gattServices) {
                    Log.i(TAG, "SERVICE: " + s.getUuid());

                    List<BluetoothGattCharacteristic> characteristics = s.getCharacteristics();
                    mData = new ArrayList<>();

                    for (BluetoothGattCharacteristic c : characteristics) {
                        Log.i(TAG, "\t\tCHARACTERISTIC: " + c.getUuid());

                        if(c.getProperties() == BluetoothGattCharacteristic.PROPERTY_READ) {
                            mCharacteristicsQueue.add(c);
                        }

                    }
                }
            }

            // after the queue is being populated, we perform read operation on each characteristics in the queue
            // starting from the first one
            if (mCharacteristicsQueue.size() != 0) {
                mBluetoothGatt.readCharacteristic(mCharacteristicsQueue.poll());
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // when a characteristic is successfully being read, we add the data to the mData array,
            // and if there's more characteristics to read in the queue, we continue reading them until there's none left
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readCharacteristic(characteristic);
                if (mCharacteristicsQueue.peek() != null) {
                    mBluetoothGatt.readCharacteristic(mCharacteristicsQueue.poll());
                } else {
                    // no more characteristics to be read, send the last data off
                    byte[] bytes = new byte[mData.size()];
                    for(int i = 0; i < mData.size(); i++) {
                        bytes[i] = mData.get(i);
                    }
                    packet p = new packet(1, bytes, mCurrentTime);

                    // spawn process to send data off
                    new RelayData().execute(p);

                    // clear data and disconnect device
                    mData = new ArrayList<>();
                    mCurrentServiceUUID = null;

                    Log.i(TAG, "done, disconnecting");
                    mBluetoothGatt.disconnect();
                }
            }
        }
    };

    /**
     * Reads a bluetooth GATT characteristic data, put it into a packet form and sends to an access point
     * @param characteristic GATT characteristic
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        final byte[] data = characteristic.getValue();

        if(mCurrentServiceUUID == null) {
            mCurrentServiceUUID = characteristic.getService().getUuid().toString();
        }

        if(!characteristic.getService().getUuid().toString().equals(mCurrentServiceUUID)) {
            // forms the packet
            byte[] bytes = new byte[mData.size()];
            for(int i = 0; i < mData.size(); i++) {
                bytes[i] = mData.get(i);
            }
            packet p = new packet(1, bytes, mCurrentTime);
            // spawn process to send data off
            new RelayData().execute(p);

            mData = new ArrayList<>();
            mCurrentServiceUUID = characteristic.getService().getUuid().toString();
        }

        mData.add(data[0]);
    }

    /**
     * Structure of a 'packet'
     */
    private class packet {
        private int user_id;
        private byte[] data;
        private String time;

        public packet(int user_id, byte[] data, String time) {
            this.user_id = user_id;
            this.data = data;
            this.time = time;
        }
    }

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
                        if(status == false) {
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
    private class RelayData extends AsyncTask<packet, Void, Void> {

        // detect state change (connection dropped)
        private boolean stateChange = false;

        /**
         * store the sensor data locally in the database
         * @param p packet to store in database
         */
        private void storeInDatabase(packet p) {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();

                // TODO: fix device id
                values.put(DatabaseEntry.MOTION_DEVICE_ID, "0x" + p.user_id);

                values.put(DatabaseEntry.MOTION_GX, p.data[0]);
                values.put(DatabaseEntry.MOTION_GY, p.data[1]);
                values.put(DatabaseEntry.MOTION_GZ, p.data[2]);

                values.put(DatabaseEntry.MOTION_AX, p.data[3]);
                values.put(DatabaseEntry.MOTION_AY, p.data[4]);
                values.put(DatabaseEntry.MOTION_AZ, p.data[5]);

                values.put(DatabaseEntry.MOTION_MX, p.data[6]);
                values.put(DatabaseEntry.MOTION_MY, p.data[7]);
                values.put(DatabaseEntry.MOTION_MZ, p.data[8]);

                values.put(DatabaseEntry.MOTION_ENTRY_REAL_DATE, p.time);

                db.insertWithOnConflict(DatabaseEntry.TABLE_MOTION, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                db.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(packet... params) {
            try {
                packet p = params[0];

                if(inRange) {
                    // in range, send data to server
                    Socket socket = new Socket(HOST, PORT);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    StringBuilder builder = new StringBuilder();
                    builder.append("0 ");
                    builder.append("0x" + p.user_id);
                    builder.append(" ");
                    for(byte b : p.data) {
                        builder.append(b);
                        builder.append(" ");
                    }
                    builder.append(p.time);
                    builder.append("\n");

                    Log.i(TAG, "SENDING DATA: " + builder.toString());

                    outputStream.writeBytes(builder.toString());
                    socket.close();
                } else {
                    // not in range, store to database
                    storeInDatabase(p);
                }
            } catch(UnknownHostException uhe) {
                // connection went down
                inRange = false;
                storeInDatabase(params[0]);
                stateChange = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // if there was a state change (connection went down),
            // start waiting for connection to restore
            if(stateChange) {
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
                // try connecting
                Socket socket = new Socket(HOST, PORT);

                // the code below will only process if the connection is re-established
                Log.i(TAG, "Connection is back up!");
                inRange = true;
                socket.close();

                // sends callback notify that connection has been restored
                listener.taskStatus(inRange);

            } catch (UnknownHostException e) {
                // connection is still down
                Log.i(TAG, "Connection is still down ... ");
                inRange = false;
                // sends callback to notify that connection is still down
                listener.taskStatus(inRange);
            } catch (IOException ioe) {
                ioe.printStackTrace();
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
            // basically SELECT * FROM TABLE_MOTION
            Cursor c = db.query(DatabaseEntry.TABLE_MOTION, null, null, null, null, null, null);

            c.moveToFirst();
            try {
                // for each entry, sends it off
                while (c.moveToNext()) {
                    int id = c.getInt(0);

                    Socket socket = new Socket(HOST, PORT);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    // construct into a 'packet'
                    StringBuilder builder = new StringBuilder();
                    builder.append("0 ");
                    builder.append(c.getString(1));
                    builder.append(" ");

                    for(int i = 2; i < 11; i++) {
                        builder.append(c.getString(i));
                        builder.append(" ");
                    }

                    builder.append(c.getString(11));
                    builder.append("\n");

                    Log.i(TAG, "SENDING DATABASE DATA: " + builder.toString());
                    outputStream.writeBytes(builder.toString());
                    socket.close();

                    // delete the entry from the database after sending
                    db.delete(DatabaseEntry.TABLE_MOTION, DatabaseEntry.MOTION_ID + "=?", new String[]{Integer.toString(id)});
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                c.close();
            }

            Log.i(TAG, "Sending completed");

            return null;
        }
    }

}
