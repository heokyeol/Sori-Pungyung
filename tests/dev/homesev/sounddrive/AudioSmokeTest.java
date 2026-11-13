package dev.homesev.sounddrive;

/** Host-side checks for the audio core (no Android needed). Run with plain javac/java. */
public final class AudioSmokeTest {
    private static int pass, fail;

    public static void main(String[] args) throws Exception {
        float sr = 48000;

        // 1) Panning direction.
        Spatial.Target t = new Spatial.Target();
        Spatial.target(0, -0.6f, sr, t);
        check("front is balanced", Math.abs(t.gL - t.gR) < 1e-4);
        Spatial.target(0.6f, 0, sr, t);
        check("right source louder on right", t.gR > t.gL * 3);
        check("right source delays left ear", t.dL > 20 && t.dR == 0);
        check("right source shadows left ear", t.aL < t.aR);
        Spatial.target(-0.6f, 0, sr, t);
        check("left source louder on left", t.gL > t.gR * 3);
        float frontA, rearA;
        Spatial.target(0, -0.6f, sr, t); frontA = t.aL;
        Spatial.target(0, 0.6f, sr, t); rearA = t.aL;
        check("behind sounds darker", rearA < frontA);
        Spatial.target(0, -0.2f, sr, t); float near = t.gL + t.gR;
        Spatial.target(0, -1.0f, sr, t); float far = t.gL + t.gR;
        check("farther is quieter", far < near * 0.5);

        // 2) Every generator renders finite, non-silent, bounded audio for 20 s.
        for (SoundSource s : Sources.all()) {
            if (s.asset != null) s.loadFailed = true; // no assets on the PC: test the synth fallback
            if (s instanceof Sources.Recorded) continue; // recording-only sounds are silent without the asset
            float[] buf = new float[512];
            double sum = 0, peak = 0;
            boolean finite = true;
            int blocks = (int) (sr * 20 / buf.length);
            for (int b = 0; b < blocks; b++) {
                s.render(buf, buf.length, sr);
                for (float v : buf) {
                    if (!Float.isFinite(v)) finite = false;
                    sum += v * v;
                    peak = Math.max(peak, Math.abs(v));
                }
            }
            double rms = Math.sqrt(sum / (blocks * (double) buf.length));
            System.out.printf("   %-6s rms=%.3f peak=%.3f%n", s.id, rms, peak);
            check(s.id + " finite", finite);
            check(s.id + " audible", rms > 0.01);
            check(s.id + " not clipping hard", peak < 2.5);
        }

        // 3) Full mix: all on, output within [-1,1], fades in, panning reflected in L/R energy.
        SoundSource[] all = Sources.all();
        for (SoundSource s : all) { s.enabled = true; s.loadFailed = true; }
        Mixer m = new Mixer(all, sr);
        float[] out = new float[1024];
        double peak = 0;
        for (int b = 0; b < 400; b++) {
            m.render(out, 512);
            for (float v : out) peak = Math.max(peak, Math.abs(v));
        }
        check("mix bounded", peak <= 1.0);
        check("mix audible", peak > 0.05);

        SoundSource[] one = { new Sources.Pad() };
        one[0].loadFailed = true; // no assets on the PC: use the synth path
        one[0].enabled = true;
        one[0].x = 0.8f; one[0].y = 0;
        Mixer mr = new Mixer(one, sr);
        double el = 0, er = 0;
        for (int b = 0; b < 200; b++) {
            mr.render(out, 512);
            for (int i = 0; i < 512; i++) { el += out[2 * i] * out[2 * i]; er += out[2 * i + 1] * out[2 * i + 1]; }
        }
        check("pad placed right → right channel louder", er > el * 4);

        // 4) Toggling off fades to silence.
        one[0].enabled = false;
        for (int b = 0; b < 100; b++) mr.render(out, 512);
        double tail = 0;
        for (float v : out) tail = Math.max(tail, Math.abs(v));
        check("disabled source goes silent", tail < 1e-6);

        // 4b) Recorded loop path: stereo noise loop placed right → right louder; front → balanced; behind darker.
        SoundSource[] rec = { new Sources.Waves() };
        Noise nz = new Noise(5);
        short[] loopData = new short[48000 * 2];
        for (int i = 0; i < loopData.length; i++) loopData[i] = (short) (nz.white() * 0.3f * 32767);
        rec[0].loopPcm = loopData; rec[0].loopCh = 2; rec[0].enabled = true;
        double[] lr = loopEnergy(rec, sr, 0.8f, 0f);
        check("loop placed right → right louder", lr[1] > lr[0] * 3);
        double[] fr = loopEnergy(rec, sr, 0f, -0.6f);
        check("loop in front → balanced", Math.abs(fr[0] - fr[1]) < 0.15 * (fr[0] + fr[1]));
        double[] lnear = loopEnergy(rec, sr, 0f, -0.2f), lfar = loopEnergy(rec, sr, 0f, -1f);
        check("loop farther → quieter", lfar[0] + lfar[1] < (lnear[0] + lnear[1]) * 0.5);

        // 4c) Motion: orbit keeps distance, inner turns faster; 360° turn keeps the layout.
        SoundSource[] mv = { new Sources.Pad(), new Sources.Rain() };
        mv[0].x = 0.2f; mv[0].y = 0; mv[1].x = 0.9f; mv[1].y = 0;
        Mixer mo = new Mixer(mv, sr);
        mo.motion = Mixer.MOTION_ORBIT;
        for (int i = 0; i < 100; i++) mo.moveSources(0.1f); // 10 s
        double a0 = Math.atan2(mv[0].y, mv[0].x), a1 = Math.atan2(mv[1].y, mv[1].x);
        check("orbit keeps distance", Math.abs(Math.hypot(mv[0].x, mv[0].y) - 0.2) < 1e-3 && Math.abs(Math.hypot(mv[1].x, mv[1].y) - 0.9) < 1e-3);
        check("orbit: inner sound turned farther (" + String.format("%.2f vs %.2f rad", a0, a1) + ")", a0 > a1 && a1 > 0);
        mv[0].x = 0.5f; mv[0].y = 0; mv[1].x = 0; mv[1].y = 0.5f;
        mo.motion = Mixer.MOTION_ROTATE;
        for (int i = 0; i < 150; i++) mo.moveSources(0.1f); // 15 s = quarter turn
        double gap = Math.atan2(mv[1].y, mv[1].x) - Math.atan2(mv[0].y, mv[0].x);
        gap = Math.IEEEremainder(gap, 2 * Math.PI); // compare angles across the ±180° seam
        check("360° turn keeps the layout", Math.abs(Math.abs(gap) - Math.PI / 2) < 1e-3);
        check("360° turn: quarter turn in 15 s", Math.abs(mv[0].x) < 0.01 && Math.abs(mv[0].y - 0.5) < 0.01);

        // 5) Optional: write a 30 s stereo WAV preview (rain left-front, birds right-back, pad front).
        if (args.length > 0) writePreview(args[0], sr, args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[] {"rain", "birds", "pad"});

        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static void writePreview(String path, float sr, String[] ids) throws java.io.IOException {
        SoundSource[] src = Sources.all();
        for (SoundSource s : src) { s.enabled = false; for (String id : ids) if (id.equals(s.id)) s.enabled = true; }
        Mixer m = new Mixer(src, sr);
        int frames = (int) (sr * 30), block = 512;
        java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream(frames * 4);
        float[] buf = new float[block * 2];
        for (int done = 0; done < frames; done += block) {
            // Slowly orbit the birds around the listener so the movement is audible.
            double a = done / (double) frames * Math.PI * 2;
            src[4].x = (float) (0.6 * Math.sin(a)); src[4].y = (float) (-0.6 * Math.cos(a));
            m.render(buf, block);
            for (float v : buf) {
                int q = Math.round(Math.max(-1f, Math.min(1f, v)) * 32767);
                pcm.write(q & 0xff); pcm.write((q >> 8) & 0xff);
            }
        }
        byte[] data = pcm.toByteArray();
        java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes())
         .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2)
         .putInt((int) sr).putInt((int) sr * 4).putShort((short) 4).putShort((short) 16)
         .put("data".getBytes()).putInt(data.length);
        try (java.io.FileOutputStream f = new java.io.FileOutputStream(path)) { f.write(h.array()); f.write(data); }
        System.out.println("   preview → " + path);
    }

    private static double[] loopEnergy(SoundSource[] src, float sr, float x, float y) {
        src[0].x = x; src[0].y = y;
        Mixer m = new Mixer(src, sr);
        float[] out = new float[1024];
        double el = 0, er = 0;
        for (int b = 0; b < 200; b++) {
            m.render(out, 512);
            if (b < 100) continue; // let fades/smoothing settle
            for (int i = 0; i < 512; i++) { el += out[2 * i] * out[2 * i]; er += out[2 * i + 1] * out[2 * i + 1]; }
        }
        return new double[] {el, er};
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }
}
