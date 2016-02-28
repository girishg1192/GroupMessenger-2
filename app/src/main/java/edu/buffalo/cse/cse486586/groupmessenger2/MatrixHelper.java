package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by girish on 2/11/16.
 *
public class KeyDBHelper extends SQLiteOpenHelper {
    private static String DB_NAME = "key_db";
    private static int DB_VER = 3;
    private static String TABLE_NAME = "KEYTABLE";

    public KeyDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VER);
    }

    @Override
    public void onCreate(SQLiteDatabase sql){
        String createTable = "CREATE TABLE " + TABLE_NAME + "(key VARCHAR(255) UNIQUE,value VARCHAR(255));";
        sql.execSQL(createTable);
        Log.v("DB_HELPER", "done");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ;
    }
}*/
public class MatrixHelper {
    private static final String[] KEY_FIELD = {"key", "value"};
    public MatrixCursor cursor;
//    private static final String VALUE_FIELD = "value";

    public MatrixHelper(String [] args) {
        cursor = new MatrixCursor(KEY_FIELD);
//        cursor.newRow();
        cursor.addRow(new Object[]{args[0], args[1]});
    }
    public void onCreate(){

    }
}
