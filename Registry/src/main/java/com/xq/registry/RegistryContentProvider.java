package com.xq.registry;

import static com.xq.registry.Constants.COLUMN_EXTRAS;
import static com.xq.registry.Constants.COLUMN_ID;
import static com.xq.registry.Constants.COLUMN_KEY;
import static com.xq.registry.Constants.COLUMN_VALUE;
import static com.xq.registry.Constants.DB_REGISTRY;
import static com.xq.registry.Constants.DB_VERSION;
import static com.xq.registry.Constants.TABLE_REGISTRY_LIST;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

public class RegistryContentProvider extends ContentProvider {

    private static RegistryContentProvider instance;

    public static RegistryContentProvider getInstance(){
        return instance;
    }

    //Authority
    public String AUTHORITY;

    //表URI
    public String URI_REGISTRY_LIST;

    private UriMatcher uriMatcher;

    private DBHelper dbHelper;

    @Override
    public boolean onCreate() {

        instance = this;

        AUTHORITY = getContext().getPackageName() + ".RegistryContentProvider";
        URI_REGISTRY_LIST = "content://" + AUTHORITY + "/" + TABLE_REGISTRY_LIST;

        dbHelper = new DBHelper(getContext());

        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, TABLE_REGISTRY_LIST,0);

        return true;
    }


    @Override
    public Cursor query( Uri uri,  String[] projection,  String selection,  String[] selectionArgs,  String sortOrder) {
        if (uriMatcher.match(uri) == 0){
            return dbHelper.getReadableDatabase().query(TABLE_REGISTRY_LIST,projection,selection,selectionArgs,null,null,sortOrder);
        }
        return null;
    }


    @Override
    public String getType( Uri uri) {
        //不需要处理
        return null;
    }


    @Override
    public Uri insert( Uri uri,  ContentValues values) {
        if (uriMatcher.match(uri) == 0){
            List<Uri> uriList = insertOrUpdate(uri,values,COLUMN_KEY + "=?",new String[]{values.getAsString(COLUMN_KEY)});
            return uriList.isEmpty()?null:uriList.get(0);
        }
        return null;
    }

    @Override
    public int delete( Uri uri,  String selection,  String[] selectionArgs) {
        if (uriMatcher.match(uri) == 0){
            return dbHelper.getWritableDatabase().delete(TABLE_REGISTRY_LIST,selection,selectionArgs);
        }
        return 0;
    }

    @Override
    public int update( Uri uri,  ContentValues values,  String selection,  String[] selectionArgs) {
        if (uriMatcher.match(uri) == 0){
            List<Uri> uriList = insertOrUpdate(uri,values,COLUMN_KEY + "=?",new String[]{values.getAsString(COLUMN_KEY)});
            return uriList.size();
        }
        return 0;
    }

    private List<Uri> insertOrUpdate( Uri uri,  ContentValues values,  String selection,  String[] selectionArgs){

        List<Uri> uriList = new LinkedList<>();

        boolean putSuccess = false;

        if (!containQuery(selection,selectionArgs)){
            long ret = dbHelper.getWritableDatabase().insert(TABLE_REGISTRY_LIST,null,values);
            if (ret >= 0){
                putSuccess = true;
            }
        } else {
            int count = dbHelper.getWritableDatabase().update(TABLE_REGISTRY_LIST,values,selection,selectionArgs);
            if (count > 0){
                putSuccess = true;
            }
        }

        if (putSuccess){
            Cursor cursor = dbHelper.getReadableDatabase().query(TABLE_REGISTRY_LIST,null,selection,selectionArgs,null,null,null);
            cursor.moveToFirst();
            uriList.add(ContentUris.withAppendedId(uri,cursor.getInt(cursor.getColumnIndex(COLUMN_ID))));
            cursor.close();
        }

        for (Uri tempUri : uriList){
            getContext().getContentResolver().notifyChange(tempUri,null);
        }

        return uriList;
    }

    private boolean containQuery(String selection,String[] selectionArgs){
        Cursor cursor = dbHelper.getReadableDatabase().query(TABLE_REGISTRY_LIST,null,selection,selectionArgs,null,null,null);
        boolean contain = cursor != null && cursor.getCount() > 0;
        if (cursor != null){
            cursor.close();
        }
        return contain;
    }

    private class DBHelper extends SQLiteOpenHelper {

        private final String CREATE_TABLE = String.format("create table if not exists %s("
                +"%s integer primary key,"
                +"%s string,"
                +"%s string,"
                +"%s string)"
                , TABLE_REGISTRY_LIST,COLUMN_ID,COLUMN_KEY,COLUMN_VALUE,COLUMN_EXTRAS);

        public DBHelper( Context context) {
            super(context, DB_REGISTRY+".db", null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (newVersion > oldVersion){
                Log.e("RegistryContentProvider","onUpgrade oldVersion = " + oldVersion + " , newVersion = " + newVersion);
            }
        }

    }

}
