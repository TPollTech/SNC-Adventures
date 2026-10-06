#!/usr/bin/env python3
"""Scan .mca region files for intoxicantes mod blocks (definitive diagnosis)."""
import io, os, struct, sys, zlib

def read_nbt(buf: io.BytesIO):
    """Minimal NBT reader -> (name, value). Values: dict/list/int/float/str/bytes."""
    t = buf.read(1)[0]
    if t == 0:
        return None, None
    nlen = struct.unpack(">H", buf.read(2))[0]
    name = buf.read(nlen).decode("utf-8", "replace")
    return name, read_payload(buf, t)

def read_payload(buf, t):
    if t == 1: return buf.read(1)[0]                      # byte
    if t == 2: return struct.unpack(">h", buf.read(2))[0] # short
    if t == 3: return struct.unpack(">i", buf.read(4))[0] # int
    if t == 4: return struct.unpack(">q", buf.read(8))[0] # long
    if t == 5: return struct.unpack(">f", buf.read(4))[0] # float
    if t == 6: return struct.unpack(">d", buf.read(8))[0] # double
    if t == 7:
        n = struct.unpack(">i", buf.read(4))[0]; return buf.read(n)
    if t == 8:
        n = struct.unpack(">H", buf.read(2))[0]; return buf.read(n).decode("utf-8", "replace")
    if t == 9:
        it = buf.read(1)[0]; n = struct.unpack(">i", buf.read(4))[0]
        return [read_payload(buf, it) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = buf.read(1)
            if not tt or tt[0] == 0: break
            nlen = struct.unpack(">H", buf.read(2))[0]
            nm = buf.read(nlen).decode("utf-8", "replace")
            d[nm] = read_payload(buf, tt[0])
        return d
    if t == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return [struct.unpack(">i", buf.read(4))[0] for _ in range(n)]
    if t == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return [struct.unpack(">q", buf.read(8))[0] for _ in range(n)]
    raise ValueError(f"tag {t}")

def unpack_states(data, palette_len, n=4096):
    bits = max(4, (palette_len - 1).bit_length())
    per = 64 // bits
    out, idx = [], 0
    for lv in data:
        u = lv & 0xFFFFFFFFFFFFFFFF
        for s in range(per):
            if idx >= n: return out
            out.append((u >> (s * bits)) & ((1 << bits) - 1))
            idx += 1
    return out

WANT = ("intoxicantes:poste_luz", "intoxicantes:placa_esquinao", "intoxicantes:hidrante", "intoxicantes:faixa_pedestre")

def scan_region(path):
    hits = []
    with open(path, "rb") as f:
        raw = f.read()
    for ci in range(1024):
        off = struct.unpack(">I", b"\x00" + raw[ci*4:ci*4+3])[0] * 4096
        if off == 0 or off + 5 > len(raw): continue
        ln = struct.unpack(">I", raw[off:off+4])[0]
        comp = raw[off+4]
        blob = raw[off+5:off+4+ln]
        try:
            data = zlib.decompress(blob) if comp == 2 else (zlib.decompress(blob, -15) if comp == 1 else blob)
        except Exception:
            continue
        try:
            _, root = read_nbt(io.BytesIO(data))
        except Exception:
            continue
        if not isinstance(root, dict): continue
        cx, cz = root.get("xPos", 0), root.get("zPos", 0)
        secs = root.get("sections", [])
        for sec in secs:
            if not isinstance(sec, dict): continue
            bs = sec.get("block_states")
            if not isinstance(bs, dict): continue
            pal = bs.get("palette", [])
            names = [p.get("Name", "") if isinstance(p, dict) else "" for p in pal]
            if not any(w in n for w in WANT for n in names): continue
            Y = sec.get("Y", 0)
            idxs = unpack_states(bs.get("data", []), len(pal))
            for i, pi in enumerate(idxs):
                if pi >= len(pal): continue
                nm = pal[pi].get("Name", "")
                if not any(w in nm for w in WANT): continue
                y = i & 63; z = (i >> 6) & 15; x = (i >> 12) & 15
                props = pal[pi].get("Properties", {})
                hits.append((cx*16+x, Y*16+y, cz*16+z, nm.split(":")[1], dict(props) if isinstance(props, dict) else {}))
    return hits

def main():
    base = sys.argv[1]  # e.g. "saves/Novo mundo (2)/dimensions/minecraft/overworld/region"
    for fn in sorted(os.listdir(base)):
        if not fn.endswith(".mca") or fn.startswith("r.-") or ".1.mca" in fn or ".0.mca" in fn:
            pass
        path = os.path.join(base, fn)
        hits = scan_region(path)
        mod = [h for h in hits if any(w in h[3] for w in ("poste", "placa", "hidrante"))]
        if mod:
            print(f"=== {fn}: {len(mod)} blocos do mod")
            for h in sorted(mod):
                print(f"  ({h[0]:4d},{h[1]:3d},{h[2]:4d}) {h[3]} {h[4]}")

if __name__ == "__main__":
    main()
