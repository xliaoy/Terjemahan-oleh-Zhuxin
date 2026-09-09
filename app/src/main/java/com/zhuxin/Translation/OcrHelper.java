package com.zhuxin.Translation;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

/**
 * 基于 ML Kit 的本地文字识别，离线可用。
 * 同时持有拉丁语系识别器（英文、法文、德文等）与日文识别器（假名 + 汉字），
 * 并行识别后取识别结果更完整的一方，从而同时支持英文与日文。
 */
public final class OcrHelper {

    public interface Callback {
        void onResult(String text);
    }

    private static final int NUM_RECOGNIZERS = 2;

    private final TextRecognizer[] recognizers = new TextRecognizer[NUM_RECOGNIZERS];

    public OcrHelper() {
        recognizers[0] = TextRecognition.getClient(
                new TextRecognizerOptions.Builder().build()); // 拉丁语系
        recognizers[1] = TextRecognition.getClient(
                new JapaneseTextRecognizerOptions.Builder().build()); // 日文
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
                // 两个识别器都完成：取结果更完整（更长）的一个
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

    public void close() {
        for (TextRecognizer r : recognizers) {
            if (r != null) {
                r.close();
            }
        }
    }
}
