package com.zhuxin.Translation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 悬浮窗服务：识别区域与悬浮窗合并为单个窗口。
 * 点击窗口翻译；长按窗口拖动移动位置；长按右下角手柄调整识别区域大小。
 * 翻译结果展示在窗口内。
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "floating";
    private static final int NOTIF_ID = 100;
    private static final long LONG_PRESS_MS = 250;

    private static FloatingWindowService instance;

    private WindowManager wm;
    private View container;
    private TextView textView;
    private WindowManager.LayoutParams params;
    private View minBall;
    private WindowManager.LayoutParams minParams;
    private boolean minimized = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OcrHelper ocrHelper = new OcrHelper();

    private int initialX;
    private int initialY;
    private float touchRawX;
    private float touchRawY;
    private float lastTouchRawY;
    private boolean moving = false;
    private boolean scrolling = false;
    private int touchSlop;
    private boolean resizing = false;
    private int resizeBaseW;
    private int resizeBaseH;
    private float resizeDownRawX;
    private float resizeDownRawY;

    public static FloatingWindowService get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Notification notif = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notif);
        }
        addView();
    }

    private void addView() {
        container = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
        textView = container.findViewById(R.id.floating_text);
        ImageView corner = container.findViewById(R.id.floating_corner);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int regionW = Prefs.regionWidth(this);
        int regionH = Prefs.regionHeight(this);
        params = new WindowManager.LayoutParams(
                regionW,
                regionH,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 60;
        params.y = 200;

        minBall = LayoutInflater.from(this).inflate(R.layout.floating_ball, null);
        minParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        minParams.gravity = Gravity.TOP | Gravity.START;
        minParams.x = params.x;
        minParams.y = params.y;

        applyOpacity();
        setupDrag();
        setupBallTouch();
        setupCorner(corner);

        container.findViewById(R.id.floating_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                minimize();
            }
        });

        wm.addView(container, params);
        show(getString(R.string.floating_tap_to_translate));
    }

    /**
     * 统一手势：单击任意位置触发翻译；长按拖动窗口；上下滑动滚动译文。
     * 长按阈值较短以提升移动灵敏度，滚动需超过 slop*2 才生效以避免误判。
     */
    private void setupDrag() {
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        container.setOnTouchListener(new View.OnTouchListener() {
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    moving = true;
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        touchRawX = event.getRawX();
                        touchRawY = event.getRawY();
                        lastTouchRawY = event.getRawY();
                        moving = false;
                        scrolling = false;
                        mainHandler.removeCallbacks(longPressRunnable);
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (moving) {
                            params.x = initialX + (int) (event.getRawX() - touchRawX);
                            params.y = initialY + (int) (event.getRawY() - touchRawY);
                            wm.updateViewLayout(container, params);
                            return true;
                        }
                        float dy = event.getRawY() - lastTouchRawY;
                        lastTouchRawY = event.getRawY();
                        if (!scrolling && Math.abs(event.getRawY() - touchRawY) > touchSlop * 2) {
                            scrolling = true;
                            mainHandler.removeCallbacks(longPressRunnable);
                        }
                        if (scrolling) {
                            scrollTextBy(dy);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPressRunnable);
                        boolean wasMoving = moving;
                        boolean wasScrolling = scrolling;
                        moving = false;
                        scrolling = false;
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && !wasMoving && !wasScrolling) {
                            translateBelow();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** 手动滚动译文，dy 为本次竖直位移（向上滑为负）。 */
    private void scrollTextBy(float dy) {
        if (textView == null || textView.getLayout() == null) {
            return;
        }
        int range = textView.getLayout().getHeight() - textView.getHeight();
        if (range <= 0) {
            return;
        }
        int target = Math.max(0, Math.min(range, textView.getScrollY() - (int) dy));
        textView.scrollTo(0, target);
    }

    /** 按住右下角手柄拖动调整窗口（识别区域）大小，按下即生效，无需长按。 */
    private void setupCorner(final View corner) {
        corner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        resizeBaseW = params.width;
                        resizeBaseH = params.height;
                        resizeDownRawX = event.getRawX();
                        resizeDownRawY = event.getRawY();
                        resizing = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (resizing) {
                            params.width = clampSize(Math.max(180,
                                    resizeBaseW + (int) (event.getRawX() - resizeDownRawX)));
                            params.height = clampSize(Math.max(120,
                                    resizeBaseH + (int) (event.getRawY() - resizeDownRawY)));
                            wm.updateViewLayout(container, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (resizing) {
                            Prefs.setRegionWidth(FloatingWindowService.this, params.width);
                            Prefs.setRegionHeight(FloatingWindowService.this, params.height);
                        }
                        resizing = false;
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private int clampSize(int v) {
        return Math.max(150, Math.min(v, 3000));
    }

    private void translateBelow() {
        if (!ScreenCaptureService.isReady()) {
            show(getString(R.string.floating_need_permission));
            return;
        }

        final String engine = Prefs.translateEngine(this);
        final String baseUrl = Prefs.baseUrl(this);
        final String apiKey = Prefs.apiKey(this);
        final String model = Prefs.model(this);
        if (Prefs.TE_AI.equals(engine) && (apiKey.isEmpty() || model.isEmpty())) {
            show(getString(R.string.msg_need_ai_config));
            return;
        }

        show(getString(R.string.floating_translating));

        // 隐藏窗口避免截图包含自身内容，短暂延迟让画面刷新后再截取窗口区域
        container.setVisibility(View.INVISIBLE);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Rect region = getRegion();
                ScreenCaptureService.get().capture(region, new ScreenCaptureService.CaptureCallback() {
                    @Override
                    public void onResult(final Bitmap bitmap) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                container.setVisibility(View.VISIBLE);
                                if (bitmap == null) {
                                    DebugLog.error(FloatingWindowService.this, "Capture",
                                            "截图失败（bitmap == null），请确认录屏权限", null);
                                    show(getString(R.string.msg_capture_failed));
                                    return;
                                }
                                handleCaptured(bitmap, engine, baseUrl, apiKey, model);
                            }
                        });
                    }
                });
            }
        }, 150);
    }

    private void handleCaptured(final Bitmap bitmap, final String engine,
                                final String baseUrl, final String apiKey, final String model) {
        OcrHelper.Callback ocrCb = new OcrHelper.Callback() {
            @Override
            public void onResult(final String text) {
                if (text == null || text.trim().isEmpty()) {
                    if (DebugLog.enabled(FloatingWindowService.this)) {
                        String saved = saveRegionPng(bitmap);
                        DebugLog.event(FloatingWindowService.this, "OCR",
                                "未识别到文字" + (saved != null ? "，已保存截图 " + saved : ""));
                        show(getString(R.string.msg_no_text_saved));
                    } else {
                        show(getString(R.string.msg_no_text_plain));
                    }
                    bitmap.recycle();
                    return;
                }
                if (!containsForeign(text)) {
                    if (DebugLog.enabled(FloatingWindowService.this)) {
                        String saved = saveRegionPng(bitmap);
                        DebugLog.event(FloatingWindowService.this, "OCR",
                                "未检测到外文" + (saved != null ? "，已保存截图 " + saved : ""));
                        show(getString(R.string.msg_no_foreign));
                    } else {
                        show(getString(R.string.msg_no_foreign_plain));
                    }
                    bitmap.recycle();
                    return;
                }
                bitmap.recycle();
                final String sourceLang = Prefs.sourceLang(FloatingWindowService.this);
                final String targetLang = Prefs.targetLang(FloatingWindowService.this);
                if (Prefs.TE_OFFLINE.equals(engine)) {
                    translateOffline(text, sourceLang, targetLang);
                } else if (Prefs.TE_GOOGLE.equals(engine)) {
                    GoogleTranslate.translate(text, sourceLang, targetLang,
                            new GoogleTranslate.ResultCallback() {
                                @Override
                                public void onResult(String result, String error) {
                                    if (error != null) {
                                        DebugLog.error(FloatingWindowService.this, "Google",
                                                "翻译失败: " + error, null);
                                        show(getString(R.string.msg_translate_failed, error));
                                    } else if (result == null || result.isEmpty()) {
                                        DebugLog.error(FloatingWindowService.this, "Google",
                                                "翻译结果为空", null);
                                        show(getString(R.string.msg_translate_empty));
                                    } else {
                                        Prefs.addHistory(FloatingWindowService.this, text, result);
                                        show(result);
                                    }
                                }
                            });
                } else {
                    new AiClient(baseUrl, apiKey, model).translate(
                            text, sourceLang, targetLang,
                            new AiClient.TextCallback() {
                                @Override
                                public void onResult(String result, String error) {
                                    if (error != null) {
                                        DebugLog.error(FloatingWindowService.this, "AI",
                                                "翻译失败: " + error, null);
                                        show(getString(R.string.msg_translate_failed, error));
                                    } else if (result == null || result.isEmpty()) {
                                        DebugLog.error(FloatingWindowService.this, "AI",
                                                "翻译结果为空", null);
                                        show(getString(R.string.msg_translate_empty));
                                    } else {
                                        Prefs.addHistory(FloatingWindowService.this, text, result);
                                        show(result);
                                    }
                                }
                            });
                }
            }
        };
        if (Prefs.OCR_AI_VISION.equals(Prefs.ocrEngine(FloatingWindowService.this))) {
            OcrHelper.recognizeOnline(FloatingWindowService.this, bitmap, ocrCb);
        } else {
            ocrHelper.recognize(bitmap, ocrCb);
        }
    }

    /** 是否含外文（拉丁、假名、谚文、西里尔、泰文、阿拉伯文、天城文等书写系统），用于判断是否需要翻译。 */
    private boolean containsForeign(String text) {
        return LangUtil.containsForeignScript(text);
    }

    /** 本地离线翻译：后台线程加载模型并推理。 */
    private void translateOffline(final String text, final String sourceLang, final String targetLang) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = LocalTranslator.ensureLoaded(FloatingWindowService.this);
                if (err != null) {
                    DebugLog.error(FloatingWindowService.this, "Offline",
                            "离线模型加载失败: " + err, null);
                    show(getString(R.string.msg_offline_failed, err));
                    return;
                }
                String result = LocalTranslator.translate(text, sourceLang, targetLang);
                if (result == null || result.trim().isEmpty()) {
                    DebugLog.error(FloatingWindowService.this, "Offline",
                            "离线翻译结果为空", null);
                    show(getString(R.string.msg_offline_empty));
                } else {
                    Prefs.addHistory(FloatingWindowService.this, text, result);
                    show(result);
                }
            }
        }).start();
    }

    /** 识别区域即窗口本身所占的屏幕区域。 */
    private Rect getRegion() {
        if (params == null) {
            return new Rect(60, 200, 60 + Prefs.regionWidth(this), 200 + Prefs.regionHeight(this));
        }
        return new Rect(params.x, params.y,
                params.x + Math.max(params.width, 180), params.y + Math.max(params.height, 120));
    }

    /** 设置界面调节识别区域大小时调用，实时调整窗口尺寸。 */
    public void applyRegion() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (params != null && wm != null && container != null) {
                    params.width = Prefs.regionWidth(FloatingWindowService.this);
                    params.height = Prefs.regionHeight(FloatingWindowService.this);
                    try {
                        wm.updateViewLayout(container, params);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    /** 把 OCR 失败时的原始截图保存到「下载」，用于排查是区域没对准还是文字无法识别。返回文件名，未保存返回 null。 */
    private String saveRegionPng(Bitmap bmp) {
        try {
            if (bmp == null || bmp.isRecycled() || Build.VERSION.SDK_INT < 29
                    || !Prefs.debugMode(this)) {
                return null;
            }
            String name = "ocr_region_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date()) + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) {
                    try (OutputStream s = os) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, s);
                    }
                }
                return name;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void show(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (textView != null) {
                    textView.setText(msg);
                }
            }
        });
    }

    /** 应用设置里的透明度（值越大越透明）。 */
    public void applyOpacity() {
        if (container != null) {
            int p = Prefs.opacityPercent(this);
            container.setAlpha(1f - p / 100f);
        }
    }

    /** 最小化为图标悬浮球。 */
    private void minimize() {
        if (minimized) {
            return;
        }
        minimized = true;
        if (container != null && wm != null) {
            try {
                wm.removeView(container);
            } catch (Exception ignored) {
            }
        }
        minParams.x = params.x;
        minParams.y = params.y;
        wm.addView(minBall, minParams);
    }

    /** 从图标悬浮球展开为完整悬浮窗。 */
    private void expand() {
        if (!minimized) {
            return;
        }
        minimized = false;
        if (minBall != null && wm != null) {
            try {
                wm.removeView(minBall);
            } catch (Exception ignored) {
            }
        }
        params.x = minParams.x;
        params.y = minParams.y;
        wm.addView(container, params);
    }

    /** 最小化图标悬浮球的拖动与点击展开。 */
    private void setupBallTouch() {
        minBall.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = minParams.x;
                        initialY = minParams.y;
                        touchRawX = event.getRawX();
                        touchRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        minParams.x = initialX + (int) (event.getRawX() - touchRawX);
                        minParams.y = initialY + (int) (event.getRawY() - touchRawY);
                        wm.updateViewLayout(minBall, minParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx = event.getRawX() - touchRawX;
                        float dy = event.getRawY() - touchRawY;
                        if (Math.hypot(dx, dy) < 10) {
                            expand();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_notification)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (container != null && wm != null) {
            try {
                wm.removeView(container);
            } catch (Exception ignored) {
            }
        }
        if (minBall != null && wm != null) {
            try {
                wm.removeView(minBall);
            } catch (Exception ignored) {
            }
        }
        ocrHelper.close();
        instance = null;
        super.onDestroy();
    }
}
