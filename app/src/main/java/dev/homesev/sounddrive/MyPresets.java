package dev.homesev.sounddrive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "나만의 소리" presets the listener saved: which sounds are on, where, and how loud.
 * Pure Java (no Android) so it can be tested on the host; stored as one text value.
 * Format: one preset per line, "name\tid:x,y,vol;id:x,y,vol".
 */
final class MyPresets {
    static final int MAX = 6;
    static final float TOLERANCE = 0.03f;

    static final class Preset {
        String name;
        final Map<String, float[]> sounds = new LinkedHashMap<>(); // id -> {x, y, volume}
        Preset(String name) { this.name = name; }
    }

    final List<Preset> list = new ArrayList<>();

    /** The current on-sounds as a preset (locked sounds are never part of one). */
    static Preset capture(String name, SoundSource[] sources) {
        Preset p = new Preset(name);
        for (SoundSource s : sources) if (!s.locked && s.enabled) p.sounds.put(s.id, new float[] {s.x, s.y, s.volume});
        return p;
    }

    /** Same on-set, and every sound within TOLERANCE in x, y and volume. */
    static boolean matches(Preset p, SoundSource[] sources) {
        int on = 0;
        for (SoundSource s : sources) {
            if (s.locked || !s.enabled) continue;
            on++;
            float[] v = p.sounds.get(s.id);
            if (v == null) return false;
            if (Math.abs(v[0] - s.x) > TOLERANCE || Math.abs(v[1] - s.y) > TOLERANCE || Math.abs(v[2] - s.volume) > TOLERANCE) return false;
        }
        return on == p.sounds.size();
    }

    /** Turn exactly the preset's sounds on at their saved place and volume. Returns the sounds that newly turned on. */
    static List<SoundSource> apply(Preset p, SoundSource[] sources) {
        List<SoundSource> popped = new ArrayList<>();
        for (SoundSource s : sources) {
            if (s.locked) continue;
            float[] v = p.sounds.get(s.id);
            if (v != null) {
                s.x = v[0]; s.y = v[1]; s.volume = v[2];
                if (!s.enabled) popped.add(s);
                s.enabled = true;
            } else {
                s.enabled = false;
            }
        }
        return popped;
    }

    Preset find(SoundSource[] sources) {
        for (Preset p : list) if (matches(p, sources)) return p;
        return null;
    }

    /** "내 소리 N" with the smallest unused N. */
    String nextName() {
        for (int n = 1; ; n++) {
            String name = "내 소리 " + n;
            boolean used = false;
            for (Preset p : list) if (p.name.equals(name)) { used = true; break; }
            if (!used) return name;
        }
    }

    String encode() {
        StringBuilder b = new StringBuilder();
        for (Preset p : list) {
            if (b.length() > 0) b.append('\n');
            b.append(p.name.replace('\t', ' ').replace('\n', ' ')).append('\t');
            boolean first = true;
            for (Map.Entry<String, float[]> e : p.sounds.entrySet()) {
                if (!first) b.append(';');
                first = false;
                float[] v = e.getValue();
                b.append(e.getKey()).append(':').append(String.format(Locale.ROOT, "%.4f,%.4f,%.4f", v[0], v[1], v[2]));
            }
        }
        return b.toString();
    }

    static MyPresets decode(String text) {
        MyPresets m = new MyPresets();
        if (text == null || text.isEmpty()) return m;
        for (String line : text.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            Preset p = new Preset(line.substring(0, tab));
            for (String part : line.substring(tab + 1).split(";")) {
                int colon = part.indexOf(':');
                if (colon <= 0) continue;
                String[] v = part.substring(colon + 1).split(",");
                if (v.length != 3) continue;
                try {
                    p.sounds.put(part.substring(0, colon), new float[] {Float.parseFloat(v[0]), Float.parseFloat(v[1]), Float.parseFloat(v[2])});
                } catch (NumberFormatException ignored) { }
            }
            if (!p.sounds.isEmpty() && m.list.size() < MAX) m.list.add(p);
        }
        return m;
    }
}
