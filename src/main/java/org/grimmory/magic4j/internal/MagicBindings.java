package org.grimmory.magic4j.internal;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFM downcall handles for the libmagic C API ({@code <magic.h>}).
 *
 * <p>This class must only be loaded after {@link NativeLoader#ensureLoaded()} has successfully
 * called {@link System#load}, so that {@link SymbolLookup#loaderLookup()} can resolve all symbols.
 */
public final class MagicBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private MagicBindings() {}

  private static MethodHandle downcall(String name, FunctionDescriptor desc) {
    return LINKER.downcallHandle(
        LOOKUP
            .find(name)
            .orElseThrow(() -> new UnsatisfiedLinkError("libmagic symbol not found: " + name)),
        desc);
  }

  /**
   * {@code magic_t magic_open(int flags)} — create and return a magic cookie. Returns {@code NULL}
   * on allocation failure.
   */
  public static final MethodHandle MAGIC_OPEN =
      downcall("magic_open", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

  /** {@code void magic_close(magic_t cookie)} — close the cookie and free all resources. */
  public static final MethodHandle MAGIC_CLOSE =
      downcall("magic_close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

  /**
   * {@code int magic_load(magic_t cookie, const char *filename)} — load the magic database from a
   * colon-separated list of file paths, or the compiled-in default when {@code filename} is {@code
   * NULL}. Returns 0 on success, -1 on failure.
   */
  public static final MethodHandle MAGIC_LOAD =
      downcall("magic_load", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  /**
   * {@code int magic_load_buffers(magic_t cookie, void **buffers, size_t *sizes, size_t nbuffers)}
   * — load the magic database from in-memory buffers. Returns 0 on success, -1 on failure.
   */
  public static final MethodHandle MAGIC_LOAD_BUFFERS =
      downcall(
          "magic_load_buffers",
          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

  /**
   * {@code const char *magic_file(magic_t cookie, const char *filename)} — return a string
   * describing the type of the named file. Returns {@code NULL} on error; the string is owned by
   * libmagic.
   */
  public static final MethodHandle MAGIC_FILE =
      downcall("magic_file", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  /**
   * {@code const char *magic_buffer(magic_t cookie, const void *buffer, size_t length)} — return a
   * string describing the type of the given memory buffer. Returns {@code NULL} on error; the
   * string is owned by libmagic.
   */
  public static final MethodHandle MAGIC_BUFFER =
      downcall("magic_buffer", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

  /**
   * {@code const char *magic_error(magic_t cookie)} — return a textual description of the last
   * error, or {@code NULL} if there was no error.
   */
  public static final MethodHandle MAGIC_ERROR =
      downcall("magic_error", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  /**
   * {@code int magic_setflags(magic_t cookie, int flags)} — set flags on an existing cookie.
   * Returns -1 on systems that don't support {@code utime(3)} when {@link
   * org.grimmory.magic4j.MagicFlags#MAGIC_PRESERVE_ATIME} is set.
   */
  public static final MethodHandle MAGIC_SETFLAGS =
      downcall("magic_setflags", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

  /**
   * {@code int magic_version(void)} — return the version number of the libmagic shared library
   * compiled into the binary.
   */
  public static final MethodHandle MAGIC_VERSION =
      downcall("magic_version", FunctionDescriptor.of(ValueLayout.JAVA_INT));
}
