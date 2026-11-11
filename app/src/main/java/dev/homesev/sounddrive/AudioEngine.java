package dev.homesev.sounddrive;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

/** Streams the Mixer output to an AudioTrack on a dedicated thread. */
final class AudioEngine {
    static final int SR = 48000, BLOCK = 512;
    final Mixer mixer;
    private Thread thread;
    private volatile boolean running;
    private AudioTrack track;

    AudioEngine(SoundSource[] sources) { mixer = new Mixer(sources, SR); }

    boolean isRunning() { return running; }

    synchronized void start() {
        if (running) return;
        int min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        int bytes = Math.max(min, BLOCK * 2 * 4 * 4);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        running = true;
        final AudioTrack t = track;
        thread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            float[] buf = new float[BLOCK * 2];
            try {
                t.play();
                while (running) {
                    mixer.render(buf, BLOCK);
                    int w = t.write(buf, 0, buf.length, AudioTrack.WRITE_BLOCKING);
                    if (w < 0) { Log.w("SoundDrive", "AudioTrack write " + w); break; }
                }
            } catch (RuntimeException e) {
                Log.e("SoundDrive", "audio thread", e);
            } finally {
                try { t.pause(); t.flush(); } catch (RuntimeException ignored) {}
                t.release();
            }
        }, "SoundDriveAudio");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            thread = null;
        }
        track = null;
    }
}
