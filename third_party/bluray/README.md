# Blu-ray ISO playback component

JNI bridge for direct Blu-ray / UHD Blu-ray ISO playback, ported from ETKN TV (`third_party/bluray`).
Only the JNI class names differ (`xyz.etkn.mobile.player.bluray.BlurayNative`).

`scripts/build_bluray_native.py` runs in GitHub Actions (NDK r27c), fetches and verifies these upstream releases, and
writes `app/src/main/jniLibs/<abi>/libetkn_bluray.so` (git-ignored, cached by source hash):

- libbluray 1.3.4, LGPL-2.1-or-later, SHA-256 `478ffd68a0f5dde8ef6ca989b7f035b5a0a22c599142e5cd3ff7b03bbebe5f2b`
- libudfread 1.1.2, LGPL-2.1-or-later, SHA-256 `d5946f19b27288ce0911eb11fe583c136767421220ece3bafc96245efd208915`
- FFmpeg 6.0 (demux-only: MPEG-TS demuxer, parsers, Blu-ray LPCM decoder; LGPL), SHA-256 `57be87c22d9b49c112b6d24bc67d42508660e6b718b3db89c44e47e289137082`

The app reads the original ISO by HTTP Range; libbluray selects the server's probed playlist (`BlurayPlaylist`) or its
main title and FFmpeg demuxes the MPEG-TS packets into Media3 samples. Decoding stays in Media3 (hardware MediaCodec
first, NextLib FFmpeg fallback). Disc Java applications and encrypted discs are rejected. `seek_point.c.inc` is
appended to the pinned `bluray.c` in a generated translation unit; upstream sources are not edited.

License texts ship in `app/src/main/assets/licenses/`.
