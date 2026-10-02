# Mock Immich server

A stand-in Immich server for trying ImmichTV without a real library. It makes up a library of
photos and videos over the last few years and serves it through the parts of the Immich API the
app uses: `users/me`, `people`, `people/{id}`, `people/{id}/statistics`, `albums`, `albums/{id}`,
`timeline/buckets`, `timeline/bucket`, person and asset thumbnails, and video playback. Response
shapes follow Immich's OpenAPI spec.

The library comes with a dozen albums covering what the Albums tab shows: albums shared with you
by "Asha Rao", one you share with her, one shown oldest first, one with a long name and
description, and an empty one.

People are each in a different share of the photos. Meera is a favorite, Asha and Ravi have
birthdays, one person has a name too long to fit, and one hasn't been named, so the app should
leave them out.

Every image shows its own date and number (`#1` is the newest), so the order and grouping on
screen are easy to check. Videos show the same over a running clock, with a beep every second, so
seeking and sound are easy to check too. They support byte ranges, as Immich's do, and are made
the first time they're played, which takes a few seconds for the longest.

## Run it

Needs Python 3.10+, ImageMagick 7 (`magick`) and, for videos to play, ffmpeg.

```sh
python3 tools/mock-immich-server/mock_immich_server.py
```

Then log in on the TV with:

- Server URL: `http://10.0.2.2:2283` from the Android emulator, or `http://<this computer's IP>:2283` from a real TV
- API key: `test-api-key`

## Real photos

For screenshots and demos, `--photos <folder>` shows the photos in a folder (`.jpg`, `.png` or
`.webp`) in place of the generated images, without the date and number on them:

- Each subfolder becomes an album named after it, holding only its own photos. Outside albums too,
  all of a day's photos come from one subfolder, as they would on a trip.
- The `people` subfolder holds a face for each person, in a file named after them (`Meera.jpg`).
  These take the place of the made-up people.
- Videos are a still of their photo, with no clock or beeps.

`fetch_demo_photos.py` downloads the library in the screenshots of the main README: about 260 CC0
photos from [StockSnap](https://stocksnap.io) (90 MB), already laid out like this.

```sh
python3 tools/mock-immich-server/fetch_demo_photos.py
python3 tools/mock-immich-server/mock_immich_server.py --photos ~/.cache/mock-immich-server/demo-photos --seed 16
```

`--seed 16` is the library the screenshots were taken with. If you were logged in to the mock server
with another seed, sign out and log in again, since each seed makes a different user.

## Options

| Option | Default | |
|---|---|---|
| `--assets` | 800 | How many photos and videos to make up |
| `--years` | 3 | How far back the library goes |
| `--seed` | 42 | The same seed always makes the same library |
| `--delay-ms` | 0 | Slows every API response, to see loading placeholders |
| `--legacy-durations` | off | Sends video durations as `H:MM:SS` strings, like Immich before v3 |
| `--legacy-albums` | off | Lists albums like Immich before v3: only your own unless `shared=true` is sent, with the owner as a separate field |
| `--photos` | none | A folder of real photos to show instead of generated images (see above) |
| `--port` | 2283 | |
| `--api-key` | `test-api-key` | |
| `--cache-dir` | `~/.cache/mock-immich-server` | Where generated images and videos are kept |
