package thyeway.xyz.activitytracker;

public class SensorFactory {

    public Sensor getSensor(String UUID, String device_name, String device_mac) {
        if(UUID.equalsIgnoreCase(UUIDs.LUX_SERVICE)) {
            return new LuxSensor(device_name, device_mac);
        }

        return null;
    }

}
