package dev.homesev.sounddrive;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import ai.pleos.playground.location.fused.FusedLocation;
import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;

/** Pleos build: vehicle position from the Pleos FusedLocation SDK (public API). */
final class DeviceLocation {
    static final String[] PERMISSIONS = {"pleos.car.permission.FUSED_LOCATION", "android.permission.ACCESS_FINE_LOCATION"};

    private final Context ctx;
    private final Handler main;
    private final LocationSink sink;
    private FusedLocation fused;
    private FusedLocationListener listener;

    DeviceLocation(Context ctx, Handler main, LocationSink sink) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.sink = sink;
    }

    boolean hasPermission() {
        return ctx.checkSelfPermission(PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (fused != null || !hasPermission()) return;
        try {
            fused = new FusedLocation(ctx);
            if (!fused.isSupported()) { Log.w("SoundDrive", "FusedLocation not supported"); fused.release(); fused = null; return; }
            fused.initialize();
            listener = new FusedLocationListener() {
                @Override public void onChanged(LocationData d) {
                    if (d != null) main.post(() -> sink.onLocation(d.getLatitude(), d.getLongitude()));
                }
                @Override public void onError(Exception e) { Log.w("SoundDrive", "FusedLocation", e); }
            };
            fused.registerFusedLocationCallback(listener);
        } catch (RuntimeException e) {
            Log.w("SoundDrive", "FusedLocation start", e);
            fused = null;
        }
    }

    void stop() {
        try { if (fused != null && listener != null) fused.unregisterFusedLocationCallback(listener); } catch (RuntimeException ignored) {}
        try { if (fused != null) fused.release(); } catch (RuntimeException ignored) {}
        fused = null;
        listener = null;
    }
}
