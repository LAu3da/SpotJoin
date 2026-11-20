package com.example.spotjoin;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "spotjoin.db";
    private static final int DATABASE_VERSION = 5;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, " +
                "details TEXT, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "participants INTEGER DEFAULT 1, " +
                "max_participants INTEGER DEFAULT 0, " +
                "event_datetime TEXT, " +
                "end_datetime TEXT DEFAULT '', " +
                "is_all_day INTEGER DEFAULT 0, " +
                "category TEXT DEFAULT '個人募集', " +
                "is_joined INTEGER DEFAULT 0, " +
                "is_mine INTEGER DEFAULT 0, " +
                "event_url TEXT DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS events");
        onCreate(db);
    }
}