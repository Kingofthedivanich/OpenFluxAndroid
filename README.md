# OpenFluxAndroid (fork)

## Description

Fork of [p1neappleXpress/OpenFluxAndroid](https://github.com/p1neappleXpress/OpenFluxAndroid),
paired with the core fork [Kingofthedivanich/OpenFlux-WebGui](https://github.com/Kingofthedivanich/OpenFlux-WebGui).
The delta from upstream is small: an `applicationId` suffix (installs
alongside the upstream release) and an exit-node public key / PSK field for
that core fork's mandatory Noise encryption. Everything else (transport UI,
DNS relay, JNI crash fix, 16KB page alignment, proguard rules, tests) is
upstream's unchanged.

**Releases:** [OpenFluxAndroid fork releases](https://github.com/Kingofthedivanich/OpenFluxAndroid/releases).

## How it works

- The native core binary (`libp1npplydtransport.so`) is built from the
  paired core fork instead of vanilla upstream — same transports and wire
  protocol, plus Noise NKpsk0 encryption, the multi-client exit panel, and
  multi-stream.
- **Encryption:** leave the peer-key field empty and the app sends
  `--allow-plaintext` automatically (unencrypted, same as before Noise). To
  turn on a real handshake, paste the exit node's public key (from its
  startup banner, admin panel, or `/key` in the Telegram bot) into a
  tunnel's "Exit node public key" field; an optional PSK can be set
  alongside it to further close that tunnel to strangers. If a working
  tunnel suddenly fails after the exit node was updated, it's almost always
  a stale/missing peer key.
- **Multi-stream:** the Document URL field accepts a comma-separated list
  for the Yandex.Docs / Volga transports — one tunnel spread across several
  documents with failover, straight through to the core binary's `--url`.

## Install guide

Native core binary (requires Go, Android NDK r27+, `ANDROID_NDK_HOME` set):

```bash
ANDROID_NDK_HOME=/path/to/ndk ./app/src/main/build-openflux.sh /path/to/OpenFlux-WebGui
```

Installs `libp1npplydtransport.so` into `app/src/main/jniLibs/<abi>/` for
every ABI. To also refresh `libtun2socks.so`/`libpdnsd.so`/`libsystem.so`
(only if you touched `app/src/main/jni/`):

```bash
NDK_BUILD=/path/to/ndk-build.cmd ./app/src/main/build-jni.sh
```

App (requires JDK 17, Android SDK, `compileSdk`/`targetSdk` 34, `minSdk` 26):

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest   # unit tests
```
