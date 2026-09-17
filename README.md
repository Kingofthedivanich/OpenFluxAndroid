# OpenFluxAndroid (fork)

Fork of [p1neappleXpress/OpenFluxAndroid](https://github.com/p1neappleXpress/OpenFluxAndroid),
kept in sync with upstream `main` (currently `1.1.0`) — see
[What changed vs. upstream](#what-changed-vs-upstream) for the actual delta,
which is small: upstream grew a full UI for every transport, `--codec`,
`--encryption-key-file`, a DNS relay, and more, independently of this fork
and largely superseding what this fork used to add by hand.

## What changed vs. upstream

- `applicationId` is suffixed with `.fork` (`io.github.p1neapplexpress.openflux.fork`)
  so it installs alongside the upstream release instead of conflicting with
  its signature.
- The native core binaries (`libp1npplydtransport.so`) are built from
  [Kingofthedivanich/OpenFlux-WebGui](https://github.com/Kingofthedivanich/OpenFlux-WebGui)
  instead of upstream's vanilla `p1neappleXpress/OpenFlux` — same wire
  protocol and transports, plus that fork's buffer-pooling speedup (used
  symmetrically by client and exit node) and exit-node-side fixes. See its
  README for what's different there.

That's it. Everything else (UI, DNS relay, the JNI crash fix, 16KB page
alignment, proguard rules, tests) comes straight from upstream.

## Building the native core binary

Requires Go (see `../core/go.mod`), Android NDK r27+, and `ANDROID_NDK_HOME`
set. Upstream's own script handles this — point it at our core fork:

```bash
ANDROID_NDK_HOME=/path/to/ndk ./app/src/main/build-openflux.sh /path/to/OpenFlux-WebGui
```

Installs `libp1npplydtransport.so` into `app/src/main/jniLibs/<abi>/` for
every ABI the app ships and writes `app/src/main/openflux-version.txt` with
the exact commit built. On Windows, `ANDROID_NDK_HOME` must point at an NDK
whose `clang` accepts `--target=`; r27+ does.

To also refresh `libtun2socks.so` / `libpdnsd.so` / `libsystem.so` (only
needed if you touched `app/src/main/jni/`):

```bash
NDK_BUILD=/path/to/ndk-build.cmd ./app/src/main/build-jni.sh
```

## Building the app

Requires JDK 17 and the Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 26).

```bash
./gradlew assembleDebug
```
