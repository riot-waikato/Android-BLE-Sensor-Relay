package thyeway.xyz.activitytracker;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.List;

/**
 * Utility class to handle connection with an access point (Pi)
 */
public final class NetworkUtil {

    private static final String TAG = "NetworkUtil";

    private static final String SSID_REGEX = "riot-waikato-[0-9A-Z]*";
    private static final String PASSWORD = "riot-waikato";

    /**
     * Constructor
     * Private to prevent instantiated
     */
    private NetworkUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Scan and connect to a valid access point
     * @param context application context
     * @return true if the operation succeeded
     */
    public static boolean connectAccessPoint(Context context) {
        String SSID = scan(context);
        if(SSID != null) {
            return connect(context, SSID);
        } else {
            return false;
        }
    }

    /**
     * Attempt to connect to a specified access point
     * @param context application context
     * @param SSID SSID of the access point to be connected to
     * @return true if connection successfully established
     */
    private static boolean connect(Context context, String SSID) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = String.format("\"%s\"", SSID);
        configuration.preSharedKey = String.format("\"%s\"", PASSWORD);

        int networkId = wifiManager.addNetwork(configuration);
        wifiManager.disconnect();
        wifiManager.enableNetwork(networkId, true);

        Log.i(TAG, "Connecting to " + SSID);
        return wifiManager.reconnect();
    }

    /**
     * Scan for available access points and select the best one based on signal strength
     * @param context application context
     * @return SSID of the best access point available
     */
    private static String scan(Context context) {

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        int bestSignal = 0;
        String bestNetworkSSID = null;

        List<ScanResult> scanResults = null;

        // check if wifi is enabled
        if(wifiManager.isWifiEnabled()) {
            scanResults = wifiManager.getScanResults();
        } else {
            // turn wifi on
            wifiManager.setWifiEnabled(true);
        }

        for(ScanResult result : scanResults) {
            // SSID must follow the pre-defined prefix
            if(result.SSID.matches(SSID_REGEX)){
                int signal = WifiManager.calculateSignalLevel(result.level, 5);
                if(signal > bestSignal) {
                    bestSignal = signal;
                    bestNetworkSSID = result.SSID;
                }
                Log.i(TAG, "[" + result.SSID + "]: " + signal);
            }
        }

        Log.i(TAG, "Best signal: " + bestNetworkSSID);
        return bestNetworkSSID;

    }

}