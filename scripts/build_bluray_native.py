"""Build libetkn_bluray.so: Blu-ray ISO reader + MPEG-TS demuxer for Media3 (no network, menus or decryption).

Ported from ETKN TV's scripts/Build-BlurayNative.py (Linux / macOS hosts only); runs in CI, never on
the developer's machine. Upstream sources are downloaded from their release servers and verified
by SHA-256 before use; the pinned upstream files are not edited.

    python3 scripts/build_bluray_native.py --ndk "$NDK" --work "$RUNNER_TEMP/bluray" --out app/build/generated/bluray
"""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import subprocess
import tarfile
import urllib.request
from concurrent.futures import ThreadPoolExecutor

SOURCES = {
    "libbluray-1.3.4.tar.bz2": ("https://download.videolan.org/pub/videolan/libbluray/1.3.4/libbluray-1.3.4.tar.bz2", "478ffd68a0f5dde8ef6ca989b7f035b5a0a22c599142e5cd3ff7b03bbebe5f2b"),
    "libudfread-1.1.2.tar.bz2": ("https://download.videolan.org/pub/videolan/libudfread/libudfread-1.1.2.tar.bz2", "d5946f19b27288ce0911eb11fe583c136767421220ece3bafc96245efd208915"),
    "ffmpeg-6.0.tar.xz": ("https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz", "57be87c22d9b49c112b6d24bc67d42508660e6b718b3db89c44e47e289137082"),
}

# Media3 / Android 8+: API 26 targets.
TARGETS = {
    "arm64-v8a": ("aarch64", "aarch64-linux-android26"),
    "armeabi-v7a": ("arm", "armv7a-linux-androideabi26"),
    "x86_64": ("x86_64", "x86_64-linux-android26"),
}


def fetch(work: Path):
    for name, (url, digest) in SOURCES.items():
        archive = work / name
        if not archive.exists():
            urllib.request.urlretrieve(url, archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            archive.unlink()
            raise RuntimeError("Source checksum mismatch: " + name)
        folder = work / name.split(".tar.")[0]
        if not folder.exists():
            with tarfile.open(archive) as content:
                content.extractall(work, filter="data")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True, help="jniLibs-style output root (<out>/<abi>/libetkn_bluray.so)")
    parser.add_argument("--abis", nargs="+", default=list(TARGETS))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    args.work.mkdir(parents=True, exist_ok=True)
    fetch(args.work)
    bluray, udf, ffmpeg = [args.work / n for n in ("libbluray-1.3.4", "libudfread-1.1.2", "ffmpeg-6.0")]

    # Separate translation unit: the verified bluray.c plus ETKN's read-only seek-point helper.
    seek_source = args.work / "bluray_seek.c"
    seek_source.write_text('#include "' + (bluray / "src/libbluray/bluray.c").as_posix() + '"\n'
                           + (root / "third_party/bluray/seek_point.c.inc").read_text(encoding="utf-8"), encoding="utf-8")

    host = {"Linux": "linux-x86_64", "Darwin": "darwin-x86_64"}[platform.system()]
    toolbin = args.ndk / "toolchains/llvm/prebuilt" / host / "bin"
    cc, cxx = toolbin / "clang", toolbin / "clang++"

    # Upstream's core source list with BD-J disabled.
    listing = (bluray / "Makefile.am").read_text(encoding="utf-8").split("# bd-j")[0]
    files = [bluray / n for n in re.findall(r"\bsrc/[\w/]+\.c\b", listing)]
    files = [seek_source if f == bluray / "src/libbluray/bluray.c" else f for f in files]
    files += [bluray / n for n in ["src/libbluray/disc/udf_fs.c", "src/libbluray/bdj/bdjo_parse.c", "src/file/dir_posix.c",
                                   "src/file/dirs_xdg.c", "src/file/dl_posix.c", "src/file/file_posix.c", "src/file/mount.c"]]
    files += [udf / "src" / n for n in ["udfread.c", "ecma167.c", "default_blockinput.c"]]
    files += [root / "third_party/bluray/bdj_disabled.c"]
    jobs = str(min(8, os.cpu_count() or 4))

    for abi in args.abis:
        arch, target = TARGETS[abi]
        build = args.work / abi
        ffbuild = build / "ffmpeg"
        ffbuild.mkdir(parents=True, exist_ok=True)
        # Demux-only FFmpeg: MPEG-TS, the parsers it needs, and the Blu-ray LPCM decoder.
        configure = [str(ffmpeg / "configure"), "--target-os=android", "--enable-cross-compile", f"--arch={arch}",
                     f"--cc={cc} --target={target}", f"--cxx={cxx} --target={target}",
                     *[f"--{tool}={toolbin / ('llvm-' + tool)}" for tool in ["ar", "nm", "ranlib", "strip"]],
                     "--disable-programs", "--disable-doc", "--disable-debug", "--disable-autodetect", "--disable-everything",
                     "--disable-network", "--disable-avdevice", "--disable-avfilter", "--disable-swscale", "--disable-swresample",
                     "--disable-postproc", "--disable-shared", "--enable-static", "--enable-pic", "--disable-x86asm",
                     "--extra-cflags=-O2 -fPIC", "--enable-demuxer=mpegts", "--enable-decoder=pcm_bluray",
                     "--enable-parser=h264,hevc,mpegvideo,vc1,ac3,dca,mlp,aac,aac_latm,mpegaudio"]
        config = ffbuild / "ffbuild/config.mak"
        if not config.exists():
            with (build / "configure.log").open("w") as log:
                subprocess.run(configure, cwd=ffbuild, stdout=log, stderr=subprocess.STDOUT, check=True)
        with (build / "ffmpeg-build.log").open("w") as log:
            subprocess.run(["make", "-j", jobs], cwd=ffbuild, stdout=log, stderr=subprocess.STDOUT, check=True)

        defines = ["HAVE_UNISTD_H", "HAVE_FCNTL_H", "HAVE_DLFCN_H", "HAVE_DLADDR", "HAVE_PTHREAD_H", "HAVE_DIRENT_H", "HAVE_SYS_STAT_H",
                   "HAVE_INTTYPES_H", "HAVE_STDINT_H", "HAVE_STDLIB_H", "HAVE_STRING_H", "HAVE_STRINGS_H", "HAVE_MNTENT_H",
                   "HAVE_STRUCT_DIRENT_D_TYPE", "HAVE_CLOCK_GETTIME", "HAVE_TIME_H"]
        common = [f"--target={target}", "-fPIC", "-O2", "-D_GNU_SOURCE", "-D_FILE_OFFSET_BITS=64", "-D_ISOC99_SOURCE",
                  "-D_POSIX_C_SOURCE=200809L", "-DHAVE_SYS_TIME_H=1", '-DVERSION="1.3.4"', *["-D" + n + "=1" for n in defines],
                  "-I" + str(bluray / "src"), "-I" + str(bluray / "src/libbluray"), "-I" + str(udf / "src")]

        def compile_one(item):
            index, source = item
            obj = build / f"disc-{index}.o"
            result = subprocess.run([str(cc), *common, "-std=c99", "-w", "-c", str(source), "-o", str(obj)], capture_output=True, text=True)
            if result.returncode:
                raise RuntimeError(str(source) + "\n" + result.stderr)
            return obj

        with ThreadPoolExecutor(max_workers=int(jobs)) as pool:
            objects = list(pool.map(compile_one, enumerate(files)))
        out = args.out / abi / "libetkn_bluray.so"
        out.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(cxx), *common, "-shared", "-std=c++17", "-idirafter", str(ffbuild), "-idirafter", str(ffmpeg),
                        str(root / "third_party/bluray/bluray_jni.cpp"), *map(str, objects),
                        *[str(ffbuild / ("lib" + n) / ("lib" + n + ".a")) for n in ["avformat", "avcodec", "avutil"]],
                        "-ldl", "-lm", "-lz", "-llog", "-static-libstdc++", "-Wl,--no-undefined", "-Wl,--exclude-libs,ALL",
                        "-Wl,-z,max-page-size=16384", "-Wl,-soname,libetkn_bluray.so", "-o", str(out)], check=True)
        subprocess.run([str(toolbin / "llvm-strip"), "--strip-unneeded", str(out)], check=True)
        print(abi, out.stat().st_size, hashlib.sha256(out.read_bytes()).hexdigest(), flush=True)


if __name__ == "__main__":
    main()
