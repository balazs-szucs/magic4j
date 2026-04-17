package org.grimmory.magic4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.grimmory.magic4j.internal.FfmHelper;
import org.grimmory.magic4j.internal.MagicBindings;
import org.grimmory.magic4j.internal.NativeLoader;

/**
 * Thread-confined wrapper around a libmagic {@code magic_t} cookie.
 *
 * <p>Instances are <em>not</em> thread-safe. Each thread that needs concurrent detection should
 * create its own instance. Use try-with-resources to ensure the cookie is closed:
 *
 * <pre>{@code
 * try (Magic magic = Magic.open(MagicFlags.MAGIC_MIME_TYPE)) {
 *     String mimeType = magic.detect(bytes);
 * }
 * }</pre>
 *
 * <p>For one-shot detection, use the convenience statics:
 *
 * <pre>{@code
 * String mimeType = Magic.detectMimeType(bytes);
 * }</pre>
 */
public final class Magic implements AutoCloseable {

  private static final String DB_RESOURCE = "/magic.mgc";

  // Cached off-heap buffers for the bundled magic.mgc database.
  // Initialized once and kept alive for the lifetime of the JVM to satisfy libmagic's memory
  // requirements (buffers must outlive the magic_t cookie).
  private static volatile MemorySegment bundledDbBuffersArr = null;
  private static volatile MemorySegment bundledDbSizesArr = null;
  private static final Object DB_LOCK = new Object();

  private final MemorySegment cookie;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private Magic(MemorySegment cookie) {
    this.cookie = cookie;
  }

  /**
   * Opens a new magic cookie with the given flags and loads the bundled {@code magic.mgc} database
   * from the classpath.
   *
   * @param flags one or more {@link MagicFlags} constants OR-ed together
   * @throws NativeLoadException if libmagic cannot be loaded
   * @throws MagicException if the cookie cannot be created or the database cannot be loaded
   */
  public static Magic open(int flags) {
    NativeLoader.ensureLoaded();
    MemorySegment cookie = openCookie(flags);
    loadBundledDatabase(cookie);
    return new Magic(cookie);
  }

  /**
   * Opens a new magic cookie with the given flags and loads a magic database from the given path.
   *
   * @param flags one or more {@link MagicFlags} constants OR-ed together
   * @param databasePath path to a {@code .mgc} compiled magic database (or colon-separated list)
   * @throws NativeLoadException if libmagic cannot be loaded
   * @throws MagicException if the cookie cannot be created or the database cannot be loaded
   */
  public static Magic open(int flags, Path databasePath) {
    NativeLoader.ensureLoaded();
    MemorySegment cookie = openCookie(flags);
    loadDatabaseFromPath(cookie, databasePath);
    return new Magic(cookie);
  }

  /**
   * Identifies the type of the given byte array.
   *
   * @return a string describing the type (format depends on flags passed to {@link #open(int)})
   * @throws MagicException if detection fails or this instance has been closed
   */
  public String detect(byte[] data) {
    return detect(data, 0, data.length);
  }

  /**
   * Identifies the type of a slice of the given byte array.
   *
   * @param data source array
   * @param offset starting offset within {@code data}
   * @param length number of bytes to examine
   * @return a string describing the type
   * @throws MagicException if detection fails or this instance has been closed
   */
  public String detect(byte[] data, int offset, int length) {
    checkOpen();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buf = arena.allocate(length);
      buf.copyFrom(MemorySegment.ofArray(data).asSlice(offset, length));
      MemorySegment result =
          (MemorySegment) MagicBindings.MAGIC_BUFFER.invokeExact(cookie, buf, (long) length);
      if (FfmHelper.isNull(result)) {
        throw new MagicException("magic_buffer() failed: " + magicError(cookie));
      }
      return FfmHelper.fromCString(result);
    } catch (MagicException e) {
      throw e;
    } catch (Throwable t) {
      throw new MagicException("magic_buffer() invocation failed", t);
    }
  }

  /**
   * Identifies the type of the given file.
   *
   * @param file path to the file to examine
   * @return a string describing the type
   * @throws MagicException if detection fails or this instance has been closed
   */
  public String detect(Path file) {
    checkOpen();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pathSeg = FfmHelper.toCString(arena, file.toAbsolutePath().toString());
      MemorySegment result = (MemorySegment) MagicBindings.MAGIC_FILE.invokeExact(cookie, pathSeg);
      if (FfmHelper.isNull(result)) {
        throw new MagicException("magic_file() failed: " + magicError(cookie));
      }
      return FfmHelper.fromCString(result);
    } catch (MagicException e) {
      throw e;
    } catch (Throwable t) {
      throw new MagicException("magic_file() invocation failed", t);
    }
  }

  /**
   * Identifies the type of data read from the given input stream. The stream is consumed in full
   * via {@link InputStream#readAllBytes()} before detection begins.
   *
   * @param in the input stream to read
   * @return a string describing the type
   * @throws IOException if reading the stream fails
   * @throws MagicException if detection fails or this instance has been closed
   */
  public String detect(InputStream in) throws IOException {
    checkOpen();
    return detect(in.readAllBytes());
  }

  /**
   * Changes the active flags on this cookie without re-opening or re-loading the database. Useful
   * when reusing a single cookie to run different detection modes (e.g. first description, then
   * MIME type).
   *
   * @param flags new flags, replacing the current set entirely
   * @throws MagicException if the update fails or this instance has been closed
   */
  public void setFlags(int flags) {
    checkOpen();
    try {
      int rc = (int) MagicBindings.MAGIC_SETFLAGS.invokeExact(cookie, flags);
      if (rc != 0) {
        throw new MagicException(
            "magic_setflags() failed for flags 0x" + Integer.toHexString(flags));
      }
    } catch (MagicException e) {
      throw e;
    } catch (Throwable t) {
      throw new MagicException("magic_setflags() invocation failed", t);
    }
  }

  /**
   * One-shot MIME type detection from a byte array. Opens a cookie with {@link
   * MagicFlags#MAGIC_MIME_TYPE}, detects, and closes.
   *
   * @return a MIME type string such as {@code "image/png"}
   */
  public static String detectMimeType(byte[] data) {
    try (Magic magic = open(MagicFlags.MAGIC_MIME_TYPE)) {
      return magic.detect(data);
    }
  }

  /**
   * One-shot MIME type detection from a slice of a byte array.
   *
   * @return a MIME type string such as {@code "image/png"}
   */
  public static String detectMimeType(byte[] data, int offset, int length) {
    try (Magic magic = open(MagicFlags.MAGIC_MIME_TYPE)) {
      return magic.detect(data, offset, length);
    }
  }

  /**
   * One-shot MIME type detection from an input stream. The stream is consumed in full.
   *
   * @return a MIME type string such as {@code "audio/mpeg"}
   * @throws IOException if reading the stream fails
   */
  public static String detectMimeType(InputStream in) throws IOException {
    try (Magic magic = open(MagicFlags.MAGIC_MIME_TYPE)) {
      return magic.detect(in);
    }
  }

  /**
   * One-shot MIME type detection from a file. Opens a cookie with {@link
   * MagicFlags#MAGIC_MIME_TYPE}, detects, and closes.
   *
   * @return a MIME type string such as {@code "application/pdf"}
   */
  public static String detectMimeType(Path file) {
    try (Magic magic = open(MagicFlags.MAGIC_MIME_TYPE)) {
      return magic.detect(file);
    }
  }

  /**
   * Closes the underlying magic cookie. Safe to call more than once; subsequent calls are no-ops.
   */
  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        MagicBindings.MAGIC_CLOSE.invokeExact(cookie);
      } catch (Throwable t) {
        throw new MagicException("Failed to close libmagic cookie", t);
      }
    }
  }

  private static MemorySegment openCookie(int flags) {
    try {
      MemorySegment c = (MemorySegment) MagicBindings.MAGIC_OPEN.invokeExact(flags);
      if (FfmHelper.isNull(c)) {
        throw new MagicException(
            "magic_open() returned NULL — out of memory or unsupported flags: " + flags);
      }
      return c;
    } catch (MagicException e) {
      throw e;
    } catch (Throwable t) {
      throw new MagicException("Failed to open libmagic cookie", t);
    }
  }

  private static void loadBundledDatabase(MemorySegment cookie) {
    ensureBundledDatabaseInitialized();
    try {
      int rc =
          (int)
              MagicBindings.MAGIC_LOAD_BUFFERS.invokeExact(
                  cookie, bundledDbBuffersArr, bundledDbSizesArr, 1L);
      if (rc != 0) {
        throw new MagicException("magic_load_buffers() failed: " + magicError(cookie));
      }
    } catch (MagicException e) {
      throw e;
    } catch (Throwable t) {
      throw new MagicException("Failed to load bundled magic database", t);
    }
  }

  private static void loadDatabaseFromPath(MemorySegment cookie, Path path) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pathSeg = FfmHelper.toCString(arena, path.toAbsolutePath().toString());
      int rc = (int) MagicBindings.MAGIC_LOAD.invokeExact(cookie, pathSeg);
      if (rc != 0) {
        throw new MagicException("magic_load() failed for " + path + ": " + magicError(cookie));
      }
    } catch (MagicException e) {
      throw e;
    } catch (Throwable t) {
      throw new MagicException("Failed to load magic database from " + path, t);
    }
  }

  private static void ensureBundledDatabaseInitialized() {
    if (bundledDbBuffersArr != null) return;
    synchronized (DB_LOCK) {
      if (bundledDbBuffersArr != null) return;

      try (InputStream is = Magic.class.getResourceAsStream(DB_RESOURCE)) {
        if (is == null) {
          throw new MagicException(
              "Bundled magic database not found at '"
                  + DB_RESOURCE
                  + "'. Ensure the magic4j JAR is complete, or use Magic.open(flags, databasePath)"
                  + " to supply the database explicitly.");
        }

        byte[] db = is.readAllBytes();
        // Use an automatic arena so the memory is freed only when the class is unloaded.
        Arena arena = Arena.ofAuto();

        // 1. Allocate off-heap buffer and copy database bytes.
        MemorySegment dataBuffer = arena.allocate(db.length);
        dataBuffer.copyFrom(MemorySegment.ofArray(db));

        // 2. Allocate the pointers array (void *buffers[1])
        MemorySegment buffersArr = arena.allocate(ValueLayout.ADDRESS);
        buffersArr.set(ValueLayout.ADDRESS, 0, dataBuffer);

        // 3. Allocate the sizes array (size_t sizes[1])
        MemorySegment sizesArr = arena.allocate(ValueLayout.JAVA_LONG);
        sizesArr.set(ValueLayout.JAVA_LONG, 0, db.length);

        // Publish to static fields.
        bundledDbSizesArr = sizesArr;
        bundledDbBuffersArr = buffersArr;
      } catch (IOException e) {
        throw new MagicException("Failed to read bundled magic database", e);
      }
    }
  }

  private void checkOpen() {
    if (closed.get()) {
      throw new MagicException("This Magic instance has already been closed");
    }
  }

  private static String magicError(MemorySegment cookie) {
    try {
      MemorySegment errPtr = (MemorySegment) MagicBindings.MAGIC_ERROR.invokeExact(cookie);
      if (FfmHelper.isNull(errPtr)) return "(no error)";
      String msg = FfmHelper.fromCString(errPtr);
      return msg != null ? msg : "(no error)";
    } catch (Throwable t) {
      return "(could not retrieve error: " + t.getMessage() + ")";
    }
  }
}
