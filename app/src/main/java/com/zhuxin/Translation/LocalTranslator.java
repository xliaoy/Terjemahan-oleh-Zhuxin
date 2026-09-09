package com.zhuxin.Translation;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 本地离线翻译引擎（腾讯混元 HY-MT1.5-1.8B + llama.cpp）。
 * 通过 JNI 调用 libllama_jni.so 完成本地推理，无需联网。
 */
public final class LocalTranslator {

    private static final Object LOCK = new Object();

    private static volatile boolean inited = false;
    private static volatile boolean loaded = false;
    private static volatile boolean available = false;

    static {
        try {
            System.loadLibrary("llama_jni");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    private LocalTranslator() {
    }

    /** 离线模型文件存放路径。 */
    public static File modelPath(Context c) {
        return new File(c.getFilesDir(), "models/HY-MT1.5-1.8B-Q4_K_M.gguf");
    }

    /** 模型文件是否有效：存在且以 GGUF 魔数开头（可识别出下载不完整或损坏的文件）。 */
    public static boolean isModelValid(Context c) {
        File f = modelPath(c);
        if (!f.exists() || f.length() < 8) {
            return false;
        }
        byte[] magic = new byte[4];
        try (InputStream in = new FileInputStream(f)) {
            int n = in.read(magic);
            if (n < 4) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return magic[0] == 'G' && magic[1] == 'G' && magic[2] == 'U' && magic[3] == 'F';
    }

    /** 确保本地引擎已初始化并加载模型。返回 null 表示成功，否则返回错误信息。 */
    public static String ensureLoaded(Context c) {
        synchronized (LOCK) {
            if (loaded) {
                return null;
            }
            if (!available) {
                return "当前设备不支持本地引擎（缺少原生库）";
            }
            try {
                if (!inited) {
                    String libDir = c.getApplicationInfo().nativeLibraryDir;
                    nativeInit(libDir);
                    inited = true;
                }
                File model = modelPath(c);
                if (!isModelValid(c)) {
                    return "离线模型未下载或已损坏，请先在设置里重新下载模型";
                }
                if (!nativeLoadModel(model.getAbsolutePath())) {
                    return "模型加载失败";
                }
                if (!nativePrepare()) {
                    return "模型初始化失败（内存不足？）";
                }
                loaded = true;
                return null;
            } catch (Throwable t) {
                return "本地引擎初始化异常：" + t.getMessage();
            }
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** 阻塞式翻译，须在后台线程调用。返回译文，失败返回 null。 */
    public static String translate(String content, String sourceLang, String targetLang) {
        synchronized (LOCK) {
            String prompt = buildPrompt(content, sourceLang, targetLang);
            return nativeTranslate(prompt, 256);
        }
    }

    /** 释放模型与后端资源。 */
    public static void release() {
        synchronized (LOCK) {
            if (loaded) {
                nativeUnload();
                loaded = false;
            }
            if (inited) {
                nativeShutdown();
                inited = false;
            }
        }
    }

    private static String buildPrompt(String text, String source, String target) {
        String targetWord = resolveTargetWord(text, source, target);
        return "将以下文本翻译为" + targetWord + "，注意只需要输出翻译后的结果，不要额外解释：\n" + text;
    }

    /** 根据源/目标语言（可含“自动检测”）解析实际目标语言词。 */
    private static String resolveTargetWord(String text, String source, String target) {
        if (!LangUtil.AUTO.equals(target)) {
            return LangUtil.offlineWord(target);
        }
        if (!LangUtil.AUTO.equals(source)) {
            return LangUtil.ZH.equals(source) ? "英语" : LangUtil.ZH;
        }
        // 源/目标均自动检测：纯中文文本 → 翻成英语；含任何外文（假名/谚文/西里尔/泰文/阿拉伯文等）→ 翻成中文
        return LangUtil.isChineseText(text) ? "英语" : LangUtil.ZH;
    }

    private static native boolean nativeInit(String libDir);
    private static native boolean nativeLoadModel(String path);
    private static native boolean nativePrepare();
    private static native String nativeTranslate(String content, int nPredict);
    private static native void nativeUnload();
    private static native void nativeShutdown();
}
