package thyeway.xyz.activitytracker;

import android.provider.BaseColumns;

/**
 * Companion class, specifies the layout of database schema
 */
public class DatabaseContract {

    /**
     * Constructor
     * Private because this should never be instantiated
     */
    private DatabaseContract() {
    }

    public static class DatabaseEntry implements BaseColumns {
        /* PACKETS TABLE */
        public static final String TABLE_PACKETS = "packets";
        public static final String PACKET_DATA = "data";
    }

}
