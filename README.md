# magic4j

A zero-dependency Java 22+ library that wraps [libmagic](https://www.darwinsys.com/file/) using the
stable Foreign Function & Memory (FFM) API.  No JNI glue code, no native stubs to compile, no
Reflection, just direct Java-to-native calls.

---

## Requirements

- **Java 25+**
- **libmagic** installed on the host:
  - macOS: `brew install libmagic`
  - Ubuntu/Debian: `apt-get install libmagic1`
  - Alpine: `apk add libmagic`
---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.grimmory:magic4j:0.1.0")

    // Pick exactly one native classifier for your target platform:
    runtimeOnly("org.grimmory:magic4j:0.1.0:natives-linux-x64")
    // runtimeOnly("org.grimmory:magic4j:0.1.0:natives-linux-arm64")
    // runtimeOnly("org.grimmory:magic4j:0.1.0:natives-linux-musl-x64")
    // runtimeOnly("org.grimmory:magic4j:0.1.0:natives-linux-musl-arm64")
    // runtimeOnly("org.grimmory:magic4j:0.1.0:natives-darwin-x64")
    // runtimeOnly("org.grimmory:magic4j:0.1.0:natives-darwin-arm64")
    // runtimeOnly("org.grimmory:magic4j:0.1.0:natives-windows-x64")
}
```

### Maven

```xml
<dependencies>
  <dependency>
    <groupId>org.grimmory</groupId>
    <artifactId>magic4j</artifactId>
    <version>0.1.0</version>
  </dependency>
  <!-- Pick one native classifier: -->
  <dependency>
    <groupId>org.grimmory</groupId>
    <artifactId>magic4j</artifactId>
    <version>0.1.0</version>
    <classifier>natives-linux-x64</classifier>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

---

## Quick start

### Detect a MIME type, one-shot, no lifecycle management

```java
// From a file path (recommended for media files, see note below)
String mime = Magic.detectMimeType(Path.of("/path/to/file.mp3"));
// "audio/mpeg"

// From a byte array (works reliably for PNG, JPEG, ZIP, RAR, PDF, FLAC, etc.)
byte[] bytes = Files.readAllBytes(Path.of("cover.png"));
String mime = Magic.detectMimeType(bytes);
// "image/png"

// From an InputStream
try (InputStream in = new FileInputStream("document.pdf")) {
    String mime = Magic.detectMimeType(in);
    // "application/pdf"
}

// From a byte-array slice (e.g. embedded data at a known offset)
String mime = Magic.detectMimeType(buffer, offset, length);
```

### Detect type and MIME charset together (one open, two detections)

```java
try (Magic mimeType = Magic.open(MagicFlags.MAGIC_MIME_TYPE);
     Magic mimeCharset = Magic.open(MagicFlags.MAGIC_MIME_ENCODING)) {

    String type    = mimeType.detect(bytes);    // "text/plain"
    String charset = mimeCharset.detect(bytes); // "utf-8"
    String full    = type + "; charset=" + charset;
}
```

### Reuse one cookie with different flags via `setFlags()`

```java
try (Magic magic = Magic.open(MagicFlags.MAGIC_NONE)) {
    String desc = magic.detect(bytes);        // "PNG image data, 800 x 600, 8-bit/color RGB"
    magic.setFlags(MagicFlags.MAGIC_MIME_TYPE);
    String mime = magic.detect(bytes);        // "image/png"
}
```

### Use a custom magic database

```java
Path myDb = Path.of("/usr/share/misc/magic.mgc");
try (Magic magic = Magic.open(MagicFlags.MAGIC_MIME_TYPE, myDb)) {
    String mime = magic.detect(Path.of("unknown.bin"));
}
```

---

## API overview

### `Magic`, core class

| Method | Description |
|---|---|
| `Magic.open(int flags)` | Open a cookie with the bundled database |
| `Magic.open(int flags, Path db)` | Open a cookie with a custom database |
| `magic.detect(byte[])` | Detect type of a byte array |
| `magic.detect(byte[], int offset, int length)` | Detect type of a slice |
| `magic.detect(Path)` | Detect type of a file |
| `magic.detect(InputStream)` | Detect type from a stream (reads all bytes) |
| `magic.setFlags(int flags)` | Change flags without re-opening |
| `magic.close()` | Release the native cookie (also called by try-with-resources) |
| `Magic.detectMimeType(byte[])` | Convenience one-shot: MIME type from bytes |
| `Magic.detectMimeType(byte[], int, int)` | Convenience one-shot: MIME type from slice |
| `Magic.detectMimeType(InputStream)` | Convenience one-shot: MIME type from stream |
| `Magic.detectMimeType(Path)` | Convenience one-shot: MIME type from file |

All instance methods throw `MagicException` (unchecked) on failure. `detect(InputStream)` and
`detectMimeType(InputStream)` additionally declare `IOException`.

### `MagicFlags`, common constants

| Constant | Value | Effect |
|---|---|---|
| `MAGIC_NONE` | `0` | Return a human-readable description |
| `MAGIC_MIME_TYPE` | `0x10` | Return only the MIME type |
| `MAGIC_MIME_ENCODING` | `0x400` | Return only the charset |
| `MAGIC_MIME` | `0x410` | Return `type; charset=enc` |
| `MAGIC_COMPRESS` | `0x4` | Decompress compressed files before examining |
| `MAGIC_NO_CHECK_COMPRESS` | `0x1000` | Disable the compress checker |
| `MAGIC_SYMLINK` | `0x2` | Follow symlinks |
| `MAGIC_CONTINUE` | `0x20` | Return all matching descriptions |
| `MAGIC_DEBUG` | `0x1` | Print debug output |

Flags can be OR-ed: `MagicFlags.MAGIC_MIME_TYPE | MagicFlags.MAGIC_COMPRESS`.

---

## Thread safety

`Magic` instances are **not thread-safe**.  Each thread must create its own instance.  The
convenience statics (`Magic.detectMimeType(…)`) each open, use, and close a cookie, they are
safe to call concurrently from multiple threads.

For high-throughput servers consider a thread-local or a pool:

```java
// Thread-local pattern
private static final ThreadLocal<Magic> MAGIC =
    ThreadLocal.withInitial(() -> Magic.open(MagicFlags.MAGIC_MIME_TYPE));

// Caller:
String mime = MAGIC.get().detect(bytes);
```

---

## Supported formats (verified)

The table below lists formats verified by the test suite against libmagic 5.47 (Homebrew) on macOS
and against system libmagic on Ubuntu.  Formats marked **file** are only reliably detected when
using the file-path API (`detect(Path)`) on macOS; they work with both APIs on Linux.

| Format | MIME type returned | Detection method |
|---|---|---|
| PNG | `image/png` | buffer + file |
| JPEG | `image/jpeg` | buffer + file |
| GIF | `image/gif` | buffer + file |
| TIFF | `image/tiff` | buffer + file |
| SVG | `image/svg+xml` | buffer + file |
| BMP | `image/bmp` | **file** |
| WebP | `image/webp` | **file** |
| FLAC | `audio/flac` | buffer + file |
| MP3 | `audio/mpeg` | **file** |
| WAV | `audio/x-wav` / `audio/wav` | **file** |
| AIFF | `audio/x-aiff` | **file** |
| M4A | `audio/x-m4a` (macOS) / `audio/mp4` (Linux) | **file** |
| M4B | `audio/x-m4a` (macOS) / `audio/mp4` (Linux) | **file** |
| OGG / Opus | `audio/ogg` | **file** |
| ZIP | `application/zip` | buffer + file |
| RAR v4 | `application/vnd.rar` | buffer + file |
| RAR v5 | `application/vnd.rar` | buffer + file |
| 7-Zip | `application/x-7z-compressed` | buffer + file |
| GZip | `application/gzip` | buffer + file |
| CBZ (comic) | `application/zip` | buffer + file |
| CBR (comic) | `application/vnd.rar` | buffer + file |
| PDF | `application/pdf` | buffer + file |
| EPUB | `application/epub+zip` / `application/zip`¹ | file |
| MOBI | `application/x-mobipocket-ebook` | buffer + file |
| FB2 | `text/xml` / `application/x-fictionbook+xml` | buffer + file |

¹ EPUB detection requires the EPUB-specific magic rule to be present in the database. Databases
without it fall back to `application/zip`, which is technically correct since EPUB is a ZIP
container.

---

## macOS buffer-detection note

On macOS, libmagic's `magic_buffer()` function does **not** invoke OS-level content-analysis APIs
(Core Services / UTTypeConformsTo).  As a result, RIFF-family formats (WAV, AIFF, WebP) and
ISO Base Media File Format containers (M4A, M4B, MP4) may be returned as
`application/octet-stream` when detected from a byte array.

`magic_file()` always uses the full detection engine and works correctly on all platforms.

```java
// Preferred, uses magic_file() internally
String mime = Magic.detectMimeType(Path.of("/music/track.m4b"));

// Avoid for audio/RIFF formats on macOS, uses magic_buffer() internally
byte[] bytes = Files.readAllBytes(Path.of("/music/track.m4b"));
String mime = Magic.detectMimeType(bytes); // may return "application/octet-stream" on macOS
```

## Project structure

```
src/
  main/java/org/grimmory/magic4j/
    Magic.java             # Public API, open, detect, setFlags, close
    MagicFlags.java        # Flag constants (MAGIC_MIME_TYPE, MAGIC_NONE, …)
    MagicException.java    # Unchecked exception for libmagic errors
    NativeLoadException.java
    internal/
      NativeLoader.java    # Extracts libmagic from the classifier JAR at runtime
      MagicBindings.java   # FFM method handles for magic_open/close/buffer/file/…
      FfmHelper.java       # C-string ↔ Java String, null-pointer checks
  main/resources/
    magic.mgc              # Bundled compiled magic database
    natives/{platform}/    # Populated by CI; empty in source tree
  test/java/org/grimmory/magic4j/
    MagicTest.java         # Core API tests (open/close, error paths, thread safety)
    FormatDetectionTest.java # Format detection tests (47 tests)
```
