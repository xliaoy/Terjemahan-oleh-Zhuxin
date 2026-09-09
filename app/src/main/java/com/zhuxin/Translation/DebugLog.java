package com.zhuxin.Translation;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 调试日志：仅在设置里开启「调试模式」时写入。
 * Android 10+ 写到「下载」目录的 zhuxin_debug_log.txt；低版本写到应用外部私有目录。
 */
public final class DebugLog {

    private static final String TAG = "FloatingTranslate";
    private static final String FILE_NAME = "zhuxin_debug_log.txt";
    private static final int MAX_CHARS = 200000;
    private static final Object LOCK = new Object();
    private static final StringBuilder BUFFER = new StringBuilder();

    private DebugLog() {
    }

    public static boolean enabled(Context c) {
        return c != null && Prefs.debugMode(c);
    }

    public static void event(Context c, String tag, String msg) {
        write(c, "[" + tag + "] " + msg);
    }

    public static void error(Context c, String tag, String msg, Throwable t) {
        String stack = t == null ? "" : "\n" + Log.getStackTraceString(t);
        write(c, "[" + tag + "] " + msg + stack);
    }

    private static void write(Context c, String line) {
        if (c == null || !Prefs.debugMode(c)) {
            return;
        }
        Context app = c.getApplicationContext();
        synchronized (LOCK) {
            BUFFER.append(now()).append(' ').append(line).append('\n');
            if (BUFFER.length() > MAX_CHARS) {
                BUFFER.delete(0, BUFFER.length() - MAX_CHARS);
            }
            persist(app, BUFFER.toString());
        }
    }

    private static String now() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static void persist(Context c, String content) {
        OutputStream os = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = c.getContentResolver();
                cr.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{FILE_NAME});
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    os = cr.openOutputStream(uri);
                }
            } else {
                File dir = c.getExternalFilesDir(null);
                if (dir != null) {
                    os = new FileOutputStream(new File(dir, FILE_NAME));
                }
            }
            if (os != null) {
                os.write(content.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.w(TAG, "debug log write failed", e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
