#!/usr/bin/env python3
"""Deterministic generator of the committed test-file set (LLD Test & benchmark harness 4.11, SIDX-72).

Usage:
  python3 testdata/scripts/make_testdata.py           # regenerate files in testdata/ and rewrite MANIFEST.tsv
  python3 testdata/scripts/make_testdata.py --check   # regenerate into a temp dir, compare SHA-256 with MANIFEST.tsv

All content is project-authored text; fixed timestamps, no random data (os.urandom is replaced by a seeded
byte stream while generating, so the encrypted files are reproducible too).

Legacy .doc/.ppt are converted from the generated .docx/.pptx with `soffice --headless --convert-to`.
If soffice is not on PATH a minimal OLE2 (CFB) stub with the right magic bytes is written instead. Both the
converted files and the stubs are excluded from --check (LibreOffice output is not byte-stable).
"""
import argparse
import hashlib
import io
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # testdata/
FIXED_DT = datetime(2024, 1, 1, 12, 0, 0)
ZIP_DT = (2024, 1, 1, 12, 0, 0)
PASSWORD = "sidx"
LEGACY = {"docs/sample.doc", "docs/sample.ppt"}  # excluded from --check
DIRS = ["docs", "other", "bad", "us002", "i18n"]

# (path, expected_type, purpose) -- order irrelevant, MANIFEST is sorted by path.
META = {
    "docs/sample.pdf": ("DOCUMENT", "FR-002 classification; Open with PDF"),
    "docs/sample.doc": ("DOCUMENT", "FR-002 classification; Open with DOC"),
    "docs/sample.docx": ("DOCUMENT", "FR-002 classification; Open with DOCX"),
    "docs/sample.xls": ("DOCUMENT", "FR-002 classification; Open with XLS"),
    "docs/sample.xlsx": ("DOCUMENT", "FR-002 classification; Open with XLSX"),
    "docs/sample.ppt": ("DOCUMENT", "FR-002 classification; Open with PPT"),
    "docs/sample.pptx": ("DOCUMENT", "FR-002 classification; Open with PPTX"),
    "docs/sample.txt": ("DOCUMENT", "FR-002 classification; Open with TXT"),
    "other/sample.zip": ("OTHER_FILE", "FR-003 other file"),
    "other/sample.apk": ("OTHER_FILE", "FR-003 other file (zip with empty AndroidManifest.xml, not installable)"),
    "other/sample.json": ("OTHER_FILE", "FR-003 other file"),
    "other/sample.xyz": ("OTHER_FILE", "FR-003 other file (unknown extension)"),
    "other/noextension": ("OTHER_FILE", "FR-003 other file (no extension)"),
    "other/sample.csv": ("DOCUMENT", "FR-002 csv is in DOCUMENT_EXTS (media LLD 4.2), not OTHER_FILE"),
    "bad/corrupt.pdf": ("DOCUMENT", "NFR-010 corrupt PDF, no xref/EOF"),
    "bad/password-protected.pdf": ("DOCUMENT", "NFR-010 AES-128 PDF, password sidx"),
    "bad/password-protected.docx": ("DOCUMENT", "NFR-010 agile-encrypted DOCX, password sidx"),
    "bad/zero-byte.pdf": ("DOCUMENT", "NFR-010 zero bytes"),
    "bad/photo-renamed.pdf": ("DOCUMENT", "NFR-010 wrong extension: PNG bytes named .pdf (DOCUMENT by extension/MIME)"),
    "bad/document-renamed.jpg": ("PHOTO", "NFR-010 wrong extension: PDF bytes named .jpg (thumbnail fails, type tile)"),
    "us002/Hotel booking.pdf": ("DOCUMENT", "US-002 found by name match 'booking'; body is 'Itinerary' only"),
    "us002/scan_0042.pdf": ("DOCUMENT", "US-002 negative / TC-SHELL-006: body has 'booking reference', name does not match"),
    "i18n/होटल बुकिंग.pdf": ("DOCUMENT", "NFR-009 Devanagari name"),
    "i18n/मराठी नोंदी.txt": ("DOCUMENT", "NFR-009 Devanagari name"),
    "i18n/पावती.docx": ("DOCUMENT", "NFR-009 Devanagari name"),
}


# ---------------------------------------------------------------- helpers
def label(ext):
    return "Smart Index test file (%s)" % ext


class SeededUrandom:
    """Replaces os.urandom while generating, so encryption salts/IVs are reproducible."""

    def __init__(self):
        self.rng = random.Random(20240101)
        self.orig = os.urandom

    def __enter__(self):
        import secrets
        self.orig_tb = secrets.token_bytes
        os.urandom = secrets.token_bytes = lambda n=32: bytes(self.rng.getrandbits(8) for _ in range(n))

    def __exit__(self, *a):
        import secrets
        os.urandom = self.orig
        secrets.token_bytes = self.orig_tb


def normalize_zip(data, fix_modified=False):
    """Rewrite a zip with fixed timestamps, sorted-as-written order kept, deflate level 9."""
    out = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zout:
        for info in zin.infolist():
            zi = zipfile.ZipInfo(info.filename, ZIP_DT)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zi.create_system = 3
            content = zin.read(info.filename)
            if fix_modified and info.filename == "docProps/core.xml":  # openpyxl stamps "now" on save
                import re
                content = re.sub(rb"(<dcterms:modified[^>]*>)[^<]*", rb"\g<1>2024-01-01T12:00:00Z", content)
            zout.writestr(zi, content)
    return out.getvalue()


def make_zip(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in entries:
            zi = zipfile.ZipInfo(name, ZIP_DT)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zi.create_system = 3
            z.writestr(zi, data)
    return out.getvalue()


def pdf_bytes(lines, title="Smart Index test file"):
    """Minimal uncompressed, valid single-page PDF (so body text is greppable in the raw bytes)."""
    def esc(s):
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    text = "BT /F1 18 Tf 72 720 Td 22 TL\n" + "".join("(%s) Tj T*\n" % esc(l) for l in lines) + "ET\n"
    stream = text.encode("latin-1", "replace")
    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"endstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Title (%s) /Producer (make_testdata.py) /CreationDate (D:20240101120000Z) >>" % esc(title).encode("latin-1", "replace"),
    ]
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + o + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    for off in offsets:
        out += b"%010d 00000 n \n" % off
    out += b"trailer\n<< /Size %d /Root 1 0 R /Info 6 0 R /ID [<00112233445566778899aabbccddeeff> <00112233445566778899aabbccddeeff>] >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    return bytes(out)


def png_bytes():
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + b"".join(bytes((x * 8 % 256, y * 8 % 256, 128)) for x in range(32)) for y in range(32))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def cfb_stub(stream_name, payload):
    """Minimal OLE2 compound file (v3, 512-byte sectors) with one >=4096-byte stream."""
    END, FREE, FATSECT = 0xFFFFFFFE, 0xFFFFFFFF, 0xFFFFFFFD
    data = payload.ljust(4096, b"\0")
    n = len(data) // 512
    hdr = bytearray(512)
    hdr[0:8] = bytes.fromhex("D0CF11E0A1B11AE1")
    struct.pack_into("<HHHHH", hdr, 24, 0x3E, 3, 0xFFFE, 9, 6)
    struct.pack_into("<IIIIIIIII", hdr, 40, 0, 1, 1, 0, 4096, END, 0, END, 0)
    struct.pack_into("<I", hdr, 76, 0)
    for i in range(1, 109):
        struct.pack_into("<I", hdr, 76 + 4 * i, FREE)
    fat = [FATSECT, END] + [3 + i for i in range(n - 1)] + [END]
    fat += [FREE] * (128 - len(fat))
    fat_sec = struct.pack("<128I", *fat)

    def entry(name, typ, left, right, child, start, size):
        e = bytearray(128)
        if name:
            nm = (name + "\0").encode("utf-16-le")
            e[0:len(nm)] = nm
            struct.pack_into("<H", e, 64, len(nm))
        e[66] = typ
        e[67] = 1
        struct.pack_into("<III", e, 68, left, right, child)
        struct.pack_into("<I", e, 116, start)
        struct.pack_into("<Q", e, 120, size)
        return bytes(e)

    d = (entry("Root Entry", 5, FREE, FREE, 1, END, 0)
         + entry(stream_name, 2, FREE, FREE, FREE, 2, len(data))
         + entry("", 0, FREE, FREE, FREE, 0, 0) + entry("", 0, FREE, FREE, FREE, 0, 0))
    return bytes(hdr) + fat_sec + d + data


# ---------------------------------------------------------------- generators
def gen_docx(text, fixed=True):
    import docx
    d = docx.Document()
    d.add_paragraph(text)
    cp = d.core_properties
    cp.author = cp.last_modified_by = "Smart Index testdata"
    cp.title = "Smart Index test file"
    cp.created = cp.modified = FIXED_DT
    cp.revision = 1
    b = io.BytesIO()
    d.save(b)
    return normalize_zip(b.getvalue())


def gen_xlsx(text):
    import openpyxl
    wb = openpyxl.Workbook()
    wb.active["A1"] = text
    wb.properties.creator = wb.properties.lastModifiedBy = "Smart Index testdata"
    wb.properties.created = wb.properties.modified = FIXED_DT
    b = io.BytesIO()
    wb.save(b)
    return normalize_zip(b.getvalue(), fix_modified=True)


def gen_pptx(text):
    import pptx
    p = pptx.Presentation()
    s = p.slides.add_slide(p.slide_layouts[0])
    s.shapes.title.text = text
    cp = p.core_properties
    cp.author = cp.last_modified_by = "Smart Index testdata"
    cp.created = cp.modified = FIXED_DT
    cp.revision = 1
    b = io.BytesIO()
    p.save(b)
    return normalize_zip(b.getvalue())


def gen_xls(text):
    import xlwt
    wb = xlwt.Workbook(encoding="utf-8")
    wb.add_sheet("Sheet1").write(0, 0, text)
    b = io.BytesIO()
    wb.save(b)
    return b.getvalue()


def soffice_convert(src_bytes, src_name, fmt):
    exe = shutil.which("soffice") or shutil.which("libreoffice")
    if not exe:
        return None
    with tempfile.TemporaryDirectory() as t:
        src = Path(t) / src_name
        src.write_bytes(src_bytes)
        out = Path(t) / "out"
        subprocess.run([exe, "--headless", "--convert-to", fmt, "--outdir", str(out), str(src)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
        return (out / (src.stem + "." + fmt)).read_bytes()


def gen_encrypted_pdf(base):
    import pypdf
    r = pypdf.PdfReader(io.BytesIO(base))
    w = pypdf.PdfWriter()
    w.append(r)
    w.encrypt(user_password=PASSWORD, owner_password=PASSWORD, algorithm="AES-128")
    b = io.BytesIO()
    w.write(b)
    return b.getvalue()


def gen_encrypted_docx(plain):
    import msoffcrypto
    f = msoffcrypto.OfficeFile(io.BytesIO(plain))
    out = io.BytesIO()
    f.encrypt(PASSWORD, out) if hasattr(f, "encrypt") else msoffcrypto.format.ooxml.OOXMLFile(io.BytesIO(plain)).encrypt(PASSWORD, out)
    return zero_cfb_timestamps(out.getvalue())


def zero_cfb_timestamps(data):
    """msoffcrypto stamps 'now' into the CFB directory entries; zero them for reproducibility."""
    import olefile
    ole = olefile.OleFileIO(io.BytesIO(data))
    ss, sect, fat = ole.sectorsize, ole.first_dir_sector, ole.fat
    buf = bytearray(data)
    while sect != olefile.ENDOFCHAIN:
        base = (sect + 1) * ss
        for e in range(ss // 128):
            buf[base + e * 128 + 100: base + e * 128 + 116] = bytes(16)
        sect = fat[sect]
    ole.close()
    return bytes(buf)


def generate(dest):
    """Writes every file under dest (a testdata/-like root). Returns the list of relative paths."""
    files = {}
    with SeededUrandom():
        docx = gen_docx(label("docx"))
        pptx = gen_pptx(label("pptx"))
        files["docs/sample.pdf"] = pdf_bytes([label("pdf")])
        files["docs/sample.docx"] = docx
        files["docs/sample.xlsx"] = gen_xlsx(label("xlsx"))
        files["docs/sample.xls"] = gen_xls(label("xls"))
        files["docs/sample.pptx"] = pptx
        files["docs/sample.txt"] = (label("txt") + "\n").encode()
        files["docs/sample.doc"] = (soffice_convert(docx, "sample.docx", "doc")
                                    or cfb_stub("WordDocument", b"\xec\xa5\xc1\x00" + label("doc").encode()))
        files["docs/sample.ppt"] = (soffice_convert(pptx, "sample.pptx", "ppt")
                                    or cfb_stub("PowerPoint Document", label("ppt").encode()))

        files["other/sample.zip"] = make_zip([("readme.txt", b"Smart Index test file (zip)\n")])
        files["other/sample.apk"] = make_zip([("AndroidManifest.xml", b"")])
        files["other/sample.json"] = b'{"name": "Smart Index test file", "type": "json"}\n'
        files["other/sample.xyz"] = b"Smart Index test file (xyz)\n"
        files["other/noextension"] = b"Smart Index test file (no extension)\n"
        files["other/sample.csv"] = b"id,name,value\n1,alpha,10\n2,beta,20\n3,gamma,30\n"

        files["bad/corrupt.pdf"] = b"%PDF-1.4\n" + bytes((i * 31 + 7) % 256 for i in range(2048))
        files["bad/password-protected.pdf"] = gen_encrypted_pdf(pdf_bytes([label("encrypted pdf")]))
        files["bad/password-protected.docx"] = gen_encrypted_docx(gen_docx(label("encrypted docx")))
        files["bad/zero-byte.pdf"] = b""
        files["bad/photo-renamed.pdf"] = png_bytes()
        files["bad/document-renamed.jpg"] = pdf_bytes([label("pdf named jpg")])

        files["us002/Hotel booking.pdf"] = pdf_bytes(["Itinerary"], title="Itinerary")
        files["us002/scan_0042.pdf"] = pdf_bytes(["Your booking reference is SIDX-4711"], title="Scan")

        files["i18n/होटल बुकिंग.pdf"] = pdf_bytes([label("devanagari pdf")])
        files["i18n/मराठी नोंदी.txt"] = (
            "मराठी नोंदी - " + label("txt") + "\n").encode("utf-8")
        files["i18n/पावती.docx"] = gen_docx(label("devanagari docx"))

    assert set(files) == set(META), set(files) ^ set(META)
    for rel, data in files.items():
        p = Path(dest) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        assert len(data) < 100 * 1024, rel
    return sorted(files)


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def manifest_text(root, rels):
    rows = ["path\tbytes\tsha256\texpected_type\tpurpose"]
    for rel in sorted(rels, key=lambda s: s.encode("utf-8")):
        p = Path(root) / rel
        rows.append("%s\t%d\t%s\t%s\t%s" % (rel, p.stat().st_size, sha(p), META[rel][0], META[rel][1]))
    return "\n".join(rows) + "\n"


def read_manifest():
    lines = (ROOT / "MANIFEST.tsv").read_text(encoding="utf-8").splitlines()
    assert lines[0] == "path\tbytes\tsha256\texpected_type\tpurpose"
    return {l.split("\t")[0]: l.split("\t") for l in lines[1:] if l}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    if a.check:
        man = read_manifest()
        with tempfile.TemporaryDirectory() as t:
            rels = generate(t)
            bad = []
            if set(rels) != set(man):
                bad.append("path set differs: %s" % sorted(set(rels) ^ set(man)))
            for rel in rels:
                if rel in LEGACY:
                    continue
                if rel in man and sha(Path(t) / rel) != man[rel][2]:
                    bad.append("sha256 differs: " + rel)
        if bad:
            print("\n".join(bad))
            return 1
        print("OK: %d files regenerated, %d legacy files (LibreOffice) excluded" % (len(rels), len(LEGACY)))
        return 0
    for d in DIRS:
        shutil.rmtree(ROOT / d, ignore_errors=True)
    rels = generate(ROOT)
    (ROOT / "MANIFEST.tsv").write_text(manifest_text(ROOT, rels), encoding="utf-8", newline="\n")
    print("wrote %d files and MANIFEST.tsv" % len(rels))
    return 0


if __name__ == "__main__":
    sys.exit(main())
