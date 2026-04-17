package org.grimmory.magic4j;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.grimmory.magic4j.internal.NativeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MagicTest {

  // PNG: \x89PNG\r\n\x1a\n + minimal IHDR chunk header
  private static final byte[] PNG_BYTES = {
    (byte) 0x89,
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
    0x00,
    0x00,
    0x00,
    0x0D,
    0x49,
    0x48,
    0x44,
    0x52
  };

  // JPEG: FF D8 FF E0 ... JFIF
  private static final byte[] JPEG_BYTES = {
    (byte) 0xFF,
    (byte) 0xD8,
    (byte) 0xFF,
    (byte) 0xE0,
    0x00,
    0x10,
    0x4A,
    0x46,
    0x49,
    0x46,
    0x00,
    0x01
  };

  // PDF: %PDF-1.4
  private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

  // Plain text
  private static final byte[] TEXT_BYTES =
      "Hello, world! This is plain text.".getBytes(StandardCharsets.UTF_8);

  @Test
  void detectPngMimeType() {
    assertEquals("image/png", Magic.detectMimeType(PNG_BYTES));
  }

  @Test
  void detectJpegMimeType() {
    assertEquals("image/jpeg", Magic.detectMimeType(JPEG_BYTES));
  }

  @Test
  void detectPdfMimeType() {
    assertEquals("application/pdf", Magic.detectMimeType(PDF_BYTES));
  }

  @Test
  void detectTextMimeType() {
    String result = Magic.detectMimeType(TEXT_BYTES);
    assertTrue(result.startsWith("text/"), "Expected text/* MIME type, got: " + result);
  }

  @Test
  void detectMimeTypeFromFile(@TempDir Path tmpDir) throws IOException {
    Path file = tmpDir.resolve("test.png");
    Files.write(file, PNG_BYTES);
    assertEquals("image/png", Magic.detectMimeType(file));
  }

  @Test
  void detectJpegFromFile(@TempDir Path tmpDir) throws IOException {
    Path file = tmpDir.resolve("test.jpg");
    Files.write(file, JPEG_BYTES);
    assertEquals("image/jpeg", Magic.detectMimeType(file));
  }

  @Test
  void detectWithSlice() {
    // Prepend 4 garbage bytes; detect on the PNG slice starting at offset 4.
    byte[] data = new byte[4 + PNG_BYTES.length];
    System.arraycopy(PNG_BYTES, 0, data, 4, PNG_BYTES.length);
    try (Magic magic = Magic.open(MagicFlags.MAGIC_MIME_TYPE)) {
      assertEquals("image/png", magic.detect(data, 4, PNG_BYTES.length));
    }
  }

  @Test
  void doubleCloseIsSafe() {
    Magic magic = Magic.open(MagicFlags.MAGIC_MIME_TYPE);
    magic.close();
    assertDoesNotThrow(magic::close);
  }

  @Test
  void useAfterCloseThrows() {
    Magic magic = Magic.open(MagicFlags.MAGIC_MIME_TYPE);
    magic.close();
    assertThrows(MagicException.class, () -> magic.detect(PNG_BYTES));
  }

  @Test
  void magicMimeFlagIncludesCharset() {
    try (Magic magic = Magic.open(MagicFlags.MAGIC_MIME)) {
      String result = magic.detect(PNG_BYTES);
      assertTrue(result.contains("image/png"), "Expected image/png in result: " + result);
      assertTrue(result.contains("charset="), "Expected charset in MIME result: " + result);
    }
  }

  @Test
  void tryWithResourcesPattern() {
    // Verifies the AutoCloseable contract works correctly in practice.
    String result;
    try (Magic magic = Magic.open(MagicFlags.MAGIC_MIME_TYPE)) {
      result = magic.detect(PDF_BYTES);
    }
    assertEquals("application/pdf", result);
  }

  @Test
  void libraryVersionIsPositive() {
    // Trigger library load, then query the compiled-in version number.
    NativeLoader.ensureLoaded();
    try {
      int version = (int) org.grimmory.magic4j.internal.MagicBindings.MAGIC_VERSION.invokeExact();
      assertTrue(version > 0, "Expected positive version number, got: " + version);
    } catch (Throwable t) {
      fail("magic_version() invocation failed: " + t);
    }
  }
}
