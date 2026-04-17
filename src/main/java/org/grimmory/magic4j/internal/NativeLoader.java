package org.grimmory.magic4j.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import org.grimmory.magic4j.NativeLoadException;

/**
 * Loads the libmagic native library from the classpath classifier JAR, falling back to the
 * system-installed library via {@link System#loadLibrary}.
 *
 * <p>This class must be initialized (via {@link #ensureLoaded()}) before {@link
 * org.grimmory.magic4j.internal.MagicBindings} is first accessed.
 */
public final class NativeLoader {

  private static volatile boolean loaded = false;
  private static volatile Throwable loadError = null;

  private NativeLoader() {}

  /** Ensures libmagic is loaded exactly once. Thread-safe via double-checked locking. */
  public static void ensureLoaded() {
    if (loaded) return;
    if (loadError != null) {
      throw new NativeLoadException("Native library failed to load previously", loadError);
    }
    synchronized (NativeLoader.class) {
      if (loaded) return;
      if (loadError != null) {
        throw new NativeLoadException("Native library failed to load previously", loadError);
      }
      try {
        tryLoadFromClasspath();
        loaded = true;
      } catch (NativeLoadException classpathMiss) {
        try {
          System.loadLibrary("magic");
          loaded = true;
        } catch (UnsatisfiedLinkError e) {
          NativeLoadException ex =
              new NativeLoadException(
                  "libmagic not found for platform '"
                      + detectPlatform()
                      + "'. No classifier JAR on classpath, and System.loadLibrary(\"magic\")"
                      + " also failed. Install libmagic: apt install libmagic-dev"
                      + " / apk add libmagic / brew install libmagic",
                  classpathMiss);
          ex.addSuppressed(e);
          loadError = ex;
          throw ex;
        }
      } catch (Throwable t) {
        loadError = t;
        throw new NativeLoadException("Failed to load native library", t);
      }
    }
  }

  private static void tryLoadFromClasspath() {
    String platform = detectPlatform();
    String libName = nativeFilename();
    String resource = "/natives/" + platform + "/" + libName;

    if (NativeLoader.class.getResource(resource) == null) {
      throw new NativeLoadException("No libmagic binary found on classpath for " + platform);
    }

    try {
      Path tmpDir = Files.createTempDirectory("magic4j-");
      tmpDir.toFile().deleteOnExit();
      Path libPath = extractResource(resource, tmpDir, libName);
      System.load(libPath.toAbsolutePath().toString());
    } catch (IOException e) {
      throw new NativeLoadException("Failed to extract native library", e);
    }
  }

  private static Path extractResource(String resource, Path dir, String filename)
      throws IOException {
    try (InputStream is = NativeLoader.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new NativeLoadException("Resource not found: " + resource);
      }
      Path target = dir.resolve(filename);
      Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
      target.toFile().deleteOnExit();
      return target;
    }
  }

  static String detectPlatform() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String osKey;
    if (os.contains("win")) {
      osKey = "windows";
    } else if (os.contains("mac")) {
      osKey = "darwin";
    } else if (os.contains("nux")) {
      osKey = isMusl() ? "linux-musl" : "linux";
    } else {
      throw new NativeLoadException("Unsupported operating system: " + os);
    }
    return osKey + "-" + detectArch();
  }

  @SuppressFBWarnings(
      value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
      justification = "Musl detection requires probing standard linker locations")
  private static boolean isMusl() {
    try {
      Path ldMusl = Path.of("/lib");
      if (Files.exists(ldMusl)) {
        try (var files = Files.list(ldMusl)) {
          if (files.anyMatch(p -> p.getFileName().toString().startsWith("ld-musl-"))) {
            return true;
          }
        }
      }
    } catch (Exception _) {
    }
    try {
      String maps = Files.readString(Path.of("/proc/self/maps"));
      if (maps.contains("musl")) return true;
    } catch (Exception _) {
    }
    return false;
  }

  private static String detectArch() {
    String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
    if ("x86_64".equals(arch) || "amd64".equals(arch)) return "x64";
    if ("aarch64".equals(arch) || "arm64".equals(arch)) return "arm64";
    throw new NativeLoadException("Unsupported architecture: " + arch);
  }

  static String nativeFilename() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return "magic1.dll";
    if (os.contains("mac")) return "libmagic.dylib";
    return "libmagic.so";
  }
}
