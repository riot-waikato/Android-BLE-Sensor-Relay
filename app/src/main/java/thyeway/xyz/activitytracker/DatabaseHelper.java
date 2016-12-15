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
    private static final String NOT_NULL = " NOT NULL";

    // CREATE statement for Packets table
    private static final String SQL_CREATE_PACKETS_TABLE =
            CREATE_TABLE + DatabaseEntry.TABLE_PACKETS + " (" +
                    DatabaseEntry.PACKET_DATA + TEXT_TYPE + NOT_NULL + ")";

    // DROP statement for Motion table
    private static final String SQL_DELETE_PACKETS = "DROP TABLE IF EXISTS " + DatabaseEntry.TABLE_PACKETS;

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
        System.out.println(SQL_CREATE_PACKETS_TABLE);
        db.execSQL(SQL_CREATE_PACKETS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_PACKETS);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
