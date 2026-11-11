package dev.homesev.sounddrive;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where to find unlockable sounds: an offline radar (default) and a real map.
 * The map uses MapLibre with OpenFreeMap tiles (free, no API key; needs internet).
 * Place pins come from the app's own offline list, not from a map API.
 */
public final class PlacesActivity extends Activity implements Player.Listener, RadarView.Listener {
    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final double[] FALLBACK = {37.5665, 126.9780}; // Seoul City Hall until a fix arrives

    private Player player;
    private float dp;
    private FrameLayout content;
    private RadarView radar;
    private MapView mapView;
    private MapLibreMap map;
    private Style style;
    private TextView info;
    private Button radarBtn, mapBtn, filterBtn;
    private boolean showAll, mapMode;
    private boolean cameraPlaced;
    private Button guideBtn;
    private Navigator navigator;
    private double selLat, selLon;
    private String selName;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        MapLibre.getInstance(this);
        dp = getResources().getDisplayMetrics().density;
        player = Player.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0F17);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = px(10);
        bar.setPadding(pad, pad, pad, pad);
        Button back = button("← 닫기");
        back.setOnClickListener(v -> finish());
        radarBtn = button("레이더");
        radarBtn.setOnClickListener(v -> setMapMode(false));
        mapBtn = button("지도");
        mapBtn.setOnClickListener(v -> setMapMode(true));
        filterBtn = button("");
        filterBtn.setOnClickListener(v -> { showAll = !showAll; refreshData(); });
        for (Button x : new Button[] {back, radarBtn, mapBtn, filterBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(52), 1f);
            lp.setMarginEnd(px(6));
            bar.addView(x, lp);
        }
        root.addView(bar);

        content = new FrameLayout(this);
        radar = new RadarView(this, this);
        content.addView(radar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapView = new MapView(this);
        mapView.onCreate(b);
        mapView.setVisibility(View.GONE);
        content.addView(mapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        info = new TextView(this);
        info.setTextColor(0xFFCFD6E2);
        info.setTextSize(16);
        info.setPadding(px(16), px(12), px(16), px(16));
        root.addView(info);

        // "Guide me there" — car build only (built-in navigation via the Pleos SDK)
        guideBtn = button("🧭 여기로 안내");
        guideBtn.setTextSize(17);
        style(guideBtn, true);
        guideBtn.setVisibility(View.GONE);
        guideBtn.setOnClickListener(v -> guide());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56));
        glp.setMargins(px(16), 0, px(16), px(16));
        root.addView(guideBtn, glp);
        if (Navigator.available()) navigator = new Navigator(this);

        setContentView(root);
        mapView.getMapAsync(m -> {
            map = m;
            m.getUiSettings().setRotateGesturesEnabled(false);
            m.setStyle(new Style.Builder().fromUri(MAP_STYLE), s -> {
                style = s;
                s.addSource(new GeoJsonSource("places"));
                s.addSource(new GeoJsonSource("me"));
                CircleLayer pins = new CircleLayer("places-dots", "places").withProperties(
                        PropertyFactory.circleColor(Expression.get("c")),
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(1.5f));
                s.addLayer(pins);
                SymbolLayer labels = new SymbolLayer("places-labels", "places").withProperties(
                        PropertyFactory.textField(Expression.get("n")),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textOffset(new Float[] {0f, 1.3f}),
                        PropertyFactory.textColor(Color.BLACK),
                        PropertyFactory.textHaloColor(Color.WHITE),
                        PropertyFactory.textHaloWidth(1.5f),
                        PropertyFactory.textOptional(true));
                labels.setMinZoom(6f); // regions are few, label them early
                s.addLayer(labels);
                s.addLayer(new CircleLayer("me-dot", "me").withProperties(
                        PropertyFactory.circleColor(0xFF3D7BFF),
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2.5f)));
                refreshData();
            });
            m.addOnMapClickListener(pt -> {
                if (style == null) return false;
                android.graphics.PointF sp = m.getProjection().toScreenLocation(pt);
                List<Feature> f = m.queryRenderedFeatures(new android.graphics.RectF(sp.x - 20, sp.y - 20, sp.x + 20, sp.y + 20), "places-dots");
                if (f.isEmpty()) return false;
                Feature ft = f.get(0);
                double[] me = here();
                Point p = (Point) ft.geometry();
                String catId = ft.getStringProperty("cat");
                if (catId.startsWith("spot:")) {
                    Seasons.Spot spot = null;
                    for (Seasons.Spot x : Seasons.ALL) if (x.id.equals(catId.substring(5))) spot = x;
                    if (spot == null) return false;
                    select(p.latitude(), p.longitude(), spot.place);
                    String sound = spot.sound;
                    for (SoundSource s2 : player.sources) if (s2.id.equals(spot.sound)) sound = s2.name;
                    info.setTag("tapped");
                    info.setText(String.format(Locale.KOREA, "%s · %s쪽 %.0fkm\n%s: %s", spot.place,
                            Unlocks.direction(bearing(me[0], me[1], p.latitude(), p.longitude())),
                            distance(me[0], me[1], p.latitude(), p.longitude()) / 1000,
                            player.spotInfo(spot.id) != null ? "이미 열린 소리" : spot.seasonName() + "에 가면 열리는 소리", sound));
                    return true;
                }
                Regions.Region rg = Regions.byId(catId.startsWith("region:") ? catId.substring(7) : catId);
                if (rg == null) return false;
                Unlocks.Category c = asCategory(rg);
                double d = distance(me[0], me[1], p.latitude(), p.longitude());
                select(p.latitude(), p.longitude(), ft.getStringProperty("n"));
                showInfo(c, ft.getStringProperty("n"), d, bearing(me[0], me[1], p.latitude(), p.longitude()));
                return true;
            });
        });
        setMapMode(false);
        refreshData();
    }

    // ── Data ───────────────────────────────────────────────────────────────
    /** Regions to show: unvisited ones, or all with "전부 보기". Hidden landmarks are never shown. */
    private Set<String> hiddenRegions() { return showAll ? new HashSet<>() : player.regionsVisited(); }

    /** A region drawn like a place category (its emoji, the colour of its sound). */
    private Unlocks.Category asCategory(Regions.Region r) {
        SoundSource s = null;
        for (SoundSource x : player.sources) if (x.id.equals(r.sound)) s = x;
        return new Unlocks.Category(r.id, r.sound, r.name, r.radiusM, r.hint(), r.emoji, s != null ? s.color : 0xFFFFFFFF);
    }

    private List<Unlocks.Place> regionPlaces(double[] me, int max) {
        List<Unlocks.Place> out = new ArrayList<>();
        for (Regions.Region r : Regions.nearest(me[0], me[1], hiddenRegions(), max)) {
            double d = Regions.distance(r, me[0], me[1]);
            if (d > RadarView.RANGE_M) continue;
            double[] pt = Regions.nearestPoint(r, me[0], me[1]);
            Unlocks.Place pl = new Unlocks.Place(asCategory(r), pt[0], pt[1], r.name, d, Regions.bearing(r, me[0], me[1]));
            pl.radiusM = r.radiusM;
            out.add(pl);
        }
        return out;
    }

    private double[] here() {
        double[] l = player.lastLocation();
        return l != null ? l : FALLBACK;
    }

    private void refreshData() {
        filterBtn.setText(showAll ? "전부 보기" : "안 열린 것만");
        double[] me = here();
        boolean fix = player.lastLocation() != null;
        List<Unlocks.Place> near = regionPlaces(me, 12);
        radar.setPlaces(near, fix);
        if (info.getTag() == null) {
            String head = !showAll && player.regionsVisited().size() == Regions.ALL.length ? "모든 지역 소리를 열었어요!"
                    : near.isEmpty() ? "300km 안에는 열 수 있는 지역이 없어요. 지도에서 찾아보세요."
                    : "점을 누르면 어떤 소리가 열리는지 보여 줘요.";
            info.setText((fix ? head : "위치를 아직 못 받았어요. 서울시청 기준으로 보여 줘요.") + seasonLine(me));
        }
        if (style != null) {
            List<Feature> feats = new ArrayList<>();
            Set<String> hide = hiddenRegions();
            for (Regions.Region r : Regions.ALL) {
                if (hide.contains(r.id)) continue;
                int color = asCategory(r).color;
                for (double[] pt : r.points) {
                    Feature f = Feature.fromGeometry(Point.fromLngLat(pt[1], pt[0]));
                    f.addStringProperty("cat", "region:" + r.id);
                    f.addStringProperty("n", r.name);
                    f.addStringProperty("c", String.format("#%06X", color & 0xFFFFFF));
                    feats.add(f);
                }
            }
            int month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1, now = Seasons.season(month);
            for (Seasons.Spot sp : Seasons.ALL) {
                boolean open = player.spotInfo(sp.id) != null;
                if (!showAll && (open || sp.season != now)) continue;
                for (double[] pt : sp.points) {
                    Feature f = Feature.fromGeometry(Point.fromLngLat(pt[1], pt[0]));
                    f.addStringProperty("cat", "spot:" + sp.id);
                    f.addStringProperty("n", sp.place + " (" + sp.seasonName() + ")");
                    f.addStringProperty("c", "#FFD27A");
                    feats.add(f);
                }
            }
            GeoJsonSource src = style.getSourceAs("places");
            if (src != null) src.setGeoJson(FeatureCollection.fromFeatures(feats));
            GeoJsonSource meSrc = style.getSourceAs("me");
            if (meSrc != null) {
                List<Feature> mine = new ArrayList<>();
                if (fix) mine.add(Feature.fromGeometry(Point.fromLngLat(me[1], me[0])));
                meSrc.setGeoJson(FeatureCollection.fromFeatures(mine));
            }
            if (!cameraPlaced || (fix && !cameraWasFix)) {
                map.setCameraPosition(new CameraPosition.Builder().target(new LatLng(me[0], me[1])).zoom(7).build());
                cameraPlaced = true;
                cameraWasFix = fix;
            }
        }
    }
    private boolean cameraWasFix;

    /** "\n이번 가을 명소: 정선 민둥산 · 동쪽 162km (억새 바람)" — and select it for guidance. */
    private String seasonLine(double[] me) {
        int month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;
        Seasons.Spot sp = Seasons.nearestInSeason(me[0], me[1], month, player.spotsUnlocked());
        if (sp == null) return "\n이번 " + Seasons.SEASON_NAMES[Seasons.season(month)] + " 명소는 모두 열었어요";
        double d = Seasons.distance(sp, me[0], me[1]);
        String sound = sp.sound;
        for (SoundSource s : player.sources) if (s.id.equals(sp.sound)) sound = s.name;
        if (selName == null) select(sp.points[0][0], sp.points[0][1], sp.place);
        return String.format(Locale.KOREA, "\n이번 %s 명소: %s · %s쪽 %.0fkm (%s)", sp.seasonName(), sp.place,
                Unlocks.direction(Seasons.bearing(sp, me[0], me[1])), d / 1000, sound);
    }

    @Override public void onPlaceTapped(Unlocks.Place p) {
        select(p.lat, p.lon, p.name);
        showInfo(p.category, p.name, p.distanceM, p.bearingDeg, p.radiusM);
    }

    private void select(double lat, double lon, String name) {
        selLat = lat; selLon = lon; selName = name;
        if (navigator != null) guideBtn.setVisibility(View.VISIBLE);
    }

    private void guide() {
        if (navigator == null || selName == null) return;
        String name = selName;
        navigator.go(selLat, selLon, name, err -> runOnUiThread(() -> {
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            else Toast.makeText(this, "🧭 " + name + "(으)로 안내를 시작해요", Toast.LENGTH_SHORT).show();
        }));
    }

    private void showInfo(Unlocks.Category c, String name, double d, double bearing) { showInfo(c, name, d, bearing, c.radiusM); }

    private void showInfo(Unlocks.Category c, String name, double d, double bearing, double radiusM) {
        String sound = c.sound;
        for (SoundSource s : player.sources) if (s.id.equals(c.sound)) sound = s.name;
        boolean open = player.regionInfo(c.id) != null;
        String dist = d >= 1000 ? String.format(Locale.KOREA, "%.1fkm", d / 1000) : String.format(Locale.KOREA, "%.0fm", d);
        info.setTag("tapped");
        info.setText(c.emoji + " " + name + " · " + Unlocks.direction(bearing) + "쪽 " + dist
                + "\n" + (open ? "이미 열린 소리: " + sound : "가면 열리는 소리: " + sound
                + (radiusM >= 1000 ? String.format(Locale.KOREA, " (%.0fkm 안으로)", radiusM / 1000) : String.format(Locale.KOREA, " (%.0fm 안으로)", radiusM))));
    }

    private void setMapMode(boolean on) {
        mapMode = on;
        radar.setVisibility(on ? View.GONE : View.VISIBLE);
        mapView.setVisibility(on ? View.VISIBLE : View.GONE);
        style(radarBtn, !on);
        style(mapBtn, on);
        if (on) Toast.makeText(this, "지도는 인터넷이 필요해요", Toast.LENGTH_SHORT).show();
    }

    // ── Player events ──────────────────────────────────────────────────────
    @Override public void onPlayerChanged() {
        if (player.driving()) {
            Toast.makeText(this, "주행 중에는 지도를 닫아요", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override public void onLocation(double lat, double lon) { refreshData(); }

    @Override public void onUnlocked(String soundName, String placeName) {
        info.setTag(null);
        refreshData();
        Toast.makeText(this, "🔓 " + placeName + "에서 '" + soundName + "' 소리가 열렸어요", Toast.LENGTH_LONG).show();
    }

    // ── Lifecycle (MapView needs every call) ───────────────────────────────
    @Override protected void onStart() { super.onStart(); mapView.onStart(); player.addListener(this); player.setVisible(true); refreshData(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { player.setVisible(false); player.removeListener(this); mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { mapView.onDestroy(); if (navigator != null) navigator.release(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out); }

    // ── Helpers ────────────────────────────────────────────────────────────
    private int px(float v) { return Math.round(v * dp); }

    private Button button(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        style(b, false);
        return b;
    }

    private void style(Button b, boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(px(12));
        d.setColor(active ? 0xFF3D7BFF : 0xFF2A3446);
        b.setBackground(d);
    }

    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1, dLon = (lon2 - lon1) * Math.cos(Math.toRadians(lat1));
        return Math.sqrt(dLat * dLat + dLon * dLon) * 111_195;
    }

    static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1, dLon = (lon2 - lon1) * Math.cos(Math.toRadians(lat1));
        return (Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360;
    }
}
