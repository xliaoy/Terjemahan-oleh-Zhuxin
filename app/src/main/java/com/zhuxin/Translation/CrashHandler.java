package com.zhuxin.Translation;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局未捕获异常处理器：崩溃时把堆栈写到本地文件，方便无 logcat 环境下诊断。
 * 写入位置：
 * 1. 系统「下载」目录（crash_log.txt，通过 MediaStore，无需权限，Android 10+）
 * 2. app 私有目录 files/crash_<时间戳>.txt（兜底，始终可写）
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String DOWNLOAD_FILE = "crash_log.txt";

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        String report = buildReport(thread, throwable);
        try {
            saveToAppPrivate(report);
            saveToDownloads(report);
        } catch (Exception ignored) {
        }
        // 交给系统默认处理，正常结束进程并弹出“已停止运行”
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private String buildReport(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== FloatingTranslate Crash Report ===");
        pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("Thread: " + (thread == null ? "unknown" : thread.getName()));
        pw.println();
        if (throwable != null) {
            throwable.printStackTrace(pw);
        } else {
            pw.println("null throwable");
        }
        pw.flush();
        return sw.toString();
    }

    private void saveToAppPrivate(String report) throws Exception {
        String name = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        File dir = new File(appContext.getFilesDir(), "crash");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File f = new File(dir, name);
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(report.getBytes("UTF-8"));
        } finally {
            fos.close();
        }
    }

    /** 写入公共「下载」目录，方便用户直接访问/分享。Android 10+ 无需任何权限。 */
    private void saveToDownloads(String report) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, DOWNLOAD_FILE);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = appContext.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return;
            }
            try (OutputStream os = appContext.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(report.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }
}
