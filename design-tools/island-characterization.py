#!/usr/bin/env python3
"""Compare protected Island code with the checked-in, device-confirmed baseline.

This is a preservation contract, not proof of OEM rendering or firewall safety.
The host behavior check executes the actual shipped bridge, not a rewritten model.
There is deliberately no update/bless flag: changing the contract needs review.
"""
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src/com/example/tingxiejian"
FIXTURE = ROOT / "design-tools/fixtures/island-characterization"
BASELINE = json.loads((ROOT / "docs/upgrade/baseline.json").read_text())
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def mask_java(source):
    """Mask literals/comments, keeping indexes so braces inside them do not count."""
    pattern = r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    return re.sub(pattern, lambda m: "".join("\n" if c == "\n" else " " for c in m[0]), source)


def service_contract(source):
    masked = mask_java(source)
    methods = {}
    names = "ensureForeground|notify|createChannel|buildNotification|stopActionUri|updateNotice"
    pattern = re.compile(r"^[ \t]*private\s+(?:static\s+)?(?:void|Notification|String)\s+(" + names
                         + r")\s*\(([^)]*)\)\s*\{", re.M)
    for match in pattern.finditer(masked):
        depth = 1
        end = match.end()
        while end < len(masked) and depth:
            depth += (masked[end] == "{") - (masked[end] == "}")
            end += 1
        require(depth == 0, "unterminated protected LocalService method")
        signature = match[1] + "(" + " ".join(match[2].split()) + ")"
        require(signature not in methods, "duplicate protected method: " + signature)
        methods[signature] = digest(source[match.start():end].encode())
    require(len(methods) == 7, "expected 7 protected LocalService methods/overloads")
    fields = {}
    names = ("CHANNEL_QUIET", "CHANNEL_ISLAND", "NOTIFICATION_ID", "BOOTSTRAP_ID",
             "DONE_LINGER_MS", "NOTIFY_WHEN", "islandWanted", "bootstrapActive",
             "islandFirstFrame", "lastNotifyAt", "firstNotify")
    for name in names:
        declarations = list(re.finditer(r"^[ \t]*(?:(?:private|static|final)\s+)*"
                                        r"(?:String|int|long|boolean)\s+" + name
                                        + r"\b[^;]*;", masked, re.M))
        require(len(declarations) == 1, "missing/duplicate protected field: " + name)
        match = declarations[0]
        fields[name] = digest(source[match.start():match.end()].encode())
    return {"methods": methods, "fields": fields}


def canonical_element(element):
    return {"tag": element.tag, "attributes": dict(sorted(element.attrib.items())),
            "text": (element.text or "").strip(),
            "children": [canonical_element(child) for child in element]}


def manifest_contract(path):
    root = ET.parse(path).getroot()
    app = root.find("application")
    require(app is not None, "application missing in manifest")
    providers = [e for e in app.findall("provider")
                 if e.get(ANDROID + "name") == "rikka.shizuku.ShizukuProvider"]
    require(len(providers) == 1, "Shizuku provider must appear exactly once")
    queries = root.findall("queries")
    require(len(queries) == 1, "queries must appear exactly once")
    permissions = {}
    for entry in root.findall("uses-permission"):
        name = entry.get(ANDROID + "name")
        require(name not in permissions, "duplicate manifest permission: " + str(name))
        permissions[name] = canonical_element(entry)
    metadata = {}
    for entry in app.findall("meta-data"):
        name = entry.get(ANDROID + "name")
        require(name not in metadata, "duplicate application metadata: " + str(name))
        metadata[name] = canonical_element(entry)
    return {"package": root.get("package"), "provider": canonical_element(providers[0]),
            "queries": canonical_element(queries[0]), "permissions": permissions,
            "metadata": metadata}


def verify_preservation():
    for name, expected in BASELINE["protected_files"].items():
        snapshot = (FIXTURE / "baseline" / (name + ".txt")).read_bytes()
        require(digest(snapshot) == expected, "baseline snapshot altered: " + name)
        require(digest((SRC / name).read_bytes()) == expected, "protected source changed: " + name)
    service = (FIXTURE / "baseline/LocalService.java.txt").read_bytes()
    require(digest(service) == BASELINE["local_service_baseline_sha256"], "LocalService snapshot altered")
    expected = json.loads((FIXTURE / "contract.json").read_text())
    require(expected["head"] == BASELINE["head"], "characterization baseline HEAD differs")
    require(service_contract(service.decode()) == expected["service"], "LocalService baseline contract altered")
    # Only approved notification change: exceptions permanently disable OEM retries for that run.
    # Derive the expected method from the immutable original, rather than blessing a new hash.
    notification_change = 'Log.w(TAG, "notify failed", error);'
    approved = service.decode().replace(notification_change, notification_change
            + '\n            islandWanted = false; // A failed OEM submission must not retry its private API this run.')
    require(service_contract((SRC / "LocalService.java").read_text()) == service_contract(approved),
            "LocalService notification method/constant differs from baseline")
    old_manifest = FIXTURE / "baseline/AndroidManifest.xml.txt"
    require(digest(old_manifest.read_bytes()) == expected["manifest_sha256"], "Manifest snapshot altered")
    old = manifest_contract(old_manifest)
    require(old == expected["manifest"], "parsed Manifest baseline contract altered")
    current = manifest_contract(ROOT / "AndroidManifest.xml")
    for key in ("package", "provider", "queries"):
        require(current[key] == old[key], "protected Manifest subtree changed: " + key)
    for group in ("permissions", "metadata"):
        for name, element in old[group].items():
            require(current[group].get(name) == element, "protected Manifest declaration changed: " + name)
    extra_permissions = set(current["permissions"]) - set(old["permissions"])
    require(extra_permissions <= {"android.permission.RECORD_AUDIO", "android.permission.POST_PROMOTED_NOTIFICATIONS"},
            "unexpected added Manifest permission: " + str(sorted(extra_permissions)))
    print("PASS: Island preservation (6 complete files, 7 service methods, 11 fields, parsed Manifest)", flush=True)


def verify_checker_sensitivity():
    """Confirm the preservation checker notices material mutations and ignores XML layout."""
    service = (FIXTURE / "baseline/LocalService.java.txt").read_text()
    old = service_contract(service)
    for before, after in (("NOTIFICATION_ID = 11", "NOTIFICATION_ID = 91"),
                          ('CHANNEL_ISLAND = "transcribe_island_v1"', 'CHANNEL_ISLAND = "different"'),
                          ("now - lastNotifyAt < 5000", "now - lastNotifyAt < 500"),
                          ("useIsland ? CHANNEL_ISLAND : CHANNEL_QUIET", "CHANNEL_QUIET")):
        require(before in service, "sensitivity test no longer selects baseline text")
        require(service_contract(service.replace(before, after)) != old,
                "characterization missed service mutation: " + before)
    manifest = (FIXTURE / "baseline/AndroidManifest.xml.txt").read_text()
    with tempfile.TemporaryDirectory(prefix="island-xml-characterization-") as directory:
        path = pathlib.Path(directory) / "AndroidManifest.xml"
        path.write_text(manifest)
        old = manifest_contract(path)
        path.write_text(manifest.replace("    ", "\t").replace("/><", "/>\n<"))
        require(manifest_contract(path) == old, "XML formatting must not change semantic contract")
        for before, after in (("com.example.tingxiejian.shizuku", "wrong.authority"),
                              ("INTERACT_ACROSS_USERS_FULL", "INTERNET"),
                              ("com.xiaomi.xmsf", "wrong.visibility"),
                              ('V3_SUPPORT" android:value="true"', 'V3_SUPPORT" android:value="false"'),
                              ("moe.shizuku.manager.permission.API_V23", "wrong.permission")):
            require(before in manifest, "sensitivity test no longer selects baseline XML")
            path.write_text(manifest.replace(before, after))
            require(manifest_contract(path) != old, "characterization missed XML mutation: " + before)
    print("PASS: characterization sensitivity (9 material mutations detected; XML layout ignored)", flush=True)


def verify_bridge_behavior():
    sources = sorted((FIXTURE / "stubs").rglob("*.java"))
    sources += [SRC / "ShizukuIslandBridge.java", SRC / "XiaomiXmsfValidationGate.java",
                FIXTURE / "IslandBridgeBehaviorCheck.java"]
    with tempfile.TemporaryDirectory(prefix="tingxiejian-island-characterization-") as output:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", output, *map(str, sources)], check=True)
        subprocess.run(["java", "-ea", "-cp", output,
                        "com.example.tingxiejian.IslandBridgeBehaviorCheck"], check=True)


if __name__ == "__main__":
    try:
        verify_preservation()
        verify_checker_sensitivity()
        verify_bridge_behavior()
    except (AssertionError, OSError, subprocess.CalledProcessError, ET.ParseError) as error:
        raise SystemExit("FAIL: " + str(error))
