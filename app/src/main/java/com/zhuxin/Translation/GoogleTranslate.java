package com.zhuxin.Translation;

import org.json.JSONArray;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Google 免费翻译接口（translate.googleapis.com 非官方端点，无需 API Key）。
 * 覆盖常见语言，接口不可用时返回错误信息由上层提示。
 */
public final class GoogleTranslate {

    public interface ResultCallback {
        void onResult(String text, String error);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private GoogleTranslate() {
    }

    public static void translate(final String text, final String sourceLang,
                                 final String targetLang, final ResultCallback cb) {
        if (text == null || text.trim().isEmpty()) {
            cb.onResult(null, "空文本");
            return;
        }
        String q = text.length() > 1500 ? text.substring(0, 1500) : text;
        String sl = toCode(sourceLang);
        String tl = targetCode(targetLang, sourceLang);
        final String url;
        try {
            url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t"
                    + "&sl=" + sl + "&tl=" + tl
                    + "&q=" + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            cb.onResult(null, e.getMessage());
            return;
        }

        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cb.onResult(null, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        cb.onResult(null, "HTTP " + r.code());
                        return;
                    }
                    JSONArray root = new JSONArray(body);
                    JSONArray sentences = root.optJSONArray(0);
                    StringBuilder sb = new StringBuilder();
                    if (sentences != null) {
                        for (int i = 0; i < sentences.length(); i++) {
                            String seg = sentences.optJSONArray(i).optString(0, "");
                            sb.append(seg);
                        }
                    }
                    cb.onResult(sb.toString().trim(), null);
                } catch (Exception e) {
                    cb.onResult(null, e.getMessage());
                }
            }
        });
    }

    /** 目标语言代码：目标为"自动检测"时按源语言决定（中文→英文，其他→简体中文）。 */
    private static String targetCode(String targetLang, String sourceLang) {
        if (LangUtil.AUTO.equals(targetLang)) {
            return LangUtil.ZH.equals(sourceLang) ? "en" : "zh-CN";
        }
        return toCode(targetLang);
    }

    /** 把应用内语言标签映射为 Google 翻译的语言代码。 */
    public static String toCode(String label) {
        if (label == null || LangUtil.AUTO.equals(label)) {
            return "auto";
        }
        switch (label) {
            case "简体中文": return "zh-CN";
            case "英文": return "en";
            case "日文": return "ja";
            case "韩文": return "ko";
            case "法文": return "fr";
            case "德文": return "de";
            case "西班牙文": return "es";
            case "俄文": return "ru";
            case "葡萄牙文": return "pt";
            case "意大利文": return "it";
            case "泰文": return "th";
            case "越南文": return "vi";
            case "印尼文": return "id";
            case "马来文": return "ms";
            case "阿拉伯文": return "ar";
            case "印地文": return "hi";
            case "土耳其文": return "tr";
            case "波兰文": return "pl";
            case "荷兰文": return "nl";
            default: return "auto";
        }
    }
}
