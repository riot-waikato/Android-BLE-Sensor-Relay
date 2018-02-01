package thyeway.xyz.activitytracker;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * An ArrayAdapter class which is used to display information about Bluetooth Devices on a View in
 * an Android activity.
 */
class BluetoothDeviceArrayAdapter extends ArrayAdapter<BluetoothDevice> {

    public final String TAG = "BTDeviceArrayAdapter";

    // an array for available devices and selected devices
    private ArrayList<Sensor> mLeDevices;
    private ArrayList<Sensor> mLeDevicesSelected;
    private LayoutInflater mInflater;

    public BluetoothDeviceArrayAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        this.mLeDevices = new ArrayList<Sensor>();
        this.mLeDevicesSelected = new ArrayList<Sensor>();
        this.mLeDevices.addAll(mLeDevices);
        mInflater = LayoutInflater.from(context);
    }

    /**
     * Class representing the fields that will be displayed in the list view.
     */
    private class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        CheckBox selected;
        ImageButton deviceInfo;
    }

    /**
     * Begins Gatt discovery of devices.
     * TODO: This will continually poll devices that are not RIOT devices because they are never
     * TODO: added to the list. Fix this.
     * TODO: Actually make this do what the function is supposed to do, to avoid confusion.
     * @param device
     */
    @Override
    public void add(BluetoothDevice device) {
        boolean exist = false;
        for (Sensor sensor : mLeDevices) {
            if (sensor.device_mac.equalsIgnoreCase(device.getAddress())) {
                exist = true;
                break;
            }
        }

        if (!exist) {
            device.connectGatt(getContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> gattServices = gatt.getServices();

                // for each service, get all its characteristics and add it to the queue
                for (BluetoothGattService s : gattServices) {
                    Log.i(TAG, "SERVICE: " + s.getUuid());
                    SensorFactory sensorFactory = new SensorFactory();
                    Sensor newSensor = sensorFactory.getSensor(s.getUuid().toString(), gatt.getDevice().getName(), gatt.getDevice().getAddress());
                    if(newSensor != null) {
                        Log.i(TAG, "Valid sensor: " + newSensor.getClass().toString());
                        for(Sensor sensor : mLeDevices) {
                            if(sensor.device_mac.equalsIgnoreCase(newSensor.device_mac)) {
                                return;
                            }
                        }
                        mLeDevices.add(newSensor);

                        // Update the UI of the Activity.
                        Context context = BluetoothDeviceArrayAdapter.this.getContext();

                        // Check that the given context can be cast to an Activity so that
                        // runOnUiThread can be called.
                        if (context instanceof Activity) {
                            Activity activity = (Activity)context;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    notifyDataSetChanged();
                                }
                            });
                        }
                        break;
                    }
                }
                gatt.disconnect();
            }

        }
    };

    @Override
    public int getCount() {
        return mLeDevices.size();
    }

    public ArrayList<Sensor> getSelected() {
        return mLeDevicesSelected;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.bluetooth_device_info, null);
            holder = new ViewHolder();
            holder.deviceAddress = (TextView) convertView.findViewById(R.id.textViewDeviceMACAddress);
            holder.deviceName = (TextView) convertView.findViewById(R.id.textViewDeviceName);
            holder.selected = (CheckBox) convertView.findViewById(R.id.checkBoxSelectDevice);
            holder.deviceInfo = (ImageButton) convertView.findViewById(R.id.imageButtonDeviceInfo);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Sensor sensor = mLeDevices.get(position);

        // set device name and MAC address
        final String deviceName = sensor.device_name;
        if (deviceName != null && deviceName.length() > 0) {
            holder.deviceName.setText(deviceName);
        } else {
            holder.deviceName.setText(R.string.error_unknown_device);
        }
        holder.deviceAddress.setText(sensor.device_mac);

        // add and remove from array when checkbox is checked or unchecked
        holder.selected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mLeDevicesSelected.contains(sensor)) {
                    mLeDevicesSelected.remove(sensor);
                } else {
                    mLeDevicesSelected.add(sensor);
                }
            }
        });

        // starts the ViewDevice activity
        // pass the MAC address of the device to the activity
        holder.deviceInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), ViewDeviceActivity.class);
                intent.putExtra("address", sensor.device_mac);
                getContext().startActivity(intent);
            }
        });

        return convertView;
    }
}