/*
 * Copyright 2021 S. Webber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projog.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Utility class to automatically update old style test scripts to conform to new, more concise, syntax. */
public class UpdateLegacyTests {
   public static void main(String[] args) throws IOException {
      x("../projog/src/main/java");
      x("../projog/src/test/prolog");
   }

   public static void x(String root) throws IOException {
      Files.walkFileTree(new File(root).toPath(), new SimpleFileVisitor<Path>() {
         @Override
         public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            boolean isJava = path.getFileName().toString().endsWith(".java");
            boolean isProlog = path.getFileName().toString().endsWith(".pl");
            if (isJava || isProlog) {
               System.out.println(path.getFileName());

               List<String> input = Files.readAllLines(path);
               List<String> output = new ArrayList<>();
               boolean inTest = isProlog;
               boolean inAnswer = false;
               for (String line : input) {
                  if (inTest) {
                     if ("*/".equals(line.trim())) {
                        if (!isJava) {
                           throw new RuntimeException(line);
                        }
                        output.add("*/"); // TODO output.add(" */");
                        inTest = false;
                     } else if (line.trim().startsWith("%QUERY")) {
                        output.add("%?-" + line.trim().substring(line.trim().indexOf(' ')));
                     } else if (line.trim().startsWith("%FALSE")) {
                        output.add("%FAIL" + line.trim().substring(line.trim().indexOf(' ')));
                     } else if (line.trim().equals("%ANSWER/")) {
                        output.add("%YES");
                     } else if (line.trim().equals("%ANSWER")) {
                        inAnswer = !inAnswer;
                     } else if (line.trim().startsWith("%ANSWER")) {
                        String[] answer = split(line);
                        output.add("% " + answer[0].trim() + "=" + answer[1].trim());
                     } else if (inAnswer && line.trim().startsWith("%")) {
                        String[] answer = split(line);
                        output.add("% " + answer[0].trim() + "=" + answer[1].trim());
                     } else {
                        if (line.startsWith(" ")) {
                           line = line.substring(1);
                        }
                        output.add(line);
                     }
                  } else if ("/* TEST".equals(line.trim())) {
                     if (!isJava) {
                        throw new RuntimeException(line);
                     }
                     output.add("/* TEST");
                     inTest = true;
                  } else {
                     output.add(line);
                  }
               }

               if (!input.equals(output)) {
                  // TODO Files.write(path, output);
               }
            }

            return FileVisitResult.CONTINUE;
         }
      });
      System.out.println("FINISHED");
   }

   private static String[] split(String s) {
      String x = s.trim().substring(s.trim().indexOf(' '));
      int p = x.indexOf('=');
      if (p == -1) {
         throw new RuntimeException(s);
      }
      return new String[] {x.substring(0, p).trim(), x.substring(p + 1).trim()};
   }
}
