#!/usr/bin/env python3
"""
A small stand-in for an Immich server, for trying ImmichTV without a real library.

It serves a generated library of photos and videos spread over the last few years through the
parts of the Immich API the app uses (users, people, albums, timeline, thumbnails, video playback).
Response shapes follow Immich's OpenAPI spec. Images and videos are made on first request with
ImageMagick and ffmpeg and cached, and each one shows its own date and number so ordering is easy
to check.

Needs Python 3.10+, ImageMagick 7 (`magick`) and, to play videos, ffmpeg. Nothing else.

    python3 tools/mock-immich-server/mock_immich_server.py
    # From the Android emulator the server is http://10.0.2.2:2283, API key "test-api-key".
"""

import argparse
import base64
import colorsys
import json
import math
import random
import re
import shutil
import subprocess
import threading
import time
import uuid
from datetime import date, datetime, timedelta
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

PLACES = [
    ("Hyderabad", "India", 5.5),
    ("Bengaluru", "India", 5.5),
    ("Goa", "India", 5.5),
    ("San Francisco", "United States of America", -7.0),
    ("New York", "United States of America", -4.0),
    ("London", "United Kingdom", 1.0),
    ("Paris", "France", 2.0),
    ("Tokyo", "Japan", 9.0),
    (None, None, 5.5),
]
RATIOS = [4 / 3, 3 / 4, 16 / 9, 1.0, 9 / 16, 3 / 2]
PEOPLE = ["Asha", "Ravi", "Meera", "Karthik", "Lakshmi"]
# Birthdays and favorites for some of PEOPLE, to see them on their pages and first in the list.
BIRTH_DATES = {"Asha": "1990-04-12", "Ravi": "1987-11-03"}
FAVORITE_PEOPLE = {"Meera"}
# Listed after PEOPLE: a name too long to fit under a face, and someone Immich found but nobody named.
EXTRA_PEOPLE = ["Venkata Subrahmanyam Chandrasekhar", ""]
ALBUM_NAMES = ["Goa trip", "Diwali at home", "Tokyo", "Karthik's birthday", "Weekend in Paris", "Monsoon walks",
               "New York", "Graduation day", "Hyderabad food crawl", "Family reunion", "London"]
PREVIEW_SIZE = 1440
THUMBNAIL_SIZE = 250
VIDEO_SIZE = 960


def find_font(pattern):
    """The file of a font installed on this computer, or None to leave the choice to ffmpeg."""
    try:
        path = subprocess.run(["fc-match", "-f", "%{file}", pattern], capture_output=True, text=True).stdout
    except FileNotFoundError:
        return None
    return path if path and "'" not in path else None


# ffmpeg only reads bold from a font file, not from a font name.
CLOCK_FONT = find_font("Liberation Sans:bold")


def thumbhash(w, h, rgba):
    """Port of the ThumbHash reference encoder (https://github.com/evanw/thumbhash, MIT)."""

    def jround(x):  # Java's Math.round, which the reference relies on
        return math.floor(x + 0.5)

    n = w * h
    avg = [0.0, 0.0, 0.0]
    avg_a = 0.0
    for i in range(n):
        alpha = rgba[i * 4 + 3] / 255
        for c in range(3):
            avg[c] += alpha / 255 * rgba[i * 4 + c]
        avg_a += alpha
    if avg_a > 0:
        avg = [v / avg_a for v in avg]
    has_alpha = avg_a < n
    l_limit = 5 if has_alpha else 7
    lx = max(1, jround(l_limit * w / max(w, h)))
    ly = max(1, jround(l_limit * h / max(w, h)))
    l, p, q, a = [], [], [], []
    for i in range(n):
        alpha = rgba[i * 4 + 3] / 255
        r, g, b = (avg[c] * (1 - alpha) + alpha / 255 * rgba[i * 4 + c] for c in range(3))
        l.append((r + g + b) / 3)
        p.append((r + g) / 2 - b)
        q.append(r - g)
        a.append(alpha)

    def encode(channel, nx, ny):
        dc, ac, scale = 0.0, [], 0.0
        for cy in range(ny):
            cx = 0
            while cx * ny < nx * (ny - cy):
                fx = [math.cos(math.pi / w * cx * (x + 0.5)) for x in range(w)]
                f = 0.0
                for y in range(h):
                    fy = math.cos(math.pi / h * cy * (y + 0.5))
                    row = y * w
                    for x in range(w):
                        f += channel[row + x] * fx[x] * fy
                f /= n
                if cx > 0 or cy > 0:
                    ac.append(f)
                    scale = max(scale, abs(f))
                else:
                    dc = f
                cx += 1
        if scale > 0:
            ac = [0.5 + 0.5 / scale * v for v in ac]
        return dc, ac, scale

    l_dc, l_ac, l_scale = encode(l, max(3, lx), max(3, ly))
    p_dc, p_ac, p_scale = encode(p, 3, 3)
    q_dc, q_ac, q_scale = encode(q, 3, 3)
    a_dc, a_ac, a_scale = encode(a, 5, 5) if has_alpha else (0.0, [], 0.0)

    is_landscape = w > h
    header24 = (jround(63 * l_dc) | (jround(31.5 + 31.5 * p_dc) << 6) | (jround(31.5 + 31.5 * q_dc) << 12)
                | (jround(31 * l_scale) << 18) | ((1 << 23) if has_alpha else 0))
    header16 = ((ly if is_landscape else lx) | (jround(63 * p_scale) << 3) | (jround(63 * q_scale) << 9)
                | ((1 << 15) if is_landscape else 0))
    ac_start = 6 if has_alpha else 5
    acs = [l_ac, p_ac, q_ac] + ([a_ac] if has_alpha else [])
    out = bytearray(ac_start + (sum(len(x) for x in acs) + 1) // 2)
    out[0:5] = bytes([header24 & 255, (header24 >> 8) & 255, (header24 >> 16) & 255,
                      header16 & 255, (header16 >> 8) & 255])
    if has_alpha:
        out[5] = jround(15 * a_dc) | (jround(15 * a_scale) << 4)
    index = 0
    for ac in acs:
        for v in ac:
            out[ac_start + (index >> 1)] |= jround(15 * v) << ((index & 1) << 2)
            index += 1
    return bytes(out)


def gradient_thumbhash(top, bottom, ratio):
    """ThumbHash of a top-to-bottom gradient, matching the generated image without rendering it."""
    w, h = (24, max(1, round(24 / ratio))) if ratio >= 1 else (max(1, round(24 * ratio)), 24)
    rgba = []
    for y in range(h):
        t = y / max(1, h - 1)
        color = [round(top[c] + (bottom[c] - top[c]) * t) for c in range(3)]
        rgba += (color + [255]) * w
    return base64.b64encode(thumbhash(w, h, rgba)).decode()


def random_color(rng, lightness):
    r, g, b = colorsys.hls_to_rgb(rng.random(), lightness, 0.45 + rng.random() * 0.4)
    return [round(r * 255), round(g * 255), round(b * 255)]


def hex_color(rgb):
    return "#%02x%02x%02x" % tuple(rgb)


class Library:
    def __init__(self, count, seed, years):
        rng = random.Random(seed)
        self.user_id = str(uuid.UUID(int=rng.getrandbits(128), version=4))
        self.people = [
            {"id": str(uuid.UUID(int=rng.getrandbits(128), version=4)), "name": name,
             "color": hex_color(random_color(rng, 0.5))}
            for name in PEOPLE
        ]
        # A separate generator, so adding people doesn't change the rest of the library.
        people_rng = random.Random(seed + 1)
        self.people += [
            {"id": str(uuid.UUID(int=people_rng.getrandbits(128), version=4)), "name": name,
             "color": hex_color(random_color(people_rng, 0.5))}
            for name in EXTRA_PEOPLE
        ]
        today = date.today()
        start = today - timedelta(days=365 * years)

        # Photos come in bursts, like real trips and parties, with today and yesterday always present.
        days = {today, today - timedelta(days=1)}
        while len(days) < max(2, count // 6):
            days.add(start + timedelta(days=rng.randrange((today - start).days)))
        days = sorted(days)

        per_day = [rng.choice([1, 2, 3, 4, 6, 8, 12, 15]) for _ in days]
        while sum(per_day) > count:
            i = rng.randrange(len(days))
            per_day[i] = max(1, per_day[i] - 1)
        while sum(per_day) < count:
            per_day[rng.randrange(len(days))] += 1

        self.assets = []
        for day, photos in zip(days, per_day):
            city, country, offset = rng.choice(PLACES)
            for _ in range(photos):
                local = datetime.combine(day, datetime.min.time()) + timedelta(
                    seconds=rng.randrange(7 * 3600, 23 * 3600), milliseconds=rng.randrange(1000))
                if day == today:
                    local = min(local, datetime.now() - timedelta(minutes=1))
                is_video = rng.random() < 0.08
                ratio = rng.choice(RATIOS)
                top, bottom = random_color(rng, 0.62), random_color(rng, 0.3)
                self.assets.append({
                    "id": str(uuid.UUID(int=rng.getrandbits(128), version=4)),
                    "local": local,
                    "utc": local - timedelta(hours=offset),
                    "offset": offset,
                    "is_video": is_video,
                    "duration_ms": rng.randrange(3_000, 180_000) if is_video else None,
                    "is_favorite": rng.random() < 0.1,
                    "ratio": ratio,
                    "top": top,
                    "bottom": bottom,
                    "city": city,
                    "country": country,
                })

        # Newest first, as the server orders a bucket, then numbered for the image labels.
        self.assets.sort(key=lambda asset: (asset["local"].date(), asset["utc"]), reverse=True)
        for number, asset in enumerate(self.assets, start=1):
            asset["number"] = number
            asset["thumbhash"] = gradient_thumbhash(asset["top"], asset["bottom"], asset["ratio"])
        self.by_id = {asset["id"]: asset for asset in self.assets}

        self.me = {"id": self.user_id, "name": "Demo User", "email": "demo@example.com"}
        self.friend = {"id": str(uuid.UUID(int=rng.getrandbits(128), version=4)), "name": "Asha Rao",
                       "email": "asha@example.com"}
        self.albums = self.make_albums(rng)

    def make_albums(self, rng):
        """Albums of a few consecutive photo days each, like trips, with one of each kind the app shows."""
        by_day = {}
        for asset in self.assets:
            by_day.setdefault(asset["local"].date(), []).append(asset)
        days = sorted(by_day, reverse=True)
        starts = sorted(rng.sample(range(len(days) - 4), k=len(ALBUM_NAMES)))
        albums = []
        for name, start in zip(ALBUM_NAMES, starts):
            albums.append({
                "id": str(uuid.UUID(int=rng.getrandbits(128), version=4)),
                "name": name,
                "description": "",
                "assets": [asset for day in days[start:start + rng.randint(1, 4)] for asset in by_day[day]],
                "owner": self.me,
                "shared_with": [],
                "order": "desc",
            })
        albums[1]["owner"], albums[1]["shared_with"] = self.friend, [self.me]
        albums[5]["owner"], albums[5]["shared_with"] = self.friend, [self.me]
        albums[2]["shared_with"] = [self.friend]
        albums[3]["order"] = "asc"
        albums[3]["description"] = "Shown oldest first, as chosen for this album in Immich."
        albums[4]["name"] = "A very long album name that will not fit on one line of its card"
        albums[4]["description"] = ("A long description that goes on for a while, to check that it wraps onto a "
                                    "second line and is then cut off neatly instead of pushing the photos down "
                                    "the screen. " * 3).strip()
        albums.append({"id": str(uuid.UUID(int=rng.getrandbits(128), version=4)), "name": "Ideas for next year",
                       "description": "", "assets": [], "owner": self.me, "shared_with": [], "order": "desc"})
        return albums

    def visible_albums(self):
        return [album for album in self.albums
                if album["owner"] is self.me or self.me in album["shared_with"]]

    def album(self, album_id):
        return next((album for album in self.visible_albums() if album["id"] == album_id), None)

    def filtered(self, params):
        assets = self.assets
        if params.get("albumId"):
            album = self.album(params["albumId"])
            album_ids = {asset["id"] for asset in album["assets"]} if album else set()
            assets = [asset for asset in assets if asset["id"] in album_ids]
        if params.get("isFavorite") == "true":
            assets = [asset for asset in assets if asset["is_favorite"]]
        elif params.get("isFavorite") == "false":
            assets = [asset for asset in assets if not asset["is_favorite"]]
        if params.get("personId"):
            person_ids = self.person_asset_ids(params["personId"])
            assets = [asset for asset in assets if asset["id"] in person_ids]
        return assets

    def person(self, person_id):
        return next((person for person in self.people if person["id"] == person_id), None)

    def person_asset_ids(self, person_id):
        """Pretends each person is in a different share of the photos: the first in every third, and so on."""
        index = next((i for i, person in enumerate(self.people) if person["id"] == person_id), None)
        return set() if index is None else {asset["id"] for asset in self.assets[index::index + 3]}


class Handler(BaseHTTPRequestHandler):
    server_version = "MockImmich/1.0"
    library: Library
    api_key: str
    delay: float
    legacy_durations: bool
    legacy_albums: bool
    cache_dir: Path
    render_locks: dict = {}
    render_locks_guard = threading.Lock()

    def do_GET(self):
        url = urlparse(self.path)
        params = {key: values[0] for key, values in parse_qs(url.query).items()}
        path = url.path.rstrip("/")
        if not path.startswith("/api/"):
            return self.send_json({"message": "Not found", "statusCode": 404}, HTTPStatus.NOT_FOUND)
        path = path[len("/api"):]

        if path == "/server/ping":
            return self.send_json({"res": "pong"})
        if path == "/server/version":
            return self.send_json({"major": 3, "minor": 2, "patch": 2, "prerelease": None})
        if self.headers.get("x-api-key") != self.api_key:
            return self.send_json({"message": "Invalid API key", "statusCode": 401,
                                   "error": "Unauthorized"}, HTTPStatus.UNAUTHORIZED)
        if self.delay:
            time.sleep(self.delay)

        parts = path.strip("/").split("/")
        if path == "/users/me":
            return self.send_json({"id": self.library.user_id, "email": "demo@example.com", "name": "Demo User",
                                   "profileImagePath": "profile.jpg"})
        if len(parts) == 3 and parts[0] == "users" and parts[2] == "profile-image":
            return self.send_image(f"profile-{parts[1]}", lambda out: self.render_avatar(out, "#5c6bc0", "DU"))
        if path == "/albums":
            albums = self.library.visible_albums()
            if self.legacy_albums:
                # Before v3, only your own albums unless asked for shared ones (by you or with you).
                is_shared = {"true": True, "false": False}.get(params.get("shared"))
                if is_shared is None:
                    albums = [album for album in albums if album["owner"] is self.library.me]
                else:
                    albums = [album for album in albums if self.is_shared(album) == is_shared]
            return self.send_json([self.album_response(album) for album in albums])
        if len(parts) == 2 and parts[0] == "albums":
            album = self.library.album(parts[1])
            if album is None:
                return self.send_json({"message": "Not found or no album.read access", "statusCode": 400,
                                       "error": "Bad Request"}, HTTPStatus.BAD_REQUEST)
            return self.send_json(self.album_response(album))
        if path == "/people":
            # Immich's order: favorites, then named people, then those in the most photos.
            people = sorted(self.library.people, key=lambda person: (
                person["name"] not in FAVORITE_PEOPLE, not person["name"],
                -len(self.library.person_asset_ids(person["id"]))))
            return self.send_json({"people": [self.person_response(person) for person in people],
                                   "total": len(people), "hidden": 0, "hasNextPage": False})
        if parts[0] == "people" and len(parts) in (2, 3):
            person = self.library.person(parts[1])
            if len(parts) == 3 and parts[2] == "thumbnail":
                if person is None:
                    return self.not_found()
                return self.send_image(f"person-{person['id']}",
                                       lambda out: self.render_avatar(out, person["color"], person["name"][:1] or "?"))
            if person is None:
                return self.send_json({"message": "Not found or no person.read access", "statusCode": 400,
                                       "error": "Bad Request"}, HTTPStatus.BAD_REQUEST)
            if len(parts) == 2:
                return self.send_json(self.person_response(person))
            if parts[2] == "statistics":
                return self.send_json({"assets": len(self.library.person_asset_ids(person["id"]))})
        if path == "/timeline/buckets":
            counts = {}
            for asset in self.library.filtered(params):
                key = asset["local"].strftime("%Y-%m-01")
                counts[key] = counts.get(key, 0) + 1
            return self.send_json([{"timeBucket": key, "count": counts[key]}
                                   for key in sorted(counts, reverse=params.get("order") != "asc")])
        if path == "/timeline/bucket":
            bucket = params.get("timeBucket", "")[:7]
            assets = [asset for asset in self.library.filtered(params)
                      if asset["local"].strftime("%Y-%m") == bucket]
            if params.get("order") == "asc":
                assets.reverse()
            return self.send_json(self.bucket_response(assets))
        if len(parts) == 3 and parts[0] == "assets" and parts[2] == "thumbnail":
            asset = self.library.by_id.get(parts[1])
            if asset is None:
                return self.not_found()
            size = PREVIEW_SIZE if params.get("size") == "preview" else THUMBNAIL_SIZE
            return self.send_image(f"asset-{asset['id']}-{size}", lambda out: self.render_asset(out, asset, size))
        if len(parts) == 4 and parts[0] == "assets" and parts[2:] == ["video", "playback"]:
            asset = self.library.by_id.get(parts[1])
            if asset is None:
                return self.not_found()
            if not asset["is_video"]:
                return self.send_json({"message": "Asset is not a video", "statusCode": 400,
                                       "error": "Bad Request"}, HTTPStatus.BAD_REQUEST)
            if shutil.which("ffmpeg") is None:
                return self.send_json({"message": "The mock server needs ffmpeg to make videos", "statusCode": 500,
                                       "error": "Internal Server Error"}, HTTPStatus.INTERNAL_SERVER_ERROR)
            video = self.cached(f"video-{asset['id']}.mp4", lambda out: self.render_video(out, asset))
            return self.send_file_range(video, "video/mp4")
        return self.not_found()

    def bucket_response(self, assets):
        def duration(asset):
            ms = asset["duration_ms"]
            if ms is None:
                return None
            if not self.legacy_durations:
                return ms
            seconds = ms / 1000
            return "%d:%02d:%09.6f" % (seconds // 3600, seconds % 3600 // 60, seconds % 60)

        return {
            "id": [asset["id"] for asset in assets],
            "ownerId": [self.library.user_id for _ in assets],
            "ratio": [round(asset["ratio"], 3) for asset in assets],
            "isFavorite": [asset["is_favorite"] for asset in assets],
            "visibility": ["timeline" for _ in assets],
            "isTrashed": [False for _ in assets],
            "isImage": [not asset["is_video"] for asset in assets],
            "thumbhash": [asset["thumbhash"] for asset in assets],
            # Postgres sends UTC timestamps without an offset.
            "fileCreatedAt": [asset["utc"].isoformat(timespec="milliseconds") for asset in assets],
            "createdAt": [asset["utc"].isoformat(timespec="milliseconds") for asset in assets],
            "localOffsetHours": [asset["offset"] for asset in assets],
            "duration": [duration(asset) for asset in assets],
            "projectionType": [None for _ in assets],
            "livePhotoVideoId": [None for _ in assets],
            "city": [asset["city"] for asset in assets],
            "country": [asset["country"] for asset in assets],
        }

    def is_shared(self, album):
        return album["owner"] is not self.library.me or bool(album["shared_with"])

    def person_response(self, person):
        return {"id": person["id"], "name": person["name"], "isHidden": False,
                "isFavorite": person["name"] in FAVORITE_PEOPLE, "thumbnailPath": f"/people/{person['id']}.jpeg",
                "birthDate": BIRTH_DATES.get(person["name"]), "updatedAt": "2025-01-01T00:00:00.000Z",
                "color": person["color"]}

    def album_response(self, album):
        def user(u):
            return {"id": u["id"], "name": u["name"], "email": u["email"], "profileImagePath": "",
                    "avatarColor": "primary", "profileChangedAt": "2025-01-01T00:00:00.000Z"}

        def local_as_utc(asset):
            # Immich writes the local time the photo was taken as if it were UTC.
            return asset["local"].isoformat(timespec="milliseconds") + "Z"

        assets = album["assets"]
        me, owner = self.library.me, album["owner"]
        response = {
            "id": album["id"],
            "albumName": album["name"],
            "description": album["description"],
            "albumThumbnailAssetId": assets[0]["id"] if assets else None,
            "assetCount": len(assets),
            "createdAt": "2025-01-01T00:00:00.000Z",
            "updatedAt": "2025-01-01T00:00:00.000Z",
            "hasSharedLink": False,
            "isActivityEnabled": True,
            "order": album["order"],
            "shared": self.is_shared(album),
        }
        if assets:
            newest = max(assets, key=lambda asset: asset["local"])
            response["startDate"] = local_as_utc(min(assets, key=lambda asset: asset["local"]))
            response["endDate"] = local_as_utc(newest)
            response["lastModifiedAssetTimestamp"] = newest["utc"].isoformat(timespec="milliseconds") + "Z"
        if self.legacy_albums:
            response["owner"] = user(owner)
            response["ownerId"] = owner["id"]
            response["assets"] = []
            response["albumUsers"] = [{"user": user(u), "role": "editor"} for u in album["shared_with"]]
        else:
            # The owner first, then the signed-in user if it's someone else's album, then the rest.
            others = [u for u in album["shared_with"] if u is not me]
            members = [owner] + ([me] if owner is not me else []) + others
            response["albumUsers"] = [{"user": user(u), "role": "owner" if u is owner else "editor"}
                                      for u in members]
        return response

    def render_asset(self, out, asset, size):
        ratio = asset["ratio"]
        # Thumbnails are sized by their short side and previews by their long side, as in Immich.
        if size == THUMBNAIL_SIZE:
            w, h = (round(size * ratio), size) if ratio >= 1 else (size, round(size / ratio))
        else:
            w, h = (size, round(size / ratio)) if ratio >= 1 else (round(size * ratio), size)
        big = max(12, round(min(w, h) / 9))
        local = asset["local"]
        title = local.strftime("%b %d, %Y").replace(" 0", " ")
        subtitle = f"{local.strftime('%H:%M')}  ·  #{asset['number']}" + ("  ·  VIDEO" if asset["is_video"] else "")
        subprocess.run([
            "magick", "-size", f"{w}x{h}", f"gradient:{hex_color(asset['top'])}-{hex_color(asset['bottom'])}",
            "-gravity", "center", "-fill", "white", "-font", "Liberation-Sans-Bold",
            "-pointsize", str(big), "-annotate", f"+0-{round(big * 0.5)}", title,
            "-pointsize", str(round(big * 0.55)), "-annotate", f"+0+{round(big * 0.6)}", subtitle,
            "-quality", "85", str(out),
        ], check=True)

    def render_video(self, out, asset):
        """The asset's preview with a running clock, and a beep every second."""
        background = out.with_suffix(".jpg")
        self.render_asset(background, asset, VIDEO_SIZE)
        seconds = asset["duration_ms"] / 1000
        clock = r"%{eif\:t/60\:d}\:%{eif\:mod(t,60)\:d\:2}"
        font = f"fontfile='{CLOCK_FONT}':" if CLOCK_FONT else ""
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-loop", "1", "-framerate", "10", "-i", str(background),
            "-f", "lavfi", "-i", "aevalsrc='0.2*sin(2*PI*880*t)*lt(mod(t,1),0.08)':s=44100",
            "-t", f"{seconds:.3f}",
            "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,"
                   f"drawtext={font}text='{clock}':fontsize=h/10:fontcolor=white:"
                   "box=1:boxcolor=black@0.4:boxborderw=12:x=(w-tw)/2:y=h*0.78",
            "-c:v", "libx264", "-preset", "ultrafast", "-tune", "stillimage", "-pix_fmt", "yuv420p", "-g", "20",
            "-c:a", "aac", "-b:a", "64k", "-shortest", "-movflags", "+faststart", "-f", "mp4", str(out),
        ], check=True)
        background.unlink()

    @staticmethod
    def render_avatar(out, color, text):
        subprocess.run([
            "magick", "-size", "256x256", f"xc:{color}", "-gravity", "center", "-fill", "white",
            "-font", "Liberation-Sans-Bold", "-pointsize", "120", "-annotate", "+0+0", text,
            "-quality", "85", str(out),
        ], check=True)

    def cached(self, file_name, render):
        """Makes a file with render(path) the first time it's asked for, then reuses it."""
        out = self.cache_dir / file_name
        with self.render_locks_guard:
            lock = self.render_locks.setdefault(file_name, threading.Lock())
        with lock:
            if not out.exists():
                temp = out.with_name(f"tmp-{out.name}")
                render(temp)
                temp.replace(out)
        return out

    def send_image(self, name, render):
        body = self.cached(f"{name}.jpg", render).read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "image/jpeg")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "private, max-age=86400, no-transform")
        self.end_headers()
        self.wfile.write(body)

    def send_file_range(self, path, content_type):
        """Sends a file, or the part asked for with a Range header, as Immich does for videos."""
        body = path.read_bytes()
        size = len(body)
        start, end = 0, size - 1
        requested = self.headers.get("Range")
        if requested:
            match = re.fullmatch(r"bytes=(\d*)-(\d*)", requested.strip())
            if match and match.group(1):
                start = int(match.group(1))
                end = min(int(match.group(2)), size - 1) if match.group(2) else size - 1
            elif match and match.group(2):
                start = max(0, size - int(match.group(2)))
            if not match or start >= size or start > end:
                self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                self.send_header("Content-Range", f"bytes */{size}")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
        self.send_response(HTTPStatus.PARTIAL_CONTENT if requested else HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if requested:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Cache-Control", "private, max-age=86400, no-transform")
        self.end_headers()
        try:
            self.wfile.write(body[start:end + 1])
        except (BrokenPipeError, ConnectionResetError):
            pass  # Players drop the connection when they seek.

    def send_json(self, body, status=HTTPStatus.OK):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def not_found(self):
        self.send_json({"message": "Not found", "statusCode": 404, "error": "Not Found"}, HTTPStatus.NOT_FOUND)

    def log_message(self, format, *args):
        print(f"{self.address_string()} {format % args}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=2283)
    parser.add_argument("--api-key", default="test-api-key")
    parser.add_argument("--assets", type=int, default=800, help="how many photos and videos to make up")
    parser.add_argument("--years", type=int, default=3, help="how far back the library goes")
    parser.add_argument("--seed", type=int, default=42, help="same seed, same library")
    parser.add_argument("--delay-ms", type=int, default=0, help="slow every API response down, to see loading states")
    parser.add_argument("--legacy-durations", action="store_true",
                        help="send video durations as H:MM:SS strings, like Immich before v3")
    parser.add_argument("--legacy-albums", action="store_true",
                        help="list albums like Immich before v3: only your own unless shared=true is sent")
    parser.add_argument("--cache-dir", type=Path, default=Path.home() / ".cache" / "mock-immich-server")
    args = parser.parse_args()

    args.cache_dir.mkdir(parents=True, exist_ok=True)
    Handler.library = Library(args.assets, args.seed, args.years)
    Handler.api_key = args.api_key
    Handler.delay = args.delay_ms / 1000
    Handler.legacy_durations = args.legacy_durations
    Handler.legacy_albums = args.legacy_albums
    Handler.cache_dir = args.cache_dir

    library = Handler.library
    months = len({asset["local"].strftime("%Y-%m") for asset in library.assets})
    print(f"Mock Immich server on http://0.0.0.0:{args.port} (emulator: http://10.0.2.2:{args.port})")
    print(f"API key: {args.api_key}")
    videos = sum(asset["is_video"] for asset in library.assets)
    print(f"Library: {len(library.assets)} assets ({videos} videos) over {months} months and "
          f"{len(library.albums)} albums, images and videos cached in {args.cache_dir}", flush=True)
    if shutil.which("ffmpeg") is None:
        print("ffmpeg isn't installed, so videos won't play", flush=True)
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
