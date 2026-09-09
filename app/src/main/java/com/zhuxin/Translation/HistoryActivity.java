package com.zhuxin.Translation;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 翻译历史列表，最新在前。
 */
public class HistoryActivity extends AppCompatActivity {

    private final SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            root.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        ListView listView = findViewById(R.id.history_list);
        TextView empty = findViewById(R.id.history_empty);

        List<String[]> history = Prefs.history(this);
        if (history.isEmpty()) {
            empty.setVisibility(TextView.VISIBLE);
            listView.setVisibility(ListView.GONE);
            return;
        }

        empty.setVisibility(TextView.GONE);
        listView.setVisibility(ListView.VISIBLE);

        List<Map<String, String>> data = new ArrayList<>();
        for (String[] item : history) {
            Map<String, String> row = new HashMap<>();
            row.put("dst", item[1]);
            row.put("src", item[0]);
            row.put("time", formatTime(item[2]));
            data.add(row);
        }

        listView.setAdapter(new SimpleAdapter(
                this,
                data,
                R.layout.history_item,
                new String[]{"dst", "src", "time"},
                new int[]{R.id.history_dst, R.id.history_src, R.id.history_time}));
    }

    private String formatTime(String millis) {
        try {
            return fmt.format(new Date(Long.parseLong(millis)));
        } catch (Exception e) {
            return "";
        }
    }
}
