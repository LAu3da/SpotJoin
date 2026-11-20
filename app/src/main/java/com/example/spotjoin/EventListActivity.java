package com.example.spotjoin;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EventListActivity extends AppCompatActivity {
    private double myLat;
    private double myLng;
    private List<EventItem> eventList = new ArrayList<>();
    private EventAdapter adapter;
    private Spinner spinnerSortBy;
    private Spinner spinnerOrder;

    // 1件分のイベントデータ構造
    static class EventItem {
        int id;
        String title;
        String details;
        int participants;
        float distanceMeters;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        myLat = getIntent().getDoubleExtra("lat", 36.5311);
        myLng = getIntent().getDoubleExtra("lng", 136.6284);

        spinnerSortBy = findViewById(R.id.spinner_sort_by);
        spinnerOrder = findViewById(R.id.spinner_order);
        ListView listView = findViewById(R.id.list_events);
        Button backButton = findViewById(R.id.button_back_map);

        // プルダウンの選択肢設定
        ArrayAdapter<String> sortByAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"距離順", "参加人数（規模）順"});
        spinnerSortBy.setAdapter(sortByAdapter);

        ArrayAdapter<String> orderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"昇順（近い・少ない順）", "降順（遠い・多い順）"});
        spinnerOrder.setAdapter(orderAdapter);

        adapter = new EventAdapter();
        listView.setAdapter(adapter);

        // 並び替え条件が変更されたら即座に再ソート
        AdapterView.OnItemSelectedListener sortListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortAndRefresh();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerSortBy.setOnItemSelectedListener(sortListener);
        spinnerOrder.setOnItemSelectedListener(sortListener);

        backButton.setOnClickListener(v -> finish());

        loadEventsFromDb();
    }

    private void loadEventsFromDb() {
        eventList.clear();
        DatabaseHelper helper = new DatabaseHelper(this);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, title, details, latitude, longitude, participants FROM events", null);

        while (cursor.moveToNext()) {
            EventItem item = new EventItem();
            item.id = cursor.getInt(0);
            item.title = cursor.getString(1);
            item.details = cursor.getString(2);
            double evLat = cursor.getDouble(3);
            double evLng = cursor.getDouble(4);
            item.participants = cursor.getInt(5);

            // 現在地からの距離（メートル）を計算
            float[] results = new float[1];
            Location.distanceBetween(myLat, myLng, evLat, evLng, results);
            item.distanceMeters = results[0];

            eventList.add(item);
        }
        cursor.close();
        db.close();

        sortAndRefresh();
    }

    private void sortAndRefresh() {
        boolean isByDistance = (spinnerSortBy.getSelectedItemPosition() == 0);
        boolean isAscending = (spinnerOrder.getSelectedItemPosition() == 0);

        Collections.sort(eventList, new Comparator<EventItem>() {
            @Override
            public int compare(EventItem a, EventItem b) {
                int cmp;
                if (isByDistance) {
                    cmp = Float.compare(a.distanceMeters, b.distanceMeters);
                } else {
                    cmp = Integer.compare(a.participants, b.participants);
                }
                return isAscending ? cmp : -cmp; // 降順の場合は符号を反転
            }
        });
        adapter.notifyDataSetChanged();
    }

    // リスト表示＆「参加する」ボタンの制御
    class EventAdapter extends BaseAdapter {
        @Override
        public int getCount() { return eventList.size(); }
        @Override
        public Object getItem(int position) { return eventList.get(position); }
        @Override
        public long getItemId(int position) { return eventList.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(EventListActivity.this).inflate(R.layout.item_event, parent, false);
            }

            EventItem item = eventList.get(position);
            TextView titleView = convertView.findViewById(R.id.text_event_title);
            TextView metaView = convertView.findViewById(R.id.text_event_meta);
            TextView detailsView = convertView.findViewById(R.id.text_event_details);
            Button joinButton = convertView.findViewById(R.id.button_join);

            titleView.setText(item.title);
            String distStr = (item.distanceMeters < 1000)
                    ? String.format("%.0fm", item.distanceMeters)
                    : String.format("%.1fkm", item.distanceMeters / 1000f);
            metaView.setText("現在地から約 " + distStr + " ／ 参加人数: " + item.participants + "人");
            detailsView.setText(item.details);

            joinButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // DBの参加人数を+1する
                    DatabaseHelper helper = new DatabaseHelper(EventListActivity.this);
                    SQLiteDatabase db = helper.getWritableDatabase();
                    db.execSQL("UPDATE events SET participants = participants + 1 WHERE id = ?", new Object[]{item.id});
                    db.close();

                    Toast.makeText(EventListActivity.this, "「" + item.title + "」に参加登録しました！", Toast.LENGTH_SHORT).show();
                    loadEventsFromDb(); // リストを再読み込みして人数と並び順を更新
                }
            });

            return convertView;
        }
    }
}