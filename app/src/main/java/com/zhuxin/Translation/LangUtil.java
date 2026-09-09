package com.zhuxin.Translation;

/**
 * 语言相关工具：自动检测常量、中日韩检测、目标语言解析与离线模型语言名映射。
 */
public final class LangUtil {

    public static final String AUTO = "自动检测";
    public static final String ZH = "简体中文";
    public static final String EN = "英文";

    private LangUtil() {
    }

    /** 是否含中日韩字符（用于“自动检测”时的方向判断）。 */
    public static boolean containsCjk(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeScript sc = Character.UnicodeScript.of(s.charAt(i));
            if (sc == Character.UnicodeScript.HAN
                    || sc == Character.UnicodeScript.HIRAGANA
                    || sc == Character.UnicodeScript.KATAKANA
                    || sc == Character.UnicodeScript.HANGUL) {
                return true;
            }
        }
        return false;
    }

    /** 把下拉里的中文语言标签映射为离线模型可理解的语言词。 */
    public static String offlineWord(String label) {
        if (label == null) {
            return ZH;
        }
        switch (label) {
            case EN: return "英语";
            case "日文": return "日语";
            case "韩文": return "韩语";
            case "法文": return "法语";
            case "德文": return "德语";
            case "西班牙文": return "西班牙语";
            case "俄文": return "俄语";
            case ZH:
            default: return ZH;
        }
    }
}
