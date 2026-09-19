# OpenFluxAndroid (fork)

Fork of [p1neappleXpress/OpenFluxAndroid](https://github.com/p1neappleXpress/OpenFluxAndroid),
kept in sync with upstream `main` (currently `1.1.0`). Paired with the core
fork [Kingofthedivanich/OpenFlux-WebGui](https://github.com/Kingofthedivanich/OpenFlux-WebGui) —
this app's own delta from upstream is small (an `applicationId` suffix and
a client-side encryption UI for that core fork's Noise handshake); nearly
everything else (transport UI, DNS relay, the JNI crash fix, 16KB page
alignment, proguard rules, tests) comes straight from upstream.

**Releases:** [OpenFluxAndroid fork releases](https://github.com/Kingofthedivanich/OpenFluxAndroid/releases) —
debug APK, built against the paired core fork's current commit.

## What changed vs. upstream

- `applicationId` is suffixed with `.fork` (`io.github.p1neapplexpress.openflux.fork`)
  so it installs alongside the upstream release instead of conflicting with
  its signature.
- The native core binaries (`libp1npplydtransport.so`) are built from
  [Kingofthedivanich/OpenFlux-WebGui](https://github.com/Kingofthedivanich/OpenFlux-WebGui)
  instead of upstream's vanilla `p1neappleXpress/OpenFlux` — same wire
  protocol and transports, plus that fork's Noise NKpsk0 encryption,
  multi-client exit panel, multi-stream, netguard, and exit-node-side
  fixes. See its README for what's different there.
- **Exit node public key field**, added because the paired core fork made
  encryption mandatory by default (see [Encryption](#encryption) below) —
  upstream's own encryption UI targets upstream's core, which this app
  doesn't run against.

## Encryption

The paired core fork's exit node refuses to run at all without either an
encryption key or `--allow-plaintext` — so every tunnel config here needs
one of two things:

- **Nothing** (default): the app sends `--allow-plaintext` automatically.
  Existing configs keep working exactly as before; the tunnel is
  unauthenticated and unencrypted, same as pre-Noise behavior.
- **Exit node public key** (add/edit a tunnel → "Exit node public key
  (Noise)"): paste the base64 key the exit node prints at startup
  (`--exit-key-file` banner) or shows in the admin panel / Telegram bot
  (`/key`). Turns on a real Noise NKpsk0 handshake (X25519 + AES-256-GCM,
  rotating session keys) instead of plaintext. Validated client-side as a
  32-byte base64 value before it's saved.
  - An optional **PSK** (the old "encryption key" field, now a plain
    shared secret ≥16 characters) can be set *alongside* a peer key to
    additionally close that one tunnel to anyone without the secret — the
    core rejects a PSK without a peer key, and the app enforces the same
    pairing before saving.

If a tunnel that used to work suddenly fails after the exit node was
updated, this is almost always why — get the new public key from whoever
runs the exit node and paste it into that tunnel's config.

## Multi-stream

The **Document URL** field accepts a comma-separated list of URLs for the
Yandex.Docs / Yandex.Docs (Volga) transports — one tunnel spread across
several documents, with failover if one stops working. Nothing in the UI
labels this explicitly; it's just passed straight through to the core
binary's `--url`, which already supports it. Requires a peer running a
core build with multi-stream support (any post-Noise-merge release of the
paired fork) on both ends with the same document set.

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

Unit tests (`EncryptionKey`/`PeerKey` validators, `NativeArgs` flag
building, `TunnelPayload` parsing, and more):

```bash
./gradlew testDebugUnitTest
```
