package com.zhuxin.Translation;

import android.Manifest;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1;
    private static final int REQ_SCREEN = 2;

    private static final String[] LANG_KEYS = {
            "自动检测", "简体中文", "英文", "日文", "韩文", "法文", "德文", "西班牙文", "俄文"
    };

    private MaterialSwitch switchFloating;
    private TextView textFloatingState;
    private TextView textFloatingHint;
    private MaterialSwitch switchOffline;
    private MaterialSwitch switchDebug;
    private EditText editBaseUrl;
    private EditText editApiKey;
    private EditText editModelUrl;
    private Spinner spinnerModel;
    private Spinner spinnerSourceLang;
    private Spinner spinnerTargetLang;
    private Spinner spinnerAppLang;
    private Button btnRefreshModels;
    private Button btnDownloadModel;
    private TextView textModelStatus;
    private SeekBar seekbarOpacity;
    private TextView textOpacity;
    private TextView textOverlayStatus;
    private TextView textScreenStatus;
    private SeekBar seekbarRegionW;
    private SeekBar seekbarRegionH;
    private TextView textRegionW;
    private TextView textRegionH;
    private View pageHome;
    private View pageSettings;

    private final List<String> models = new ArrayList<>();
    private ArrayAdapter<String> modelAdapter;

    private boolean initModel = true;
    private boolean initLang = true;
    private boolean initSourceLang = true;
    private boolean initAppLang = true;
    private boolean uiUpdating = false;
    private boolean pendingStartFloating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();

        bindViews();
        requestNotificationPermissionIfNeeded();

        // 大开关：直接点击开启/关闭悬浮翻译
        switchFloating.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (uiUpdating) {
                    return;
                }
                if (isChecked) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        uiUpdating = true;
                        switchFloating.setChecked(false);
                        uiUpdating = false;
                        requestOverlayPermission();
                    } else {
                        startFloating();
                    }
                } else if (FloatingWindowService.get() != null) {
                    stopService(new Intent(MainActivity.this, FloatingWindowService.class));
                }
                updateFloatingUI();
            }
        });

        findViewById(R.id.btn_overlay_permission).setOnClickListener(v -> requestOverlayPermission());
        findViewById(R.id.btn_screen_permission).setOnClickListener(v -> requestScreenPermission());
        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        findViewById(R.id.btn_reward_wechat).setOnClickListener(v -> showRewardDialog("wechat"));
        findViewById(R.id.btn_reward_alipay).setOnClickListener(v -> showRewardDialog("alipay"));

        setupBottomNav();
        setupAiConfig();
        setupLanguage();
        setupOffline();
        setupDebug();
        setupOpacity();
        setupRegion();
        setupAppLanguage();
        updatePermissionStatus();
        updateFloatingUI();
    }

    private void setupEdgeToEdge() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);
        View root = findViewById(R.id.app_title);
        applyTopInsetPadding(root);
    }

    private void applyTopInsetPadding(final View v) {
        if (v == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top + view.getPaddingTop(),
                    view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
    }

    private void bindViews() {
        switchFloating = findViewById(R.id.switch_floating);
        textFloatingState = findViewById(R.id.text_floating_state);
        textFloatingHint = findViewById(R.id.text_floating_hint);
        pageHome = findViewById(R.id.page_home_include);
        pageSettings = findViewById(R.id.page_settings_include);
        editBaseUrl = findViewById(R.id.edit_base_url);
        editApiKey = findViewById(R.id.edit_api_key);
        spinnerModel = findViewById(R.id.spinner_model);
        spinnerSourceLang = findViewById(R.id.spinner_source_lang);
        spinnerTargetLang = findViewById(R.id.spinner_target_lang);
        spinnerAppLang = findViewById(R.id.spinner_app_lang);
        btnRefreshModels = findViewById(R.id.btn_refresh_models);
        switchOffline = findViewById(R.id.switch_offline);
        switchDebug = findViewById(R.id.switch_debug);
        editModelUrl = findViewById(R.id.edit_model_url);
        btnDownloadModel = findViewById(R.id.btn_download_model);
        textModelStatus = findViewById(R.id.text_model_status);
        seekbarOpacity = findViewById(R.id.seekbar_opacity);
        textOpacity = findViewById(R.id.text_opacity_value);
        textOverlayStatus = findViewById(R.id.text_permission_overlay_status);
        textScreenStatus = findViewById(R.id.text_permission_screen_status);
        seekbarRegionW = findViewById(R.id.seekbar_region_w);
        seekbarRegionH = findViewById(R.id.seekbar_region_h);
        textRegionW = findViewById(R.id.text_region_w);
        textRegionH = findViewById(R.id.text_region_h);
        ((TextView) findViewById(R.id.text_about_version))
                .setText(getString(R.string.version, BuildConfig.VERSION_NAME));
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    showPage(pageHome, pageSettings);
                    return true;
                } else if (id == R.id.nav_settings) {
                    showPage(pageSettings, pageHome);
                    return true;
                }
                return false;
            }
        });
        nav.setSelectedItemId(R.id.nav_home);
    }

    private void showPage(View show, View hide) {
        show.setVisibility(View.VISIBLE);
        hide.setVisibility(View.GONE);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void setupAiConfig() {
        editBaseUrl.setText(Prefs.baseUrl(this));
        editApiKey.setText(Prefs.apiKey(this));

        editBaseUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setBaseUrl(MainActivity.this, s.toString());
            }
        });
        editApiKey.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setApiKey(MainActivity.this, s.toString());
            }
        });

        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, models);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(modelAdapter);

        String savedModel = Prefs.model(this);
        if (!savedModel.isEmpty()) {
            models.add(savedModel);
            modelAdapter.notifyDataSetChanged();
            spinnerModel.setSelection(0);
        }

        spinnerModel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (initModel) {
                    initModel = false;
                    return;
                }
                if (position >= 0 && position < models.size()) {
                    Prefs.setModel(MainActivity.this, models.get(position));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        btnRefreshModels.setOnClickListener(v -> refreshModels());
    }

    private void setupLanguage() {
        // 显示文案随应用语言本地化，持久化仍使用与位置对应的规范值
        String[] labels = getResources().getStringArray(R.array.lang_display);

        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSourceLang.setAdapter(sourceAdapter);
        int srcIdx = Arrays.asList(LANG_KEYS).indexOf(Prefs.sourceLang(this));
        spinnerSourceLang.setSelection(Math.max(0, srcIdx));
        spinnerSourceLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (initSourceLang) {
                    initSourceLang = false;
                    return;
                }
                if (position >= 0 && position < LANG_KEYS.length) {
                    Prefs.setSourceLang(MainActivity.this, LANG_KEYS[position]);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetLang.setAdapter(langAdapter);

        int idx = Arrays.asList(LANG_KEYS).indexOf(Prefs.targetLang(this));
        spinnerTargetLang.setSelection(Math.max(0, idx));

        spinnerTargetLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (initLang) {
                    initLang = false;
                    return;
                }
                if (position >= 0 && position < LANG_KEYS.length) {
                    Prefs.setTargetLang(MainActivity.this, LANG_KEYS[position]);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    /** 应用语言：跟随系统 / 简体中文 / English。 */
    private void setupAppLanguage() {
        String[] options = {
                getString(R.string.lang_option_system),
                getString(R.string.lang_option_zh),
                getString(R.string.lang_option_en)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAppLang.setAdapter(adapter);

        String saved = Prefs.lang(this);
        int idx = "zh".equals(saved) ? 1 : "en".equals(saved) ? 2 : 0;
        spinnerAppLang.setSelection(idx);

        final String[] codes = {"system", "zh", "en"};
        spinnerAppLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (initAppLang) {
                    initAppLang = false;
                    return;
                }
                if (position >= 0 && position < codes.length) {
                    Prefs.setLang(MainActivity.this, codes[position]);
                    applyAppLanguage(codes[position]);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    /** 应用界面语言并重建界面。 */
    private void applyAppLanguage(String lang) {
        if ("zh".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"));
        } else if ("en".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        }
    }

    private void setupOffline() {
        switchOffline.setChecked(Prefs.offlineMode(this));
        switchOffline.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setOfflineMode(MainActivity.this, isChecked);
                if (isChecked && !LocalTranslator.isModelValid(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_model_not_downloaded_first),
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        editModelUrl.setText(Prefs.modelUrl(this));
        editModelUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setModelUrl(MainActivity.this, s.toString());
            }
        });

        updateModelStatus();

        btnDownloadModel.setOnClickListener(v -> startDownloadModel());
    }

    private void setupDebug() {
        switchDebug.setChecked(Prefs.debugMode(this));
        switchDebug.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setDebugMode(MainActivity.this, isChecked);
            }
        });
    }

    private void updateModelStatus() {
        if (LocalTranslator.isModelValid(this)) {
            double mb = LocalTranslator.modelPath(this).length() / 1024.0 / 1024.0;
            textModelStatus.setText(String.format(java.util.Locale.US,
                    getString(R.string.text_model_downloaded), mb));
            btnDownloadModel.setText(R.string.btn_downloaded);
            btnDownloadModel.setEnabled(false);
        } else {
            textModelStatus.setText(R.string.text_model_not_downloaded);
            btnDownloadModel.setText(R.string.btn_download_model);
            btnDownloadModel.setEnabled(true);
        }
    }

    private void startDownloadModel() {
        String url = editModelUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.toast_fill_model_url, Toast.LENGTH_SHORT).show();
            return;
        }
        btnDownloadModel.setEnabled(false);
        ModelDownloader.download(this, url, new ModelDownloader.ProgressCallback() {
            @Override
            public void onProgress(final long downloaded, final long total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String s;
                        if (total > 0) {
                            s = getString(R.string.toast_downloading,
                                    downloaded / 1024 / 1024, total / 1024 / 1024);
                        } else {
                            s = getString(R.string.toast_downloading_no_total,
                                    downloaded / 1024 / 1024);
                        }
                        textModelStatus.setText(s);
                    }
                });
            }

            @Override
            public void onDone(final boolean success, final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnDownloadModel.setEnabled(true);
                        if (success) {
                            Toast.makeText(MainActivity.this, R.string.toast_download_finish,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.toast_download_failed, error),
                                    Toast.LENGTH_LONG).show();
                        }
                        updateModelStatus();
                    }
                });
            }
        });
    }

    private void setupOpacity() {
        int current = Prefs.opacityPercent(this);
        seekbarOpacity.setProgress(current);
        textOpacity.setText(getString(R.string.opacity_value, current));

        seekbarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOpacity.setText(getString(R.string.opacity_value, progress));
                Prefs.setOpacityPercent(MainActivity.this, progress);
                FloatingWindowService svc = FloatingWindowService.get();
                if (svc != null) {
                    svc.applyOpacity();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupRegion() {
        seekbarRegionW.setProgress(Prefs.regionWidth(this));
        seekbarRegionH.setProgress(Prefs.regionHeight(this));
        textRegionW.setText(getString(R.string.region_w_value, Prefs.regionWidth(this)));
        textRegionH.setText(getString(R.string.region_h_value, Prefs.regionHeight(this)));

        seekbarRegionW.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textRegionW.setText(getString(R.string.region_w_value, progress));
                Prefs.setRegionWidth(MainActivity.this, progress);
                FloatingWindowService svc = FloatingWindowService.get();
                if (svc != null) {
                    svc.applyRegion();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarRegionH.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textRegionH.setText(getString(R.string.region_h_value, progress));
                Prefs.setRegionHeight(MainActivity.this, progress);
                FloatingWindowService svc = FloatingWindowService.get();
                if (svc != null) {
                    svc.applyRegion();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void refreshModels() {
        String base = editBaseUrl.getText().toString().trim();
        String key = editApiKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, R.string.toast_fill_api_key, Toast.LENGTH_SHORT).show();
            return;
        }
        btnRefreshModels.setEnabled(false);
        new AiClient(base, key, "").fetchModels(new AiClient.ListCallback() {
            @Override
            public void onResult(final List<String> list, final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnRefreshModels.setEnabled(true);
                        if (error != null) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.toast_model_get_failed, error),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        models.clear();
                        if (list != null) {
                            models.addAll(list);
                        }
                        modelAdapter.notifyDataSetChanged();
                        if (!models.isEmpty()) {
                            String saved = Prefs.model(MainActivity.this);
                            int i = models.indexOf(saved);
                            int select = i >= 0 ? i : 0;
                            spinnerModel.setSelection(select);
                            Prefs.setModel(MainActivity.this, models.get(select));
                        }
                        Toast.makeText(MainActivity.this,
                                getString(R.string.toast_model_get_ok, models.size()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void startFloating() {
        if (!ScreenCaptureService.isReady()) {
            pendingStartFloating = true;
            requestScreenPermission();
            Toast.makeText(this, R.string.toast_need_screen, Toast.LENGTH_SHORT).show();
            return;
        }
        startFloatingService();
    }

    private void startFloatingService() {
        Intent i = new Intent(this, FloatingWindowService.class);
        startForegroundService(i);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        }
    }

    private void requestScreenPermission() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SCREEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCREEN && resultCode == RESULT_OK && data != null) {
            Intent i = new Intent(this, ScreenCaptureService.class);
            i.putExtra("resultCode", resultCode);
            i.putExtra("data", data);
            startForegroundService(i);
            Toast.makeText(this, R.string.toast_screen_granted, Toast.LENGTH_SHORT).show();
            if (pendingStartFloating) {
                pendingStartFloating = false;
                startFloatingService();
            }
        } else if (requestCode == REQ_SCREEN) {
            pendingStartFloating = false;
            updateFloatingUI();
            Toast.makeText(this, R.string.toast_screen_denied, Toast.LENGTH_LONG).show();
        }
        updatePermissionStatus();
    }

    private void updatePermissionStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean screen = ScreenCaptureService.isReady();
        int ok = getColor(R.color.perm_status_ok);
        int bad = getColor(R.color.perm_status_bad);
        textOverlayStatus.setText(overlay ? R.string.permission_granted : R.string.permission_not_granted);
        textOverlayStatus.setTextColor(overlay ? ok : bad);
        textScreenStatus.setText(screen ? R.string.permission_granted : R.string.permission_not_granted);
        textScreenStatus.setTextColor(screen ? ok : bad);
    }

    private void updateFloatingUI() {
        boolean on = FloatingWindowService.get() != null;
        uiUpdating = true;
        switchFloating.setChecked(on);
        uiUpdating = false;
        textFloatingState.setText(on ? R.string.home_floating_on : R.string.home_floating_off);
        textFloatingHint.setText(on ? R.string.home_tap_hint_on : R.string.home_tap_hint_off);
    }

    /** 打赏弹窗：优先显示 res/drawable/reward_wechat.png 或 reward_alipay.png。 */
    private void showRewardDialog(String key) {
        int resId = getResources().getIdentifier("reward_" + key, "drawable", getPackageName());
        View content = getLayoutInflater().inflate(R.layout.dialog_reward, null);
        ImageView img = content.findViewById(R.id.reward_image);
        TextView text = content.findViewById(R.id.reward_text);
        if (resId != 0) {
            img.setImageResource(resId);
            img.setVisibility(View.VISIBLE);
            text.setText(getString(R.string.reward_scan_hint));
        } else {
            img.setVisibility(View.GONE);
            text.setText(getString(R.string.reward_missing, key, key));
            text.setTextColor(0xFF888888);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.reward_title)
                .setView(content)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        updateFloatingUI();
    }
}