"""One-time geocoding of the region list for default (non-seasonal) unlocks. Nominatim, 1 req/s.
Usage: python geocode_regions.py OUT.json"""
import json, sys, time, urllib.parse, urllib.request
UA = "SoundDrive-dev/0.1 (one-time region lookup)"
Q = [
    ("airport", "영종도", 8000), ("airport", "김포국제공항", 3000), ("airport", "제주국제공항", 3000),
    ("station", "철도박물관 의왕", 800), ("station", "섬진강기차마을", 1000),
    ("library", "별마당도서관", 300), ("library", "지혜의숲 파주", 400), ("library", "국립중앙도서관", 400),
    ("temple", "불국사", 1000), ("temple", "해인사", 1000), ("temple", "송광사 순천", 1000),
    ("valley", "비선대", 2000), ("valley", "뱀사골", 2500), ("valley", "용추계곡 가평", 2000),
    ("harbor", "자갈치시장", 1000), ("harbor", "인천 연안부두", 1000), ("harbor", "목포항", 1200),
    ("cafe", "안목해변", 800), ("cafe", "성수동 카페거리", 1200), ("cafe", "보정동 카페거리", 600),
    ("baseball", "잠실야구장", 500), ("baseball", "사직야구장", 500), ("baseball", "대구삼성라이온즈파크", 500),
    ("baseball", "광주기아챔피언스필드", 500), ("baseball", "인천SSG랜더스필드", 500), ("baseball", "수원KT위즈파크", 500),
    ("baseball", "한화생명볼파크", 500), ("baseball", "창원NC파크", 500), ("baseball", "고척스카이돔", 500),
]
out = []
for cat, q, r in Q:
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode({"q": q, "format": "json", "countrycodes": "kr", "limit": 1})
    try:
        res = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30))
    except Exception as e:
        res = []; print("ERR", q, e)
    if res:
        out.append([cat, q, round(float(res[0]["lat"]), 5), round(float(res[0]["lon"]), 5), r, res[0]["display_name"][:55]])
    else:
        out.append([cat, q, None, None, r, "NOT FOUND"])
    print(f"{cat:<9} {q:<14} {out[-1][2]} {out[-1][3]}  {out[-1][5]}")
    time.sleep(1.2)
json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
