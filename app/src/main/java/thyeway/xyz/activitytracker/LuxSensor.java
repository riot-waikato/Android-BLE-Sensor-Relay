package thyeway.xyz.activitytracker;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class LuxSensor extends Sensor {

    public static final String DEVICE_ID = "0x00";
    public static final String SERVICE_UUID = UUIDs.LUX_SERVICE;

    public LuxSensor(String device_name, String device_mac) {
        super("[LUX] " + device_name, device_mac);
        this.device_id = DEVICE_ID;
        this.service_uuid = SERVICE_UUID;
    }

    public Queue<BluetoothGattCharacteristic> getCharacteristics() {
        Queue<BluetoothGattCharacteristic> queue = new LinkedList<>();
        return queue;
    }

    @Override
    public void updateData() {

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
