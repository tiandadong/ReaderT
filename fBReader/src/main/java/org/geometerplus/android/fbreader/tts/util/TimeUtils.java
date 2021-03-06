package org.geometerplus.android.fbreader.tts.util;

/**
 * 时间相关的方法
 */
public class TimeUtils {

    /**
     * 字符数转响应的时间
     *
     * @param wordCount 文字数字
     * @param speed     语速
     * @return 格式化的时长格式（mm:ss）
     */
    public static String getTimeByWordCount(int wordCount, float speed) {
        int second = getTimeMillis(wordCount, speed) / 1000;
        return second2Time(second);
    }

    /**
     * 计算后所得时间毫秒
     *
     * @param wordCount 字数
     * @param speed     语速（百度语音）
     * @return 计算后所得时间毫秒
     */
    public static int getTimeMillis(int wordCount, float speed) {
        int second = 0;
        if (speed == 3) {
            second = wordCount * 321;
        } else if (speed == 5) {
            second = wordCount * 233;
        } else if (speed == 8) {
            second = wordCount * 162;
        } else if (speed == 10) {
            second = wordCount * 144;
        } else if (speed == 15) {
            second = wordCount * 146;
        }
        return second;
    }

    /**
     * 秒转相对应的时间格式
     *
     * @param count 秒
     * @return 时间字符串格式（mm:ss）
     */
    private static String second2Time(int count) {
        StringBuilder builder = new StringBuilder();
        int minute = count / 60;
        int second = count % 60;
        // 两位数以上就全显示，否则补零
        if (minute >= 10) {
            builder.append(minute);
        } else {
            builder.append(0).append(minute);
        }
        builder.append(":");
        if (second >= 10) {
            builder.append(second);
        } else {
            builder.append(0).append(second);
        }
        return builder.toString();
    }

    public static String millis2Time(int millis) {
        return second2Time(millis / 1000);
    }
}