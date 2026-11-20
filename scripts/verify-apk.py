#!/usr/bin/env python3
"""Structural smoke test for a freshly built APK.

Run by build-apk.sh before it reports success. Checks the archive, manifest,
version fields, launcher declaration and core DEX classes.
"""
import importlib.util
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('pm', ROOT / 'scripts' / 'patch-manifest.py')
pm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pm)


def main():
    apk, want_name, want_code = sys.argv[1], sys.argv[2], int(sys.argv[3])
    errors = []

    with zipfile.ZipFile(apk) as z:
        corrupt = z.testzip()
        if corrupt:
            errors.append(f'corrupt archive entry: {corrupt}')
        names = set(z.namelist())
        for required in ('AndroidManifest.xml', 'classes.dex', 'resources.arsc'):
            if required not in names:
                errors.append(f'missing {required}')
        manifest = z.read('AndroidManifest.xml')
        dex = z.read('classes.dex')

    try:
        pool, _, attrs = pm.parse(manifest)
    except Exception as e:
        errors.append(f'manifest parse failed: {e}')
    else:
        got_name = next((a[2] for a in attrs if a[0] == 'versionName'), None)
        got_code = next((a[2] for a in attrs if a[0] == 'versionCode'), None)
        if got_name is None or pool.get(got_name) != want_name:
            errors.append(f'versionName mismatch: {pool.get(got_name)} != {want_name}')
        if got_code != want_code:
            errors.append(f'versionCode mismatch: {got_code} != {want_code}')
        strings = set(pool.values())
        for required in (
            'com.familyrelay.local',
            'android.intent.action.MAIN',
            'android.intent.category.LAUNCHER',
            'MainActivity',
        ):
            if not any(required in s for s in strings):
                errors.append(f'manifest missing {required}')

    for cls in (
        'Lcom/familyrelay/local/MainActivity;',
        'Lcom/familyrelay/local/SmsReceiver;',
        'Lcom/familyrelay/local/RelayStore;',
        'Lcom/familyrelay/local/PhoneProfile;',
    ):
        if cls.encode() not in dex:
            errors.append(f'DEX missing {cls}')
    if len(dex) < 10000:
        errors.append(f'DEX suspiciously small: {len(dex)} bytes')

    if errors:
        print('APK VERIFICATION FAILED:')
        for error in errors:
            print(f'  - {error}')
        sys.exit(1)
    print(f'APK verified: launcher, package, {want_name}/{want_code}, core classes')


if __name__ == '__main__':
    main()
