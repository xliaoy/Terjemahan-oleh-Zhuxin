package com.zhuxin.Translation;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 离线模型下载器：把 GGUF 模型文件流式下载到应用私有目录，带进度回调。
 */
public final class ModelDownloader {

    public interface ProgressCallback {
        void onProgress(long downloadedBytes, long totalBytes);
        void onDone(boolean success, String error);
    }

    private static volatile Call currentCall;

    public static void cancel() {
        Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
    }

    public static void download(Context c, String url, final ProgressCallback cb) {
        final File target = LocalTranslator.modelPath(c);
        File dir = target.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        final File tmp = new File(target.getAbsolutePath() + ".part");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(url).get().build();
        Call call = client.newCall(request);
        currentCall = call;

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                currentCall = null;
                if (call.isCanceled()) {
                    cb.onDone(false, "已取消");
                } else {
                    cb.onDone(false, e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        currentCall = null;
                        cb.onDone(false, "HTTP " + r.code());
                        return;
                    }
                    long total = r.body() != null ? r.body().contentLength() : -1L;
                    InputStream in = r.body() != null ? r.body().byteStream() : null;
                    if (in == null) {
                        currentCall = null;
                        cb.onDone(false, "空响应");
                        return;
                    }
                    try (InputStream is = in; FileOutputStream fos = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        long downloaded = 0;
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                            downloaded += n;
                            cb.onProgress(downloaded, total);
                        }
                        fos.flush();
                    }
                    if (tmp.renameTo(target)) {
                        currentCall = null;
                        cb.onDone(true, null);
                    } else {
                        currentCall = null;
                        cb.onDone(false, "保存失败");
                    }
                } catch (Exception e) {
                    currentCall = null;
                    cb.onDone(false, e.getMessage());
                }
            }
        });
    }
}
