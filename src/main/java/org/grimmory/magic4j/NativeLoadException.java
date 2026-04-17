package org.grimmory.magic4j;

import java.io.Serial;

/** Thrown when the libmagic native library cannot be loaded from the classpath or the system. */
public class NativeLoadException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public NativeLoadException(String message) {
    super(message);
  }

  public NativeLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
