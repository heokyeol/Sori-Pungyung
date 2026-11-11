package dev.homesev.sounddrive;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.util.Log;

import java.util.Locale;

import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.listener.ResultListener;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.listener.EventListener;

/**
 * Voice in/out with the public Pleos SDKs (on-device mode, no server key):
 * SpeechToText for commands, TextToSpeech for short spoken answers.
 * Falls back to Android's own TTS if the Pleos TTS can't start.
 */
final class VoiceControl {
    interface Heard {
        void onListening(boolean on);
        void onPartial(String text);
        void onText(String text);
        void onError(String why);
    }
    interface Speaking { void onSpeaking(boolean on); }

    private static final String TAG = "SoundDrive";
    private final Context ctx;
    private final Handler main;
    private final Speaking speaking;
    private SpeechToText stt;
    private TextToSpeech tts;
    private android.speech.tts.TextToSpeech fallback;
    private boolean sttReady, ttsReady, fallbackReady;
    private Heard heard;

    VoiceControl(Context ctx, Handler main, Speaking speaking) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.speaking = speaking;
        startStt();
        startTts();
    }

    private void startStt() {
        try {
            stt = new SpeechToText(ctx, ai.pleos.playground.stt.constant.Mode.ON_DEVICE);
            stt.setLocale(Locale.KOREAN);
            stt.addListener(new ResultListener() {
                @Override public void onUpdated(String text, boolean isFinal) {
                    main.post(() -> {
                        if (heard == null) return;
                        if (isFinal) { Heard h = heard; heard = null; h.onListening(false); h.onText(text == null ? "" : text); }
                        else heard.onPartial(text == null ? "" : text);
                    });
                }
                @Override public void onUpdatedEpdData(long a, long b) {}
                @Override public void onStartedRecognition() { main.post(() -> { if (heard != null) heard.onListening(true); }); }
                @Override public void onEndedRecognition() { main.post(() -> { if (heard != null) heard.onListening(false); }); }
                @Override public void onError(ai.pleos.playground.stt.constant.ErrorType e) {
                    main.post(() -> { if (heard != null) { Heard h = heard; heard = null; h.onListening(false); h.onError(sttError(e)); } });
                }
                @Override public void onReady() {}
                @Override public void onUpdatedRms(double rms) {}
            });
            stt.initialize(new ai.pleos.playground.stt.listener.OnInitializeListener() {
                @Override public void onSuccess() { sttReady = true; Log.i(TAG, "Pleos STT ready"); }
                @Override public void onFailure(ai.pleos.playground.stt.constant.ErrorType e) { Log.w(TAG, "Pleos STT init failed: " + e); }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Pleos STT unavailable", e);
            stt = null;
        }
    }

    private void startTts() {
        try {
            tts = new TextToSpeech(ctx, ai.pleos.playground.tts.constant.Mode.ON_DEVICE);
            tts.setLocale(Locale.KOREAN);
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.addEventListener(new EventListener() {
                @Override public void onStart() { main.post(() -> speaking.onSpeaking(true)); }
                @Override public void onReady() {}
                @Override public void onDone() { main.post(() -> speaking.onSpeaking(false)); }
                @Override public void onError(ai.pleos.playground.tts.constant.ErrorType e) { main.post(() -> speaking.onSpeaking(false)); }
                @Override public void onStop() { main.post(() -> speaking.onSpeaking(false)); }
                @Override public void onUpdatedRms(double rms) {}
            });
            tts.initialize(new ai.pleos.playground.tts.listener.OnInitializeListener() {
                @Override public void onSuccess() { ttsReady = true; Log.i(TAG, "Pleos TTS ready"); }
                @Override public void onFailure(ai.pleos.playground.tts.constant.ErrorType e) { Log.w(TAG, "Pleos TTS init failed: " + e); startFallback(); }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Pleos TTS unavailable", e);
            tts = null;
            startFallback();
        }
    }

    private void startFallback() {
        if (fallback != null) return;
        main.post(() -> fallback = new android.speech.tts.TextToSpeech(ctx, status -> {
            fallbackReady = status == android.speech.tts.TextToSpeech.SUCCESS;
            if (fallbackReady) fallback.setLanguage(Locale.KOREAN);
        }));
    }

    boolean canListen() { return stt != null && sttReady; }

    /** Listen for one sentence. */
    void listen(Heard h) {
        if (!canListen()) { h.onError("음성인식을 아직 쓸 수 없어요"); return; }
        heard = h;
        try { stt.request(); h.onListening(true); }
        catch (RuntimeException e) { heard = null; h.onError("음성인식을 시작하지 못했어요"); Log.w(TAG, "stt request", e); }
    }

    void cancel() {
        heard = null;
        try { if (stt != null) stt.stop(); } catch (RuntimeException ignored) {}
    }

    /** Speak a short answer (music ducks while it talks). */
    void say(String text) {
        Log.i(TAG, "say: " + text);
        try {
            if (tts != null && ttsReady) { tts.speak(text, true); return; }
        } catch (RuntimeException e) { Log.w(TAG, "Pleos TTS speak", e); startFallback(); }
        if (fallback != null && fallbackReady) {
            speaking.onSpeaking(true);
            fallback.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { main.post(() -> speaking.onSpeaking(false)); }
                @Override public void onError(String id) { main.post(() -> speaking.onSpeaking(false)); }
            });
            fallback.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "sd");
        }
    }

    private static String sttError(ai.pleos.playground.stt.constant.ErrorType e) {
        if (e == null) return "잘 못 들었어요";
        switch (e.name()) {
            case "MIC_ERROR": case "PERMISSION_DENIED": return "마이크를 쓸 수 없어요";
            case "TIMEOUT": return "말씀이 없어서 멈췄어요";
            case "NETWORK_ERROR": return "인터넷 연결이 필요해요";
            default: return "잘 못 들었어요";
        }
    }

    void release() {
        cancel();
        try { if (stt != null) stt.release(); } catch (RuntimeException ignored) {}
        try { if (tts != null) { tts.stop(); tts.release(); } } catch (RuntimeException ignored) {}
        if (fallback != null) fallback.shutdown();
    }
}
