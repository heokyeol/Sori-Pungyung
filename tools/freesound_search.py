"""List Freesound search results (CC0 first, then CC-BY) without an API key.
Usage: python freesound_search.py OUT.json "query" ...   Prints top results per query."""
import html, json, re, sys, time, urllib.parse, urllib.request

UA = "Mozilla/5.0 (Macintosh) SoundDrive-dev (personal test)"
LICS = {"cc0": '"Creative Commons 0"', "by": '"Attribution"'}

def fetch(q, lic):
    url = "https://freesound.org/search/?" + urllib.parse.urlencode(
        {"q": q, "f": "license:" + LICS[lic], "s": "Downloads (most first)"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read().decode("utf-8", "ignore")

def parse(page, lic):
    out = []
    for m in re.finditer(r'<div\s+class="bw-player"(.*?)tabindex', page, re.S):
        a = m.group(1)
        g = lambda k: (re.search(k + r'="([^"]*)"', a) or [None, ""])[1]
        sid = g("data-sound-id")
        if not sid: continue
        out.append({"id": sid, "user": g("data-username"), "title": html.unescape(g("data-title")),
                    "dur": float(g("data-duration") or 0), "sr": g("data-samplerate"),
                    "downloads": int(g("data-num-downloads") or 0),
                    "ogg": g("data-ogg").replace("-lq.ogg", "-hq.ogg"), "license": lic,
                    "page": f"https://freesound.org/people/{g('data-username')}/sounds/{sid}/"})
    return out

res = {}
for q in sys.argv[2:]:
    rows = []
    for lic in ("cc0", "by"):
        try: rows += parse(fetch(q, lic), lic)
        except Exception as e: print("ERR", q, lic, e)
        time.sleep(2.0)
    res[q] = rows
    print(f"== {q}")
    for r in rows[:5] + [x for x in rows if x["license"] == "by"][:3]:
        print(f"   {r['license']:<3} {r['id']:>7} {r['dur']:6.1f}s dl={r['downloads']:<6} {r['title'][:50]} | {r['user']}")
json.dump(res, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
