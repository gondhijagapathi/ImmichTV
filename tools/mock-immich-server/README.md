# Mock Immich server

A stand-in Immich server for trying ImmichTV without a real library. It makes up a library of
photos and videos over the last few years and serves it through the parts of the Immich API the
app uses: `users/me`, `people`, `albums`, `timeline/buckets`, `timeline/bucket` and asset
thumbnails. Response shapes follow Immich's OpenAPI spec.

Every image shows its own date and number (`#1` is the newest), so the order and grouping on
screen are easy to check.

## Run it

Needs Python 3.10+ and ImageMagick 7 (`magick`).

```sh
python3 tools/mock-immich-server/mock_immich_server.py
```

Then log in on the TV with:

- Server URL: `http://10.0.2.2:2283` from the Android emulator, or `http://<this computer's IP>:2283` from a real TV
- API key: `test-api-key`

## Options

| Option | Default | |
|---|---|---|
| `--assets` | 800 | How many photos and videos to make up |
| `--years` | 3 | How far back the library goes |
| `--seed` | 42 | The same seed always makes the same library |
| `--delay-ms` | 0 | Slows every API response, to see loading placeholders |
| `--legacy-durations` | off | Sends video durations as `H:MM:SS` strings, like Immich before v3 |
| `--port` | 2283 | |
| `--api-key` | `test-api-key` | |
| `--cache-dir` | `~/.cache/mock-immich-server` | Where generated images are kept |
