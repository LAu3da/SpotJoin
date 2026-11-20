package com.example.spotjoin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.core.app.ActivityCompat;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {
    private GoogleMap mMap;
    private LocationManager locationManager;
    private LatLng currentLocation = new LatLng(36.5311, 136.6284);
    private LatLng selectedLocation = null;
    private Marker selectedMarker = null;
    private boolean isFirstLocation = true;

    private View mapContainer;
    private View listContainer;
    private Button tabMapButton;
    private Button tabListButton;
    private Button fetchWebButton;
    private ListView listView;

    private Button filterAllBtn, filterJoinedBtn, filterOfficialBtn, filterRecruitBtn;
    private int currentFilterMode = 0;

    private List<EventItem> allEventList = new ArrayList<>();
    private List<EventItem> displayedEventList = new ArrayList<>();
    private EventAdapter eventAdapter;
    private Spinner spinnerSortBy;
    private Spinner spinnerOrder;

    // マーカーID -> そのピンに紐づくイベントIDのリスト（同一場所の複数イベント対応）
    private final Map<String, List<Integer>> markerEventIdsMap = new HashMap<>();

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    static class EventItem {
        int id;
        String title;
        String details;
        double lat;
        double lng;
        int participants;
        int maxParticipants;
        String datetime;
        String endDatetime;
        boolean isAllDay;
        String category;
        boolean isJoined;
        boolean isMine;
        String eventUrl;
        float distanceMeters;

        boolean isFull() {
            return maxParticipants > 0 && participants >= maxParticipants;
        }

        boolean isOfficialEvent() {
            return "地域イベント".equals(category);
        }

        boolean hasOfficialUrl() {
            return eventUrl != null && (eventUrl.startsWith("http://") || eventUrl.startsWith("https://"));
        }

        String getFormattedSchedule() {
            if (isAllDay) {
                String cleanStart = datetime.replace(" 終日", "");
                if (endDatetime != null && !endDatetime.isEmpty() && !cleanStart.equals(endDatetime)) {
                    return cleanStart + " 〜 " + endDatetime + "（終日）";
                }
                return datetime;
            }
            if (endDatetime != null && !endDatetime.isEmpty()) {
                return datetime + " 〜 " + endDatetime;
            }
            return datetime;
        }
    }

    static class FetchedEventCandidate {
        String title;
        String normalizedTitle;
        String details;
        LatLng latLng;
        String startDate;
        String endDate;
        String url;
    }

    static class MarkerCluster {
        LatLng anchorLatLng;
        List<EventItem> items = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapContainer = findViewById(R.id.layout_map_container);
        listContainer = findViewById(R.id.layout_list_container);
        tabMapButton = findViewById(R.id.tab_map);
        tabListButton = findViewById(R.id.tab_list);
        fetchWebButton = findViewById(R.id.button_fetch_web_events);
        listView = findViewById(R.id.list_events);

        filterAllBtn = findViewById(R.id.filter_all);
        filterJoinedBtn = findViewById(R.id.filter_joined);
        filterOfficialBtn = findViewById(R.id.filter_official);
        filterRecruitBtn = findViewById(R.id.filter_recruit);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        findViewById(R.id.button_post).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LatLng target = (selectedLocation != null) ? selectedLocation : currentLocation;
                Intent intent = new Intent(MainActivity.this, PostActivity.class);
                intent.putExtra("lat", target.latitude);
                intent.putExtra("lng", target.longitude);
                startActivity(intent);
            }
        });

        findViewById(R.id.button_my_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16));
                    Toast.makeText(MainActivity.this, "現在地に移動しました", Toast.LENGTH_SHORT).show();
                }
            }
        });

        fetchWebButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchRealtimeIshikawaEvents();
            }
        });

        filterAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilterMode(0); }
        });
        filterJoinedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilterMode(1); }
        });
        filterOfficialBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilterMode(2); }
        });
        filterRecruitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setFilterMode(3); }
        });

        spinnerSortBy = findViewById(R.id.spinner_sort_by);
        spinnerOrder = findViewById(R.id.spinner_order);

        spinnerSortBy.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"距離順", "開催日時順", "参加人数順"}));
        spinnerOrder.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"昇順（近い・早い）", "降順（遠い・遅い）"}));

        eventAdapter = new EventAdapter();
        listView.setAdapter(eventAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                EventItem item = displayedEventList.get(position);
                switchTab(true, null);
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(item.lat, item.lng), 15));
                }
            }
        });

        AdapterView.OnItemSelectedListener sortListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilterAndSort();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerSortBy.setOnItemSelectedListener(sortListener);
        spinnerOrder.setOnItemSelectedListener(sortListener);

        tabMapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchTab(true, null); }
        });
        tabListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchTab(false, null); }
        });

        fetchRealtimeIshikawaEvents();
    }

    private void setFilterMode(int mode) {
        currentFilterMode = mode;
        Button[] btns = {filterAllBtn, filterJoinedBtn, filterOfficialBtn, filterRecruitBtn};
        for (int i = 0; i < btns.length; i++) {
            if (i == mode) {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0044CC")));
                btns[i].setTextColor(Color.WHITE);
            } else {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                btns[i].setTextColor(Color.parseColor("#1A1A1C"));
            }
        }
        applyFilterAndSort();
    }

    private void switchTab(boolean showMap, List<Integer> highlightEventIds) {
        if (showMap) {
            mapContainer.setVisibility(View.VISIBLE);
            listContainer.setVisibility(View.GONE);
            tabMapButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0044CC")));
            tabMapButton.setTextColor(Color.WHITE);
            tabListButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E5E7EB")));
            tabListButton.setTextColor(Color.parseColor("#1A1A1C"));
            if (mMap != null) {
                loadEventsFromDb();
            }
        } else {
            mapContainer.setVisibility(View.GONE);
            listContainer.setVisibility(View.VISIBLE);
            tabMapButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E5E7EB")));
            tabMapButton.setTextColor(Color.parseColor("#1A1A1C"));
            tabListButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0044CC")));
            tabListButton.setTextColor(Color.WHITE);

            if (highlightEventIds != null && !highlightEventIds.isEmpty()) {
                setFilterMode(0);
            }
            if (mMap != null) {
                loadEventsFromDb();
            }
            if (highlightEventIds != null && !highlightEventIds.isEmpty()) {
                // ピンで選択された場所の複数イベントを一覧の最上部にまとめて表示する
                Set<Integer> targetSet = new HashSet<>(highlightEventIds);
                List<EventItem> prioritized = new ArrayList<>();
                List<EventItem> others = new ArrayList<>();
                for (EventItem item : displayedEventList) {
                    if (targetSet.contains(item.id)) {
                        prioritized.add(item);
                    } else {
                        others.add(item);
                    }
                }
                displayedEventList.clear();
                displayedEventList.addAll(prioritized);
                displayedEventList.addAll(others);
                eventAdapter.notifyDataSetChanged();

                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        listView.setSelection(0);
                    }
                });

                if (prioritized.size() > 1) {
                    Toast.makeText(this, "この開催場所のイベント " + prioritized.size() + "件 を上部にまとめました", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                List<Integer> eventIds = markerEventIdsMap.get(marker.getId());
                if (eventIds != null && !eventIds.isEmpty()) {
                    switchTab(false, eventIds);
                    return true;
                }
                return false;
            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                selectedLocation = latLng;
                if (selectedMarker != null) {
                    selectedMarker.remove();
                }
                selectedMarker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .icon(BitmapDescriptorFactory.fromBitmap(
                                createTagMarkerBitmap("ここに新規募集を作成", "下の青いボタンを押してください", "#C2410C"))));
                Toast.makeText(MainActivity.this, "開催場所を選択しました", Toast.LENGTH_SHORT).show();
            }
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            initCurrentLocation();
        }

        loadEventsFromDb();
    }

    @SuppressLint("MissingPermission")
    private void initCurrentLocation() {
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location bestLocation = (lastGps != null) ? lastGps : lastNet;

        if (bestLocation != null) {
            currentLocation = new LatLng(bestLocation.getLatitude(), bestLocation.getLongitude());
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 13));
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
            }
        }
        selectedLocation = null;
        if (mMap != null) {
            loadEventsFromDb();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @SuppressLint("MissingPermission")
    private void loadEventsFromDb() {
        allEventList.clear();
        markerEventIdsMap.clear();

        if (mMap != null) {
            mMap.clear();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        }

        DatabaseHelper helper = new DatabaseHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();

        String todayStr = new SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(new Date());
        db.execSQL("DELETE FROM events WHERE " +
                        "(length(end_datetime) >= 10 AND substr(end_datetime, 1, 10) < ?) OR " +
                        "(length(end_datetime) < 10 AND length(event_datetime) >= 10 AND substr(event_datetime, 1, 10) < ?)",
                new Object[]{todayStr, todayStr});

        Cursor cursor = db.rawQuery("SELECT id, title, details, latitude, longitude, participants, max_participants, event_datetime, end_datetime, is_all_day, category, is_joined, is_mine, event_url FROM events", null);

        while (cursor.moveToNext()) {
            EventItem item = new EventItem();
            item.id = cursor.getInt(0);
            item.title = cleanEventTitle(cursor.getString(1));
            item.details = cursor.getString(2);
            item.lat = cursor.getDouble(3);
            item.lng = cursor.getDouble(4);
            item.participants = cursor.getInt(5);
            item.maxParticipants = cursor.getInt(6);
            item.datetime = cursor.getString(7);
            item.endDatetime = cursor.getString(8);
            item.isAllDay = (cursor.getInt(9) == 1);
            item.category = cursor.getString(10);
            item.isJoined = (cursor.getInt(11) == 1);
            item.isMine = (cursor.getInt(12) == 1);
            item.eventUrl = cursor.getString(13);

            float[] results = new float[1];
            Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, item.lat, item.lng, results);
            item.distanceMeters = results[0];

            allEventList.add(item);
        }
        cursor.close();
        db.close();

        // 同じ開催場所（半径150m以内）にあるイベントを1つのマーカー（クラスター）にまとめる
        if (mMap != null) {
            List<MarkerCluster> clusters = new ArrayList<>();
            for (EventItem item : allEventList) {
                MarkerCluster matchedCluster = null;
                for (MarkerCluster cluster : clusters) {
                    float[] dist = new float[1];
                    Location.distanceBetween(
                            cluster.anchorLatLng.latitude, cluster.anchorLatLng.longitude,
                            item.lat, item.lng, dist);
                    if (dist[0] < 150f) {
                        matchedCluster = cluster;
                        break;
                    }
                }
                if (matchedCluster != null) {
                    matchedCluster.items.add(item);
                } else {
                    MarkerCluster newCluster = new MarkerCluster();
                    newCluster.anchorLatLng = new LatLng(item.lat, item.lng);
                    newCluster.items.add(item);
                    clusters.add(newCluster);
                }
            }

            for (MarkerCluster cluster : clusters) {
                List<Integer> ids = new ArrayList<>();
                for (EventItem ev : cluster.items) {
                    ids.add(ev.id);
                }

                String markerTitle;
                String subLabel;
                String tagColor;

                if (cluster.items.size() > 1) {
                    // 複数イベントが同じ場所に集中している場合は1つの大きな集約タグにする
                    String spotLabel = buildClusterSpotLabel(cluster.items);
                    markerTitle = spotLabel + "（全" + cluster.items.size() + "件のイベント）";
                    subLabel = cluster.items.get(0).title + " ほか" + (cluster.items.size() - 1) + "件 / 詳細一覧へ";
                    tagColor = "#0F766E";
                } else {
                    EventItem single = cluster.items.get(0);
                    markerTitle = single.title;
                    if (single.isOfficialEvent()) {
                        tagColor = "#047857";
                        subLabel = single.getFormattedSchedule() + " / 詳細を見る";
                    } else if (single.isFull()) {
                        tagColor = "#4B5563";
                        subLabel = single.getFormattedSchedule() + " (満員) / 詳細を見る";
                    } else if (single.isMine) {
                        tagColor = "#6D28D9";
                        String countText = (single.maxParticipants > 0)
                                ? single.participants + "/" + single.maxParticipants + "人"
                                : single.participants + "人";
                        subLabel = single.getFormattedSchedule() + " (" + countText + ") / 詳細を見る";
                    } else {
                        tagColor = "#0044CC";
                        String countText = (single.maxParticipants > 0)
                                ? single.participants + "/" + single.maxParticipants + "人"
                                : single.participants + "人";
                        subLabel = single.getFormattedSchedule() + " (" + countText + ") / 詳細を見る";
                    }
                }

                Bitmap tagBitmap = createTagMarkerBitmap(markerTitle, subLabel, tagColor);
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(cluster.anchorLatLng)
                        .icon(BitmapDescriptorFactory.fromBitmap(tagBitmap)));

                if (marker != null) {
                    markerEventIdsMap.put(marker.getId(), ids);
                }
            }
        }

        applyFilterAndSort();
    }

    /**
     * 同じ場所に集まった複数イベントから「兼六園イベント」「金沢駅周辺イベント」などの集約タイトルを生成
     */
    private String buildClusterSpotLabel(List<EventItem> items) {
        String[] landmarkKeywords = {
                "兼六園", "金沢城", "金沢駅", "21世紀美術館", "香林坊", "近江町市場", "ひがし茶屋街",
                "いしかわ四高記念公園", "しいのき迎賓館", "石川県産業展示館", "野々市市文化会館フォルテ",
                "白山比咩神社", "松任駅", "こまつドーム", "那谷寺", "山代温泉", "山中温泉", "和倉温泉", "輪島"
        };
        for (String landmark : landmarkKeywords) {
            for (EventItem item : items) {
                if ((item.title != null && item.title.contains(landmark))
                        || (item.details != null && item.details.contains(landmark))) {
                    return landmark + " イベント";
                }
            }
        }
        String firstTitle = items.get(0).title;
        if (firstTitle.length() > 12) {
            firstTitle = firstTitle.substring(0, 12) + "…";
        }
        return firstTitle + " 周辺";
    }

    private String cleanEventTitle(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("[「『（(]?\\s*(の)?詳細(情報)?はこちら\\s*[」』）)]?", "");
        cleaned = cleaned.replaceAll("[「『（(]?\\s*詳しくはこちら\\s*[」』）)]?", "");
        cleaned = cleaned.replaceAll("[「『（(]?\\s*詳細を見る\\s*[」』）)]?", "");
        return cleaned.trim();
    }

    private Bitmap createTagMarkerBitmap(String title, String subText, String bgColorHex) {
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(32f);
        titlePaint.setFakeBoldText(true);

        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#F3F4F6"));
        subPaint.setTextSize(24f);

        float textWidth = Math.max(titlePaint.measureText(title), subPaint.measureText(subText));
        int paddingH = 28;
        int paddingV = 16;
        int pointerHeight = 18;
        int width = (int) textWidth + paddingH * 2;
        int boxHeight = 84 + paddingV;
        int totalHeight = boxHeight + pointerHeight;

        Bitmap bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor(bgColorHex));

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);

        RectF rect = new RectF(2, 2, width - 2, boxHeight);
        canvas.drawRoundRect(rect, 16f, 16f, bgPaint);
        canvas.drawRoundRect(rect, 16f, 16f, borderPaint);

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(width / 2f - 14f, boxHeight);
        path.lineTo(width / 2f + 14f, boxHeight);
        path.lineTo(width / 2f, totalHeight);
        path.close();
        canvas.drawPath(path, bgPaint);

        canvas.drawText(title, paddingH, 42f, titlePaint);
        canvas.drawText(subText, paddingH, 76f, subPaint);

        return bitmap;
    }

    private boolean isPastEventFromText(String title, String contextText) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        String combined = Normalizer.normalize(title + " " + contextText, Normalizer.Form.NFKC);

        if (combined.contains("終了しました") || combined.contains("開催終了") || combined.contains("中止となりました")
                || combined.contains("2025") || combined.contains("2024") || combined.contains("2023")
                || combined.contains("令和7") || combined.contains("令和6")) {
            return true;
        }

        Pattern fullDatePattern = Pattern.compile("(20[2-3][0-9])[年/\\.-]([0-1]?[0-9])[月/\\.-]([0-3]?[0-9])日?");
        Matcher mFull = fullDatePattern.matcher(combined);
        Calendar latestDate = null;
        while (mFull.find()) {
            try {
                int y = Integer.parseInt(mFull.group(1));
                int m = Integer.parseInt(mFull.group(2)) - 1;
                int d = Integer.parseInt(mFull.group(3));
                Calendar c = Calendar.getInstance();
                c.set(y, m, d, 23, 59, 59);
                if (latestDate == null || c.after(latestDate)) {
                    latestDate = c;
                }
            } catch (Exception ignored) {}
        }
        if (latestDate != null) {
            return latestDate.before(today);
        }

        List<Calendar> parsedCals = parseMonthDayCalendars(combined, today);
        if (!parsedCals.isEmpty()) {
            Calendar latestMd = parsedCals.get(parsedCals.size() - 1);
            return latestMd.before(today);
        }

        return false;
    }

    private List<Calendar> parseMonthDayCalendars(String normalizedText, Calendar today) {
        List<Calendar> list = new ArrayList<>();
        int currentYear = today.get(Calendar.YEAR);
        int curMonth = today.get(Calendar.MONTH);

        Pattern multiDayPattern = Pattern.compile(
                "([0-1]?[0-9])月([0-3]?[0-9])日(?:\\s*[（(][月火水木金土日祝][）)])?(?:\\s*[・,、〜～\\-−—]\\s*|\\s*)([0-3]?[0-9])日"
        );
        Matcher mMulti = multiDayPattern.matcher(normalizedText);
        if (mMulti.find()) {
            try {
                int m = Integer.parseInt(mMulti.group(1)) - 1;
                int d1 = Integer.parseInt(mMulti.group(2));
                int d2 = Integer.parseInt(mMulti.group(3));
                int targetYear = (curMonth >= Calendar.OCTOBER && m <= Calendar.FEBRUARY) ? currentYear + 1 : currentYear;

                Calendar c1 = Calendar.getInstance();
                c1.set(targetYear, m, d1, 0, 0, 0);
                Calendar c2 = Calendar.getInstance();
                c2.set(targetYear, m, d2, 23, 59, 59);
                list.add(c1);
                list.add(c2);
                return list;
            } catch (Exception ignored) {}
        }

        Pattern mdPattern = Pattern.compile("([0-1]?[0-9])月([0-3]?[0-9])日");
        Matcher mMd = mdPattern.matcher(normalizedText);
        while (mMd.find()) {
            try {
                int m = Integer.parseInt(mMd.group(1)) - 1;
                int d = Integer.parseInt(mMd.group(2));
                int targetYear = (curMonth >= Calendar.OCTOBER && m <= Calendar.FEBRUARY) ? currentYear + 1 : currentYear;

                Calendar c = Calendar.getInstance();
                c.set(targetYear, m, d, 23, 59, 59);
                list.add(c);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private String[] extractScheduleDates(String title, String contextText) {
        Calendar today = Calendar.getInstance();
        int currentYear = today.get(Calendar.YEAR);
        String combined = Normalizer.normalize(title + " " + contextText, Normalizer.Form.NFKC);

        Pattern fullDatePattern = Pattern.compile("(20[2-3][0-9])[年/\\.-]([0-1]?[0-9])[月/\\.-]([0-3]?[0-9])日?");
        Matcher mFull = fullDatePattern.matcher(combined);
        List<String> fullDates = new ArrayList<>();
        while (mFull.find()) {
            try {
                int y = Integer.parseInt(mFull.group(1));
                int m = Integer.parseInt(mFull.group(2));
                int d = Integer.parseInt(mFull.group(3));
                fullDates.add(String.format(Locale.JAPAN, "%04d/%02d/%02d", y, m, d));
            } catch (Exception ignored) {}
        }
        if (!fullDates.isEmpty()) {
            String start = fullDates.get(0);
            String end = fullDates.get(fullDates.size() - 1);
            return new String[]{start + " 終日", start.equals(end) ? "" : end};
        }

        List<Calendar> mdCals = parseMonthDayCalendars(combined, today);
        if (!mdCals.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN);
            String start = sdf.format(mdCals.get(0).getTime());
            String end = sdf.format(mdCals.get(mdCals.size() - 1).getTime());
            return new String[]{start + " 終日", start.equals(end) ? "" : end};
        }

        Pattern slashMulti = Pattern.compile("([0-1]?[0-9])/([0-3]?[0-9])(?:\\s*[（(][月火水木金土日祝][）)])?(?:\\s*[・,、〜～\\-−—]\\s*)([0-3]?[0-9])");
        Matcher mSlash = slashMulti.matcher(combined);
        if (mSlash.find()) {
            try {
                int m = Integer.parseInt(mSlash.group(1));
                int d1 = Integer.parseInt(mSlash.group(2));
                int d2 = Integer.parseInt(mSlash.group(3));
                int curMonth = today.get(Calendar.MONTH) + 1;
                int targetYear = (curMonth >= 10 && m <= 2) ? currentYear + 1 : currentYear;
                String start = String.format(Locale.JAPAN, "%04d/%02d/%02d", targetYear, m, d1);
                String end = String.format(Locale.JAPAN, "%04d/%02d/%02d", targetYear, m, d2);
                return new String[]{start + " 終日", start.equals(end) ? "" : end};
            } catch (Exception ignored) {}
        }

        return new String[]{"開催日程は公式ページ参照", ""};
    }

    private String normalizeEventTitle(String rawTitle) {
        String norm = Normalizer.normalize(rawTitle, Normalizer.Form.NFKC);
        norm = norm.replaceAll("【[^】]*】", "");
        norm = norm.replaceAll("\\([^)]*\\)", "");
        norm = norm.replaceAll("[\\s・＆&「」『』〜～\\-−—_!！?？]", "");
        return norm.toLowerCase(Locale.JAPAN);
    }

    /**
     * 「まったく同じイベント」が複数サイトに載っている場合のみ重複除外する
     * （同じ兼六園でも内容が違う別イベントは残し、マップ上で1本の集約ピンにまとめる）
     */
    private boolean isSameEventDuplicate(List<FetchedEventCandidate> existingList, FetchedEventCandidate candidate) {
        for (FetchedEventCandidate existing : existingList) {
            if (!candidate.normalizedTitle.isEmpty() && !existing.normalizedTitle.isEmpty()) {
                if (existing.normalizedTitle.equals(candidate.normalizedTitle)) {
                    return true;
                }
                if (candidate.normalizedTitle.length() >= 8 && existing.normalizedTitle.contains(candidate.normalizedTitle)) {
                    return true;
                }
                if (existing.normalizedTitle.length() >= 8 && candidate.normalizedTitle.contains(existing.normalizedTitle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void fetchRealtimeIshikawaEvents() {
        fetchWebButton.setEnabled(false);
        fetchWebButton.setText("県内検索中...");

        networkExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String todayStr = new SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(new Date());
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.JAPAN);
                Set<String> seenUrls = new HashSet<>();
                List<FetchedEventCandidate> mergedCandidates = new ArrayList<>();

                DatabaseHelper helper = new DatabaseHelper(MainActivity.this);
                SQLiteDatabase db = helper.getWritableDatabase();
                db.execSQL("DELETE FROM events WHERE category = '地域イベント'");

                String[][] sources = {
                        {"https://www.hot-ishikawa.jp/event/", "https://www.hot-ishikawa.jp", "石川県公式観光情報"},
                        {"https://www.kanazawa-kankoukyoukai.or.jp/event/", "https://www.kanazawa-kankoukyoukai.or.jp", "金沢市観光協会"}
                };

                Pattern linkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']*event/detail[^\"']*|[^\"']*event/[0-9]+[^\"']*)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

                for (String[] src : sources) {
                    if (mergedCandidates.size() >= 25) break;
                    try {
                        URL url = new URL(src[0]);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(6000);
                        conn.setReadTimeout(6000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)");

                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder html = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                html.append(line).append(" ");
                            }
                            reader.close();

                            String fullHtml = html.toString();
                            Matcher matcher = linkPattern.matcher(fullHtml);
                            while (matcher.find() && mergedCandidates.size() < 25) {
                                String href = matcher.group(1).trim();
                                String cleanTitle = cleanEventTitle(matcher.group(2));

                                if (cleanTitle.length() < 4 || cleanTitle.length() > 60) continue;
                                String fullUrl = href.startsWith("http") ? href : (href.startsWith("/") ? src[1] + href : src[1] + "/" + href);
                                if (seenUrls.contains(fullUrl)) continue;
                                seenUrls.add(fullUrl);

                                int startIdx = Math.max(0, matcher.start() - 300);
                                int endIdx = Math.min(fullHtml.length(), matcher.end() + 450);
                                String surroundingText = fullHtml.substring(startIdx, endIdx).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ");

                                if (isPastEventFromText(cleanTitle, surroundingText)) {
                                    continue;
                                }

                                String[] extractedDates = extractScheduleDates(cleanTitle, surroundingText);
                                LatLng latLng = resolveIshikawaCoordinate(geocoder, cleanTitle, surroundingText, mergedCandidates.size());

                                FetchedEventCandidate candidate = new FetchedEventCandidate();
                                candidate.title = cleanTitle;
                                candidate.normalizedTitle = normalizeEventTitle(cleanTitle);
                                candidate.details = src[2] + " 掲載イベント（「公式ページ」から詳細確認可能）";
                                candidate.latLng = latLng;
                                candidate.startDate = extractedDates[0];
                                candidate.endDate = extractedDates[1];
                                candidate.url = fullUrl;

                                if (!isSameEventDuplicate(mergedCandidates, candidate)) {
                                    mergedCandidates.add(candidate);
                                }
                            }
                        }
                        conn.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                for (FetchedEventCandidate ev : mergedCandidates) {
                    db.execSQL("INSERT INTO events (title, details, latitude, longitude, participants, max_participants, event_datetime, end_datetime, is_all_day, category, is_joined, is_mine, event_url) VALUES (?, ?, ?, ?, 0, 0, ?, ?, 1, '地域イベント', 0, 0, ?)",
                            new Object[]{ev.title, ev.details, ev.latLng.latitude, ev.latLng.longitude, ev.startDate, ev.endDate, ev.url});
                }

                int addedCount = mergedCandidates.size();
                final boolean isLiveFetched = (addedCount > 0);

                if (addedCount == 0) {
                    String[][] fallbackEvents = {
                            {"Big Apple in Nonoichi 2026", "【野々市市】野々市市文化会館フォルテで開催されるビッグバンドジャズ祭", "36.5293", "136.6143", "11:00", "17:00"},
                            {"全国鍋サミット＆おでん博覧会", "【金沢市】石川県産業展示館1号館（入場無料・県内各地の鍋が集結）", "36.5821", "136.5936", "10:00", "16:00"},
                            {"金沢城・兼六園四季物語 秋の段", "【金沢市】金沢城公園・兼六園の夜間ライトアップ特別開園", "36.5621", "136.6626", "17:30", "20:45"},
                            {"ひがし茶屋街 伝統工芸と夜間散策", "【金沢市】ひがし茶屋街周辺での工芸展示・町家公開イベント", "36.5724", "136.6669", "10:00", "19:00"},
                            {"金沢21世紀美術館 市民ギャラリー特別展", "【金沢市】金沢21世紀美術館 市民ギャラリーA・B", "36.5608", "136.6582", "10:00", "18:00"},
                            {"近江町市場 秋の味覚・旬鮮まつり", "【金沢市】近江町市場内特設会場での海鮮・地元食材フェア", "36.5715", "136.6565", "09:00", "15:00"},
                            {"白山比咩神社 紅葉と門前市", "【白山市】白山比咩神社表参道・おはぎ屋通り周辺", "36.4357", "136.6358", "09:30", "15:30"},
                            {"松任駅南広場 週末クラフトマルシェ", "【白山市】白山市松任駅南口広場でのハンドメイド市", "36.5218", "136.5661", "10:00", "16:00"},
                            {"こまつドーム 地域物産＆秋マルシェ", "【小松市】いしかわ子ども交流センター小松館周辺", "36.3789", "136.4678", "10:00", "16:00"},
                            {"那谷寺 奇岩遊仙境・紅葉特別拝観", "【小松市】那谷寺境内での紅葉ライトアップと特別公開", "36.3133", "136.4203", "09:15", "16:30"},
                            {"山代温泉 紅葉ライトアップ＆湯めぐり", "【加賀市】山代温泉・古総湯および湯の曲輪周辺で開催", "36.3218", "136.3642", "17:00", "21:00"},
                            {"山中温泉 鶴仙渓・秋の川床カフェ", "【加賀市】鶴仙渓遊歩道・あやとりはし付近", "36.2458", "136.3742", "09:30", "16:00"},
                            {"和倉温泉・七尾湾 復興応援マルシェ", "【七尾市】七尾フィッシャーマンズ・ワーフ能登食祭市場", "37.0468", "136.9682", "10:00", "15:30"},
                            {"輪島朝市 出張復興マーケット", "【輪島市】輪島市内特設会場での地元海産物・輪島塗販売", "37.3906", "136.8992", "09:00", "14:00"}
                    };

                    for (String[] ev : fallbackEvents) {
                        String directSearchUrl = "https://www.google.com/search?q=" + Uri.encode("石川県 " + ev[0] + " 公式");
                        db.execSQL("INSERT INTO events (title, details, latitude, longitude, participants, max_participants, event_datetime, end_datetime, is_all_day, category, is_joined, is_mine, event_url) VALUES (?, ?, ?, ?, 0, 0, ?, ?, 0, '地域イベント', 0, 0, ?)",
                                new Object[]{ev[0], ev[1], Double.parseDouble(ev[2]), Double.parseDouble(ev[3]), todayStr + " " + ev[4], ev[5], directSearchUrl});
                        addedCount++;
                    }
                }
                db.close();

                final int finalCount = addedCount;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        fetchWebButton.setEnabled(true);
                        fetchWebButton.setText("県内イベント更新");
                        String msg = isLiveFetched
                                ? "石川県全域の開催中・予定イベント" + finalCount + "件を取得しました"
                                : "石川県全域の開催イベント" + finalCount + "件を読み込みました";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        loadEventsFromDb();
                    }
                });
            }
        });
    }

    private LatLng resolveIshikawaCoordinate(Geocoder geocoder, String title, String desc, int index) {
        String combined = title + " " + desc;
        String[] ishikawaSpots = {
                "輪島", "珠洲", "穴水", "能登町", "七尾", "和倉温泉", "中能登", "羽咋", "志賀", "宝達志水",
                "かほく", "津幡", "内灘", "兼六園", "金沢城", "金沢駅", "21世紀美術館", "香林坊", "近江町", "ひがし茶屋街",
                "野々市", "白山", "鶴来", "松任", "能美", "川北", "小松", "那谷寺", "加賀", "山代温泉", "山中温泉", "片山津"
        };
        for (String spot : ishikawaSpots) {
            if (combined.contains(spot)) {
                try {
                    List<Address> list = geocoder.getFromLocationName("石川県 " + spot, 1);
                    if (list != null && !list.isEmpty()) {
                        return new LatLng(list.get(0).getLatitude(), list.get(0).getLongitude());
                    }
                } catch (Exception ignored) {}
            }
        }
        double[][] fallbackCoords = {
                {36.5780, 136.6481},
                {36.5293, 136.6143},
                {36.5150, 136.5656},
                {36.4083, 136.4456},
                {36.3025, 136.3150},
                {37.0431, 136.9675},
                {36.8906, 136.7792},
                {37.3906, 136.8992}
        };
        double[] c = fallbackCoords[index % fallbackCoords.length];
        return new LatLng(c[0], c[1]);
    }

    private void applyFilterAndSort() {
        displayedEventList.clear();
        for (EventItem item : allEventList) {
            if (currentFilterMode == 0) {
                displayedEventList.add(item);
            } else if (currentFilterMode == 1) {
                if (item.isJoined || item.isMine) displayedEventList.add(item);
            } else if (currentFilterMode == 2) {
                if (item.isOfficialEvent()) displayedEventList.add(item);
            } else if (currentFilterMode == 3) {
                if (!item.isOfficialEvent()) displayedEventList.add(item);
            }
        }

        if (spinnerSortBy != null && spinnerOrder != null) {
            int sortType = spinnerSortBy.getSelectedItemPosition();
            boolean isAscending = (spinnerOrder.getSelectedItemPosition() == 0);

            Collections.sort(displayedEventList, new Comparator<EventItem>() {
                @Override
                public int compare(EventItem a, EventItem b) {
                    int cmp;
                    if (sortType == 0) {
                        cmp = Float.compare(a.distanceMeters, b.distanceMeters);
                    } else if (sortType == 1) {
                        cmp = a.datetime.compareTo(b.datetime);
                    } else {
                        cmp = Integer.compare(a.participants, b.participants);
                    }
                    return isAscending ? cmp : -cmp;
                }
            });
        }
        eventAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        if (isFirstLocation && mMap != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 13));
            isFirstLocation = false;
            loadEventsFromDb();
        }
    }

    class EventAdapter extends BaseAdapter {
        @Override
        public int getCount() { return displayedEventList.size(); }
        @Override
        public Object getItem(int position) { return displayedEventList.get(position); }
        @Override
        public long getItemId(int position) { return displayedEventList.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_event, parent, false);
            }

            EventItem item = displayedEventList.get(position);
            TextView badgeView = convertView.findViewById(R.id.text_event_badge);
            TextView datetimeView = convertView.findViewById(R.id.text_event_datetime);
            TextView titleView = convertView.findViewById(R.id.text_event_title);
            TextView metaView = convertView.findViewById(R.id.text_event_meta);
            TextView detailsView = convertView.findViewById(R.id.text_event_details);
            Button joinButton = convertView.findViewById(R.id.button_join);

            Button routeBtn = convertView.findViewById(R.id.button_route);
            Button officialUrlBtn = convertView.findViewById(R.id.button_official_url);
            Button shareBtn = convertView.findViewById(R.id.button_share_event);

            View ownerActions = convertView.findViewById(R.id.layout_owner_actions);
            Button editButton = convertView.findViewById(R.id.button_edit_event);
            Button deleteButton = convertView.findViewById(R.id.button_delete_event);

            if (item.isOfficialEvent()) {
                badgeView.setText("イベント開催中");
                badgeView.setBackgroundColor(Color.parseColor("#ECFDF5"));
                badgeView.setTextColor(Color.parseColor("#047857"));
            } else if (item.isFull() && !item.isMine) {
                badgeView.setText("満員締切");
                badgeView.setBackgroundColor(Color.parseColor("#FEE2E2"));
                badgeView.setTextColor(Color.parseColor("#B91C1C"));
            } else if (item.isMine) {
                badgeView.setText("主催中");
                badgeView.setBackgroundColor(Color.parseColor("#EDE9FE"));
                badgeView.setTextColor(Color.parseColor("#6D28D9"));
            } else {
                badgeView.setText("個人募集");
                badgeView.setBackgroundColor(Color.parseColor("#E8F0FE"));
                badgeView.setTextColor(Color.parseColor("#0044CC"));
            }

            datetimeView.setText(item.getFormattedSchedule());
            titleView.setText(item.title);
            String distStr = (item.distanceMeters < 1000)
                    ? String.format("%.0fm", item.distanceMeters)
                    : String.format("%.1fkm", item.distanceMeters / 1000f);

            if (item.isOfficialEvent()) {
                metaView.setText("現在地から約 " + distStr + " ・ イベント開催中");
                metaView.setTextColor(Color.parseColor("#047857"));
            } else {
                String participantStatus;
                if (item.maxParticipants > 0) {
                    int remain = Math.max(0, item.maxParticipants - item.participants);
                    participantStatus = "参加 " + item.participants + "/" + item.maxParticipants + "人" +
                            (remain == 0 ? "（満員）" : "（あと" + remain + "枠）");
                } else {
                    participantStatus = "参加 " + item.participants + "人（定員なし）";
                }
                metaView.setText("約 " + distStr + " ・ " + participantStatus);
                metaView.setTextColor(item.isFull() ? Color.parseColor("#B91C1C") : Color.parseColor("#0044CC"));
            }
            detailsView.setText(item.details);

            routeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri dirUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + item.lat + "," + item.lng);
                    startActivity(new Intent(Intent.ACTION_VIEW, dirUri));
                }
            });

            if (item.hasOfficialUrl()) {
                officialUrlBtn.setVisibility(View.VISIBLE);
                officialUrlBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Uri webUri = Uri.parse(item.eventUrl);
                        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                    }
                });
            } else {
                officialUrlBtn.setVisibility(View.GONE);
            }

            shareBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("【SpotJoin イベント案内】\n");
                    sb.append("イベント名：").append(item.title).append("\n");
                    sb.append("日時：").append(item.getFormattedSchedule()).append("\n");
                    sb.append("詳細：").append(item.details).append("\n");
                    if (item.hasOfficialUrl()) {
                        sb.append("詳細ページ：").append(item.eventUrl).append("\n");
                    }
                    sb.append("場所（Googleマップ）：\nhttps://www.google.com/maps/search/?api=1&query=").append(item.lat).append(",").append(item.lng);

                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                    startActivity(Intent.createChooser(sendIntent, "友達にイベントを共有"));
                }
            });

            if (item.isMine) {
                joinButton.setVisibility(View.GONE);
                ownerActions.setVisibility(View.VISIBLE);
            } else if (item.isOfficialEvent()) {
                joinButton.setVisibility(View.VISIBLE);
                ownerActions.setVisibility(View.GONE);
                joinButton.setEnabled(true);
                joinButton.setText("地図で見る");
                joinButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#047857")));
                joinButton.setTextColor(Color.WHITE);
                joinButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchTab(true, null);
                        if (mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(item.lat, item.lng), 15));
                        }
                    }
                });
            } else {
                joinButton.setVisibility(View.VISIBLE);
                ownerActions.setVisibility(View.GONE);

                if (item.isJoined) {
                    joinButton.setEnabled(true);
                    joinButton.setText("参加済み");
                    joinButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E5E7EB")));
                    joinButton.setTextColor(Color.parseColor("#374151"));
                } else if (item.isFull()) {
                    joinButton.setEnabled(false);
                    joinButton.setText("満員締切");
                    joinButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D1D5DB")));
                    joinButton.setTextColor(Color.parseColor("#4B5563"));
                } else {
                    joinButton.setEnabled(true);
                    joinButton.setText("参加する");
                    joinButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0044CC")));
                    joinButton.setTextColor(Color.WHITE);
                }

                joinButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DatabaseHelper helper = new DatabaseHelper(MainActivity.this);
                        SQLiteDatabase db = helper.getWritableDatabase();
                        if (item.isJoined) {
                            db.execSQL("UPDATE events SET participants = MAX(1, participants - 1), is_joined = 0 WHERE id = ?", new Object[]{item.id});
                            Toast.makeText(MainActivity.this, "「" + item.title + "」の参加をキャンセルしました", Toast.LENGTH_SHORT).show();
                        } else {
                            if (item.maxParticipants > 0 && item.participants >= item.maxParticipants) {
                                Toast.makeText(MainActivity.this, "定員に達しました", Toast.LENGTH_SHORT).show();
                            } else {
                                db.execSQL("UPDATE events SET participants = participants + 1, is_joined = 1 WHERE id = ?", new Object[]{item.id});
                                Toast.makeText(MainActivity.this, "「" + item.title + "」に参加登録しました", Toast.LENGTH_SHORT).show();
                            }
                        }
                        db.close();
                        loadEventsFromDb();
                    }
                });
            }

            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, PostActivity.class);
                    intent.putExtra("edit_id", item.id);
                    intent.putExtra("edit_title", item.title);
                    intent.putExtra("edit_details", item.details);
                    intent.putExtra("edit_datetime", item.datetime);
                    intent.putExtra("edit_end_datetime", item.endDatetime);
                    intent.putExtra("edit_is_all_day", item.isAllDay);
                    intent.putExtra("edit_max_participants", item.maxParticipants);
                    startActivity(intent);
                }
            });

            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("募集の削除")
                            .setMessage("「" + item.title + "」を削除してもよろしいですか？")
                            .setPositiveButton("削除する", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    DatabaseHelper helper = new DatabaseHelper(MainActivity.this);
                                    SQLiteDatabase db = helper.getWritableDatabase();
                                    db.execSQL("DELETE FROM events WHERE id = ?", new Object[]{item.id});
                                    db.close();
                                    Toast.makeText(MainActivity.this, "募集を削除しました", Toast.LENGTH_SHORT).show();
                                    loadEventsFromDb();
                                }
                            })
                            .setNegativeButton("キャンセル", null)
                            .show();
                }
            });

            return convertView;
        }
    }
}