#!/usr/bin/env python3
"""Builds and signs the engine update manifest.

  python3 tools/sign_manifest.py --key ota-private.pem --sequence 12 --version "NewPipe Extractor v0.26.5" \
      --engine out/engine-12.jar --asset potoken.html=out/potoken-12.html --asset inject.js=out/inject-12.js \
      --out out/

Writes manifest.json and manifest.json.sig (Base64 DER ECDSA-SHA256 over the exact manifest bytes)
into --out. Upload manifest.json, manifest.json.sig and every referenced file to the same folder.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ENGINE_API = 3


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--key", required=True)
    p.add_argument("--sequence", type=int, required=True)
    p.add_argument("--version", required=True)
    p.add_argument("--engine", required=True)
    p.add_argument("--asset", action="append", default=[], help="name=path, e.g. potoken.html=out/potoken-12.html")
    p.add_argument("--notes", default="")
    p.add_argument("--out", required=True)
    a = p.parse_args()

    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    engine = Path(a.engine)
    manifest = {
        "schema": 1,
        "sequence": a.sequence,
        "engineApi": ENGINE_API,
        "engine": {"version": a.version, "file": engine.name, "sha256": sha256(engine)},
        "assets": [],
        "notes": a.notes,
    }
    for item in a.asset:
        name, _, path = item.partition("=")
        f = Path(path)
        manifest["assets"].append({"name": name, "file": f.name, "sha256": sha256(f)})

    body = json.dumps(manifest, indent=2, sort_keys=True).encode("utf-8")
    key = serialization.load_pem_private_key(Path(a.key).read_bytes(), password=None)
    signature = key.sign(body, ec.ECDSA(hashes.SHA256()))  # DER encoded, what Java's SHA256withECDSA expects
    (out / "manifest.json").write_bytes(body)
    (out / "manifest.json.sig").write_text(base64.b64encode(signature).decode() + "\n")
    print(f"Signed manifest #{a.sequence} -> {out}/manifest.json(.sig)")


if __name__ == "__main__":
    main()
