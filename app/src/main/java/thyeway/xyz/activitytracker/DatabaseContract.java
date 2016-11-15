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
        /* MOTION TABLE */
        public static final String TABLE_MOTION = "motion";
        public static final String MOTION_DEVICE_ID = "dev_id";
        public static final String MOTION_ID = "id";
        public static final String MOTION_GX = "gx";
        public static final String MOTION_GY = "gy";
        public static final String MOTION_GZ = "gz";
        public static final String MOTION_AX = "ax";
        public static final String MOTION_AY = "ay";
        public static final String MOTION_AZ = "az";
        public static final String MOTION_MX = "mx";
        public static final String MOTION_MY = "my";
        public static final String MOTION_MZ = "mz";
        public static final String MOTION_ENTRY_REAL_DATE = "real_date";
    }

}
