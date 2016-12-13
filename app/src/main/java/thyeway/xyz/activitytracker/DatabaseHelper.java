package thyeway.xyz.activitytracker;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static thyeway.xyz.activitytracker.DatabaseContract.DatabaseEntry;

/**
 * Helper class to assist in database creation and version management
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "activity.db";

    // SQL CREATE statement syntax, for consistency
    private static final String CREATE_TABLE = "CREATE TABLE ";
    private static final String TEXT_TYPE = " TEXT";
    private static final String REAL_TYPE = " REAL";
    private static final String INT_TYPE = " INTEGER";
    private static final String DATE_TYPE = " DATE";
    private static final String NOT_NULL = " NOT NULL";
    private static final String PRIMARY_KEY = " PRIMARY KEY";
    private static final String AUTO_INCREMENT = " AUTOINCREMENT";
    private static final String COMMA_SEP = ",";

    // CREATE statement for Motion table
    private static final String SQL_CREATE_MOTION_TABLE =
            CREATE_TABLE + DatabaseEntry.TABLE_MOTION + " (" +
                    DatabaseEntry.MOTION_ID + INT_TYPE + PRIMARY_KEY + AUTO_INCREMENT + COMMA_SEP +
                    DatabaseEntry.MOTION_DEVICE_ID + TEXT_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_GX + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_GY + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_GZ + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_AX + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_AY + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_AZ + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_MX + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_MY + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_MZ + REAL_TYPE + NOT_NULL + COMMA_SEP +
                    DatabaseEntry.MOTION_ENTRY_REAL_DATE + DATE_TYPE + NOT_NULL + ")";

    // CREATE statement for Lux table
//    private static final String SQL_CREATE_LUX_TABLE =
//            CREATE_TABLE + DatabaseEntry

    // DROP statement for Motion table
    private static final String SQL_DELETE_MOTION = "DROP TABLE IF EXISTS " + DatabaseEntry.TABLE_MOTION;

    /**
     * Constructor
     *
     * @param context context to use to open or create the database
     */
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        System.out.println(SQL_CREATE_MOTION_TABLE);
        db.execSQL(SQL_CREATE_MOTION_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_MOTION);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
