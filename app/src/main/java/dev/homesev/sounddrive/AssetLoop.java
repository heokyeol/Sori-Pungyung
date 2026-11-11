package dev.homesev.sounddrive;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/** Decodes a compressed audio asset (OGG/MP3/AAC) into float[channel][sample]. */
final class AssetLoop {
    private AssetLoop() {}

    static float[][] decode(Context ctx, String name) {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try (AssetFileDescriptor fd = ctx.getAssets().openFd(name)) {
            ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            ex.selectTrack(0);
            MediaFormat fmt = ex.getTrackFormat(0);
            int ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream(8 << 20);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false, outDone = false;
            byte[] tmp = new byte[0];
            while (!outDone) {
                if (!inDone) {
                    int ii = codec.dequeueInputBuffer(10_000);
                    if (ii >= 0) {
                        ByteBuffer in = codec.getInputBuffer(ii);
                        int size = ex.readSampleData(in, 0);
                        if (size < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true; }
                        else { codec.queueInputBuffer(ii, 0, size, ex.getSampleTime(), 0); ex.advance(); }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10_000);
                if (oi >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(oi);
                    if (tmp.length < info.size) tmp = new byte[info.size];
                    out.position(info.offset);
                    out.get(tmp, 0, info.size);
                    pcm.write(tmp, 0, info.size);
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    ch = codec.getOutputFormat().getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
            }
            ShortBuffer sb = ByteBuffer.wrap(pcm.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            int frames = sb.remaining() / ch;
            float[][] outCh = new float[Math.min(ch, 2)][frames];
            for (int i = 0; i < frames; i++) {
                for (int c = 0; c < outCh.length; c++) outCh[c][i] = sb.get(i * ch + c) / 32768f;
            }
            Log.i("SoundDrive", "decoded " + name + ": " + frames + " frames, " + ch + " ch");
            return frames > 0 ? outCh : null;
        } catch (Exception e) {
            Log.w("SoundDrive", "decode " + name, e);
            return null;
        } finally {
            if (codec != null) try { codec.stop(); codec.release(); } catch (RuntimeException ignored) {}
            ex.release();
        }
    }
}
