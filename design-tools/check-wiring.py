#!/usr/bin/env python3
"""
Cross-checks Android resources against Java code, for every screen.

For each Activity it finds the layout it inflates, then asserts that:
  * every `need(R.id.x)` / `findViewById(R.id.x)` id exists in that layout;
  * every field the id is assigned to has a type the layout element can be cast to
    (the exact bug that crashed v0.7: a plain <View> bound to an ImageView field).

Layout links (`@drawable/...`, `@style/...`, `@font/...`, `@color/...`) must exist too.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
RES = ROOT / "res"

# Layout element -> Java classes an findViewById result may legally be assigned to.
ELEMENT_PARENT = {
    "View": {"View"},
    "TextView": {"View", "TextView"},
    "Button": {"View", "TextView", "Button"},
    "CompoundButton": {"View", "TextView", "Button", "CompoundButton"},
    "Switch": {"View", "TextView", "Button", "CompoundButton", "Switch"},
    "CheckBox": {"View", "TextView", "Button", "CompoundButton", "CheckBox"},
    "EditText": {"View", "TextView", "EditText"},
    "ImageView": {"View", "ImageView"},
    "ViewGroup": {"View", "ViewGroup"},
    "LinearLayout": {"View", "ViewGroup", "LinearLayout"},
    "FrameLayout": {"View", "ViewGroup", "FrameLayout"},
    "ScrollView": {"View", "ViewGroup", "FrameLayout", "ScrollView"},
    "HorizontalScrollView": {"View", "ViewGroup", "FrameLayout", "ScrollView", "HorizontalScrollView"},
    "ListView": {"View", "ViewGroup", "AbsListView", "ListView"},
    "ProgressBar": {"View", "ProgressBar"},
    "SeekBar": {"View", "ProgressBar", "AbsSeekBar", "SeekBar"},
    "Space": {"View", "Space"},
    "RingView": {"View", "RingView"},
    "WaveView": {"View", "WaveView"},
}
CUSTOM_PARENT = {"RingView": "View", "WaveView": "View"}

def fail(msg):
    print("FAIL:", msg)
    sys.exit(1)

def layout_ids(path):
    ids = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.search(r'android:id="@\+id/(\w+)"', line)
        if m:
            ids[m.group(1)] = "View"
    text = path.read_text(encoding="utf-8")
    for m in re.finditer(r'<([\w.]+)[^>]*?android:id="@\+id/(\w+)"', text, re.S):
        element = m.group(1).split(".")[-1]
        ids[m.group(2)] = element
    return ids

def java_fields(path):
    fields = {}
    for m in re.finditer(r'(?:private|protected|public)?\s*(?:final\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*;',
                         path.read_text(encoding="utf-8")):
        fields[m.group(2)] = m.group(1)
    return fields

def check_activity(java_path):
    text = java_path.read_text(encoding="utf-8")
    m = re.search(r'setContentView\(R\.layout\.(\w+)\)', text)
    if not m:
        return 0
    layout = RES / "layout" / (m.group(1) + ".xml")
    if not layout.is_file():
        fail("%s: layout %s 不存在" % (java_path.name, layout.name))
    ids = layout_ids(layout)
    fields = java_fields(java_path)
    checks = 0

    for call in re.finditer(r'(\w+)\s*=\s*need\(R\.id\.(\w+)\)', text):
        field, ident = call.group(1), call.group(2)
        if ident not in ids:
            fail("%s: %s 绑定的 R.id.%s 在 %s 里不存在" % (java_path.name, field, ident, layout.name))
        declared = fields.get(field, "View")
        element = ids[ident]
        allowed = ELEMENT_PARENT.get(element, {element})
        if declared not in allowed:
            fail("%s: %s 声明为 %s，但 R.id.%s 在布局里是 <%s>（findViewById 会抛 ClassCastException）"
                 % (java_path.name, field, declared, ident, element))
        checks += 2

    for call in re.finditer(r'findViewById\(R\.id\.(\w+)\)', text):
        ident = call.group(1)
        if ident not in ids:
            fail("%s: findViewById(R.id.%s) 在 %s 里不存在" % (java_path.name, ident, layout.name))
        checks += 1

    for ident in re.findall(r'R\.id\.(\w+)', text):
        if ident not in ids:
            fail("%s: R.id.%s 在 %s 里不存在" % (java_path.name, ident, layout.name))
        checks += 1
    return checks

def check_links():
    checks = 0
    names = {
        "drawable": {p.stem for p in (RES / "drawable").glob("*.xml")},
        "style": set(re.findall(r'<style name="([\w.]+)"',
                                (RES / "values" / "styles.xml").read_text(encoding="utf-8"))),
        "font": {p.stem for p in (RES / "font").glob("*")},
        "color": set(re.findall(r'<color name="(\w+)"',
                                (RES / "values" / "colors.xml").read_text(encoding="utf-8")))
                | set(re.findall(r'<color name="(\w+)"',
                                (RES / "values-night" / "colors.xml").read_text(encoding="utf-8"))),
        "layout": {p.stem for p in (RES / "layout").glob("*.xml")},
    }
    for xml in list(RES.rglob("*.xml")):
        text = xml.read_text(encoding="utf-8")
        for kind, name in re.findall(r'@(drawable|style|font|color|layout)/([\w.]+)', text):
            if name not in names[kind]:
                fail("%s: @%s/%s 不存在" % (xml.relative_to(ROOT), kind, name))
            checks += 1
    return checks

def check_custom_views():
    checks = 0
    for java in SRC.rglob("*.java"):
        for m in re.finditer(r'class\s+(\w+)\s+extends\s+(\w+)', java.read_text(encoding="utf-8")):
            name, parent = m.group(1), m.group(2)
            if name in ("RingView", "WaveView"):
                if CUSTOM_PARENT[name] != parent and parent not in ELEMENT_PARENT[name]:
                    fail("%s: 自定义视图 %s 的父类 %s 与布局元素不匹配" % (java.name, name, parent))
                checks += 1
    return checks

def check_layout_attrs():
    """Every element needs layout_width/height. Missing ones crash at inflate time (v0.11 incident:
    25 TextViews whose style was assumed to carry dimensions)."""
    checks = 0
    for xml in sorted((RES / "layout").glob("*.xml")):
        text = xml.read_text(encoding="utf-8")
        for m in re.finditer(r'<(\w+)([^>]*?)/?>', text, re.S):
            tag, attrs = m.group(1), m.group(2)
            if tag in ("include", "merge") or tag.startswith("?"):
                continue
            line = text[:m.start()].count("\n") + 1
            if "layout_width" not in attrs or "layout_height" not in attrs:
                fail("%s:%d <%s> 缺少 layout_width/layout_height（膨胀时必崩）" % (xml.name, line, tag))
            checks += 1
    return checks

def main():
    total = 0
    for java in sorted(SRC.rglob("*Activity.java")):
        total += check_activity(java)
    total += check_links()
    total += check_custom_views()
    total += check_layout_attrs()
    print("wiring ok: %d checks" % total)

if __name__ == "__main__":
    main()
