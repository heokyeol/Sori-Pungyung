"""Search Wikimedia Commons for audio files and list license / duration / author.
Usage: python commons_search.py OUT.json "query1" "query2" ...  (writes all candidates as JSON)"""
import json, re, sys, time, urllib.parse, urllib.request

UA = "SoundDrive-dev/0.1 (personal test; ty2004107 at gmail)"
def q(search):
    params = {"action": "query", "format": "json", "generator": "search", "gsrnamespace": "6",
              "gsrlimit": "30", "gsrsearch": search + " filetype:audio",
              "prop": "videoinfo", "viprop": "url|size|mime|extmetadata|duration" if False else "url|size|mime|extmetadata",
              "viextmetadatafilter": "LicenseShortName|Artist|ObjectName|ImageDescription"}
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)

out = {}
for term in sys.argv[2:]:
    try:
        d = q(term)
    except Exception as e:
        print("ERR", term, e); continue
    rows = []
    for p in (d.get("query", {}).get("pages", {}) or {}).values():
        vi = (p.get("videoinfo") or [{}])[0]
        m = vi.get("extmetadata", {})
        lic = m.get("LicenseShortName", {}).get("value", "?")
        art = re.sub("<[^>]+>", "", m.get("Artist", {}).get("value", "")).strip()[:40]
        desc = re.sub("<[^>]+>", "", m.get("ImageDescription", {}).get("value", "")).strip()[:80]
        rows.append({"title": p["title"], "license": lic, "artist": art, "size": vi.get("size", 0),
                     "url": vi.get("url"), "page": vi.get("descriptionurl"), "desc": desc})
    out[term] = rows
    ok = [r for r in rows if re.match(r"^(CC0|Public domain|CC BY \d|CC BY-SA \d)", r["license"])]
    print(f"== {term}: {len(rows)} found, {len(ok)} usable")
    for r in sorted(ok, key=lambda r: (not r["license"].startswith(("CC0", "Public")), -r["size"]))[:6]:
        print(f"   {r['license']:<13} {r['size']//1024:>6}KB  {r['title'][5:][:55]}  | {r['artist'][:25]}")
    time.sleep(0.5)
json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
