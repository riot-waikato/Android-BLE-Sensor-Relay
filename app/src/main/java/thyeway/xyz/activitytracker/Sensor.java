package thyeway.xyz.activitytracker;


public abstract class Sensor {

    String device_mac;
    String device_name;
    String device_id;
    String sensor_value;
    String sequence_number; // for debugging purpose only?
    String last_read_time;  // time the data was read
    String service_uuid;

    public Sensor(String device_name, String mac) {
        this.device_name = device_name;
        this.device_mac = mac;
    }

    public abstract void updateData();

    public abstract String createPacket();

}
