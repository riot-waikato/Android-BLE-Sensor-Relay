package thyeway.xyz.activitytracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * View device activity, read and displays all characteristics of a specific device
 * Note: this activity is for demonstration purpose and is not part of the relay system
 */
public class ViewDeviceActivity extends AppCompatActivity {

    // debug use
    private final String TAG = "ViewDevice";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    // maintain a queue of GATT characteristics to be read
    private Queue<BluetoothGattCharacteristic> mCharacteristicsQueue;

    // maintain two set of data, we should always display the old data:
    // when receiving data, we put it in mData, once we have received everything, only we move it to mOldData
    // or else the data will never display because it is constantly updating
    private ArrayList<CharacteristicData> mOldData;
    private ArrayList<CharacteristicData> mData;
    private CharacteristicsAdapter adapter;

    private String DEVICE_NAME;
    private String DEFAULT_DEVICE_NAME = "Unknown Device";
    private String DEVICE_ADDRESS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_device_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.DeviceInformation);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // initialize queue
        mCharacteristicsQueue = new LinkedList<>();

        // initialize arrays and link it to the listview
        mOldData = new ArrayList<>();
        mData = new ArrayList<>();
        adapter = new CharacteristicsAdapter(this, mOldData);
        ListView listView = (ListView) findViewById(R.id.listViewDeviceCharacteristics);
        listView.setAdapter(adapter);

        // get device information from previous activity
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        // the previous activity will provide the device MAC address
        DEVICE_ADDRESS = bundle.getString("address");

        // initialize bluetooth manager
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // connect to the bluetooth device
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        mBluetoothGatt = mBluetoothDevice.connectGatt(this, false, mGattCallback);

        // get the device name, if none specified (happens), use the pre-defined default device name
        DEVICE_NAME = (mBluetoothDevice.getName() == null) ? DEFAULT_DEVICE_NAME : mBluetoothDevice.getName();

        // update the textviews
        TextView textViewDeviceName = (TextView) findViewById(R.id.textViewDeviceName);
        TextView textViewDeviceAddress = (TextView) findViewById(R.id.textViewDeviceAddress);
        textViewDeviceName.setText(DEVICE_NAME);
        textViewDeviceAddress.setText(DEVICE_ADDRESS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // disconnect and close the connection when returning to previous activity
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
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

                for (BluetoothGattService s : gattServices) {
                    List<BluetoothGattCharacteristic> characteristics = s.getCharacteristics();
                    for (BluetoothGattCharacteristic c : characteristics) {
                        if (c.getProperties() == BluetoothGattCharacteristic.PROPERTY_READ) {
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
                mData.add(new CharacteristicData(characteristic.getUuid().toString(), characteristic.getValue()));
                if (mCharacteristicsQueue.peek() != null) {
                    mBluetoothGatt.readCharacteristic(mCharacteristicsQueue.poll());
                } else {
                    // once all characteristics have been read, replace the old data with the new ones, and display them
                    // do discoverServices again to trigger reading a new set of data
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mOldData = new ArrayList<>();
                            mOldData.addAll(mData);
                            adapter.clear();
                            adapter.addAll(mOldData);
                            adapter.notifyDataSetChanged();
                            mData = new ArrayList<>();

                            mBluetoothGatt.discoverServices();
                        }
                    });
                }
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Data structure to present the characteristic data
     */
    private class CharacteristicData {
        private String UUID;
        private byte[] data;

        public CharacteristicData(String UUID, byte[] data) {
            this.UUID = UUID;
            this.data = data;
        }

        /**
         * convert byte[] data to string
         * @return
         */
        public String dataToString() {
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                return stringBuilder.toString();
            }
            return null;
        }
    }

    /**
     * Custom array adapter to display the GATT characteristics in a listview
     */
    private class CharacteristicsAdapter extends ArrayAdapter<CharacteristicData> {
        public CharacteristicsAdapter(Context context, ArrayList<CharacteristicData> data) {
            super(context, 0, data);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            CharacteristicData data = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.device_characteristics, parent, false);
            }

            TextView textViewUUID = (TextView) convertView.findViewById(R.id.textViewUUID);
            TextView textViewValue = (TextView) convertView.findViewById(R.id.textViewValue);

            textViewUUID.setText(data.UUID);
            textViewValue.setText(data.dataToString());

            return convertView;
        }
    }
}
