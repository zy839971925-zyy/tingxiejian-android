#!/usr/bin/env bash
# Island payload contract test.
#
# Compiles the SHIPPED XiaomiIslandPayloadBuilder with a plain desktop JDK and asserts the exact
# HyperOS FocusTemplateV3 shape the verified reference emits (XiaomiSuperIslandPublisher.kt,
# Xiaomi-SuperIsland-Playground, Apache-2.0). Field parity is the contract: a payload with wrong
# nesting is silently ignored by SystemUI, so only byte-level assertions catch regressions.
#
# vendor/json-desktop.jar is the reference org.json used ONLY here (the app uses Android's own
# org.json at runtime); it exists so the desktop test runs the same builder code as the phone.
set -euo pipefail
APP="$(cd "$(dirname "$0")/.." && pwd)"
test -s "$APP/vendor/json-desktop.jar" || { echo 'Missing vendor/json-desktop.jar; see docs/BUILDING.md' >&2; exit 1; }
OUT="$APP/build/payload-test"
rm -rf "$OUT"; mkdir -p "$OUT"

javac -encoding UTF-8 -cp "$APP/vendor/json-desktop.jar" -d "$OUT" \
    "$APP/src/com/example/tingxiejian/XiaomiIslandPayloadBuilder.java" \
    "$APP/design-tools/IslandPayloadDump.java" || exit 1

java -cp "$OUT:$APP/vendor/json-desktop.jar" com.example.tingxiejian.IslandPayloadDump > "$OUT/dump.jsonl" || exit 1

cat > "$OUT/check.py" <<'PYEOF'
import sys, json, html

lines = [l for l in sys.stdin.read().splitlines() if l.strip()]
assert len(lines) == 6, f"expected 6 cases, got {len(lines)}"
cases = [json.loads(l) for l in lines]

def check(cond, msg):
    if not cond:
        print("FAIL:", msg)
        sys.exit(1)

V3 = "com.xzakota.hyper.notification.focus.FocusNotification.FocusTemplateFactory.V3"

for i, c in enumerate(cases):
    check(set(c.keys()) == {"type", "param_v2"}, f"case{i}: top-level must be exactly type+param_v2: {sorted(c.keys())}")
    check(c["type"] == V3, f"case{i}: type serial name")
    p = c["param_v2"]
    check(p["protocol"] == 1, f"case{i}: protocol 1")
    check(p["business"] == "custom_island", f"case{i}: business custom_island")
    check(p["ticker"] and p["tickerPic"] == "miui.focus.pic_island_logo", f"case{i}: ticker/tickerPic")
    check(p["aodTitle"] and p["aodPic"] == "miui.focus.pic_island_logo", f"case{i}: aodTitle/aodPic")
    check(p["updatable"] is True and p["reopen"] == "close", f"case{i}: updatable/reopen")
    check(0 < p["timeout"] <= 720, f"case{i}: timeout clamped to 720")
    b = p["baseInfo"]
    check(b["type"] == 2, f"case{i}: baseInfo type 2")
    check(b["title"] and b["content"], f"case{i}: baseInfo title/content")
    check(b["colorTitle"] == "#16785C" and b["colorTitleDark"] == "#16785C", f"case{i}: baseInfo colors")
    check(("subTitle" in b) == bool(b["showDivider"]), f"case{i}: showDivider/subTitle pair")
    pr = p["progressInfo"]
    check(set(pr.keys()) == {"progress", "colorProgress", "colorProgressEnd"}, f"case{i}: progressInfo keys {sorted(pr.keys())}")
    check(pr["colorProgress"] == "#16785C" and pr["colorProgressEnd"].startswith("#"), f"case{i}: progress colors")
    check("actions" not in p, f"case{i}: reference accessory is exclusive: PROGRESS_TWO, not ACTIONS")
    isl = p["param_island"]
    check(set(isl.keys()) >= {"islandProperty", "islandOrder", "highlightColor",
                              "bigIslandArea", "smallIslandArea", "islandTimeout"}, f"case{i}: param_island keys {sorted(isl.keys())}")
    check(isl["islandProperty"] == 1 and isl["islandOrder"] is False, f"case{i}: islandProperty/order")
    big = isl["bigIslandArea"]
    check(set(big.keys()) == {"imageTextInfoLeft", "progressTextInfo"}, f"case{i}: bigIslandArea keys {sorted(big.keys())}")
    left = big["imageTextInfoLeft"]
    check(left["type"] == 1 and left["picInfo"] == {"type": 1, "pic": "miui.focus.pic_island_primary"},
          f"case{i}: imageTextInfoLeft")
    check(set(left["textInfo"].keys()) >= {"title", "narrowFont", "showHighlightColor"},
          f"case{i}: textInfo keys {sorted(left['textInfo'].keys())}")
    ring = big["progressTextInfo"]["progressInfo"]
    check(set(ring.keys()) == {"progress", "colorReach", "colorUnReach", "isCCW"}, f"case{i}: island ring keys")
    check(ring["isCCW"] is True, f"case{i}: isCCW true")
    check(ring["colorReach"] == "#16785C" and ring["colorUnReach"].startswith("#"), f"case{i}: ring colors")
    small = isl["smallIslandArea"]["combinePicInfo"]
    check(small["picInfo"] == {"type": 1, "pic": "miui.focus.pic_island_primary"}, f"case{i}: small picInfo")
    check(small["progressInfo"]["isCCW"] is True, f"case{i}: small ring isCCW")
    vals = {pr["progress"], ring["progress"], small["progressInfo"]["progress"]}
    check(len(vals) == 1, f"case{i}: progress disagree {vals}")

low = cases[3]["param_v2"]
assert low["progressInfo"]["progress"] == 0 and low["param_island"]["bigIslandArea"]["progressTextInfo"]["progressInfo"]["progress"] == 0, "clamping -5 -> 0"
high = cases[4]["param_v2"]
assert high["progressInfo"]["progress"] == 100 and high["param_island"]["smallIslandArea"]["combinePicInfo"]["progressInfo"]["progress"] == 100, "clamping 150 -> 100"

c0, c1 = cases[0]["param_v2"], cases[1]["param_v2"]
assert c0["enableFloat"] is True and c0["islandFirstFloat"] is True, "create must float"
assert c1["enableFloat"] is False and c1["islandFirstFloat"] is False, "update must not re-float"

e = cases[5]["param_v2"]["baseInfo"]
for key in ("title", "content", "subTitle"):
    v = e[key]
    check(v == html.escape(html.unescape(v), quote=True), f"escaping malformed in {key}: {v!r}")
assert '"' in html.unescape(e["title"]), "quote lost"
assert "\\" in html.unescape(e["content"]), "backslash lost"

for i, raw in enumerate(lines):
    check(len(raw.encode("utf-8")) <= 3072, f"case{i}: payload {len(raw.encode('utf-8'))} bytes > 3072")

print(f"PASS: island payload ({len(cases)} cases: FocusTemplateFactory.V3 field parity, baseInfo/"
      f"exclusive progress accessory, island areas, clamping, firstFrame float flags, escaping, <=3072 bytes)")
PYEOF

python3 "$OUT/check.py" < "$OUT/dump.jsonl"
