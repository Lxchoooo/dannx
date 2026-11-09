#!/usr/bin/env python3
"""Genera las notas de release desde la sección 'Sin publicar' del CHANGELOG."""

from pathlib import Path

CHANGELOG = Path("CHANGELOG.md")
FALLBACK = (
    "Compilado y firmado por GitHub Actions. "
    "Ver CHANGELOG.md para el detalle."
)


def main() -> int:
    if not CHANGELOG.exists():
        print(FALLBACK)
        return 0

    lines = CHANGELOG.read_text(encoding="utf-8").splitlines()

    start = None
    for index, line in enumerate(lines):
        if line.strip().lower() == "## sin publicar":
            start = index + 1
            break

    if start is None:
        print(FALLBACK)
        return 0

    end = len(lines)
    for index in range(start, len(lines)):
        if lines[index].startswith("## "):
            end = index
            break

    notes = "\n".join(lines[start:end]).strip()
    print(notes or FALLBACK)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
