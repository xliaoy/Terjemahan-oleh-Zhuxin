package com.zhuxin.Translation;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用设置存储。AI 接口配置（Base URL / API Key / 模型）由用户在设置界面自行填写，
 * 这里仅提供带默认值的读取与写入。
 */
public final class Prefs {

    private static final String NAME = "floating_translate";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_TARGET_LANG = "target_lang";
    private static final String KEY_SOURCE_LANG = "source_lang";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_REGION_W = "region_w";
    private static final String KEY_REGION_H = "region_h";
    private static final String KEY_REGION_DX = "region_dx";
    private static final String KEY_REGION_DY = "region_dy";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_OFFLINE_MODE = "offline_mode";
    private static final String KEY_MODEL_URL = "model_url";
    private static final String KEY_LANG = "app_lang";
    private static final String KEY_DEBUG_MODE = "debug_mode";

    private static final int MAX_HISTORY = 50;

    public static final String DEFAULT_BASE_URL = "https://open.zxui.tech/v1";

    /** 离线模型默认下载地址（ModelScope，国内可达）。 */
    public static final String DEFAULT_MODEL_URL =
            "https://modelscope.cn/api/v1/models/Tencent-Hunyuan/HY-MT1.5-1.8B-GGUF/repo"
                    + "?Revision=master&FilePath=HY-MT1.5-1.8B-Q4_K_M.gguf";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String baseUrl(Context c) {
        String v = sp(c).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        return v == null || v.isEmpty() ? DEFAULT_BASE_URL : v;
    }

    public static void setBaseUrl(Context c, String v) {
        sp(c).edit().putString(KEY_BASE_URL, v == null ? "" : v).apply();
    }

    public static String apiKey(Context c) {
        String v = sp(c).getString(KEY_API_KEY, "");
        return v == null ? "" : v;
    }

    public static void setApiKey(Context c, String v) {
        sp(c).edit().putString(KEY_API_KEY, v == null ? "" : v).apply();
    }

    public static String model(Context c) {
        String v = sp(c).getString(KEY_MODEL, "");
        return v == null ? "" : v;
    }

    public static void setModel(Context c, String v) {
        sp(c).edit().putString(KEY_MODEL, v == null ? "" : v).apply();
    }

    public static String targetLang(Context c) {
        String v = sp(c).getString(KEY_TARGET_LANG, "自动检测");
        return v == null || v.isEmpty() ? "自动检测" : v;
    }

    public static void setTargetLang(Context c, String v) {
        sp(c).edit().putString(KEY_TARGET_LANG, v == null ? "" : v).apply();
    }

    public static String sourceLang(Context c) {
        String v = sp(c).getString(KEY_SOURCE_LANG, "自动检测");
        return v == null || v.isEmpty() ? "自动检测" : v;
    }

    public static void setSourceLang(Context c, String v) {
        sp(c).edit().putString(KEY_SOURCE_LANG, v == null ? "" : v).apply();
    }

    /** 是否使用本地离线翻译。 */
    public static boolean offlineMode(Context c) {
        return sp(c).getBoolean(KEY_OFFLINE_MODE, false);
    }

    public static void setOfflineMode(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_OFFLINE_MODE, v).apply();
    }

    /** 离线模型下载地址。 */
    public static String modelUrl(Context c) {
        String v = sp(c).getString(KEY_MODEL_URL, DEFAULT_MODEL_URL);
        return v == null || v.isEmpty() ? DEFAULT_MODEL_URL : v;
    }

    public static void setModelUrl(Context c, String v) {
        sp(c).edit().putString(KEY_MODEL_URL, v == null ? "" : v).apply();
    }

    /** 悬浮窗背景透明度百分比 0-100，值越大越透明。 */
    public static int opacityPercent(Context c) {
        return sp(c).getInt(KEY_OPACITY, 20);
    }

    public static void setOpacityPercent(Context c, int v) {
        sp(c).edit().putInt(KEY_OPACITY, v).apply();
    }

    /** 识别区域宽度（像素）。 */
    public static int regionWidth(Context c) {
        return sp(c).getInt(KEY_REGION_W, 700);
    }

    public static void setRegionWidth(Context c, int v) {
        sp(c).edit().putInt(KEY_REGION_W, v).apply();
    }

    /** 识别区域高度（像素）。 */
    public static int regionHeight(Context c) {
        return sp(c).getInt(KEY_REGION_H, 500);
    }

    public static void setRegionHeight(Context c, int v) {
        sp(c).edit().putInt(KEY_REGION_H, v).apply();
    }

    /** 识别区域左上角相对悬浮球底部左角的横向偏移（像素）。 */
    public static int regionDx(Context c) {
        return sp(c).getInt(KEY_REGION_DX, 0);
    }

    /** 识别区域左上角相对悬浮球顶部左角的纵向偏移（像素），不受悬浮窗高度变化影响。 */
    public static int regionDy(Context c) {
        return sp(c).getInt(KEY_REGION_DY, 170);
    }

    public static void setRegionOffset(Context c, int dx, int dy) {
        sp(c).edit().putInt(KEY_REGION_DX, dx).putInt(KEY_REGION_DY, dy).apply();
    }

    /**
     * 翻译历史，最新在前。每项为 [原文, 译文, 时间戳毫秒]。
     */
    public static List<String[]> history(Context c) {
        List<String[]> list = new ArrayList<>();
        String s = sp(c).getString(KEY_HISTORY, "");
        if (s == null || s.isEmpty()) {
            return list;
        }
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                list.add(new String[]{
                        o.optString("src", ""),
                        o.optString("dst", ""),
                        String.valueOf(o.optLong("time", 0L))
                });
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void addHistory(Context c, String src, String dst) {
        try {
            JSONArray old = new JSONArray();
            String s = sp(c).getString(KEY_HISTORY, "");
            if (s != null && !s.isEmpty()) {
                old = new JSONArray(s);
            }
            JSONObject item = new JSONObject();
            item.put("src", src);
            item.put("dst", dst);
            item.put("time", System.currentTimeMillis());

            JSONArray next = new JSONArray();
            next.put(item);
            for (int i = 0; i < old.length() && next.length() < MAX_HISTORY; i++) {
                next.put(old.optJSONObject(i));
            }
            sp(c).edit().putString(KEY_HISTORY, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** 应用界面语言：system/zh/en，默认跟随系统。 */
    public static String lang(Context c) {
        return sp(c).getString(KEY_LANG, "system");
    }

    public static void setLang(Context c, String v) {
        sp(c).edit().putString(KEY_LANG, v).apply();
    }

    /** 调试模式：开启后识别失败时保存截图与日志到「下载」，关闭则不保存。 */
    public static boolean debugMode(Context c) {
        return sp(c).getBoolean(KEY_DEBUG_MODE, false);
    }

    public static void setDebugMode(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_DEBUG_MODE, v).apply();
    }
}
