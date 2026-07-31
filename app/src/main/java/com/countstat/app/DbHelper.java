package com.countstat.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 计件记录的本地 SQLite 数据库（v2）。
 *
 * 表 records：
 *   date        TEXT PRIMARY KEY  记录日期 yyyy-MM-dd
 *   machines    TEXT              机器明细 JSON 数组 [{name,qty,price}]
 *   updated_at  INTEGER           最后更新时间戳，用于同步比对
 *
 * 同一日期只保留一条，保存走 REPLACE 语义。
 */
public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "countstat.db";
    private static final int DB_VERSION = 2;

    public static final String TABLE = "records";
    public static final String COL_DATE = "date";
    public static final String COL_MACHINES = "machines";
    public static final String COL_UPDATED = "updated_at";

    private final Context context;

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_DATE + " TEXT PRIMARY KEY," +
                COL_MACHINES + " TEXT NOT NULL," +
                COL_UPDATED + " INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 原型阶段直接重建；真实场景应做迁移
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /** 插入或更新一条记录（以日期为主键）。 */
    public void upsert(Record record) {
        SQLiteDatabase db = getWritableDatabase();
        db.replace(TABLE, null, toValues(record));
    }

    /** 批量插入，用于首次初始化示例数据。 */
    public void bulkInsert(List<Record> records) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Record record : records) {
                db.replace(TABLE, null, toValues(record));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 删除指定日期的记录，返回受影响行数。 */
    public int delete(String date) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE, COL_DATE + " = ?", new String[]{date});
    }

    /** 读取全部记录（按日期降序）。 */
    public List<Record> getAllRecords() {
        List<Record> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null, null, null, null, null, COL_DATE + " DESC");
        try {
            while (cursor.moveToNext()) {
                records.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return records;
    }

    /** 当前记录条数。 */
    public int count() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /** 数据库文件路径，用于 WebDAV 备份/恢复。 */
    public java.io.File getDbFile() {
        return context.getDatabasePath(DB_NAME);
    }

    private ContentValues toValues(Record record) {
        ContentValues values = new ContentValues();
        values.put(COL_DATE, record.date);
        values.put(COL_MACHINES, machinesToJson(record));
        values.put(COL_UPDATED, record.updatedAt <= 0 ? System.currentTimeMillis() : record.updatedAt);
        return values;
    }

    private Record fromCursor(Cursor cursor) {
        Record record = new Record();
        record.date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
        record.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED));
        jsonToMachines(cursor.getString(cursor.getColumnIndexOrThrow(COL_MACHINES)), record);
        return record;
    }

    private String machinesToJson(Record record) {
        JSONArray array = new JSONArray();
        try {
            for (Record.Machine m : record.machines) {
                JSONObject obj = new JSONObject();
                obj.put("name", m.name);
                obj.put("qty", m.quantity);
                obj.put("price", m.unitPrice);
                array.put(obj);
            }
        } catch (Exception ignored) {
        }
        return array.toString();
    }

    private void jsonToMachines(String json, Record record) {
        record.machines.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Record.Machine m = new Record.Machine();
                m.name = obj.optString("name", "M" + (i + 1));
                m.quantity = obj.optInt("qty", 0);
                m.unitPrice = obj.optDouble("price", 0.35);
                record.machines.add(m);
            }
        } catch (Exception ignored) {
        }
    }
}
