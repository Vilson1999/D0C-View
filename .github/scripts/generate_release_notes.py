#!/usr/bin/env python3
"""Generate per-version GitHub release notes strictly from new release details.

Priority order:
1. Explicit workflow_dispatch release notes (INPUT_NOTES)
2. RELEASE_NOTES.md — only if it contains a section matching TAG/APP_VERSION,
   or if it is unversioned and was modified since the previous release tag
3. New non-infra commits since the previous tag in the same release channel

Sections (New Features, Improvements, Fixes) with no items are omitted.
If no details are available, all detail sections are omitted completely.
"""

from __future__ import annotations

import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

PLACEHOLDER_VALUES = {
    "none",
    "n/a",
    "na",
    "nil",
    "nothing",
    "no changes",
    "no new changes",
    "no fixes",
    "no improvements",
    "no new features",
    "no details",
    "no details available",
    "tbd",
    "-",
    "*",
}

INFRA_ONLY_PREFIXES = (".github/",)
INFRA_ONLY_FILES = {
    "RELEASE_NOTES.md",
    "README.md",
    "LICENSE",
    ".gitignore",
    ".env.example",
}

SKIP_COMMIT_RE = re.compile(
    r"^(?:"
    r"merge\b|"
    r"(?:ci|chore|docs|test|tests|build|release)(?:\([^)]*\))?:\s*|"
    r"bump\s+version\b|"
    r"update\s+release[_\s-]?notes\b|"
    r"wip\b"
    r")",
    re.IGNORECASE,
)

CONVENTIONAL_RE = re.compile(
    r"^(?P<type>[a-zA-Z]+)(?:\([^)]+\))?(?:!)?:\s*(?P<desc>.+)$"
)

VERSION_HEADER_RE = re.compile(
    r"^#{1,2}\s+(?:\[?([^\]\n]+?)\]?(?:\s+[-—–]\s+.*)?)\s*$"
)

SECTION_HEADER_RE = re.compile(r"^(#{2,3})\s+(.+?)\s*$")


def run_git(*args: str) -> str | None:
    try:
        res = subprocess.run(
            ["git", *args],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        return res.stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def strip_html_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


def is_placeholder_line(line: str) -> bool:
    cleaned = line.strip()
    if not cleaned:
        return True
    cleaned = re.sub(r"^[-*•]\s*", "", cleaned).strip().rstrip(".").lower()
    return cleaned in PLACEHOLDER_VALUES


def canonical_category(heading: str) -> str | None:
    low = heading.strip().lower()
    if any(k in low for k in ("feature", "what's new", "whats new", "added")):
        return "New Features"
    if any(k in low for k in ("improv", "enhanc", "optimiz", "performance")):
        return "Improvements"
    if any(k in low for k in ("fix", "bug", "patch", "resolved")):
        return "Fixes"
    return None


def clean_markdown_details(raw_text: str) -> str:
    """Keep only non-empty detail sections and omit empty headings or placeholders."""
    text = strip_html_comments(raw_text).strip()
    if not text:
        return ""

    lines = text.splitlines()
    has_section_headers = any(SECTION_HEADER_RE.match(line.strip()) for line in lines)

    if not has_section_headers:
        meaningful = [line.rstrip() for line in lines if not is_placeholder_line(line)]
        return "\n".join(meaningful).strip()

    preamble: list[str] = []
    sections: list[tuple[str, list[str]]] = []
    current_heading: str | None = None
    current_lines: list[str] = []

    def flush() -> None:
        nonlocal current_heading, current_lines
        if current_heading is None:
            return
        meaningful = [ln.rstrip() for ln in current_lines if not is_placeholder_line(ln)]
        if meaningful:
            sections.append((current_heading, meaningful))
        current_heading = None
        current_lines = []

    for line in lines:
        m = SECTION_HEADER_RE.match(line.strip())
        if m:
            flush()
            title = m.group(2).strip()
            current_heading = canonical_category(title) or title
            current_lines = []
        elif current_heading is None:
            if not is_placeholder_line(line):
                preamble.append(line.rstrip())
        else:
            current_lines.append(line)

    flush()

    merged: dict[str, list[str]] = {}
    order: list[str] = []
    for title, body in sections:
        if title not in merged:
            merged[title] = []
            order.append(title)
        merged[title].extend(body)

    out_parts: list[str] = []
    if preamble:
        out_parts.append("\n".join(preamble))
    for title in order:
        body = merged[title]
        if body:
            out_parts.append(f"## {title}\n\n" + "\n".join(body))

    return "\n\n".join(out_parts).strip()


def looks_like_version_token(token: str) -> bool:
    t = token.strip().lower()
    if t == "unreleased":
        return True
    if re.match(r"^(?:version\s+)?(?:v|universal-|beta-)?\d+\.\d+(?:\.\d+)*(?:[-+._][a-z0-9.]+)?$", t):
        return True
    return False


def build_version_candidates(tag: str, app_version: str) -> set[str]:
    candidates: set[str] = set()
    for raw in (tag, app_version):
        val = raw.strip().lower()
        if not val:
            continue
        candidates.add(val)
        candidates.add(val.lstrip("v"))
        stripped_channel = re.sub(r"^(?:universal-|beta-)", "", val)
        candidates.add(stripped_channel)
        candidates.add(stripped_channel.lstrip("v"))
    candidates.discard("")
    return candidates


def normalize_version_key(header_token: str) -> str:
    t = header_token.strip().lower()
    t = re.sub(r"^version\s+", "", t)
    return t


def tag_matches_channel(tag: str, channel: str) -> bool:
    if channel == "stable":
        return tag.startswith("v") and "-beta" not in tag and not tag.startswith("universal-")
    if channel == "beta":
        return "-beta" in tag or tag.startswith("beta-")
    if channel == "universal":
        return tag.startswith("universal-")
    return True


def find_previous_channel_tag(current_tag: str, channel: str) -> str | None:
    raw_tags = run_git(
        "for-each-ref",
        "--sort=-creatordate",
        "--merged=HEAD",
        "--format=%(refname:short)",
        "refs/tags",
    )
    if not raw_tags:
        return None

    for tag in raw_tags.splitlines():
        tag = tag.strip()
        if not tag or tag == current_tag:
            continue
        if not tag_matches_channel(tag, channel):
            continue
        return tag
    return None


def file_changed_for_release(path: str, prev_tag: str | None) -> bool:
    if prev_tag:
        diff = run_git("diff", "--name-only", f"{prev_tag}..HEAD", "--", path)
        return bool(diff)
    diff = run_git("diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD", "--", path)
    return bool(diff)


def extract_release_notes_file(
    notes_path: Path, tag: str, app_version: str, prev_tag: str | None
) -> str:
    if not notes_path.is_file():
        return ""

    raw = strip_html_comments(notes_path.read_text(encoding="utf-8", errors="replace"))
    lines = raw.splitlines()

    version_sections: list[tuple[str, list[str]]] = []
    current_version: str | None = None
    current_lines: list[str] = []

    for line in lines:
        m = VERSION_HEADER_RE.match(line.strip())
        if m and looks_like_version_token(m.group(1)):
            if current_version is not None:
                version_sections.append((current_version, current_lines))
            current_version = normalize_version_key(m.group(1))
            current_lines = []
        elif current_version is not None:
            current_lines.append(line)

    if current_version is not None:
        version_sections.append((current_version, current_lines))

    if version_sections:
        candidates = build_version_candidates(tag, app_version)
        for ver_key, sec_lines in version_sections:
            if ver_key in candidates or ver_key.lstrip("v") in candidates:
                return clean_markdown_details("\n".join(sec_lines))
        if file_changed_for_release(notes_path.name, prev_tag):
            for ver_key, sec_lines in version_sections:
                if ver_key == "unreleased":
                    return clean_markdown_details("\n".join(sec_lines))
        return ""

    # Unversioned RELEASE_NOTES.md: only use if updated for this release
    if file_changed_for_release(notes_path.name, prev_tag):
        return clean_markdown_details(raw)
    return ""


def is_infra_only_commit(sha: str) -> bool:
    changed = run_git("diff-tree", "--no-commit-id", "--name-only", "-r", sha)
    if not changed:
        return False
    files = [f.strip() for f in changed.splitlines() if f.strip()]
    if not files:
        return False
    return all(
        f.startswith(INFRA_ONLY_PREFIXES) or f in INFRA_ONLY_FILES for f in files
    )


def format_commit_bullet(text: str) -> str:
    cleaned = text.strip()
    if not cleaned:
        return ""
    return cleaned[0].upper() + cleaned[1:]


def classify_commit(subject: str) -> tuple[str, str] | None:
    s = subject.strip()
    if not s or SKIP_COMMIT_RE.match(s):
        return None

    m = CONVENTIONAL_RE.match(s)
    if m:
        ctype = m.group("type").lower()
        desc = format_commit_bullet(m.group("desc"))
        if ctype in ("fix", "bugfix", "hotfix"):
            return ("Fixes", desc)
        if ctype in ("feat", "feature"):
            return ("New Features", desc)
        if ctype in ("improve", "perf", "refactor", "style", "ui", "ux", "polish", "enhance"):
            return ("Improvements", desc)
        return None

    low = s.lower()
    if re.search(r"\b(fix|fixed|fixes|fixing|bug|hotfix|crash|crashes|crashed|resolve|resolved|patch)\b", low):
        return ("Fixes", format_commit_bullet(s))
    if re.search(r"\b(feat|feature|features|add|added|adds|implement|implemented|support\s+for)\b", low):
        return ("New Features", format_commit_bullet(s))
    if re.search(
        r"\b(improve|improved|improves|improvement|optimize|optimized|enhance|enhanced|"
        r"perf|performance|compress|compressed|refactor|update|updated|ui|theme|"
        r"layout|padding|icon|color|tint|cache)\b",
        low,
    ):
        return ("Improvements", format_commit_bullet(s))
    return None


def extract_commit_notes(prev_tag: str | None) -> str:
    if prev_tag:
        raw_log = run_git("log", f"{prev_tag}..HEAD", "--no-merges", "--format=%H\x1f%s")
    else:
        raw_log = run_git("log", "-n", "1", "--no-merges", "--format=%H\x1f%s", "HEAD")

    if not raw_log:
        return ""

    buckets: dict[str, list[str]] = {
        "New Features": [],
        "Improvements": [],
        "Fixes": [],
    }
    seen: set[str] = set()

    for entry in raw_log.splitlines():
        if "\x1f" not in entry:
            continue
        sha, subject = entry.split("\x1f", 1)
        sha = sha.strip()
        subject = subject.strip()
        if not sha or not subject:
            continue
        if is_infra_only_commit(sha):
            continue
        classified = classify_commit(subject)
        if not classified:
            continue
        category, text = classified
        dedupe_key = f"{category}:{text.lower()}"
        if dedupe_key in seen:
            continue
        seen.add(dedupe_key)
        buckets[category].append(f"- {text}")

    sections: list[str] = []
    for category in ("New Features", "Improvements", "Fixes"):
        items = buckets[category]
        if items:
            sections.append(f"## {category}\n\n" + "\n".join(items))

    return "\n\n".join(sections).strip()


def format_size(num_bytes: int) -> str:
    if num_bytes >= 1024 * 1024:
        return f"{num_bytes / (1024 * 1024):.1f} MB"
    if num_bytes >= 1024:
        return f"{num_bytes / 1024:.1f} KB"
    return f"{num_bytes} B"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def abi_description(filename: str) -> str:
    if "armeabi-v7a" in filename:
        return "ARMv7 (32-bit devices)"
    if "arm64-v8a" in filename:
        return "ARMv8 (64-bit modern devices, recommended)"
    if "universal" in filename:
        return "Universal (ARMv7 + ARMv8)"
    return "Android APK"


def main() -> int:
    tag = os.environ.get("TAG", "").strip()
    release_title = os.environ.get("RELEASE_TITLE", f"D0C View {tag}").strip()
    channel_label = os.environ.get("CHANNEL_LABEL", "Stable Release").strip()
    channel = os.environ.get("CHANNEL", "stable").strip().lower()
    app_version = os.environ.get("APP_VERSION", "").strip()
    version_code = os.environ.get("VERSION_CODE", "").strip()
    short_sha = os.environ.get("SHORT_SHA", "")[:7]
    input_notes = os.environ.get("INPUT_NOTES", "")
    apk_dir = Path(os.environ.get("APK_DIR", "release-apks"))
    output_file = Path(os.environ.get("OUTPUT_FILE", "release-notes-body.md"))
    downloads_heading = os.environ.get("DOWNLOADS_HEADING", "APK Downloads & Checksums").strip()
    show_abi_tip = os.environ.get("SHOW_ABI_TIP", "false").strip().lower() == "true"

    prev_tag = find_previous_channel_tag(tag, channel)

    details = ""
    if input_notes.strip():
        details = clean_markdown_details(input_notes)
    if not details:
        details = extract_release_notes_file(Path("RELEASE_NOTES.md"), tag, app_version, prev_tag)
    if not details:
        details = extract_commit_notes(prev_tag)

    lines: list[str] = [
        f"## {release_title}",
        "",
        f"- **Release Channel:** {channel_label}",
        f"- **App Version:** `{app_version}` (Version Code: `{version_code}`)",
        f"- **Commit:** `{short_sha}`",
    ]

    if details:
        lines.extend(["", details])

    apk_files = sorted(apk_dir.glob("*.apk"))
    if apk_files:
        lines.extend(
            [
                "",
                f"## {downloads_heading}",
                "",
                "| APK File | Target ABI | Size | SHA-256 |",
                "| --- | --- | --- | --- |",
            ]
        )
        for apk in apk_files:
            fname = apk.name
            fsize = format_size(apk.stat().st_size)
            fsha = sha256_file(apk)
            abi = abi_description(fname)
            lines.append(f"| `{fname}` | {abi} | {fsize} | `{fsha}` |")

        if show_abi_tip:
            lines.extend(
                [
                    "",
                    "> **Which APK should I install?** Choose **arm64-v8a** for most modern Android phones, **armeabi-v7a** for older 32-bit devices, or **universal** if unsure.",
                ]
            )

    output_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
