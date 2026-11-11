package dev.homesev.sounddrive;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.util.Log;

/** Galaxy build: phone position from Android's own GPS (network as a rough fallback). */
final class DeviceLocation {
    static final String[] PERMISSIONS = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"};
    private static final long MIN_MS = 10_000;
    private static final float MIN_M = 30;

    private final Context ctx;
    private final Handler main;
    private final LocationSink sink;
    private final LocationManager lm;
    private boolean running;
    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location l) { sink.onLocation(l.getLatitude(), l.getLongitude()); }
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
        @Override public void onStatusChanged(String p, int s, android.os.Bundle b) {}
    };

    DeviceLocation(Context ctx, Handler main, LocationSink sink) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.sink = sink;
        lm = this.ctx.getSystemService(LocationManager.class);
    }

    boolean hasPermission() {
        return ctx.checkSelfPermission(PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(PERMISSIONS[1]) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    void start() {
        if (running || lm == null || !hasPermission()) return;
        running = true;
        for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                lm.requestLocationUpdates(p, MIN_MS, MIN_M, listener, main.getLooper());
                Location last = lm.getLastKnownLocation(p);
                if (last != null && System.currentTimeMillis() - last.getTime() < 5 * 60_000) listener.onLocationChanged(last);
            } catch (RuntimeException e) { Log.w("SoundDrive", "location " + p, e); }
        }
    }

    void stop() {
        if (!running) return;
        running = false;
        try { lm.removeUpdates(listener); } catch (RuntimeException ignored) {}
    }
}
