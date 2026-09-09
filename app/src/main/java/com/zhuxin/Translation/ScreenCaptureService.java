package com.zhuxin.Translation;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * 屏幕截图前台服务：持有 MediaProjection，提供指定区域的截图能力。
 */
public class ScreenCaptureService extends Service {

    public interface CaptureCallback {
        void onResult(Bitmap bitmap);
    }

    private static final String CHANNEL_ID = "screen_capture";
    private static final int NOTIF_ID = 1;

    private static ScreenCaptureService instance;

    private MediaProjection projection;
    private MediaProjectionManager projectionManager;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread thread;
    private Handler handler;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;

    public static ScreenCaptureService get() {
        return instance;
    }

    public static boolean isReady() {
        return instance != null && instance.projection != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 必须在 onCreate 立即前台化，否则 startForegroundService 会抛超时异常。
        // 本服务仅在用户刚同意 MediaProjection 授权后由 onActivityResult 启动，处于授权豁免窗口内。
        startForegroundCompat();

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        densityDpi = dm.densityDpi;

        thread = new HandlerThread("screen-capture");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            if (projection == null) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
        Intent data = null;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra("data", Intent.class);
        } else {
            data = intent.getParcelableExtra("data");
        }

        if (resultCode == Activity.RESULT_OK && data != null && projection == null) {
            try {
                projection = projectionManager.getMediaProjection(resultCode, data);
            } catch (Exception e) {
                projection = null;
            }
            if (projection != null) {
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        projection = null;
                    }
                }, handler);
            } else {
                stopSelf();
            }
        } else if (projection == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        Notification notif = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, notif);
        }
    }

    /** 截取指定屏幕区域（region 为屏幕绝对坐标），裁剪后通过回调返回。 */
    public synchronized void capture(final Rect region, final CaptureCallback cb) {
        if (projection == null) {
            cb.onResult(null);
            return;
        }

        releaseCapture();
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "capture",
                screenWidth,
                screenHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler);

        final long deadline = System.currentTimeMillis() + 3000;
        handler.post(new Runnable() {
            @Override
            public void run() {
                Bitmap cropped = tryAcquire(region);
                if (cropped != null) {
                    releaseCapture();
                    cb.onResult(cropped);
                } else if (System.currentTimeMillis() < deadline) {
                    handler.postDelayed(this, 60);
                } else {
                    releaseCapture();
                    cb.onResult(null);
                }
            }
        });
    }

    private Bitmap tryAcquire(Rect region) {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                return null;
            }
            Bitmap full = imageToBitmap(image);
            // 新建 VirtualDisplay 后的前几帧常为全黑，跳过等待真实画面，
            // 否则黑帧裁剪后 OCR 必然识别不到文字。
            if (!frameHasContent(full)) {
                full.recycle();
                return null;
            }
            Rect clamped = clampRegion(region);
            if (clamped == null) {
                return full;
            }
            Bitmap cropped = Bitmap.createBitmap(full, clamped.left, clamped.top, clamped.width(), clamped.height());
            full.recycle();
            return cropped;
        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    /** 采样判断整帧是否接近全黑（避免把尚未就绪的黑帧当作截图结果）。 */
    private boolean frameHasContent(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int stepX = Math.max(1, w / 40);
        int stepY = Math.max(1, h / 40);
        long sum = 0;
        int n = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bmp.getPixel(x, y);
                sum += ((c >> 16) & 0xff) + ((c >> 8) & 0xff) + (c & 0xff);
                n++;
            }
        }
        return n == 0 || sum / (3L * n) >= 16;
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        int paddedWidth = screenWidth + rowPadding / pixelStride;

        Bitmap bmp = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        if (paddedWidth != screenWidth) {
            Bitmap trimmed = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight);
            bmp.recycle();
            return trimmed;
        }
        return bmp;
    }

    private Rect clampRegion(Rect region) {
        if (region == null || region.isEmpty()) {
            return null;
        }
        int left = Math.max(0, region.left);
        int top = Math.max(0, region.top);
        int right = Math.min(screenWidth, region.right);
        int bottom = Math.min(screenHeight, region.bottom);
        if (right <= left || bottom <= top) {
            return null;
        }
        return new Rect(left, top, right, bottom);
    }

    private synchronized void releaseCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "屏幕截图", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("悬浮翻译")
                .setContentText("正在捕获屏幕")
                .setSmallIcon(R.drawable.ic_notification)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseCapture();
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        if (thread != null) {
            thread.quitSafely();
        }
        instance = null;
        super.onDestroy();
    }
}
