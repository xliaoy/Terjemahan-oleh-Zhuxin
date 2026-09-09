package com.zhuxin.Translation;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * 基于 ML Kit 的本地文字识别，离线可用。
 * 同时持有拉丁语系（英文、法文、德文等）、日文（假名 + 汉字）、韩文（谚文）与
 * 天城文（印地文等）四类识别器，并行识别后取识别结果更完整的一方。
 */
public final class OcrHelper {

    public interface Callback {
        void onResult(String text);
    }

    private static final int NUM_RECOGNIZERS = 4;

    private final TextRecognizer[] recognizers = new TextRecognizer[NUM_RECOGNIZERS];

    public OcrHelper() {
        recognizers[0] = TextRecognition.getClient(
                new TextRecognizerOptions.Builder().build()); // 拉丁语系
        recognizers[1] = TextRecognition.getClient(
                new JapaneseTextRecognizerOptions.Builder().build()); // 日文
        recognizers[2] = TextRecognition.getClient(
                new KoreanTextRecognizerOptions.Builder().build()); // 韩文
        recognizers[3] = TextRecognition.getClient(
                new DevanagariTextRecognizerOptions.Builder().build()); // 印地文等天城文
    }

    public void recognize(Bitmap bitmap, final Callback cb) {
        if (bitmap == null || bitmap.isRecycled()) {
            cb.onResult(null);
            return;
        }

        // 截图过小时适度放大，提升小字（含印尼语等拉丁语系）识别率
        Bitmap input = bitmap;
        if (bitmap.getWidth() < 1600) {
            float scale = 1600f / bitmap.getWidth();
            scale = Math.min(scale, 3f);
            input = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.round(bitmap.getWidth() * scale),
                    Math.round(bitmap.getHeight() * scale),
                    true);
        }
        final Bitmap scaled = input;
        final boolean isScaled = scaled != bitmap;

        final InputImage image = InputImage.fromBitmap(scaled, 0);
        final String[] results = new String[NUM_RECOGNIZERS];
        final boolean[] done = new boolean[NUM_RECOGNIZERS];

        final Runnable maybeFinish = new Runnable() {
            @Override
            public void run() {
                for (boolean d : done) {
                    if (!d) {
                        return;
                    }
                }
                // 所有识别器都完成：取结果更完整（更长）的一个
                String best = "";
                for (String r : results) {
                    if (r != null && r.length() > best.length()) {
                        best = r;
                    }
                }
                if (isScaled) {
                    scaled.recycle();
                }
                cb.onResult(best.isEmpty() ? null : best.trim());
            }
        };

        for (int i = 0; i < NUM_RECOGNIZERS; i++) {
            final int idx = i;
            recognizers[i].process(image)
                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text result) {
                            results[idx] = result == null ? "" : result.getText();
                            done[idx] = true;
                            maybeFinish.run();
                        }
                    })
                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            results[idx] = "";
                            done[idx] = true;
                            maybeFinish.run();
                        }
                    });
        }
    }

    /**
     * AI 视觉在线 OCR：把截图交给配置的 OpenAI 兼容视觉模型提取文字（需模型支持图片输入）。
     * 优先使用独立的视觉 OCR 配置（Base URL / API Key / 模型），留空时回退到主 AI 配置。
     */
    public static void recognizeOnline(android.content.Context c, Bitmap bitmap, final Callback cb) {
        String baseUrl = Prefs.visionBaseUrl(c);
        if (baseUrl.isEmpty()) {
            baseUrl = Prefs.baseUrl(c);
        }
        String apiKey = Prefs.visionApiKey(c);
        if (apiKey.isEmpty()) {
            apiKey = Prefs.apiKey(c);
        }
        String model = Prefs.visionModel(c);
        if (model.isEmpty()) {
            model = Prefs.model(c);
        }
        new AiClient(baseUrl, apiKey, model).extractText(bitmap, new AiClient.TextCallback() {
            @Override
            public void onResult(String text, String error) {
                if (error != null) {
                    DebugLog.error(c, "OCR", "AI 视觉 OCR 失败: " + error, null);
                    cb.onResult(null);
                } else {
                    cb.onResult(text);
                }
            }
        });
    }

    public void close() {
        for (TextRecognizer r : recognizers) {
            if (r != null) {
                r.close();
            }
        }
    }
}
