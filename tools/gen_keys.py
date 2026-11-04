#!/usr/bin/env python3
"""Creates the ECDSA P-256 key pair that signs engine updates.

  python3 tools/gen_keys.py

Writes ota-private.pem (KEEP SECRET, never commit it) and prints the public key to paste into
app/build.gradle.kts as resValue("string", "engine_public_key", "<value>").
"""
import base64
import sys
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

out = Path(sys.argv[1] if len(sys.argv) > 1 else "ota-private.pem")
if out.exists():
    sys.exit(f"{out} already exists; refusing to overwrite a signing key")

key = ec.generate_private_key(ec.SECP256R1())
out.write_bytes(key.private_bytes(
    serialization.Encoding.PEM,
    serialization.PrivateFormat.PKCS8,
    serialization.NoEncryption(),
))
out.chmod(0o600)
public = key.public_key().public_bytes(
    serialization.Encoding.DER,
    serialization.PublicFormat.SubjectPublicKeyInfo,
)
print(f"Private key written to {out} (keep it secret).")
print("Public key (X.509, Base64) for engine_public_key:")
print(base64.b64encode(public).decode())
