package org.grimmory.magic4j;

import java.io.Serial;

/** Thrown when a libmagic operation fails (e.g. detection returns {@code NULL}). */
public class MagicException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MagicException(String message) {
    super(message);
  }

  public MagicException(String message, Throwable cause) {
    super(message, cause);
  }
}
