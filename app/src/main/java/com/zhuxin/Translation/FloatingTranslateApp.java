package com.zhuxin.Translation;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * 应用入口：注册全局崩溃日志处理，并在启动时应用用户选择的界面语言。
 */
public class FloatingTranslateApp extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        applyAppLanguage();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
    }

    /** 读取 Prefs.app_lang 并应用到全应用（跟随系统 / 简体中文 / English）。 */
    public static void applyAppLanguage() {
        String lang = Prefs.lang(appContext);
        if ("zh".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"));
        } else if ("en".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        }
    }
}