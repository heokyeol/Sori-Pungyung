"""One-time geocoding of the 20 regions (시·군·섬 단위). Nominatim, 1 req/s. Usage: python geocode_areas.py OUT.json"""
import json, sys, time, urllib.parse, urllib.request
UA = "SoundDrive-dev/0.1 (one-time region lookup)"
Q = ["영종도", "여의도한강공원", "반포한강공원", "뚝섬한강공원", "춘천시", "속초시", "설악동", "강릉시", "철원군 동송읍", "단양군 단양읍",
     "태안군 태안읍", "전주한옥마을", "곡성군 곡성읍", "순천만습지", "여수시 이순신광장", "남해군 남해읍", "경주시", "하회마을",
     "부산광역시", "통영시", "울릉도", "우도", "제주시청", "서귀포시청"]
out = {}
for q in Q:
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode({"q": q, "format": "json", "countrycodes": "kr", "limit": 1})
    try:
        r = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30))
    except Exception as e:
        r = []; print("ERR", q, e)
    out[q] = [round(float(r[0]["lat"]), 5), round(float(r[0]["lon"]), 5), r[0]["display_name"][:50]] if r else None
    print(f"{q:<14} {out[q]}")
    time.sleep(1.2)
json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
