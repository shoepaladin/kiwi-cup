#!/usr/bin/env python3
"""
Package the hzd-machines-wf project into a zip suitable for CloudPebble import.

Usage:
  cd pebble/hzd-machines-wf
  python tools/create_zip.py

Produces: hzd-machines-wf.zip in the project root.

CloudPebble expects the zip to contain the project files at the TOP LEVEL
(not inside a subdirectory), so we strip the leading path when archiving.
"""

import os, zipfile

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT_ZIP      = os.path.join(PROJECT_ROOT, '..', '..', 'releases', 'hzd-machines-wf.zip')

INCLUDE_EXTS = {'.c', '.json', '.js', '.png', '.ttf', '.bmp', '.h'}
INCLUDE_FILES = {'wscript'}  # exact filename matches (no extension required)
EXCLUDE_DIRS  = {'tools', '.git', '__pycache__'}


def should_include(rel_path):
    parts = rel_path.replace('\\', '/').split('/')
    if any(p in EXCLUDE_DIRS for p in parts):
        return False
    name = os.path.basename(rel_path)
    _, ext = os.path.splitext(name)
    return ext.lower() in INCLUDE_EXTS or name in INCLUDE_FILES


def main():
    os.makedirs(os.path.dirname(OUT_ZIP), exist_ok=True)
    added = []
    with zipfile.ZipFile(OUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as zf:
        for dirpath, dirnames, filenames in os.walk(PROJECT_ROOT):
            # Prune excluded dirs in-place so os.walk skips them
            dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]
            for fname in filenames:
                full = os.path.join(dirpath, fname)
                rel  = os.path.relpath(full, PROJECT_ROOT)
                if should_include(rel):
                    zf.write(full, rel)
                    added.append(rel)

    print(f"Created: {OUT_ZIP}")
    print(f"Included {len(added)} files:")
    for f in sorted(added):
        print(f"  {f}")


if __name__ == '__main__':
    main()
