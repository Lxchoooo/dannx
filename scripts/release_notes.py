#!/usr/bin/env python3
"""Genera las notas públicas de una release.

Prioridad:
1. `docs/releases/v<versión>.md` (o `docs/releases/<tag>.md`).
2. La sección `## v<versión>` del CHANGELOG.
3. La release más nueva de `docs/releases/`, para builds sin tag.
"""

from pathlib import Path
import os
import re

CHANGELOG = Path("CHANGELOG.md")
RELEASES = Path("docs/releases")
FALLBACK = "See CHANGELOG.md for the full list of changes."


def release_file(version: str) -> Path | None:
    normalized = version.strip().lstrip("v")
    if normalized:
        for name in (f"v{normalized}.md", f"{normalized}.md"):
            path = RELEASES / name
            if path.is_file():
                return path
        return None
    if not RELEASES.is_dir():
        return None
    files = sorted(
        RELEASES.glob("v*.md"),
        key=version_key,
    )
    return files[-1] if files else None


def version_key(path: Path) -> tuple[int, int, int]:
    match = re.search(r"(\d+)\.(\d+)\.(\d+)", path.stem)
    if not match:
        return (0, 0, 0)
    return tuple(int(part) for part in match.groups())


def changelog_notes(version: str) -> str:
    if not CHANGELOG.exists():
        return ""
    targets = ["## sin publicar"]
    if version:
        targets.insert(0, f"## v{version.lstrip('v')}".lower())
    lines = CHANGELOG.read_text(encoding="utf-8").splitlines()
    start = None
    for index, line in enumerate(lines):
        if line.strip().lower() in targets:
            start = index + 1
            break
    if start is None:
        return ""
    end = len(lines)
    for index in range(start, len(lines)):
        if lines[index].startswith("## "):
            end = index
            break
    return "\n".join(lines[start:end]).strip()


def main() -> int:
    version = os.environ.get("VERSION_NAME", "").strip()
    path = release_file(version)
    if path is not None:
        print(path.read_text(encoding="utf-8").strip())
        return 0
    notes = changelog_notes(version)
    print(notes or FALLBACK)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
