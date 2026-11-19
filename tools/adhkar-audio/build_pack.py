#!/usr/bin/env python3
"""Builds the encrypted Adhkar Audio pack the app downloads once (see core AdhkarAudioPack).

    python3 tools/adhkar-audio/build_pack.py --audio-root <folder with the recordings> --key-file <32-byte key, hex>

For each recording in recordings.tsv it:
  1. re-encodes the source to mono MP3 at 64 kbps with even loudness (ffmpeg; speech needs no more),
  2. encrypts it with AES-256-GCM under the pack key, the recording's name as associated data,
  3. writes it to site/a/v1/<opaque name>.bin (the name is an HMAC of the recording, so it says nothing),
then writes app/src/main/assets/adhkar-audio.tsv: the base URL, each file's size and SHA-256, and which recording
reads which dhikr.

The recordings and the key never go in this public repo; only the encrypted files and the asset do. The key lives
next to the signing key (outside the repo) and reaches release builds as mawaqeet.adhkarAudioKeyFile. Nonces are
random, so each run changes every file: run it only when a recording changes, and commit the site files and the
asset together. Needs ffmpeg and the `cryptography` package.
"""
import argparse
import hashlib
import hmac
import os
import subprocess
import sys
import tempfile

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
VERSION = "v1"
BASE = f"https://mawaqeet.nileorbit.com/a/{VERSION}/"


def read_recordings():
    rows = []
    with open(os.path.join(HERE, "recordings.tsv"), encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            recording, source, adhkar = line.rstrip("\n").split("\t")
            rows.append((recording, source, [a.split("/", 1) for a in adhkar.split(",")]))
    return rows


def encode(source):
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "out.mp3")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", source, "-map_metadata", "-1", "-vn",
             "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-ac", "1", "-ar", "44100", "-b:a", "64k", out],
            check=True,
        )
        with open(out, "rb") as f:
            return f.read()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--audio-root", required=True)
    p.add_argument("--key-file", required=True)
    args = p.parse_args()
    key = bytes.fromhex(open(args.key_file).read().strip())
    if len(key) != 32:
        sys.exit("The key file must hold 32 bytes as hex")
    out_dir = os.path.join(REPO, "site", "a", VERSION)
    os.makedirs(out_dir, exist_ok=True)
    for old in os.listdir(out_dir):
        os.remove(os.path.join(out_dir, old))

    aes = AESGCM(key)
    files, readers = [], []
    for recording, source, adhkar in read_recordings():
        plain = encode(os.path.join(args.audio_root, source))
        nonce = os.urandom(12)
        blob = nonce + aes.encrypt(nonce, plain, recording.encode("utf-8"))
        name = hmac.new(key, recording.encode("utf-8"), hashlib.sha256).hexdigest()[:20] + ".bin"
        with open(os.path.join(out_dir, name), "wb") as f:
            f.write(blob)
        files.append((recording, name, len(blob), hashlib.sha256(blob).hexdigest()))
        readers += [(s, d, recording) for s, d in adhkar]
        print(f"{recording}: {len(plain)} -> {len(blob)} bytes", file=sys.stderr)

    lines = [
        "# mawaqeet-adhkar-audio-v1 Written by tools/adhkar-audio/build_pack.py; do not edit by hand.",
        "# file: recording, file on the site, encrypted bytes, sha256 of the encrypted file. dhikr: set, dhikr id, recording.",
        f"base\t{BASE}",
    ]
    lines += [f"file\t{r}\t{n}\t{b}\t{h}" for r, n, b, h in files]
    lines += [f"dhikr\t{s}\t{d}\t{r}" for s, d, r in readers]
    with open(os.path.join(REPO, "app", "src", "main", "assets", "adhkar-audio.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    total = sum(b for _, _, b, _ in files)
    print(f"{len(files)} recordings, {total / 1e6:.2f} MB", file=sys.stderr)


if __name__ == "__main__":
    main()
