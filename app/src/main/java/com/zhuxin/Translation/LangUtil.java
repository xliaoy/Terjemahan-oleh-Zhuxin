package com.zhuxin.Translation;

/**
 * 语言相关工具：自动检测常量、外文书写系统检测、目标语言解析与离线模型语言名映射。
 */
public final class LangUtil {

    public static final String AUTO = "自动检测";
    public static final String ZH = "简体中文";
    public static final String EN = "英文";

    private LangUtil() {
    }

    /** 是否含外文书写系统字符（拉丁、假名、谚文、西里尔、泰文、阿拉伯文、天城文、希腊文等）。 */
    public static boolean containsForeignScript(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeScript sc = Character.UnicodeScript.of(s.charAt(i));
            if (sc == Character.UnicodeScript.LATIN
                    || sc == Character.UnicodeScript.HIRAGANA
                    || sc == Character.UnicodeScript.KATAKANA
                    || sc == Character.UnicodeScript.HANGUL
                    || sc == Character.UnicodeScript.CYRILLIC
                    || sc == Character.UnicodeScript.THAI
                    || sc == Character.UnicodeScript.ARABIC
                    || sc == Character.UnicodeScript.DEVANAGARI
                    || sc == Character.UnicodeScript.GREEK) {
                return true;
            }
        }
        return false;
    }

    /** 是否可视为中文文本：含汉字且不含外文书写系统字符（用于"自动检测"时的翻译方向判断）。 */
    public static boolean isChineseText(String s) {
        if (s == null) {
            return false;
        }
        boolean hasHan = false;
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeScript sc = Character.UnicodeScript.of(s.charAt(i));
            if (sc == Character.UnicodeScript.HAN) {
                hasHan = true;
            } else if (sc != Character.UnicodeScript.COMMON
                    && sc != Character.UnicodeScript.INHERITED) {
                // 出现汉字与通用字符（标点/数字/空白）之外的内容 → 不是纯中文
                return false;
            }
        }
        return hasHan;
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
            case "葡萄牙文": return "葡萄牙语";
            case "意大利文": return "意大利语";
            case "泰文": return "泰语";
            case "越南文": return "越南语";
            case "印尼文": return "印尼语";
            case "马来文": return "马来语";
            case "阿拉伯文": return "阿拉伯语";
            case "印地文": return "印地语";
            case "土耳其文": return "土耳其语";
            case "波兰文": return "波兰语";
            case "荷兰文": return "荷兰语";
            case ZH:
            default: return ZH;
        }
    }
}
