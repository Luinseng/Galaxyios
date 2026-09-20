#!/usr/bin/env python3
"""gearan-doctor: optional dev helper (no privileged APIs).
Checks ADB presence, lists devices, prints public build/battery info.
Usage: python android/gearan_doctor.py
"""
import shutil
import subprocess
import sys

def run(*args):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=15)
    except Exception as e:  # noqa: BLE001
        print(f"ERR {' '.join(args)}: {e}")
        return None

def main():
    if shutil.which("adb") is None:
        print("ADB not found. Install Android Platform-Tools first.")
        return 1
    r = run("adb", "devices", "-l")
    print(r.stdout if r else "(no adb output)")
    r = run("adb", "shell", "getprop", "ro.build.version.release")
    if r and r.stdout.strip():
        print("device android release:", r.stdout.strip())
    r = run("adb", "shell", "dumpsys", "battery")
    if r and r.stdout.strip():
        print("--- battery (truncated) ---")
        print("\n".join(r.stdout.splitlines()[:15]))
    print("NOTE: Gearan never bypasses provisioning; this tool only reads public info.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
