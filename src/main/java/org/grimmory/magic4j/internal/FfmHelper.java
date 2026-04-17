package org.grimmory.magic4j.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/** Utility methods for Foreign Function &amp; Memory interop with libmagic. */
public final class FfmHelper {

  private FfmHelper() {}

  /**
   * Encodes a Java String to a null-terminated UTF-8 {@link MemorySegment} ({@code const char*}).
   */
  public static MemorySegment toCString(Arena arena, String text) {
    byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
    MemorySegment seg = arena.allocate(encoded.length + 1L);
    MemorySegment.copy(encoded, 0, seg, ValueLayout.JAVA_BYTE, 0, encoded.length);
    seg.set(ValueLayout.JAVA_BYTE, encoded.length, (byte) 0);
    return seg;
  }

  /**
   * Decodes a null-terminated UTF-8 {@code const char*} returned by libmagic into a Java String.
   * The returned pointer is owned by libmagic and must not be freed.
   */
  public static String fromCString(MemorySegment ptr) {
    if (isNull(ptr)) return null;
    // Reinterpret to unbounded so we can walk past the segment's declared size.
    MemorySegment unbounded = ptr.reinterpret(Long.MAX_VALUE);
    long len = 0;
    while (unbounded.get(ValueLayout.JAVA_BYTE, len) != 0) {
      len++;
    }
    if (len == 0) return "";
    byte[] data = unbounded.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
    return new String(data, StandardCharsets.UTF_8);
  }

  /** Returns {@code true} if the given segment represents a C {@code NULL} pointer. */
  public static boolean isNull(MemorySegment seg) {
    return seg == null || seg.equals(MemorySegment.NULL) || seg.address() == 0;
  }
}
