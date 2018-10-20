/*
 * Copyright 2013-2014 S. Webber
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

import static org.projog.test.ProjogTestUtils.PROLOG_FILE_EXTENSION;
import static org.projog.test.ProjogTestUtils.TEXT_FILE_EXTENSION;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.projog.core.ArithmeticOperator;
import org.projog.core.PredicateFactory;

/**
 * Extracts Prolog tests from the comments of Java source files.
 * <p>
 * Produces {@code .txt} and {@code .pl} files for implementations of {@code PredicateFactory} and
 * {@code ArithmeticOperator}. The contents of the files are extracted from the comments in the {@code .java} file of
 * the {@code PredicateFactory} or {@code ArithmeticOperator}. The {@code .txt} file contains the contents of the
 * Javadoc comment of the class. The {@code .pl} file contains the Prolog syntax contained in the "{@code TEST}" comment
 * at the top of the class.
 *
 * @see ProjogTestExtractorConfig
 * @see ProjogTestRunner
 */
public final class ProjogTestExtractor {
   private final ProjogTestExtractorConfig config;

   public static void extractTests() {
      extractTests(new ProjogTestExtractorConfig());
   }

   public static void extractTests(ProjogTestExtractorConfig config) {
      validateConfig(config);

      ProjogTestExtractor generator = new ProjogTestExtractor(config);
      List<File> javaSourceFiles = generator.getDocumentableJavaSourceFiles(config.getJavaRootDirectory());
      Map<String, File> alreadyProcessed = new HashMap<>();
      for (File f : javaSourceFiles) {
         File previousEntry = alreadyProcessed.put(f.getName(), f);
         if (previousEntry != null) {
            throw new IllegalArgumentException("Two instances of: " + f.getName() + " first: " + previousEntry + " second: " + f);
         }
         generator.produceScriptFileFromJavaFile(f);
      }
   }

   private static void validateConfig(ProjogTestExtractorConfig config) {
      Objects.requireNonNull(config, "no ProjogTestExtractorConfig specified");
      Objects.requireNonNull(config.getJavaRootDirectory(), "no Java root directiry specified");
      Objects.requireNonNull(config.getPrologTestsDirectory(), "no Prolog tests directiry specified");
      Objects.requireNonNull(config.getFileFilter(), "no file filter specified in " + config);
      if (!config.getJavaRootDirectory().exists()) {
         throw new IllegalArgumentException("Java root directory does not exists: " + config.getJavaRootDirectory());
      }
   }

   private ProjogTestExtractor(ProjogTestExtractorConfig config) {
      this.config = config;
   }

   private List<File> getDocumentableJavaSourceFiles(File dir) {
      List<File> result = new ArrayList<>();
      for (File f : dir.listFiles()) {
         if (f.isDirectory()) {
            result.addAll(getDocumentableJavaSourceFiles(f));
         } else if (isJavaSourceFileOfDocumentedClass(f)) {
            result.add(f);
         }
      }
      return result;
   }

   private boolean isJavaSourceFileOfDocumentedClass(File file) {
      return isJavaSource(file) && config.getFileFilter().accept(file) && isDocumentable(getClass(file));
   }

   private Class<?> getClass(File file) {
      try {
         String className = getClassName(file);
         return Class.forName(className);
      } catch (ClassNotFoundException e) {
         throw new RuntimeException(e);
      }
   }

   private static boolean isDocumentable(Class<?> c) {
      return isConcrete(c) && isPublic(c) && (isPredicateFactory(c) || isArithmeticOperator(c));
   }

   private static boolean isConcrete(Class<?> c) {
      return !Modifier.isAbstract(c.getModifiers());
   }

   private static boolean isPublic(Class<?> c) {
      return Modifier.isPublic(c.getModifiers());
   }

   private static boolean isPredicateFactory(Class<?> c) {
      return PredicateFactory.class.isAssignableFrom(c);
   }

   private static boolean isArithmeticOperator(Class<?> c) {
      return ArithmeticOperator.class.isAssignableFrom(c);
   }

   private static boolean isJavaSource(File f) {
      String name = f.getName();
      return name.endsWith(".java") && !"package-info.java".equals(name);
   }

   private void produceScriptFileFromJavaFile(File javaFile) {
      try (FileReader fr = new FileReader(javaFile); BufferedReader br = new BufferedReader(fr)) {
         boolean testRead = false;
         boolean javadocRead = false;
         String line;
         while ((!testRead || !javadocRead) && (line = br.readLine()) != null) {
            line = line.trim();
            if ("/* TEST".equals(line)) {
               testRead = true;
               writeScriptFile(javaFile, br);
            } else if (testRead && !javadocRead && "/**".equals(line)) {
               javadocRead = true;
               writeTextFile(javaFile, br);
            }
         }
         if (!testRead && config.isRequireTest()) {
            throw new Exception("No Prolog tests found in: " + javaFile);
         }
         if (!javadocRead && config.isRequireJavadoc()) {
            throw new Exception("No Javadoc found in: " + javaFile);
         }
      } catch (Exception e) {
         throw new RuntimeException("Cannot generate Prolog test script from " + javaFile + " due to " + e, e);
      }
   }

   private void writeScriptFile(File javaFile, BufferedReader br) {
      File scriptFile = getOutputFile(javaFile, PROLOG_FILE_EXTENSION);
      scriptFile.getParentFile().mkdirs();

      try (FileWriter fw = new FileWriter(scriptFile); BufferedWriter bw = new BufferedWriter(fw)) {
         String line;
         while (!"*/".equals(line = br.readLine().trim())) {
            bw.write(line);
            bw.newLine();
         }
      } catch (IOException e) {
         throw new RuntimeException("Could not produce: " + scriptFile + " due to: " + e, e);
      }
   }

   /**
    * Writes comments contained in Javadoc for class to a text file.
    * <p>
    * Comments can then be reused to construct user manual documentation.
    */
   private void writeTextFile(File javaFile, BufferedReader br) {
      File textFile = getOutputFile(javaFile, TEXT_FILE_EXTENSION);
      textFile.getParentFile().mkdirs();

      try (FileWriter fw = new FileWriter(textFile); BufferedWriter bw = new BufferedWriter(fw)) {
         String line;
         while (!"*/".equals(line = br.readLine().trim())) {
            line = line.trim();
            if (line.startsWith("*")) {
               line = line.substring(1).trim();
            }
            // ignore any annotations present in input Javadoc
            if (!isAnnotation(line)) {
               bw.write(line);
               bw.newLine();
            }
         }
      } catch (IOException e) {
         throw new RuntimeException("Could not produce: " + textFile + " due to: " + e, e);
      }
   }

   private static boolean isAnnotation(String line) {
      return line.startsWith("@");
   }

   private File getOutputFile(File javaSourceFile, String extension) {
      return new File(config.getPrologTestsDirectory(), getClassName(javaSourceFile) + extension);
   }

   private String getClassName(File javaFile) {
      String rootPath = config.getJavaRootDirectory().getPath();
      String pathMinusRoot = javaFile.getPath().substring(rootPath.length() + 1);
      return pathMinusRoot.replace(File.separatorChar, '.').substring(0, pathMinusRoot.lastIndexOf('.'));
   }
}
