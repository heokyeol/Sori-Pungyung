package dev.homesev.sounddrive;

/** Host-side checks for saved "나만의 소리" presets. */
public final class MyPresetsTest {
    private static int pass, fail;

    public static void main(String[] args) {
        SoundSource[] src = Sources.all();
        for (SoundSource s : src) s.enabled = false;
        SoundSource a = src[0], b = src[1], c = src[2];
        a.enabled = true; a.x = 0.3f; a.y = -0.4f; a.volume = 0.5f;
        b.enabled = true; b.x = -0.6f; b.y = 0.2f; b.volume = 0.9f;

        MyPresets m = new MyPresets();
        check("first name", m.nextName().equals("내 소리 1"));
        MyPresets.Preset p = MyPresets.capture(m.nextName(), src);
        m.list.add(p);
        check("captures only on sounds", p.sounds.size() == 2 && p.sounds.containsKey(a.id) && !p.sounds.containsKey(c.id));
        check("matches right after save", m.find(src) == p);
        check("next name skips used", m.nextName().equals("내 소리 2"));

        a.x += 0.02f;
        check("tiny move still matches", MyPresets.matches(p, src));
        a.x += 0.1f;
        check("real move no longer matches", !MyPresets.matches(p, src));
        c.enabled = true;
        check("extra sound no longer matches", !MyPresets.matches(p, src));

        java.util.List<SoundSource> popped = MyPresets.apply(p, src);
        check("apply restores on-set", a.enabled && b.enabled && !c.enabled);
        check("apply restores place and volume", Math.abs(a.x - 0.3f) < 1e-4 && Math.abs(b.volume - 0.9f) < 1e-4);
        check("apply reports newly on", popped.isEmpty());
        check("matches after apply", MyPresets.matches(p, src));

        m.list.add(new MyPresets.Preset("빈 것")); // empty presets are dropped on load
        MyPresets back = MyPresets.decode(m.encode());
        check("encode/decode round trip", back.list.size() == 1 && back.list.get(0).name.equals("내 소리 1")
                && Math.abs(back.list.get(0).sounds.get(b.id)[0] + 0.6f) < 1e-3);
        check("decode tolerates junk", MyPresets.decode("x\n\tfoo\nname\tid:1,2\n").list.isEmpty());
        check("decode null", MyPresets.decode(null).list.isEmpty());

        MyPresets full = new MyPresets();
        for (int i = 0; i < 9; i++) { MyPresets.Preset q = MyPresets.capture(full.nextName(), src); full.list.add(q); }
        check("load keeps at most MAX", MyPresets.decode(full.encode()).list.size() == MyPresets.MAX);

        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL " + name); }
    }
}
