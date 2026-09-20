package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.McVoiceConstants;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把 TTS 音频额外送到指定的音频输出设备。
 *
 * <p>典型用途：VB-CABLE / VoiceMeeter 这类虚拟声卡——mod 把音频写进 "CABLE Input"，
 * 其他软件（Discord、OBS、会议软件）把麦克风选成 "CABLE Output"，就等于把游戏内打字
 * 当成麦克风说话了。Windows 上"录音设备"必须由驱动提供，程序本身无法凭空出现在麦克风
 * 列表里，所以这是唯一可行的做法。
 *
 * <p>只用 JDK 自带的 {@code javax.sound.sampled}，不引入任何新依赖。项目内部音频统一是
 * 48kHz / 16bit / 单声道，虚拟声卡通常要立体声，因此这里按候选格式逐个尝试并把单声道
 * 复制成立体声。
 */
public final class AudioOutputs {

    /** 目标格式候选（按优先级）：48k 立体声最通用，其次 48k 单声道。 */
    private static final int[][] RATE_CHANNELS = { {48000, 2}, {48000, 1} };

    /**
     * 设备线缓冲时长（毫秒）——这块真正决定"送外部设备"那一路的稳态延迟。
     * 实测（VB-CABLE，按 20ms 一帧实时喂）：请求 100ms → 真正排在设备前的音频平均 54ms；
     * 40ms → 32ms；20ms → 18ms。压小降延迟，但也压掉了抵抗卡顿的安全余量，
     * 40ms 是两者的折中（后面还有 1 秒的队列兜着，不会因为线程被调度延迟就丢音）。
     */
    private static final int LINE_BUFFER_MILLIS = 40;

    /** 判断虚拟声卡的名字关键字（用于界面里标注"虚拟麦克风（推荐）"）。 */
    private static final String[] VIRTUAL_HINTS = {
        "cable", "vb-audio", "vbaudio", "voicemeeter", "virtual audio", "virtual cable",
        "scream", "loopback", "vac "
    };

    /** 一个可播放的输出设备。 */
    public record Device(String name, boolean virtualCable) {
    }

    private AudioOutputs() {
    }

    /** 列出所有支持播放的设备（AudioSystem 的 mixer 名）。 */
    public static List<Device> list() {
        List<Device> devices = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            String name = info.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!supportsPlayback(AudioSystem.getMixer(info))) {
                continue;
            }
            boolean duplicate = false;
            for (Device existing : devices) {
                if (existing.name().equals(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                devices.add(new Device(name, isVirtualCable(name)));
            }
        }
        return devices;
    }

    /** 名字像虚拟声卡吗（用于推荐标注）。 */
    public static boolean isVirtualCable(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : VIRTUAL_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsPlayback(Mixer mixer) {
        for (Line.Info lineInfo : mixer.getSourceLineInfo()) {
            if (lineInfo instanceof DataLine.Info dataLine
                    && SourceDataLine.class.isAssignableFrom(dataLine.getLineClass())) {
                return true;
            }
        }
        return false;
    }

    private static Mixer.Info findMixer(String deviceName) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (deviceName.equals(info.getName()) && supportsPlayback(AudioSystem.getMixer(info))) {
                return info;
            }
        }
        return null;
    }

    /**
     * 一次朗读对应一个 Sink：打开设备 → 逐帧写入 → 关闭。用完必须 {@link #close()}。
     * 设备被拔掉或格式不支持时 open/write 会抛异常，由调用方兜住并只写日志。
     */
    public static final class Sink implements AutoCloseable {
        /** 设备侧队列上限（50 帧 ≈ 1 秒），超出丢最旧，避免延迟无限增长。 */
        private static final int QUEUE_CAPACITY = 50;

        private final SourceDataLine line;
        private final float volume;
        private final boolean stereo;
        private final ArrayDeque<short[]> queue = new ArrayDeque<>();
        private final Thread writer;
        private boolean closed;

        private Sink(SourceDataLine line, float volume, boolean stereo) {
            this.line = line;
            this.volume = volume;
            this.stereo = stereo;
            this.writer = new Thread(this::pump, "mcvoice-audioout-writer");
            this.writer.setDaemon(true);
            this.writer.start();
        }

        public static Sink open(String deviceName, float volume) throws Exception {
            Mixer.Info info = findMixer(deviceName);
            if (info == null) {
                throw new IllegalStateException("找不到可播放的输出设备: " + deviceName);
            }
            // 必须用 mixer.isLineSupported / mixer.getLine 在这个 mixer 上开线：
            // AudioSystem.getLine(...) 按全局 provider 顺序解析，会开到别的设备上（实测会漏到默认扬声器）。
            Mixer mixer = AudioSystem.getMixer(info);
            for (int[] candidate : RATE_CHANNELS) {
                AudioFormat format = new AudioFormat(candidate[0], 16, candidate[1], true, false);
                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
                if (!mixer.isLineSupported(lineInfo)) {
                    continue;
                }
                try {
                    SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                    int bufferBytes = Math.max(candidate[0] * LINE_BUFFER_MILLIS / 1000, 1024)
                        * candidate[1] * Short.BYTES;
                    line.open(format, bufferBytes);
                    line.start();
                    return new Sink(line, volume, candidate[1] == 2);
                } catch (Exception e) {
                    McVoiceConstants.LOGGER.warn(
                        "打开音频输出设备失败（{}Hz/{}ch）：{}", candidate[0], candidate[1], deviceName, e);
                }
            }
            throw new IllegalStateException("设备不支持 48kHz 输出: " + deviceName);
        }

        /**
         * 写入一帧 48kHz / 16bit / 单声道 PCM。
         *
         * <p>只入队、立刻返回：设备写入是阻塞 I/O，绝不能让它拖慢 20ms 一帧的播放节拍
         * （否则每帧变成"工作量 + 20ms"，音频引擎持续欠载 → 说话卡顿）。
         * 调用方不得复用传入的数组（与 SVC_QUEUE 的约定一致）。
         */
        public void write(short[] monoFrame) {
            synchronized (queue) {
                if (closed) {
                    return;
                }
                while (queue.size() >= QUEUE_CAPACITY) {
                    queue.pollFirst();
                }
                queue.addLast(monoFrame);
                queue.notifyAll();
            }
        }

        /** 独立的写设备线程：这里阻塞多久都不影响播放节拍。 */
        private void pump() {
            try {
                while (true) {
                    short[] frame;
                    synchronized (queue) {
                        while (queue.isEmpty() && !closed) {
                            queue.wait(100L);
                        }
                        if (queue.isEmpty()) {
                            return;
                        }
                        frame = queue.pollFirst();
                    }
                    writeFrame(frame);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                McVoiceConstants.LOGGER.warn("写入音频输出设备失败", e);
            }
        }

        private void writeFrame(short[] monoFrame) {
            short[] scaled = AudioUtil.applyVolume(monoFrame, volume);
            byte[] bytes;
            if (stereo) {
                ByteBuffer buffer = ByteBuffer.allocate(scaled.length * 2 * Short.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);
                for (short value : scaled) {
                    buffer.putShort(value);
                    buffer.putShort(value);
                }
                bytes = buffer.array();
            } else {
                bytes = AudioUtil.toBytes(scaled);
            }
            line.write(bytes, 0, bytes.length);
        }

        @Override
        public void close() {
            synchronized (queue) {
                closed = true;
                queue.notifyAll();
            }
            try {
                // 等队列里剩下的帧写完（最多 1 秒缓冲），避免结尾被切掉。
                writer.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                line.drain();
                line.stop();
            } catch (Exception ignored) {
                // 设备可能已被拔掉，忽略即可。
            }
            try {
                line.close();
            } catch (Exception ignored) {
                // 同上。
            }
        }
    }
}
