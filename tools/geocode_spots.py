"""One-time geocoding of seasonal landmarks with OpenStreetMap Nominatim (1 request/second).
Writes build/spots/geocoded.json. The app ships the coordinates; it never geocodes itself."""
import json, sys, time, urllib.parse, urllib.request
UA = "SoundDrive-dev/0.1 (one-time landmark lookup)"
Q = {
    "jinhae": ["여좌천", "진해 여좌천"], "boseong": ["대한다원", "보성 녹차밭"], "gasiri": ["가시리", "녹산로"],
    "yangyang": ["서피비치", "죽도해수욕장"], "haeundae": ["해운대해수욕장"], "damyang": ["죽녹원"],
    "maisan": ["마이산"], "naejangsan": ["내장산"], "mindungsan": ["민둥산 정선"],
    "daegwallyeong": ["대관령", "대관령 양떼목장"], "pyeongchang": ["용평리조트", "휘닉스 평창", "알펜시아"],
    "hwacheon": ["화천 산천어축제", "화천천"],
}
out = {}
for key, names in Q.items():
    pts = []
    for n in names:
        url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode({"q": n, "format": "json", "countrycodes": "kr", "limit": 1})
        try:
            r = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30))
            if r: pts.append([round(float(r[0]["lat"]), 5), round(float(r[0]["lon"]), 5), r[0]["display_name"][:60]])
            else: pts.append([None, None, "NOT FOUND: " + n])
        except Exception as e:
            pts.append([None, None, f"ERR {n}: {e}"])
        time.sleep(1.2)
    out[key] = pts
    for p in pts: print(f"{key:<14} {p[0]} {p[1]}  {p[2]}")
json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
