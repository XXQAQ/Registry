package com.xq.registry;

import static com.xq.registry.Constants.COLUMN_ID;
import static com.xq.registry.Constants.COLUMN_KEY;
import static com.xq.registry.Constants.COLUMN_VALUE;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class Registry {

    private static final String NODE_SPLIT = "/";

    private static final String ARRAY_IDENTIFICATION_START = "[";
    private static final String ARRAY_IDENTIFICATION_END = "]";
    private static final String ARRAY_IDENTIFICATION_MIDDLE = ",";

    private static Registry instance = new Registry();

    public static Registry getInstance(){
        return instance;
    }

    {

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        HandlerThread handlerThread = new HandlerThread("RegistryManagerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        getContext().getContentResolver().registerContentObserver(Uri.parse(RegistryContentProvider.getInstance().URI_REGISTRY_LIST), true, new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                Cursor cursor = getContext().getContentResolver().query(removeId(uri),null,COLUMN_ID + "=?",new String[]{String.valueOf(ContentUris.parseId(uri))},null);
                if (cursor != null){
                    cursor.moveToFirst();
                    final String key = cursor.getString(cursor.getColumnIndex(COLUMN_KEY));
                    final String value = cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
                    cursor.close();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //分发事件
                            dispatchOnValueChangedListener(key,value);
                        }
                    });
                }
            }
        });
    }

    public void putByFile(String path){
        putByJSONObject("",getFileConfigJSONObject(path));
    }

    public JSONObject getFileConfigJSONObject(String path){
        File file = new File(path);
        if (file.isDirectory()){
            try {
                JSONObject jsonObject = new JSONObject();
                String[] childes = file.list() == null?new String[0]:file.list();
                for (String child : childes){
                    if (child.startsWith("{") && child.endsWith("}") && child.substring(1,child.length()-1).equals("node")){
                        mergeJSONObject(jsonObject,new JSONObject(inputStreamToString(new FileInputStream(path+File.separator+child))));
                    } else {
                        jsonObject.put(child,getFileConfigJSONObject(path+File.separator+child));
                    }
                }
                return jsonObject;
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                return new JSONObject(inputStreamToString(new FileInputStream(file)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void putByAssetsFile(String path){
        putByJSONObject("",getAssetsFileConfigJSONObject(path));
    }

    public JSONObject getAssetsFileConfigJSONObject(String path){
        InputStream inputStream = null;
        try {
            inputStream = getContext().getAssets().open(path);
            return new JSONObject(inputStreamToString(inputStream));
        } catch (IOException ioException) {
            try {
                JSONObject jsonObject = new JSONObject();
                String[] childes = getContext().getAssets().list(path);
                for (String child : childes){
                    if (child.startsWith("{") && child.endsWith("}") && child.substring(1,child.length()-1).equals(new File(path).getName())){
                        mergeJSONObject(jsonObject,new JSONObject(inputStreamToString(getContext().getAssets().open(path+File.separator+child))));
                    } else {
                        jsonObject.put(child,getAssetsFileConfigJSONObject(path+File.separator+child));
                    }
                }
                return jsonObject;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void mergeJSONObject(JSONObject jsonObject,JSONObject other) throws JSONException {
        Iterator<String> iterator = other.keys();
        while (iterator.hasNext()){
            String key = iterator.next();
            jsonObject.put(key,other.get(key));
        }
    }

    private String inputStreamToString(InputStream inputStream){
        return inputToOutputStream(inputStream).toString();
    }

    private static ByteArrayOutputStream inputToOutputStream(final InputStream is) {
        if (is == null) return null;
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int len;
            while ((len = is.read(b, 0, 4096)) != -1) {
                os.write(b, 0, len);
            }
            return os;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void putByJson(String json){
        try {
            JSONObject jsonObject = new JSONObject(json);
            putByJSONObject("",jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void putByJSONObject(String prefix,JSONObject jsonObject){
        for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
            String key = it.next();
            Object value = jsonObject.opt(key);
            if (value instanceof JSONObject){
                putByJSONObject(prefix+NODE_SPLIT+key,(JSONObject) value);
            }
            else    if (value instanceof JSONArray){
                JSONArray jsonArray = (JSONArray) value;
                String[] array = new String[jsonArray.length()];
                for (int i=0;i<jsonArray.length();i++){
                    array[i] = jsonArray.opt(i).toString();
                }
                put(prefix+NODE_SPLIT+key,arrayToString(array));
            }
            else {
                put(prefix+NODE_SPLIT+key,jsonObject.isNull(key)?null:value.toString() );
            }
        }
    }

    public void put(String key,byte[] bytes){
        put(key,arrayToString(bytes));
    }

    public void put(String key,short[] shorts){
        put(key,arrayToString(shorts));
    }

    public void put(String key,int[] ints){
        put(key,arrayToString(ints));
    }

    public void put(String key,long[] longs){
        put(key,arrayToString(longs));
    }

    public void put(String key,float[] floats){
        put(key,arrayToString(floats));
    }

    public void put(String key,double[] doubles){
        put(key,arrayToString(doubles));
    }

    public void put(String key,boolean[] booleans){
        put(key,arrayToString(booleans));
    }

    public void put(String key,String[] strings){
        put(key,arrayToString(strings));
    }

    public void put(String key,Number number){
        put(key,number == null?null:number.toString());
    }

    public void put(String key,Boolean b){
        put(key,b == null?null:b.toString());
    }

    public void put(String key, String value){
        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY,key);
        values.put(COLUMN_VALUE,value);
        getContext().getContentResolver().update(Uri.parse(RegistryContentProvider.getInstance().URI_REGISTRY_LIST),values,COLUMN_KEY + "=?",new String[]{key});
    }

    public byte[] getByteArray(String key){
        return stringToByteArray(get(key));
    }

    public short[] getShortArray(String key){
        return stringToShortArray(get(key));
    }

    public int[] getIntArray(String key){
        return stringToIntArray(get(key));
    }

    public long[] getLongArray(String key){
        return stringToLongArray(get(key));
    }

    public float[] getFloatArray(String key){
        return stringToFloatArray(get(key));
    }

    public double[] getDoubleArray(String key){
        return stringToDoubleArray(get(key));
    }

    public boolean[] getBooleanArray(String key){
        return stringToBooleanArray(get(key));
    }

    public String[] getStringArray(String key){
        return stringToStringArray(get(key));
    }

    public byte getByte(String key,byte defaultValue){
        try {
            return Byte.parseByte(get(key));
        }catch (Exception e){

        }
        return defaultValue;
    }

    public Short getShort(String key,short defaultValue){
        try {
            return Short.parseShort(get(key));
        }catch (Exception e){

        }
        return defaultValue;
    }

    public int getInt(String key,int defaultValue){
        try {
            return Integer.parseInt(get(key));
        }catch (Exception e){

        }
        return defaultValue;
    }

    public long getLong(String key,long defaultValue){
        try {
            return Long.parseLong(get(key));
        }catch (Exception e){

        }
        return defaultValue;
    }

    public double getDouble(String key,double defaultValue){
        try {
            return Double.parseDouble(get(key));
        }catch (Exception e){

        }
        return defaultValue;
    }

    public float getFloat(String key,float defaultValue){
        try {
            return Float.parseFloat(get(key));
        }catch (Exception e){

        }
        return defaultValue;
    }

    public boolean getBoolean(String key,boolean defaultValue){
        try {
            return Boolean.parseBoolean(get(key));
        } catch (Exception e){

        }
        return defaultValue;
    }

    public String get(String key){
        Cursor cursor = getContext().getContentResolver().query(Uri.parse(RegistryContentProvider.getInstance().URI_REGISTRY_LIST),null,COLUMN_KEY + "=?",new String[]{key},null);
        if (cursor == null || cursor.getCount() <= 0){
            return null;
        }
        cursor.moveToFirst();
        String value = cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
        cursor.close();
        return value;
    }

    public byte[] stringToByteArray(String string){
        String[] stringArray = stringToStringArray(string);
        byte[] array = new byte[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Byte.parseByte(stringArray[i]);
        }
        return array;
    }

    public short[] stringToShortArray(String string){
        String[] stringArray = stringToStringArray(string);
        short[] array = new short[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Short.parseShort(stringArray[i]);
        }
        return array;
    }

    public int[] stringToIntArray(String string){
        String[] stringArray = stringToStringArray(string);
        int[] array = new int[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Integer.parseInt(stringArray[i]);
        }
        return array;
    }

    public long[] stringToLongArray(String string){
        String[] stringArray = stringToStringArray(string);
        long[] array = new long[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Long.parseLong(stringArray[i]);
        }
        return array;
    }

    public float[] stringToFloatArray(String string){
        String[] stringArray = stringToStringArray(string);
        float[] array = new float[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Float.parseFloat(stringArray[i]);
        }
        return array;
    }

    public double[] stringToDoubleArray(String string){
        String[] stringArray = stringToStringArray(string);
        double[] array = new double[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Double.parseDouble(stringArray[i]);
        }
        return array;
    }

    public boolean[] stringToBooleanArray(String string){
        String[] stringArray = stringToStringArray(string);
        boolean[] array = new boolean[stringArray.length];
        for (int i=0;i<array.length;i++){
            array[i] = Boolean.parseBoolean(stringArray[i]);
        }
        return array;
    }

    public String[] stringToStringArray(String string){
        if (string != null && string.startsWith(ARRAY_IDENTIFICATION_START) && string.endsWith(ARRAY_IDENTIFICATION_END)){
            return string.substring(1,string.length()-1).split(ARRAY_IDENTIFICATION_MIDDLE);
        }
        return new String[0];
    }

    public String arrayToString(Object array){
        if (array == null){
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ARRAY_IDENTIFICATION_START);
        for (int i=0;i<Array.getLength(array);i++){
            stringBuilder.append(Array.get(array,i));
            if (i < Array.getLength(array)-1){
                stringBuilder.append(ARRAY_IDENTIFICATION_MIDDLE);
            }
        }
        stringBuilder.append(ARRAY_IDENTIFICATION_END);
        return stringBuilder.toString();
    }

    public boolean containKey(String key){
        Cursor cursor = getContext().getContentResolver().query(Uri.parse(RegistryContentProvider.getInstance().URI_REGISTRY_LIST),null,COLUMN_KEY + "=?",new String[]{key},null);
        return cursor != null && cursor.getCount() > 0;
    }

    public void watch(final String key, final String value,final Runnable runnable){
        if (containKey(key) && isEquals(get(key),value)){
            runnable.run();
        } else {
            registerOnValueChangedListener(key, new OnValueChangedListener() {
                final AtomicBoolean hasCallback = new AtomicBoolean(false);
                @Override
                public void onChange(String k, String v) {
                    if (isEquals(v,value)){
                        if (hasCallback.compareAndSet(false,true)){
                            unregisterOnValueChangedListener(key,this);
                            runnable.run();
                        }
                    }
                }
            });
        }
    }

    public void watch(final String key,final Runnable runnable){
        if (containKey(key)){
            runnable.run();
        } else {
            registerOnValueChangedListener(key, new OnValueChangedListener() {
                final AtomicBoolean hasCallback = new AtomicBoolean(false);
                @Override
                public void onChange(String key, String value) {
                    if (hasCallback.compareAndSet(false,true)){
                        unregisterOnValueChangedListener(key,this);
                        runnable.run();
                    }
                }
            });
        }
    }

    private final Map<String, Set<OnValueChangedListener>> onValueChangedListenerMap = new ConcurrentHashMap<>();
    public void registerOnValueChangedListener(String key, OnValueChangedListener listener){
        if (!onValueChangedListenerMap.containsKey(key)){
            onValueChangedListenerMap.put(key,new LinkedHashSet<OnValueChangedListener>());
        }
        onValueChangedListenerMap.get(key).add(listener);
    }

    public void unregisterOnValueChangedListener(String key, OnValueChangedListener listener){
        if (onValueChangedListenerMap.containsKey(key)){
            onValueChangedListenerMap.get(key).remove(listener);
        }
    }

    private void dispatchOnValueChangedListener(String key,String value){
        if (onValueChangedListenerMap.containsKey(key)){
            for (OnValueChangedListener listener : new ArrayList<>(onValueChangedListenerMap.get(key))){
                listener.onChange(key,value);
            }
        }
    }

    private static boolean isEquals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    //Copy From ContentUris
    private Uri removeId(Uri contentUri) {
        // Verify that we have a valid ID to actually remove
        final String last = contentUri.getLastPathSegment();
        if (last == null) {
            throw new IllegalArgumentException("No path segments to remove");
        } else {
            Long.parseLong(last);
        }
        final List<String> segments = contentUri.getPathSegments();
        final Uri.Builder builder = contentUri.buildUpon();
        builder.path(null);
        for (int i = 0; i < segments.size() - 1; i++) {
            builder.appendPath(segments.get(i));
        }
        return builder.build();
    }

    public Context getContext(){
        return RegistryContentProvider.getInstance().getContext();
    }

}
