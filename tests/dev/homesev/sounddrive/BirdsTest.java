package dev.homesev.sounddrive;

/** Host-side checks for the bird flock, plus WAV previews of every species. */
public final class BirdsTest {
    private static int pass, fail;

    public static void main(String[] args) throws Exception {
        float sr = 48000;
        String outDir = args.length > 0 ? args[0] : null;

        // Every species call: finite, audible, bounded, ends.
        for (Birds.Species sp : Birds.ALL) {
            Birds.Call c = Birds.make(sp.id, new Noise(7));
            float[] buf = new float[512];
            double peak = 0, sum = 0; long count = 0; boolean finite = true, alive = true;
            int blocks = 0;
            java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
            while (alive && blocks < sr * 30 / 512) {
                alive = c.render(buf, 512, sr);
                for (float v : buf) {
                    if (!Float.isFinite(v)) finite = false;
                    peak = Math.max(peak, Math.abs(v)); sum += v * v; count++;
                    int q = Math.round(Math.max(-1f, Math.min(1f, v * 1.5f)) * 32767);
                    pcm.write(q & 0xff); pcm.write((q >> 8) & 0xff);
                }
                blocks++;
            }
            double sec = blocks * 512 / sr;
            System.out.printf("   %-10s %-5s %.1fs peak=%.2f%n", sp.id, sp.name, sec, peak);
            check(sp.id + " finite", finite);
            check(sp.id + " audible", peak > 0.05);
            check(sp.id + " bounded", peak < 1.5);
            check(sp.id + " ends", !alive);
            if (outDir != null) Wav.write(outDir + "/bird-" + sp.id + ".wav", pcm.toByteArray(), (int) sr, 1);
        }

        // Flock: by day only day birds, at night only night birds; voices are scattered.
        Birds b = new Birds();
        b.enabled = true; b.fade = 1;
        java.util.Set<String> heardDay = run(b, sr, 120, false);
        check("day: no night birds", !heardDay.contains("owl") && !heardDay.contains("scops") && !heardDay.contains("nightjar"));
        check("day: several species (" + heardDay + ")", heardDay.size() >= 5);
        Birds n = new Birds();
        java.util.Set<String> heardNight = run(n, sr, 120, true);
        check("night: only night birds (" + heardNight + ")", !heardNight.isEmpty() && heardNight.stream().allMatch(id -> id.equals("owl") || id.equals("scops") || id.equals("nightjar")));
        Birds only = new Birds();
        java.util.Arrays.fill(only.speciesOn, false);
        only.speciesOn[0] = true; // dove only, at night → falls back to ignoring time of day
        check("selected species still heard when time doesn't match", run(only, sr, 60, true).contains("dove"));

        // Recorded clips replace the synth for that species.
        Birds rc = new Birds();
        java.util.Arrays.fill(rc.speciesOn, false);
        rc.speciesOn[1] = true; // sparrow
        float[] clip = new float[48000];
        for (int i = 0; i < clip.length; i++) clip[i] = (float) Math.sin(i * 0.3) * 0.5f;
        rc.setClips("sparrow", clip, new int[] {0, 24000, 24000, 47999});
        float[] cl = new float[512], cr = new float[512], cm = new float[512];
        Spatial.Target ct = new Spatial.Target();
        boolean usedClip = false;
        java.lang.reflect.Field vf2 = Birds.class.getDeclaredField("voices");
        vf2.setAccessible(true);
        for (int i = 0; i < 48000 * 20 / 512 && !usedClip; i++) {
            rc.renderInto(cl, cr, 512, sr, 1, 1, ct, cm);
            for (Object v : (Object[]) vf2.get(rc)) {
                java.lang.reflect.Field cf = v.getClass().getDeclaredField("call");
                cf.setAccessible(true);
                if (cf.get(v) instanceof Birds.Clip) usedClip = true;
            }
        }
        check("recorded clip is used when loaded", usedClip);

        // Flock previews: 60 s day forest, 60 s night, stereo, flock at front-right.
        if (outDir != null) {
            preview(outDir + "/birds-day.wav", false, sr);
            preview(outDir + "/birds-night.wav", true, sr);
        }

        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static java.util.Set<String> run(Birds b, float sr, int seconds, boolean night) throws Exception {
        b.night = night;
        java.util.Set<String> heard = new java.util.TreeSet<>();
        float[] l = new float[512], r = new float[512], mono = new float[512];
        Spatial.Target tg = new Spatial.Target();
        java.lang.reflect.Field vf = Birds.class.getDeclaredField("voices");
        vf.setAccessible(true);
        for (int i = 0; i < seconds * sr / 512; i++) {
            b.renderInto(l, r, 512, sr, 1, 1, tg, mono);
            for (Object v : (Object[]) vf.get(b)) {
                java.lang.reflect.Field cf = v.getClass().getDeclaredField("call");
                cf.setAccessible(true);
                Birds.Playable c = (Birds.Playable) cf.get(v);
                if (c != null) heard.add(c.species());
            }
        }
        return heard;
    }

    private static void preview(String path, boolean night, float sr) throws Exception {
        SoundSource[] src = Sources.all();
        for (SoundSource s : src) s.enabled = s.id.equals("birds") || (night && s.id.equals("pad"));
        for (SoundSource s : src) if (s instanceof Birds) { ((Birds) s).night = night; s.x = 0.3f; s.y = -0.2f; }
        for (SoundSource s : src) if (s.id.equals("pad")) s.volume = 0.35f;
        Mixer m = new Mixer(src, sr);
        float[] buf = new float[1024];
        java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < 60 * sr / 512; i++) {
            m.render(buf, 512);
            for (float v : buf) { int q = Math.round(Math.max(-1f, Math.min(1f, v)) * 32767); pcm.write(q & 0xff); pcm.write((q >> 8) & 0xff); }
        }
        Wav.write(path, pcm.toByteArray(), (int) sr, 2);
        System.out.println("   preview → " + path);
    }

    static final class Wav {
        static void write(String path, byte[] data, int sr, int ch) throws java.io.IOException {
            java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes())
             .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) ch)
             .putInt(sr).putInt(sr * 2 * ch).putShort((short) (2 * ch)).putShort((short) 16)
             .put("data".getBytes()).putInt(data.length);
            try (java.io.FileOutputStream f = new java.io.FileOutputStream(path)) { f.write(h.array()); f.write(data); }
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }
}
