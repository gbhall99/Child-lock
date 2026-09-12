#!/usr/bin/env python3
"""Upload a signed bundle and the store listing to Play Console.

Usage:
  play-upload.py --key service-account.json --aab app-play-release.aab [--track internal] [--listing-only] [--bundle-only]

Needs: pip install google-api-python-client google-auth. The app must already
exist in Play Console (the API cannot create it), and the service account must
be invited under Users and permissions with the Release manager role.
Declarations, data safety and content rating stay manual in the Console.
"""
import argparse
import re
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.gbhall.childlock"
LANG = "en-GB"
ROOT = Path(__file__).resolve().parent.parent
LISTING = ROOT / "store" / "listing.md"
ASSETS = ROOT / "store" / "assets"


def listing_text():
    md = LISTING.read_text(encoding="utf-8")
    title = re.search(r"\*\*App name\*\*.*?: (.+)", md).group(1).strip()
    short = re.search(r"\*\*Short description\*\*.*?: (.+)", md).group(1).strip()
    full = md.split("## Full description (4000 max)", 1)[1].split("\n## ", 1)[0].strip()
    assert len(title) <= 30 and len(short) <= 80 and len(full) <= 4000, "listing limits"
    return title, short, full


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--aab")
    ap.add_argument("--track", default="internal")
    ap.add_argument("--listing-only", action="store_true")
    ap.add_argument("--bundle-only", action="store_true")
    args = ap.parse_args()
    if not args.listing_only and not args.aab:
        sys.exit("--aab is required unless --listing-only")

    creds = service_account.Credentials.from_service_account_file(
        args.key, scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = api.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    print("edit", edit_id)

    if not args.listing_only:
        media = MediaFileUpload(args.aab, mimetype="application/octet-stream", resumable=True)
        bundle = edits.bundles().upload(packageName=PACKAGE, editId=edit_id, media_body=media).execute()
        code = bundle["versionCode"]
        print("uploaded bundle, version code", code)
        edits.tracks().update(
            packageName=PACKAGE, editId=edit_id, track=args.track,
            body={"releases": [{"versionCodes": [str(code)], "status": "completed"}]},
        ).execute()
        print("track", args.track, "set to", code)

    if not args.bundle_only:
        title, short, full = listing_text()
        edits.listings().update(
            packageName=PACKAGE, editId=edit_id, language=LANG,
            body={"language": LANG, "title": title, "shortDescription": short, "fullDescription": full},
        ).execute()
        print("listing text set")
        images = edits.images()
        for kind, files in {
            "icon": ["icon-512.png"],
            "featureGraphic": ["feature-graphic.png"],
            "phoneScreenshots": sorted(p.name for p in ASSETS.glob("screenshot-*.png")),
        }.items():
            images.deleteall(packageName=PACKAGE, editId=edit_id, language=LANG, imageType=kind).execute()
            for name in files:
                images.upload(
                    packageName=PACKAGE, editId=edit_id, language=LANG, imageType=kind,
                    media_body=MediaFileUpload(str(ASSETS / name), mimetype="image/png"),
                ).execute()
                print("uploaded", kind, name)

    edits.commit(packageName=PACKAGE, editId=edit_id).execute()
    print("committed")


if __name__ == "__main__":
    main()
