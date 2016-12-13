package thyeway.xyz.activitytracker;


import android.bluetooth.BluetoothGattCharacteristic;

import java.util.ArrayList;
import java.util.Queue;

public abstract class Sensor {

    String device_mac;
    String device_name;
    String device_id;
    String sensor_value;
    String sequence_number; // for debugging purpose only?
    String last_read_time;  // time the data was read
    String service_uuid;
    ArrayList<String> gatt_characteristics;

    Queue<BluetoothGattCharacteristic> characteristicsQueue;
    Queue<BluetoothGattCharacteristic> characteristicsReadQueue;


    public Sensor(String device_name, String mac) {
        this.device_name = device_name;
        this.device_mac = mac;
    }

    public abstract void updateData(String UUID, String value);

    public abstract void addToQueue(BluetoothGattCharacteristic characteristic);

    public abstract BluetoothGattCharacteristic getQueueNext();

    public abstract void prepareReadQueue();

    public abstract boolean readReady();

    public abstract void emptyData();

    public abstract String createPacket();

}
