package org.projog.test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

/**
 * Constants and utility methods used in the build process.
 */
final class ProjogTestUtils {
   private static final String LINE_BREAK = "\n";
   static final String PROLOG_FILE_EXTENSION = ".pl";
   static final String TEXT_FILE_EXTENSION = ".txt";

   private ProjogTestUtils() {
   }

   /** Returns {@code true} if the the specified file has a prolog file extension. */
   public static boolean isPrologScript(File f) {
      String name = f.getName();
      int dotPos = name.lastIndexOf('.');
      if (dotPos > 0) {
         String extension = name.substring(dotPos).toLowerCase();
         return PROLOG_FILE_EXTENSION.equals(extension) || ".p".equals(extension) || ".pro".equals(extension) || ".prolog".equals(extension);
      } else {
         return false;
      }
   }

   /**
    * Returns the contents of the specified file as a {@code String}.
    * <p>
    * Note: Carriage returns will be represented by {@code #LINE_BREAK} rather than the underlying carriage return style
    * used in the actual file.
    *
    * @param f text file to read
    * @return list of lines contained in specified file
    */
   static String readText(File f) {
      return concatLines(readAllLines(f));
   }

   /**
    * Combines the specified list into a single {@code String}.
    * <p>
    * Each line will be followed by {@code #LINE_BREAK}.
    */
   static String concatLines(List<String> lines) {
      StringBuilder sb = new StringBuilder();
      for (String line : lines) {
         sb.append(line);
         sb.append(LINE_BREAK);
      }
      return sb.toString();
   }

   /**
    * Returns list of lines contained in specified text file.
    *
    * @param f text file to read
    * @return list of lines contained in specified file
    */
   private static List<String> readAllLines(File f) {
      try {
         return Files.readAllLines(f.toPath(), Charset.defaultCharset());
      } catch (Exception e) {
         throw new RuntimeException("could not read text file: " + f, e);
      }
   }

   /** Replaces all Windows-style {@code CR+LF} (i.e. {@code \r\n}) line endings with {@code LF} (i.e. {@code \n}). */
   static String toUnixLineEndings(String expected) {
      return expected.replace("\r\n", "\n");
   }
}
