package com.mcvoice.ttvoice.tts;

/**
 * 下载状态文案的编码载体：{@code key} 后面用 U+001F 连接若干参数。
 *
 * <p>common 模块不能依赖 Minecraft 的 Component，所以这里只做纯字符串编码，
 * 由界面层的 {@code ScreenUtil.statusComponent} 解码成可翻译文本。
 * 以 {@code voice.mcvoice.} 或 {@code download.mcvoice.} 开头的参数会被当作
 * 嵌套的翻译 token 继续解析，因此子消息（例如“，已保留 12.3 MB”）也能翻译。
 */
public final class DownloadStatus {
    /** 参数分隔符（Unit Separator），不会出现在模型名/文件名/错误信息里。 */
    public static final char SEP = '\u001f';

    private DownloadStatus() {
    }

    public static String encode(String key, String... parts) {
        StringBuilder builder = new StringBuilder(key);
        for (String part : parts) {
            builder.append(SEP).append(part == null ? "" : part);
        }
        return builder.toString();
    }

    public static String[] decode(String token) {
        return token.split(String.valueOf(SEP), -1);
    }
}
