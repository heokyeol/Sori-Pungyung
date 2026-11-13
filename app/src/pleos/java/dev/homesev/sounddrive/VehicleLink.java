package dev.homesev.sounddrive;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.area.WindowArea;
import ai.pleos.playground.vehicle.listener.WindowPositionListener;
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.control.LightSwitch;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;
import kotlin.Unit;

/**
 * Vehicle state via public APIs: speed from Android Automotive CarPropertyManager,
 * gear, window positions and the ambient (mood) light from the Pleos Vehicle SDK.
 * The only write is the ambient light; its original state is restored afterwards.
 * Windows are read only.
 */
final class VehicleLink {
    interface Listener {
        void onVehicle(Float speedKmh, String gear);
        /** Outside temperature in °C (null = unknown). */
        default void onOutsideTemp(Float celsius) {}
        /** Called once the ambient light zones are known (0 = no controllable light). */
        default void onMoodZones(int count) {}
        /** true = some window open, false = all closed, null = unknown. */
        default void onWindowOpen(Boolean anyOpen) {}
    }

    private static final String TAG = "SoundDrive";
    private final Context ctx;
    private final Handler main;
    private final Listener listener;
    private Car car;
    private CarPropertyManager props;
    private CarPropertyManager.CarPropertyEventCallback speedCb, outsideCb;
    private Vehicle vehicle;
    private GearSelectionListener gearCb;
    private Float speedKmh;
    private String gear;
    private boolean closed;
    // Ambient light
    private final List<SeatArea> lightAreas = new ArrayList<>();
    private final Map<SeatArea, AreaBounds<Integer>> lightBounds = new LinkedHashMap<>();
    private final Map<SeatArea, Integer> origColor = new LinkedHashMap<>(), origBright = new LinkedHashMap<>();
    private final Map<SeatArea, LightSwitch> origSwitch = new LinkedHashMap<>();
    private boolean moodOn, lightsForcedOff;
    private final List<Runnable> unsubscribe = new ArrayList<>();
    private float moodLevel = -1;

    VehicleLink(Context ctx, Handler main, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.listener = listener;
        startGear();
    }

    /** Call after android.car.permission.CAR_SPEED is granted. */
    void startSpeed() {
        if (props != null || closed) return;
        try {
            car = Car.createCar(ctx);
            if (car == null) return;
            props = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            speedCb = new CarPropertyManager.CarPropertyEventCallback() {
                @Override public void onChangeEvent(CarPropertyValue v) {
                    Float kmh = null;
                    if (v != null && v.getStatus() == CarPropertyValue.STATUS_AVAILABLE && v.getValue() instanceof Float)
                        kmh = Math.abs((Float) v.getValue()) * 3.6f;
                    post(kmh, gear, true, false);
                }
                @Override public void onErrorEvent(int prop, int area) { post(null, gear, true, false); }
            };
            props.registerCallback(speedCb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL);
            try { // outside temperature for the header (public AAOS property)
                outsideCb = new CarPropertyManager.CarPropertyEventCallback() {
                    @Override public void onChangeEvent(CarPropertyValue v) {
                        Float t = v != null && v.getStatus() == CarPropertyValue.STATUS_AVAILABLE && v.getValue() instanceof Float ? (Float) v.getValue() : null;
                        main.post(() -> { if (!closed) listener.onOutsideTemp(t); });
                    }
                    @Override public void onErrorEvent(int prop, int area) {}
                };
                props.registerCallback(outsideCb, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, CarPropertyManager.SENSOR_RATE_ONCHANGE);
            } catch (RuntimeException e) { Log.w(TAG, "outside temperature unavailable", e); }
        } catch (RuntimeException e) {
            Log.w(TAG, "speed unavailable", e);
            props = null;
        }
    }

    private void startGear() {
        try {
            vehicle = new Vehicle(ctx);
            vehicle.initialize();
            gearCb = new GearSelectionListener() {
                @Override public void onSelected(VehicleGear g) { post(speedKmh, gearName(g), false, true); }
                @Override public void onFailed(Exception e) { post(speedKmh, null, false, true); }
            };
            // The SDK binds asynchronously; give it a moment before the first read.
            main.postDelayed(() -> {
                if (closed) return;
                try {
                    vehicle.getGear().getCurrentGearSelectionState(
                            g -> { post(speedKmh, gearName(g), false, true); return Unit.INSTANCE; },
                            e -> { post(speedKmh, null, false, true); return Unit.INSTANCE; });
                    vehicle.getGear().registerGearSelection(gearCb);
                } catch (RuntimeException e) {
                    Log.w(TAG, "gear unavailable", e);
                }
                loadLightZones();
                startWindows();
            }, 1500);
        } catch (RuntimeException e) {
            Log.w(TAG, "Pleos Vehicle init failed", e);
            vehicle = null;
        }
    }

    // ── Windows (read only) ────────────────────────────────────────────────
    private void startWindows() {
        try {
            vehicle.getWindow().checkWindowPositionCapabilities((config, areas) -> {
                main.post(() -> {
                    if (closed || areas == null || areas.isEmpty()) return;
                    Map<WindowArea, Boolean> open = new LinkedHashMap<>();
                    for (WindowArea a : areas.keySet()) open.put(a, null);
                    for (WindowArea a : areas.keySet()) {
                        WindowPositionListener l = new WindowPositionListener() {
                            @Override public void onWindowPositionUpdated(Integer v) { window(open, a, v); }
                            @Override public void onFailed(Exception e) { window(open, a, null); }
                        };
                        try {
                            vehicle.getWindow().getCurrentWindowPosition(a, v -> { window(open, a, v); return Unit.INSTANCE; }, e -> Unit.INSTANCE);
                            vehicle.getWindow().registerWindowPosition(a, l);
                            unsubscribe.add(() -> vehicle.getWindow().unregisterWindowPosition(a, l));
                        } catch (RuntimeException e) { Log.w(TAG, "window " + a, e); }
                    }
                });
                return Unit.INSTANCE;
            }, e -> { Log.w(TAG, "window capabilities", e); return Unit.INSTANCE; });
        } catch (RuntimeException e) { Log.w(TAG, "windows unavailable", e); }
    }

    private void window(Map<WindowArea, Boolean> open, WindowArea a, Integer position) {
        main.post(() -> {
            if (closed) return;
            open.put(a, position == null || position < 0 ? null : position > 0);
            Boolean any = open.containsValue(Boolean.TRUE) ? Boolean.TRUE : open.containsValue(null) ? null : Boolean.FALSE;
            listener.onWindowOpen(any);
        });
    }

    // ── Ambient (mood) light ───────────────────────────────────────────────
    private void loadLightZones() {
        try {
            vehicle.getLight().checkAmbientLightBrightnessCapabilities((config, areas) -> {
                main.post(() -> {
                    if (closed) return;
                    lightAreas.clear();
                    lightBounds.clear();
                    if (areas != null) { lightBounds.putAll(areas); lightAreas.addAll(areas.keySet()); }
                    for (SeatArea a : lightAreas) {
                        vehicle.getLight().getAmbientLightBrightnessColor(a, v -> { main.post(() -> origColor.putIfAbsent(a, v)); return Unit.INSTANCE; }, e -> Unit.INSTANCE);
                        vehicle.getLight().getAmbientLightBrightness(a, v -> { main.post(() -> origBright.putIfAbsent(a, v)); return Unit.INSTANCE; }, e -> Unit.INSTANCE);
                        vehicle.getLight().getAmbientLightSwitch(a, v -> { main.post(() -> origSwitch.putIfAbsent(a, v)); return Unit.INSTANCE; }, e -> Unit.INSTANCE);
                    }
                    Log.i(TAG, "ambient light zones: " + describeZones());
                    listener.onMoodZones(lightAreas.size());
                });
                return Unit.INSTANCE;
            }, e -> { Log.w(TAG, "ambient light capabilities", e); main.post(() -> listener.onMoodZones(0)); return Unit.INSTANCE; });
        } catch (RuntimeException e) {
            Log.w(TAG, "ambient light unavailable", e);
            listener.onMoodZones(0);
        }
    }

    private String describeZones() {
        StringBuilder b = new StringBuilder();
        for (SeatArea a : lightAreas) b.append(java.util.Arrays.toString(seatNames(a))).append(' ').append(lightBounds.get(a)).append("; ");
        return b.toString();
    }

    private static String[] seatNames(SeatArea a) {
        SeatValue[] v = a.getValues();
        String[] out = new String[v == null ? 0 : v.length];
        for (int i = 0; i < out.length; i++) out[i] = v[i].name();
        return out;
    }

    /** Stage points of the light zones, in the same order setZoneColors expects. */
    float[][] zonePoints() {
        float[][] out = new float[lightAreas.size()][];
        for (int i = 0; i < out.length; i++) out[i] = MoodGradient.zonePoint(seatNames(lightAreas.get(i)));
        return out;
    }

    /** One 0xRRGGBB per zone; level 0..1 of each zone's brightness range. */
    void setZoneColors(int[] rgb, float level) {
        if (closed || vehicle == null || rgb == null || rgb.length != lightAreas.size()) return;
        boolean first = !moodOn || lightsForcedOff;
        moodOn = true;
        lightsForcedOff = false;
        boolean newLevel = first || Math.abs(level - moodLevel) > 0.01f;
        moodLevel = level;
        for (int i = 0; i < rgb.length; i++) {
            SeatArea a = lightAreas.get(i);
            try {
                if (first) vehicle.getLight().setAmbientLightSwitch(a, LightSwitch.ON, (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                vehicle.getLight().setAmbientLightBrightnessColor(a, rgb[i] & 0xFFFFFF, (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                if (newLevel) {
                    AreaBounds<Integer> bd = lightBounds.get(a);
                    int lo = bd == null ? 0 : bd.getMinValue(), hi = bd == null ? 100 : bd.getMaxValue();
                    vehicle.getLight().setAmbientLightBrightness(a, Math.round(lo + (hi - lo) * level), (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                }
            } catch (RuntimeException e) { Log.w(TAG, "mood zone " + i, e); }
        }
    }

    boolean moodActive() { return moodOn && !lightsForcedOff; }

    /**
     * Turn the mood light off without restoring the original (used when the sleep timer
     * ends or for the fully dark screen, so the cabin doesn't light up while sleeping).
     * The original state is still restored later by restoreMood / close.
     */
    void moodOff() {
        if (!moodOn || lightsForcedOff || vehicle == null) return;
        lightsForcedOff = true;
        moodLevel = -1;
        for (SeatArea a : lightAreas) {
            try { vehicle.getLight().setAmbientLightSwitch(a, LightSwitch.OFF, (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE); }
            catch (RuntimeException e) { Log.w(TAG, "mood off", e); }
        }
    }

    /** Put the ambient light back the way it was before setZoneColors. */
    void restoreMood() {
        if (!moodOn || vehicle == null) return;
        moodOn = false;
        lightsForcedOff = false;
        moodLevel = -1;
        for (SeatArea a : lightAreas) {
            try {
                Integer c = origColor.get(a), b = origBright.get(a);
                LightSwitch sw = origSwitch.get(a);
                if (c != null) vehicle.getLight().setAmbientLightBrightnessColor(a, c, (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                if (b != null) vehicle.getLight().setAmbientLightBrightness(a, b, (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                if (sw != null) vehicle.getLight().setAmbientLightSwitch(a, sw, (x, y) -> Unit.INSTANCE, e -> Unit.INSTANCE);
            } catch (RuntimeException e) { Log.w(TAG, "restore mood", e); }
        }
    }

    private static String gearName(VehicleGear g) {
        if (g == null || g == VehicleGear.GEAR_UNKNOWN) return null;
        if (g == VehicleGear.GEAR_PARK) return "P";
        if (g == VehicleGear.GEAR_REVERSE) return "R";
        if (g == VehicleGear.GEAR_NEUTRAL) return "N";
        return "D";
    }

    private void post(Float kmh, String g, boolean setSpeed, boolean setGear) {
        main.post(() -> {
            if (closed) return;
            if (setSpeed) speedKmh = kmh;
            if (setGear) gear = g;
            listener.onVehicle(speedKmh, gear);
        });
    }

    void close() {
        restoreMood();
        closed = true;
        for (Runnable r : unsubscribe) { try { r.run(); } catch (RuntimeException ignored) {} }
        try { if (props != null && speedCb != null) props.unregisterCallback(speedCb); } catch (RuntimeException ignored) {}
        try { if (props != null && outsideCb != null) props.unregisterCallback(outsideCb); } catch (RuntimeException ignored) {}
        try { if (car != null) car.disconnect(); } catch (RuntimeException ignored) {}
        try {
            if (vehicle != null) {
                if (gearCb != null) vehicle.getGear().unregisterGearSelection(gearCb);
                vehicle.release();
            }
        } catch (RuntimeException ignored) {}
    }
}
