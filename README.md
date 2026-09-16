# OpenFluxAndroid (fork, targeting core 0.0.3)

Fork of [p1neappleXpress/OpenFluxAndroid](https://github.com/p1neappleXpress/OpenFluxAndroid)
(`rel 0.0.2`), updated to run against
[p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux) `0.0.3`.

## What changed vs. upstream 0.0.2

- CLI args the app builds for the native binary now use the canonical
  `--role client --transport <type> ...` form instead of the deprecated
  `--client` flag main.go only aliases for one release (see
  [`AddTunFragment.kt`](app/src/main/java/io/github/p1neapplexpress/openflux/ui/AddTunFragment.kt)).
- Added the two transports core 0.0.3 shipped that the app didn't expose yet:
  `cupsonline` (Cups.online) and `mailru` (Mail.ru Docs) — both just take a
  `--url`, like Yandex.Docs/Volga.
- [`../core/build_android.sh`](../core/build_android.sh) was host-OS aware
  (macOS/Linux/Windows) and builds `arm64-v8a` + `armeabi-v7a` + `x86_64` —
  upstream only cross-built `arm64-v8a` from a macOS host, so the other two
  ABIs the app ships `jniLibs` for had no `openflux` binary at all.

## Building the native core binary

Requires Go 1.25+, Android NDK r27+, and `ANDROID_NDK_HOME` set.

```bash
ANDROID_NDK_HOME=/path/to/ndk/27.x ./build-core.sh
```

This builds `openflux` from the sibling `../core` checkout for every ABI and
installs it as `app/src/main/jniLibs/<abi>/libp1npplydtransport.so` — the
path [`NativeProcessSupervisor.kt`](app/src/main/java/io/github/p1neapplexpress/openflux/service/NativeProcessSupervisor.kt)
execs at runtime. Point `CORE_DIR` elsewhere if the core checkout isn't a
sibling directory.

## Building the app

Requires JDK 17 and the Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 26).

```bash
./gradlew assembleDebug
```

## Known gaps

- `armeabi-v7a` / `x86_64` core binaries are new (never shipped upstream) —
  test on those ABIs before relying on them.
- The exit-node's default codec is `batched` (zstd) as of core 0.0.3; the
  client doesn't need any flag for this since it just inherits the binary's
  default, but an exit node running the old 0.0.2 binary won't speak the same
  wire format — both ends must be on 0.0.3+.
- New core 0.0.3 features not yet exposed in the UI: `--codec=legacy` and
  `--encryption-key-file` (AES-256-GCM). Straightforward to add as extra
  fields on `AddTunFragment` if needed.
