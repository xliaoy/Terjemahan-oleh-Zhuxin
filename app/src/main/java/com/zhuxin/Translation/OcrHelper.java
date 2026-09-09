package com.zhuxin.Translation;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * 基于 ML Kit 的本地文字识别（拉丁语系，用于识别英文等外国文字，离线可用）。
 */
public final class OcrHelper {

    public interface Callback {
        void onResult(String text);
    }

    private final TextRecognizer recognizer =
            TextRecognition.getClient(new TextRecognizerOptions.Builder().build());

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

        InputImage image = InputImage.fromBitmap(scaled, 0);
        recognizer.process(image)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text result) {
                        cb.onResult(result == null ? "" : result.getText());
                        if (isScaled) {
                            scaled.recycle();
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        if (isScaled) {
                            scaled.recycle();
                        }
                        cb.onResult(null);
                    }
                });
    }

    public void close() {
        recognizer.close();
    }
}
