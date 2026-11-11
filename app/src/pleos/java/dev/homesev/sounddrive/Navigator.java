package dev.homesev.sounddrive;

import android.content.Context;
import android.util.Log;

import java.util.function.Consumer;

import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import kotlin.Unit;

/** Pleos build: hands a destination to the car's own navigation (public NaviHelper SDK). */
final class Navigator {
    static boolean available() { return true; }

    private final Context ctx;
    private NaviHelper navi;
    private boolean connected, connecting;
    private Runnable pending;

    Navigator(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    /** Ask the built-in navigation to guide to (lat, lon). result gets null on success or a short reason. */
    void go(double lat, double lon, String name, Consumer<String> result) {
        Runnable req = () -> {
            try {
                navi.requestRoute(new RouteInfo(lat, lon, null, null, name, "", "", "0", RouteOption.RECOMMENDED, "", false));
                result.accept(null);
            } catch (Throwable t) {
                Log.w("SoundDrive", "requestRoute", t);
                result.accept("내비게이션에 목적지를 보내지 못했어요");
            }
        };
        if (connected) { req.run(); return; }
        pending = req;
        if (connecting) return;
        connecting = true;
        try {
            navi = new NaviHelper(ctx);
            navi.initialize(() -> {
                connected = true; connecting = false;
                Runnable r = pending; pending = null;
                if (r != null) r.run();
                return Unit.INSTANCE;
            }, t -> {
                connecting = false; pending = null;
                Log.w("SoundDrive", "NaviHelper connect", t);
                result.accept("내비게이션에 연결하지 못했어요");
                return Unit.INSTANCE;
            });
        } catch (Throwable t) {
            connecting = false; pending = null;
            Log.w("SoundDrive", "NaviHelper init", t);
            result.accept("내비게이션을 쓸 수 없어요");
        }
    }

    void release() {
        try { if (navi != null) navi.release(); } catch (Throwable ignored) {}
        navi = null; connected = false;
    }
}
