package dev.homesev.sounddrive;

import android.content.Context;

import java.util.function.Consumer;

/** Galaxy build: no car navigation SDK, so the "guide me there" button is hidden. */
final class Navigator {
    static boolean available() { return false; }
    Navigator(Context ctx) {}
    void go(double lat, double lon, String name, Consumer<String> result) { result.accept("이 기기에서는 안내를 쓸 수 없어요"); }
    void release() {}
}
