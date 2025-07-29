package com.example.modelload;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DBHandler extends SQLiteOpenHelper {

    private static final String DB_NAME = "embeddingdb";

    private static final int DB_VERSION = 1;

    private static final String TABLE_NAME = "videoembeddings";

    private static final String ID_COL = "id";

    private static final String FILENAME = "filename";

    private static final String TIMESTAMPS = "timestamps";

    private static final String EMBEDDINGS = "embeddings";

    public DBHandler(Context context) {
        super(context, DB_NAME, null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query = "CREATE TABLE " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + FILENAME + " TEXT, "
                + TIMESTAMPS + " TEXT, "
                + EMBEDDINGS + " BLOB" +
                ")";

        db.execSQL(query);
        Log.d("debug", "db: table created - // "+query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void addNewEmbedding(String filename, long[] timestamps, float[][] embeddings) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        Log.d("debug", "addNewEmbedding: "+get2DArrayAsString(embeddings));
        values.put(FILENAME, filename);
        values.put(TIMESTAMPS, Arrays.toString(timestamps));
        values.put(EMBEDDINGS, Arrays.deepToString(embeddings).getBytes(StandardCharsets.UTF_8));

        long rowId = db.insertOrThrow(TABLE_NAME, null, values);
        Log.d("debug", "inserted at: "+rowId);
        db.close();
    }

    public ArrayList<EmbeddingModel> getAllEmbeddings() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        ArrayList<EmbeddingModel> embeddingList = new ArrayList<>();

        if (cursor.moveToFirst()) {
            while (cursor.moveToNext()) {
                embeddingList.add(new EmbeddingModel(
                        cursor.getString(1),
                        parseArray(cursor.getString(2)),
                        parse2DArray(cursor.getString(3))
                ));
            }
        }

        cursor.close();
        return embeddingList;
    }

    public EmbeddingModel getSingleEmbedding(String filename) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + "filename = " + "'" + filename + "'", null);

        if (cursor.moveToFirst()) {
            return new EmbeddingModel(
                    cursor.getString(1),
                    parseArray(cursor.getString(2)),
                    parse2DArray(new String(cursor.getBlob(3), StandardCharsets.UTF_8))
            );
        }

        return null;
    }

    private String get2DArrayAsString(float[][] data) {
        String res = "[";

        for (float[] row: data) {
            res += Arrays.toString(row);
        }

        res += "]";
        return res;
    }

    private static long[] parseArray(String input) {
        input = input.trim().replaceAll("[\\[\\]]", "");
        String[] values = input.split(", ");
        long[] result = new long[values.length];

        for (int i=0; i<values.length; i++) {
            result[i] = Long.parseLong(values[i]);
        }

        return result;
    }

    public static float[][] parse2DArray(String input) {
        try {
            Log.d("debug", "input: "+input);
            input = input.trim();

            if (input.startsWith("["))
                input = input.substring(1);
            if (input.endsWith("]"))
                input = input.substring(0, input.length() - 1);

            List<float[]> rows = new ArrayList<>();
            String[] rowStrings = input.split("\\],\\s*\\[");

            for (String row : rowStrings) {
                row = row.replaceAll("[\\[\\]]", "");
                String[] nums = row.split("\\s*,\\s*");
                float[] floats = new float[nums.length];
                for (int i = 0; i < nums.length; i++) {
                    floats[i] = Float.parseFloat(nums[i]);
                }
                rows.add(floats);
            }

            return rows.toArray(new float[0][]);
        } catch (Exception e) {
            Log.e("debug", "error! "+e.getMessage());
            return new float[1][];
        }
    }
}