package com.example.spotjoin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PostActivity extends AppCompatActivity {
    private double lat;
    private double lng;
    private int editId = -1;

    private Calendar startCal = Calendar.getInstance();
    private Calendar endCal = Calendar.getInstance();

    private Switch switchAllDay;
    private Button btnStartDate, btnStartTime, btnEndDate, btnEndTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        lat = getIntent().getDoubleExtra("lat", 36.5311);
        lng = getIntent().getDoubleExtra("lng", 136.6284);
        editId = getIntent().getIntExtra("edit_id", -1);

        TextView headerText = findViewById(R.id.text_post_header);
        EditText editTitle = findViewById(R.id.edit_title);
        EditText editMax = findViewById(R.id.edit_max_participants);
        EditText editDetails = findViewById(R.id.edit_details);
        Button submitBtn = findViewById(R.id.button_submit_post);

        switchAllDay = findViewById(R.id.switch_all_day);
        btnStartDate = findViewById(R.id.btn_start_date);
        btnStartTime = findViewById(R.id.btn_start_time);
        btnEndDate = findViewById(R.id.btn_end_date);
        btnEndTime = findViewById(R.id.btn_end_time);

        // デフォルトの終了時間は開始の2時間後に設定
        startCal.set(Calendar.MINUTE, 0);
        endCal.setTimeInMillis(startCal.getTimeInMillis());
        endCal.add(Calendar.HOUR_OF_DAY, 2);

        if (editId != -1) {
            headerText.setText("募集内容の編集");
            submitBtn.setText("変更を保存する");
            editTitle.setText(getIntent().getStringExtra("edit_title"));
            editDetails.setText(getIntent().getStringExtra("edit_details"));
            int maxP = getIntent().getIntExtra("edit_max_participants", 0);
            if (maxP > 0) editMax.setText(String.valueOf(maxP));
            boolean isAllDay = getIntent().getBooleanExtra("edit_is_all_day", false);
            switchAllDay.setChecked(isAllDay);
            parseSavedDateTimes(
                    getIntent().getStringExtra("edit_datetime"),
                    getIntent().getStringExtra("edit_end_datetime")
            );
        }

        updateDateTimeButtons();

        switchAllDay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateDateTimeButtons();
            }
        });

        // 開始日付（カレンダー表示からタップで選択）
        btnStartDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(
                        PostActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                                startCal.set(year, month, dayOfMonth);
                                if (endCal.before(startCal)) {
                                    endCal.set(year, month, dayOfMonth);
                                }
                                updateDateTimeButtons();
                            }
                        },
                        startCal.get(Calendar.YEAR),
                        startCal.get(Calendar.MONTH),
                        startCal.get(Calendar.DAY_OF_MONTH)
                ).show();
            }
        });

        // 開始時刻（ドラムロール式ピッカー）
        btnStartTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimePickerDialog dialog = new TimePickerDialog(
                        PostActivity.this,
                        android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                startCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                startCal.set(Calendar.MINUTE, minute);
                                if (endCal.before(startCal)) {
                                    endCal.setTimeInMillis(startCal.getTimeInMillis());
                                    endCal.add(Calendar.HOUR_OF_DAY, 1);
                                }
                                updateDateTimeButtons();
                            }
                        },
                        startCal.get(Calendar.HOUR_OF_DAY),
                        startCal.get(Calendar.MINUTE),
                        true
                );
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }
                dialog.show();
            }
        });

        // 終了日付（カレンダー表示からタップで選択）
        btnEndDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(
                        PostActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                                endCal.set(year, month, dayOfMonth);
                                updateDateTimeButtons();
                            }
                        },
                        endCal.get(Calendar.YEAR),
                        endCal.get(Calendar.MONTH),
                        endCal.get(Calendar.DAY_OF_MONTH)
                ).show();
            }
        });

        // 終了時刻（ドラムロール式ピッカー）
        btnEndTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimePickerDialog dialog = new TimePickerDialog(
                        PostActivity.this,
                        android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                endCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                endCal.set(Calendar.MINUTE, minute);
                                updateDateTimeButtons();
                            }
                        },
                        endCal.get(Calendar.HOUR_OF_DAY),
                        endCal.get(Calendar.MINUTE),
                        true
                );
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }
                dialog.show();
            }
        });

        submitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = editTitle.getText().toString().trim();
                String details = editDetails.getText().toString().trim();
                String maxStr = editMax.getText().toString().trim();

                if (title.isEmpty()) {
                    Toast.makeText(PostActivity.this, "イベント名を入力してください", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (details.isEmpty()) details = "現地集合で楽しく活動しましょう。";

                int maxParticipants = 0;
                if (!maxStr.isEmpty()) {
                    try {
                        maxParticipants = Integer.parseInt(maxStr);
                    } catch (NumberFormatException ignored) {}
                }

                boolean isAllDay = switchAllDay.isChecked();
                String startStr;
                String endStr;

                if (isAllDay) {
                    startStr = formatDate(startCal) + " 終日";
                    endStr = formatDate(endCal);
                } else {
                    startStr = formatDate(startCal) + " " + formatTime(startCal);
                    if (formatDate(startCal).equals(formatDate(endCal))) {
                        endStr = formatTime(endCal);
                    } else {
                        endStr = formatDate(endCal) + " " + formatTime(endCal);
                    }
                }

                DatabaseHelper helper = new DatabaseHelper(PostActivity.this);
                SQLiteDatabase db = helper.getWritableDatabase();

                if (editId != -1) {
                    db.execSQL("UPDATE events SET title = ?, details = ?, max_participants = ?, event_datetime = ?, end_datetime = ?, is_all_day = ? WHERE id = ?",
                            new Object[]{title, details, maxParticipants, startStr, endStr, isAllDay ? 1 : 0, editId});
                    Toast.makeText(PostActivity.this, "募集内容を更新しました", Toast.LENGTH_SHORT).show();
                } else {
                    db.execSQL("INSERT INTO events (title, details, latitude, longitude, participants, max_participants, event_datetime, end_datetime, is_all_day, category, is_joined, is_mine, event_url) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, '個人募集', 1, 1, '')",
                            new Object[]{title, details, lat, lng, maxParticipants, startStr, endStr, isAllDay ? 1 : 0});
                    Toast.makeText(PostActivity.this, "新規イベントを公開しました", Toast.LENGTH_SHORT).show();
                }

                db.close();
                finish();
            }
        });

        findViewById(R.id.button_cancel_post).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
    }

    private void parseSavedDateTimes(String dt, String endDt) {
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN);
            SimpleDateFormat fullFmt = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN);
            if (dt != null) {
                if (dt.contains("終日")) {
                    startCal.setTime(dateFmt.parse(dt.replace(" 終日", "").trim()));
                } else if (dt.length() >= 16) {
                    startCal.setTime(fullFmt.parse(dt.substring(0, 16)));
                }
            }
            if (endDt != null && !endDt.isEmpty()) {
                if (endDt.length() == 10) {
                    endCal.setTime(dateFmt.parse(endDt));
                } else if (endDt.length() >= 16) {
                    endCal.setTime(fullFmt.parse(endDt.substring(0, 16)));
                } else if (endDt.length() == 5 && endDt.contains(":")) {
                    String[] parts = endDt.split(":");
                    endCal.setTimeInMillis(startCal.getTimeInMillis());
                    endCal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                    endCal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateDateTimeButtons() {
        boolean isAllDay = switchAllDay.isChecked();
        btnStartDate.setText(formatDate(startCal));
        btnEndDate.setText(formatDate(endCal));

        if (isAllDay) {
            btnStartTime.setVisibility(View.GONE);
            btnEndTime.setVisibility(View.GONE);
        } else {
            btnStartTime.setVisibility(View.VISIBLE);
            btnEndTime.setVisibility(View.VISIBLE);
            btnStartTime.setText(formatTime(startCal));
            btnEndTime.setText(formatTime(endCal));
        }
    }

    private String formatDate(Calendar c) {
        return String.format(Locale.JAPAN, "%04d/%02d/%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private String formatTime(Calendar c) {
        return String.format(Locale.JAPAN, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}