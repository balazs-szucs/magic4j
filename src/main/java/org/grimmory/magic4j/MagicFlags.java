package org.grimmory.magic4j;

/** Flag constants for {@link Magic#open(int)}, mirroring {@code <magic.h>} values exactly. */
public final class MagicFlags {

  private MagicFlags() {}

  /** No special handling. */
  public static final int MAGIC_NONE = 0x0000000;

  /** Print debugging messages to stderr. */
  public static final int MAGIC_DEBUG = 0x0000001;

  /** Follow symlinks. */
  public static final int MAGIC_SYMLINK = 0x0000002;

  /** Unpack compressed files and examine their contents. */
  public static final int MAGIC_COMPRESS = 0x0000004;

  /** Look inside block/character special devices. */
  public static final int MAGIC_DEVICES = 0x0000008;

  /** Return a MIME type string instead of a textual description. */
  public static final int MAGIC_MIME_TYPE = 0x0000010;

  /** Return all matches, not just the first. */
  public static final int MAGIC_CONTINUE = 0x0000020;

  /** Check the magic database for consistency. */
  public static final int MAGIC_CHECK = 0x0000040;

  /** Attempt to preserve the access time of analyzed files. */
  public static final int MAGIC_PRESERVE_ATIME = 0x0000080;

  /** Don't translate unprintable characters to {@code \ooo} octal representation. */
  public static final int MAGIC_RAW = 0x0000100;

  /** Treat OS errors while opening files as real errors instead of silently printing them. */
  public static final int MAGIC_ERROR = 0x0000200;

  /** Return a MIME encoding string instead of a textual description. */
  public static final int MAGIC_MIME_ENCODING = 0x0000400;

  /**
   * Shorthand for {@link #MAGIC_MIME_TYPE} | {@link #MAGIC_MIME_ENCODING}. Returns a full MIME type
   * string including charset, e.g. {@code "image/png; charset=binary"}.
   */
  public static final int MAGIC_MIME = MAGIC_MIME_TYPE | MAGIC_MIME_ENCODING;

  /** Return the Apple creator and type (macOS-specific). */
  public static final int MAGIC_APPLE = 0x0000800;

  /** Don't look inside compressed files. */
  public static final int MAGIC_NO_CHECK_COMPRESS = 0x0001000;

  /** Don't examine tar files. */
  public static final int MAGIC_NO_CHECK_TAR = 0x0002000;

  /** Don't consult magic files (soft magic). */
  public static final int MAGIC_NO_CHECK_SOFT = 0x0004000;

  /** Don't check for EMX application type (EMX only). */
  public static final int MAGIC_NO_CHECK_APPTYPE = 0x0008000;

  /** Don't print ELF details. */
  public static final int MAGIC_NO_CHECK_ELF = 0x0010000;

  /** Don't check for various types of text files. */
  public static final int MAGIC_NO_CHECK_TEXT = 0x0020000;

  /** Don't get extra information on MS Composite Document Files. */
  public static final int MAGIC_NO_CHECK_CDF = 0x0040000;

  /** Don't examine CSV files. */
  public static final int MAGIC_NO_CHECK_CSV = 0x0080000;

  /** Don't look for known tokens inside ASCII files. */
  public static final int MAGIC_NO_CHECK_TOKENS = 0x0100000;

  /** Don't check text encodings. */
  public static final int MAGIC_NO_CHECK_ENCODING = 0x0200000;

  /** Don't examine JSON files. */
  public static final int MAGIC_NO_CHECK_JSON = 0x0400000;

  /** Return a slash-separated list of extensions for the file type. */
  public static final int MAGIC_EXTENSION = 0x1000000;

  /** Don't report on compression, only on the uncompressed data. */
  public static final int MAGIC_COMPRESS_TRANSP = 0x2000000;
}
