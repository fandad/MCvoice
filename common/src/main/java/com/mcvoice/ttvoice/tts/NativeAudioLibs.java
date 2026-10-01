package com.mcvoice.ttvoice.tts;

import com.k2fsa.sherpa.onnx.LibraryUtils;
import com.mcvoice.ttvoice.McVoiceConstants;

/**
 * 原生 onnxruntime 的加载顺序守卫（修 0.2.7 的确定性原生崩溃）。
 *
 * <p><b>根因</b>：piper-jni 与 sherpa-onnx 各自包里都带一份<b>同名</b>的 {@code onnxruntime.dll}
 * （piper 那份 12.4 MB、sherpa 那份 17.4 MB），而 Windows 的模块加载器按<b>模块名</b>去重：
 * 谁先加载，后加载的那份就拿不到，JNI 会绑到先驻留的那份上。sherpa 的 JNI 需要较新的
 * onnxruntime，一旦 Piper 先加载（它带的是旧版），sherpa 初始化时原生代码会读空指针
 * （{@code EXCEPTION_ACCESS_VIOLATION reading address 0x18}，崩在
 * {@code OfflineTts.newFromFile}），<b>整个 JVM 当场死亡，Java 层 catch 不到</b>。
 *
 * <p><b>实测（脱离游戏、最小复现，JDK 26 与 JDK 21 表现一致，与 JDK 版本无关）</b>：
 * <ul>
 *   <li>只用 sherpa → 正常（两个 JDK 都正常，所以"与 Java 26 不兼容"的猜测不成立）</li>
 *   <li>piper → sherpa → <b>必崩</b>（复现出的 pc 与真实崩溃日志相同：{@code sherpa-onnx-jni.dll+0x8add1}）</li>
 *   <li>sherpa → piper → 正常（piper 反而能与 sherpa 的 onnxruntime 共存）</li>
 *   <li>先 {@link #preloadSherpa()} 再走 piper → sherpa → 正常</li>
 * </ul>
 *
 * <p><b>策略</b>：尽早把 sherpa 的原生库抢先加载，让新的 onnxruntime 先占位（代价：约 22 MB
 * 原生库常驻内存，只在真的用 TTS 时发生）；万一预载失败（例如该平台没带 sherpa 原生库），
 * 再用 {@link #ensureSherpaUsable()} 拦住"piper 已加载还要用 sherpa"这个危险顺序 ——
 * 抛 Java 异常让上层提示，而不是让进程死掉。
 */
public final class NativeAudioLibs {
    private static boolean preloadTried;
    private static boolean sherpaResident;
    private static boolean piperResident;

    private NativeAudioLibs() {
    }

    /**
     * 抢先加载 sherpa 的原生库（连带它自带的 onnxruntime.dll）。可重复调用，只在第一次真正加载。
     * 必须在任何 Piper 使用<b>之前</b>调用，否则危险顺序已经形成。
     */
    public static synchronized void preloadSherpa() {
        if (preloadTried) {
            return;
        }
        preloadTried = true;
        try {
            LibraryUtils.load();
            sherpaResident = true;
            McVoiceConstants.LOGGER.info(
                "已抢先加载 sherpa 原生库（避免 piper 的旧版 onnxruntime.dll 先占位导致原生崩溃）");
        } catch (Throwable t) {
            // 没有原生库（非 Windows x64）或加载失败都不能影响启动，靠 ensureSherpaUsable 兜底。
            McVoiceConstants.LOGGER.warn(
                "抢先加载 sherpa 原生库失败；若本会话先用过 Piper，再用 Sherpa/Kokoro 声线将被拒绝（而不是原生崩溃）", t);
        }
    }

    /** Piper 原生库已加载（由 {@link TtsManager} 在构造 PiperEngine 后登记）。 */
    public static synchronized void markPiperLoaded() {
        piperResident = true;
    }

    /**
     * 构造 sherpa 系（SHERPA / KOKORO）引擎前的守卫。
     * 只有在"sherpa 原生库没抢到、而 piper 已经加载"时才拒绝 —— 那正是必崩的顺序。
     */
    public static synchronized void ensureSherpaUsable() {
        if (sherpaResident || !piperResident) {
            return;
        }
        throw new IllegalStateException(
            "本会话已加载 Piper 的原生库，无法再安全初始化 Sherpa/Kokoro 声线："
            + "piper 与 sherpa 各带一份同名 onnxruntime.dll，Windows 只能保留先加载的那份，"
            + "强行初始化会让游戏进程直接消失（原生崩溃，无法被捕获）。"
            + "请重启游戏后先选 Sherpa/Kokoro 声线，或继续使用 Piper/SAPI 声线。");
    }
}
