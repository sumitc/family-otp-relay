#!/usr/bin/env python3
"""Patch versionName/versionCode in a binary AndroidManifest.xml (AXML).

Usage: patch-manifest.py <in.bin> <out.bin> <newVersionName> <newVersionCode>

The version name must have the same UTF-16 byte length as the existing one;
otherwise the string-pool offsets would shift. In that case, regenerate the
manifest from source with aapt2/Gradle instead.
"""
import struct
import sys


def u16(d, o):
    return struct.unpack_from('<H', d, o)[0]


def u32(d, o):
    return struct.unpack_from('<I', d, o)[0]


def parse(d):
    """Return (pool strings, pool char offsets, manifest node info)."""
    pool, positions = {}, {}
    off = 8  # skip RES_XML_TYPE root header
    while off + 8 <= len(d):
        typ, hs, size = u16(d, off), u16(d, off + 2), u32(d, off + 4)
        if size < 8:
            break
        if typ == 0x0001:  # RES_STRING_POOL_TYPE
            count, _, _, strings_start, _ = struct.unpack_from('<IIIII', d, off + 8)
            for i in range(count):
                pos = off + strings_start + u32(d, off + hs + 4 * i)
                length = u16(d, pos)
                start = pos + 2
                pool[i] = d[start:start + length * 2].decode('utf-16-le', 'replace')
                positions[i] = start
        if typ == 0x0102:  # RES_XML_START_ELEMENT_TYPE
            name = u32(d, off + 20)
            if pool.get(name) == 'manifest':
                attr_start, attr_size, count = struct.unpack_from('<HHH', d, off + 24)
                attrs = []
                ap = off + 16 + attr_start
                for _ in range(count):
                    name_idx, raw_idx = u32(d, ap + 4), u32(d, ap + 8)
                    label = pool.get(name_idx) or pool.get(raw_idx)
                    data_type = d[ap + 15]
                    data = u32(d, ap + 16)
                    attrs.append((label, data_type, data, ap + 16))
                    ap += attr_size
                return pool, positions, attrs
        off += size
    raise ValueError('manifest node not found')


def main():
    src, dst, new_name, new_code = sys.argv[1:]
    new_code = int(new_code)
    d = bytearray(open(src, 'rb').read())
    pool, positions, attrs = parse(d)

    name_attr = next((a for a in attrs if a[0] == 'versionName' and a[1] == 0x03), None)
    code_attr = next((a for a in attrs if a[0] == 'versionCode' and a[1] == 0x10), None)
    if not name_attr or not code_attr:
        raise ValueError('versionName/versionCode attributes not found')
    if new_code <= code_attr[2]:
        raise ValueError(
            f'versionCode must increase for Play Store: '
            f'{new_code} <= {code_attr[2]}')

    idx = name_attr[2]
    old_name = pool[idx]
    old_bytes = len(new_name.encode('utf-16-le'))
    current_bytes = len(old_name.encode('utf-16-le'))
    if old_bytes != current_bytes:
        raise ValueError(
            f'versionName length must stay {current_bytes} bytes '
            f'(got {old_bytes}); regenerate the manifest from source')

    start = positions[idx]
    d[start:start + current_bytes] = new_name.encode('utf-16-le')
    struct.pack_into('<I', d, code_attr[3], new_code)
    open(dst, 'wb').write(d)

    after_pool, _, after = parse(d)
    got_name = next(a[2] for a in after if a[0] == 'versionName')
    got_code = next(a[2] for a in after if a[0] == 'versionCode')
    assert after_pool[got_name] == new_name and got_code == new_code
    print(f'Manifest patched: {old_name} ({code_attr[2]}) -> {new_name} ({new_code})')


if __name__ == '__main__':
    main()
