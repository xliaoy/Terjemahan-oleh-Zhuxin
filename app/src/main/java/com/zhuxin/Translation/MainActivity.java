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
            "自动检测", "简体中文", "英文", "日文", "韩文", "法文", "德文", "西班牙文", "俄文",
            "葡萄牙文", "意大利文", "泰文", "越南文", "印尼文", "马来文", "阿拉伯文",
            "印地文", "土耳其文", "波兰文", "荷兰文"
    };

    private MaterialSwitch switchFloating;
    private TextView textFloatingState;
    private TextView textFloatingHint;
    private MaterialSwitch switchDebug;
    private EditText editBaseUrl;
    private EditText editApiKey;
    private EditText editModelUrl;
    private EditText editVisionBaseUrl;
    private EditText editVisionApiKey;
    private Spinner spinnerVisionModel;
    private Button btnRefreshVisionModels;
    private Spinner spinnerModel;
    private Spinner spinnerOcrEngine;
    private Spinner spinnerTranslateEngine;
    private Spinner spinnerOfflineModel;
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
    private final List<String> visionModels = new ArrayList<>();
    private ArrayAdapter<String> visionModelAdapter;

    private boolean initModel = true;
    private boolean initVisionModel = true;
    private boolean initLang = true;
    private boolean initSourceLang = true;
    private boolean initAppLang = true;
    private boolean uiUpdating = false;
    private boolean pendingStartFloating = false;
    private boolean downloading = false;

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
                        pendingStartFloating = true; // 授权返回后自动继续开启
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
        setupEngines();
        setupAiConfig();
        setupLanguage();
        setupOffline();
        setupDebug();
        setupOpacity();
        setupRegion();
        setupAppLanguage();
        updatePermissionStatus();
        updateFloatingUI();
        // 注册悬浮窗服务状态监听：开关状态始终跟随服务真实运行状态
        FloatingWindowService.addStateListener(stateListener);
    }

    /** 悬浮窗服务运行状态监听：同步首页开关与权限状态。 */
    private final FloatingWindowService.StateListener stateListener =
            new FloatingWindowService.StateListener() {
                @Override
                public void onStateChanged(boolean running) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateFloatingUI();
                            updatePermissionStatus();
                        }
                    });
                }
            };

    @Override
    protected void onDestroy() {
        FloatingWindowService.removeStateListener(stateListener);
        super.onDestroy();
    }

    /** 首页：OCR 引擎与翻译引擎选择。 */
    private void setupEngines() {
        // OCR 引擎：本地 ML Kit / AI 视觉在线
        final String[] ocrOptions = {
                getString(R.string.ocr_engine_mlkit),
                getString(R.string.ocr_engine_ai_vision)
        };
        final String[] ocrKeys = {Prefs.OCR_MLKIT, Prefs.OCR_AI_VISION};
        ArrayAdapter<String> ocrAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ocrOptions);
        ocrAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOcrEngine.setAdapter(ocrAdapter);
        String curOcr = Prefs.ocrEngine(this);
        for (int i = 0; i < ocrKeys.length; i++) {
            if (ocrKeys[i].equals(curOcr)) {
                spinnerOcrEngine.setSelection(i);
                break;
            }
        }
        spinnerOcrEngine.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (uiUpdating || position < 0 || position >= ocrKeys.length) {
                    return;
                }
                // AI 视觉 OCR 必须先在设置里配好独立的 Base URL / API Key / 模型
                if (Prefs.OCR_AI_VISION.equals(ocrKeys[position])
                        && !Prefs.isVisionConfigured(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, R.string.toast_vision_not_configured,
                            Toast.LENGTH_LONG).show();
                    uiUpdating = true;
                    spinnerOcrEngine.setSelection(0);
                    uiUpdating = false;
                    Prefs.setOcrEngine(MainActivity.this, Prefs.OCR_MLKIT);
                    return;
                }
                Prefs.setOcrEngine(MainActivity.this, ocrKeys[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        // 翻译引擎：在线 AI / 离线本地 / Google 免费
        final String[] teOptions = {
                getString(R.string.te_ai),
                getString(R.string.te_offline),
                getString(R.string.te_google)
        };
        final String[] teKeys = {Prefs.TE_AI, Prefs.TE_OFFLINE, Prefs.TE_GOOGLE};
        ArrayAdapter<String> teAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, teOptions);
        teAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTranslateEngine.setAdapter(teAdapter);
        String curTe = Prefs.translateEngine(this);
        for (int i = 0; i < teKeys.length; i++) {
            if (teKeys[i].equals(curTe)) {
                spinnerTranslateEngine.setSelection(i);
                break;
            }
        }
        spinnerTranslateEngine.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (uiUpdating || position < 0 || position >= teKeys.length) {
                    return;
                }
                // 在线 AI 翻译必须先配置 API Key 与模型
                if (Prefs.TE_AI.equals(teKeys[position])
                        && !Prefs.isAiConfigured(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, R.string.toast_ai_not_configured,
                            Toast.LENGTH_LONG).show();
                    String saved = Prefs.translateEngine(MainActivity.this);
                    int back = 0;
                    for (int i = 0; i < teKeys.length; i++) {
                        if (teKeys[i].equals(saved)) {
                            back = i;
                            break;
                        }
                    }
                    uiUpdating = true;
                    spinnerTranslateEngine.setSelection(back);
                    uiUpdating = false;
                    return;
                }
                Prefs.setTranslateEngine(MainActivity.this, teKeys[position]);
                if (Prefs.TE_OFFLINE.equals(teKeys[position])
                        && !LocalTranslator.isModelValid(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, R.string.toast_model_not_downloaded_first,
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
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
        editVisionBaseUrl = findViewById(R.id.edit_vision_base_url);
        editVisionApiKey = findViewById(R.id.edit_vision_api_key);
        spinnerVisionModel = findViewById(R.id.spinner_vision_model);
        btnRefreshVisionModels = findViewById(R.id.btn_refresh_vision_models);
        spinnerModel = findViewById(R.id.spinner_model);
        spinnerSourceLang = findViewById(R.id.spinner_source_lang);
        spinnerTargetLang = findViewById(R.id.spinner_target_lang);
        spinnerAppLang = findViewById(R.id.spinner_app_lang);
        btnRefreshModels = findViewById(R.id.btn_refresh_models);
        spinnerOcrEngine = findViewById(R.id.spinner_ocr_engine);
        spinnerTranslateEngine = findViewById(R.id.spinner_translate_engine);
        spinnerOfflineModel = findViewById(R.id.spinner_offline_model);
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

        // AI 视觉 OCR 独立配置（必须独立填写，不共用主 AI 配置）
        editVisionBaseUrl.setText(Prefs.visionBaseUrl(this));
        editVisionApiKey.setText(Prefs.visionApiKey(this));
        editVisionBaseUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setVisionBaseUrl(MainActivity.this, s.toString());
            }
        });
        editVisionApiKey.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setVisionApiKey(MainActivity.this, s.toString());
            }
        });

        // 视觉 OCR 模型：在线获取下拉选择
        visionModelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, visionModels);
        visionModelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVisionModel.setAdapter(visionModelAdapter);
        String savedVisionModel = Prefs.visionModel(this);
        if (!savedVisionModel.isEmpty()) {
            visionModels.add(savedVisionModel);
            visionModelAdapter.notifyDataSetChanged();
            spinnerVisionModel.setSelection(0);
        }
        spinnerVisionModel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (initVisionModel) {
                    initVisionModel = false;
                    return;
                }
                if (position >= 0 && position < visionModels.size()) {
                    Prefs.setVisionModel(MainActivity.this, visionModels.get(position));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        btnRefreshVisionModels.setOnClickListener(v -> refreshVisionModels());

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
        // 离线模型选择：切换后更新 URL 与模型状态
        final String[] modelOptions = {
                getString(R.string.model_hunyuan),
                getString(R.string.model_qwen)
        };
        final String[] modelKeys = {Prefs.MODEL_HUNYUAN, Prefs.MODEL_QWEN};
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modelOptions);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOfflineModel.setAdapter(modelAdapter);
        String curModel = Prefs.offlineModel(this);
        for (int i = 0; i < modelKeys.length; i++) {
            if (modelKeys[i].equals(curModel)) {
                spinnerOfflineModel.setSelection(i);
                break;
            }
        }
        spinnerOfflineModel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < modelKeys.length) {
                    Prefs.setOfflineModel(MainActivity.this, modelKeys[position]);
                    // 切模型时给默认下载地址，用户可自行修改
                    editModelUrl.setText(Prefs.modelDefaultUrl(modelKeys[position]));
                    updateModelStatus();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
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
        // 正在下载：再次点击 = 取消下载
        if (downloading) {
            downloading = false;
            ModelDownloader.cancel();
            btnDownloadModel.setText(R.string.btn_download_model);
            btnDownloadModel.setEnabled(true);
            textModelStatus.setText(R.string.text_model_not_downloaded);
            Toast.makeText(this, R.string.toast_download_canceled, Toast.LENGTH_SHORT).show();
            return;
        }
        String url = editModelUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.toast_fill_model_url, Toast.LENGTH_SHORT).show();
            return;
        }
        downloading = true;
        // 立即给用户反馈，避免"不知道有没有开始下载"
        btnDownloadModel.setText(R.string.btn_cancel_download);
        btnDownloadModel.setEnabled(true);
        textModelStatus.setText(R.string.download_connecting);
        ModelDownloader.download(this, url, new ModelDownloader.ProgressCallback() {
            @Override
            public void onProgress(final long downloaded, final long total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String s;
                        if (total > 0) {
                            int pct = (int) (downloaded * 100L / total);
                            s = getString(R.string.toast_downloading,
                                    downloaded / 1024 / 1024, total / 1024 / 1024)
                                    + " (" + pct + "%)";
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
                        downloading = false;
                        if (success) {
                            Toast.makeText(MainActivity.this, R.string.toast_download_finish,
                                    Toast.LENGTH_SHORT).show();
                        } else if ("已取消".equals(error) || (error != null && error.contains("Canceled"))) {
                            Toast.makeText(MainActivity.this, R.string.toast_download_canceled,
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

    /** 刷新 AI 视觉 OCR 的模型列表（必须使用视觉 OCR 自己的 Base URL / API Key）。 */
    private void refreshVisionModels() {
        String base = editVisionBaseUrl.getText().toString().trim();
        String key = editVisionApiKey.getText().toString().trim();
        if (base.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, R.string.toast_vision_need_base_key, Toast.LENGTH_LONG).show();
            return;
        }
        btnRefreshVisionModels.setEnabled(false);
        new AiClient(base, key, "").fetchModels(new AiClient.ListCallback() {
            @Override
            public void onResult(final List<String> list, final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnRefreshVisionModels.setEnabled(true);
                        if (error != null) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.toast_model_get_failed, error),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        visionModels.clear();
                        if (list != null) {
                            visionModels.addAll(list);
                        }
                        visionModelAdapter.notifyDataSetChanged();
                        if (!visionModels.isEmpty()) {
                            String saved = Prefs.visionModel(MainActivity.this);
                            int i = visionModels.indexOf(saved);
                            int select = i >= 0 ? i : 0;
                            spinnerVisionModel.setSelection(select);
                            Prefs.setVisionModel(MainActivity.this, visionModels.get(select));
                        }
                        Toast.makeText(MainActivity.this,
                                getString(R.string.toast_model_get_ok, visionModels.size()),
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
        } else if (requestCode == REQ_OVERLAY) {
            // 悬浮窗权限授权返回：若用户之前想开启，自动继续
            if (pendingStartFloating && Settings.canDrawOverlays(this)) {
                pendingStartFloating = false;
                startFloating();
            }
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