package com.zhuxin.Translation;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OpenAI 兼容接口客户端：拉取模型列表、调用 chat/completions 做翻译。
 */
public final class AiClient {

    public interface ListCallback {
        void onResult(List<String> models, String error);
    }

    public interface TextCallback {
        void onResult(String text, String error);
    }

    private static final String TAG = "AiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AiClient(String baseUrl, String apiKey, String model) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
    }

    private Request.Builder auth(String url) {
        Request.Builder b = new Request.Builder().url(url);
        if (!apiKey.isEmpty()) {
            b.header("Authorization", "Bearer " + apiKey);
        }
        return b;
    }

    /** 从 GET {base}/models 拉取可用模型 id 列表。 */
    public void fetchModels(final ListCallback cb) {
        Request req = auth(baseUrl + "/models").get().build();
        client.newCall(req).enqueue(new Callback() {
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
                    List<String> ids = new ArrayList<>();
                    JSONObject root = new JSONObject(body);
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            String id = data.optJSONObject(i).optString("id", "");
                            if (!id.isEmpty()) {
                                ids.add(id);
                            }
                        }
                    }
                    cb.onResult(ids, null);
                } catch (Exception e) {
                    Log.e(TAG, "fetchModels error", e);
                    cb.onResult(null, e.getMessage());
                }
            }
        });
    }

    /** 调用 chat/completions 把 text 从 sourceLang 翻译成 targetLang。 */
    public void translate(final String text, final String sourceLang, final String targetLang, final TextCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "你是一个翻译引擎。只输出译文，不要解释、不要原文、不要任何额外文字。"));

            String userContent = buildInstruction(text, sourceLang, targetLang);
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", userContent));
            body.put("messages", messages);
            body.put("temperature", 0.3);

            Request req = auth(baseUrl + "/chat/completions")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    cb.onResult(null, e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response r = response) {
                        String resp = r.body() != null ? r.body().string() : "";
                        if (!r.isSuccessful()) {
                            cb.onResult(null, "HTTP " + r.code() + " " + resp);
                            return;
                        }
                        JSONObject root = new JSONObject(resp);
                        JSONArray choices = root.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            String content = choices.optJSONObject(0)
                                    .optJSONObject("message")
                                    .optString("content", "");
                            cb.onResult(content.trim(), null);
                        } else {
                            cb.onResult(null, "接口未返回翻译结果");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "translate error", e);
                        cb.onResult(null, e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onResult(null, e.getMessage());
        }
    }

    /**
     * AI 视觉 OCR：把图片传给支持视觉输入的模型，提取图中所有文字原文。
     * 依赖所配置的模型支持图片（如 gpt-4o / qwen-vl 等）。
     */
    public void extractText(final Bitmap bitmap, final TextCallback cb) {
        if (bitmap == null || bitmap.isRecycled()) {
            cb.onResult(null, "图片无效");
            return;
        }
        try {
            // 缩放并压缩图片，减少请求体积
            Bitmap working = bitmap;
            int maxDim = 1280;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (Math.max(w, h) > maxDim) {
                float scale = maxDim / (float) Math.max(w, h);
                working = Bitmap.createScaledBitmap(bitmap,
                        Math.round(w * scale), Math.round(h * scale), true);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            working.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            if (working != bitmap) {
                working.recycle();
            }
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "你是 OCR 文字识别引擎。只输出图片中的所有文字原文，保留换行与原有语序，不要翻译、不要解释、不要添加任何其他内容。"));
            JSONArray content = new JSONArray();
            content.put(new JSONObject()
                    .put("type", "text")
                    .put("text", "识别这张图片中的所有文字，逐行输出原文。"));
            content.put(new JSONObject()
                    .put("type", "image_url")
                    .put("image_url", new JSONObject()
                            .put("url", "data:image/jpeg;base64," + b64)));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", content));
            body.put("messages", messages);
            body.put("temperature", 0.1);

            Request req = auth(baseUrl + "/chat/completions")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    cb.onResult(null, e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response r = response) {
                        String resp = r.body() != null ? r.body().string() : "";
                        if (!r.isSuccessful()) {
                            cb.onResult(null, "HTTP " + r.code() + " " + resp);
                            return;
                        }
                        JSONObject root = new JSONObject(resp);
                        JSONArray choices = root.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            String content = choices.optJSONObject(0)
                                    .optJSONObject("message")
                                    .optString("content", "");
                            cb.onResult(content.trim(), null);
                        } else {
                            cb.onResult(null, "接口未返回识别结果");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "extractText error", e);
                        cb.onResult(null, e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onResult(null, e.getMessage());
        }
    }

    /** 根据源/目标语言（可含"自动检测"）构造翻译指令。 */
    private String buildInstruction(String text, String source, String target) {
        boolean autoSource = LangUtil.AUTO.equals(source);
        boolean autoTarget = LangUtil.AUTO.equals(target);
        if (autoSource && autoTarget) {
            return "自动检测下面这段文字的语言并翻译：中文翻译成英文，其他语言翻译成简体中文。只输出译文。\n" + text;
        }
        if (autoSource) {
            return "自动检测下面这段文字的语言并翻译成" + target + "。只输出译文。\n" + text;
        }
        if (autoTarget) {
            String tgt = LangUtil.ZH.equals(source) ? LangUtil.EN : LangUtil.ZH;
            return "把下面这段" + source + "文本翻译成" + tgt + "。只输出译文。\n" + text;
        }
        return "把下面这段" + source + "文本翻译成" + target + "。只输出译文。\n" + text;
    }
}
