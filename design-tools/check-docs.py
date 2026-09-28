#!/usr/bin/env python3
"""Offline checks for the bilingual GitHub documentation graph (no network required)."""

from pathlib import Path
import re
import unicodedata
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent.parent
PAIRS = (
    ("README.md", "README.en.md"),
    ("docs/README.md", "docs/README.en.md"),
    ("docs/ARCHITECTURE.md", "docs/ARCHITECTURE.en.md"),
    ("docs/BUILDING.md", "docs/BUILDING.en.md"),
    ("docs/UI-MOTION.md", "docs/UI-MOTION.en.md"),
    ("docs/RELEASE-1.0.3.md", "docs/RELEASE-1.0.3.en.md"),
    ("docs/REVIEW-2026-09-28.zh-CN.md", "docs/REVIEW-2026-09-28.md"),
    ("CHANGELOG.md", "CHANGELOG.en.md"),
    ("CONTRIBUTING.zh-CN.md", "CONTRIBUTING.md"),
    ("SECURITY.zh-CN.md", "SECURITY.md"),
    ("DISCLAIMER.md", "DISCLAIMER.en.md"),
    ("THIRD_PARTY_NOTICES.zh-CN.md", "THIRD_PARTY_NOTICES.md"),
)
LINKS = re.compile(r"\]\(([^)]+)\)|<(?:a|img)\b[^>]*(?:href|src)=\"([^\"]+)\"")
HEADINGS = re.compile(r"^#{1,6}\s+(.+)$", re.MULTILINE)


def slug(title):
    # GitHub-style heading approximation sufficient for the explicit document anchors.
    title = re.sub(r"<[^>]*>", "", title).casefold()
    return "".join(
        c for c in title if c in "-_ " or unicodedata.category(c)[0] in "LN"
    ).replace(" ", "-")


def main():
    issues = []
    paths = {p for pair in PAIRS for p in pair}
    paths.update((".github/ISSUE_TEMPLATE/bug_report.md", ".github/PULL_REQUEST_TEMPLATE.md"))
    contents = {}
    for name in sorted(paths):
        path = ROOT / name
        if not path.is_file():
            issues.append(f"missing document: {name}")
        else:
            contents[path.resolve()] = path.read_text(encoding="utf-8")

    for a, b in PAIRS:
        pa, pb = ROOT / a, ROOT / b
        if not pa.is_file() or not pb.is_file():
            continue
        for src, target in ((pa, pb), (pb, pa)):
            if f'href="{target.name}"' not in contents[src.resolve()][:550]:
                issues.append(f"missing reciprocal language link: {src.relative_to(ROOT)} → {target.name}")

    for path, text in contents.items():
        name = path.relative_to(ROOT)
        if text.count("```") % 2 or text.count("<details>") != text.count("</details>"):
            issues.append(f"unbalanced code fence or details: {name}")
        for match in LINKS.finditer(text):
            target = match.group(1) or match.group(2)
            if target.startswith(("https:", "http:", "mailto:", "data:")):
                continue
            file, _, fragment = target.partition("#")
            dest = (path.parent / unquote(file)).resolve() if file else path
            if not dest.is_file():
                issues.append(f"broken local link: {name} → {target}")
                continue
            if fragment and dest.suffix.lower() == ".md":
                destination_text = contents[dest] if dest in contents else dest.read_text(encoding="utf-8")
                headings = {slug(h) for h in HEADINGS.findall(destination_text)}
                if unquote(fragment) not in headings:
                    issues.append(f"broken heading anchor: {name} → {target}")

    if issues:
        print("FAIL: bilingual docs\n" + "\n".join(issues))
        raise SystemExit(1)
    print(f"PASS: bilingual docs ({len(PAIRS)} reciprocal pairs; local links and anchors resolve)")


if __name__ == "__main__":
    main()
