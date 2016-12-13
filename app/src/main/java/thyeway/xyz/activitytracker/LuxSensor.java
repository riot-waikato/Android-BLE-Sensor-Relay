package thyeway.xyz.activitytracker;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class LuxSensor extends Sensor {

    public static final String DEVICE_ID = "0x00";
    public static final String SERVICE_UUID = UUIDs.LUX_SERVICE;
    private static final String TAG = "LuxSensor";

    public LuxSensor(String device_name, String device_mac) {
        super("[LUX] " + device_name, device_mac);
        this.device_id = DEVICE_ID;
        this.service_uuid = SERVICE_UUID;

        this.gatt_characteristics = new ArrayList<>();
        this.gatt_characteristics.add(UUIDs.LUX_SEQUENCE_NUMBER);
        this.gatt_characteristics.add(UUIDs.LUX_VALUE);
    }

    @Override
    public void addToQueue(BluetoothGattCharacteristic characteristic) {
        if(this.characteristicsQueue == null) {
            this.characteristicsQueue = new LinkedList<>();
        }
        this.characteristicsQueue.add(characteristic);
    }

    @Override
    public void prepareReadQueue() {
        this.characteristicsReadQueue = new LinkedList<>(this.characteristicsQueue);
    }

    @Override
    public BluetoothGattCharacteristic getQueueNext() {
        if(this.characteristicsReadQueue.peek() != null) {
            return this.characteristicsReadQueue.poll();
        }
        return null;
    }

    @Override
    public void updateData(String UUID, String value) {
        switch(UUID.toLowerCase()) {
            case UUIDs.LUX_SEQUENCE_NUMBER:
                this.sequence_number = value;
                break;
            case UUIDs.LUX_VALUE:
                this.sensor_value = value;
                break;
        }
    }

    @Override
    public boolean readReady() {
        if(this.sensor_value == null && this.sequence_number == null) {
            return true;
        }
        return false;
    }

    @Override
    public void emptyData() {
        this.sensor_value = null;
        this.sequence_number = null;
    }

    /**
     * Packet Structure: 'lux', device_id, lux_value, sequence_number, timestamp
     * @return
     */
    @Override
    public String createPacket() {

        StringBuilder builder = new StringBuilder();
        builder.append("lux ");
        builder.append(this.device_id);
        builder.append(" ");
        builder.append(this.sensor_value);
        builder.append(" ");
        builder.append(this.last_read_time);
        builder.append("\n");

        return builder.toString();
    }
}
