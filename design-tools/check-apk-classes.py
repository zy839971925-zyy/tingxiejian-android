#!/usr/bin/env python3
"""Verify actual DEX class definitions, not mere references, for startup dependencies.

A Java compile can succeed with Shizuku's API/provider jars yet produce a launch-crashing APK
if its transitive `aidl` / `shared` classes were not passed to d8. This parses the DEX class_defs
rather than searching strings (a missing class can still be mentioned in another class's DEX).
"""
import pathlib
import struct
import sys
import zipfile

REQUIRED = {
    "Lcom/example/tingxiejian/TingxiejianApp;",
    "Lcom/example/tingxiejian/MainActivity;",
    "Lrikka/shizuku/Shizuku;",
    "Lrikka/shizuku/ShizukuProvider;",
    "Lrikka/sui/Sui;",
    "Lmoe/shizuku/api/BinderContainer;",
    "Lmoe/shizuku/server/IShizukuService;",  # transitive aidl
    "Lrikka/shizuku/ShizukuApiConstants;",    # transitive shared
}


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def descriptor(data, index):
    offset = u32(data, u32(data, 0x3c) + index * 4)
    # Skip unsigned LEB128 UTF-16 character count preceding MUTF-8 string data.
    while data[offset] & 0x80:
        offset += 1
    offset += 1
    end = data.index(b"\0", offset)
    return data[offset:end].decode("utf-8")


def classes(dex):
    if dex[:4] != b"dex\n":
        raise ValueError("not DEX data")
    type_off = u32(dex, 0x44)
    definitions = set()
    for pos in range(u32(dex, 0x64), u32(dex, 0x64) + u32(dex, 0x60) * 32, 32):
        class_idx = u32(dex, pos)
        definitions.add(descriptor(dex, u32(dex, type_off + 4 * class_idx)))
    return definitions


apk = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path("dist/tingxiejian-v1.0-arm64-release.apk")
with zipfile.ZipFile(apk) as package:
    all_classes = set()
    for name in package.namelist():
        if name.startswith("classes") and name.endswith(".dex"):
            all_classes.update(classes(package.read(name)))
missing = REQUIRED - all_classes
if missing:
    sys.exit("Missing DEX definitions (may crash before MainActivity): " + ", ".join(sorted(missing)))
print("APK startup dependencies verified:", len(REQUIRED), "DEX definitions")
