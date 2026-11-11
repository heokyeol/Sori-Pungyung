"""One-time extraction of tagged places in South Korea from OpenStreetMap (Overpass API).
Writes build/osm/<category>.json with [lat, lon, name]. The app ships only the result;
it never calls a map API itself.  Usage: python osm_places.py <out dir> [category ...]"""
import json, os, sys, time, urllib.parse, urllib.request

UA = "SoundDrive-dev/0.1 (one-time extraction for an offline place list)"
AREA = 'area["ISO3166-1"="KR"][admin_level=2]->.kr;'
Q = {
    "airport":  'nwr["aeroway"="aerodrome"]["iata"](area.kr);',
    "library":  'nwr["amenity"="library"](area.kr);',
    "temple":   'nwr["amenity"="place_of_worship"]["religion"="buddhist"](area.kr);',
    "station":  'nwr["railway"="station"]["station"!="subway"]["subway"!="yes"](area.kr);',
    "valley":   'nwr["natural"="valley"](area.kr); nwr["boundary"="national_park"](area.kr);',
    "harbor":   'nwr["natural"="beach"](area.kr); nwr["amenity"="ferry_terminal"](area.kr); nwr["harbour"="yes"](area.kr);',
    "cafe":     'nwr["amenity"="cafe"](area.kr);',
    "baseball": 'nwr["sport"~"baseball"]["leisure"~"stadium|sports_centre"](area.kr); nwr["leisure"="stadium"]["name"~"야구"](area.kr);',
}
out_dir = sys.argv[1]
cats = sys.argv[2:] or list(Q)
for cat in cats:
    path = f"{out_dir}/{cat}.json"
    if os.path.exists(path): print("have", cat); continue
    query = f"[out:json][timeout:300];{AREA}({Q[cat]});out center tags qt;"
    data = urllib.parse.urlencode({"data": query}).encode()
    for attempt in range(3):
        try:
            req = urllib.request.Request(os.environ.get("OVERPASS", "https://overpass-api.de/api/interpreter"), data=data, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=360) as r:
                els = json.load(r)["elements"]
            break
        except Exception as e:
            print("retry", cat, e); time.sleep(20 * (attempt + 1)); els = None
    if els is None: print("FAILED", cat); continue
    rows = []
    for e in els:
        lat = e.get("lat") or e.get("center", {}).get("lat")
        lon = e.get("lon") or e.get("center", {}).get("lon")
        if lat is None: continue
        t = e.get("tags", {})
        rows.append([round(lat, 5), round(lon, 5), t.get("name:ko") or t.get("name") or ""])
    json.dump(rows, open(path, "w"), ensure_ascii=False)
    print(f"{cat:<9} {len(rows)}")
    time.sleep(5)
