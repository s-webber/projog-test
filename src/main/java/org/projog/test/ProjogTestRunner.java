/*
 * Copyright 2013 S. Webber
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

import static org.projog.test.ProjogTestUtils.isPrologScript;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.projog.api.Projog;

/**
 * Runs tests defined in Prolog files and compares actual output against expected results.
 *
 * @see ProjogTestParser
 * @see ProjogTestExtractor
 */
public final class ProjogTestRunner {
   private final ProjogTestRunnerConfig runnerConfig;

   /**
    * Run the Prolog tests contained in the given file.
    *
    * @param testResources If {@code testResources} is a directory then all test scripts in the directory, and its
    * sub-directories, will be run. If {@code testResources} is a file then all tests contained in the file will be run.
    * @return the results of running the tests
    */
   public static TestResults runTests(File testResources) {
      return runTests(testResources, new ProjogTestRunnerConfig() {
      });
   }

   /**
    * Run the Prolog tests contained in the given file.
    *
    * @param testResources If {@code testResources} is a directory then all test scripts in the directory, and its
    * sub-directories, will be run. If {@code testResources} is a file then all tests contained in the file will be run.
    * @param runnerConfig Used to obtain the {@link Projog} instance to run the tests against.
    * @return the results of running the tests
    */
   public static TestResults runTests(File testResources, ProjogTestRunnerConfig runnerConfig) {
      List<File> scripts = getScriptsToRun(testResources);
      return new ProjogTestRunner(runnerConfig).checkScripts(scripts);
   }

   private ProjogTestRunner(ProjogTestRunnerConfig runnerConfig) {
      this.runnerConfig = runnerConfig;
   }

   private static List<File> getScriptsToRun(File f) {
      if (!f.exists()) {
         throw new RuntimeException(f.getPath() + " not found");
      }

      List<File> scripts = new ArrayList<>();
      if (f.isDirectory()) {
         findAllScriptsInDirectory(f, scripts);
      } else {
         scripts.add(f);
      }

      // sort to ensure scripts files are always run in a predictable order
      Collections.sort(scripts);

      return scripts;
   }

   private static void findAllScriptsInDirectory(File dir, List<File> scripts) {
      File[] files = dir.listFiles();
      for (File f : files) {
         if (f.isDirectory()) {
            findAllScriptsInDirectory(f, scripts);
         } else if (isPrologScript(f)) {
            scripts.add(f);
         }
      }
   }

   private TestResults checkScripts(List<File> scripts) {
      long start = System.currentTimeMillis();
      Stream<File> stream = runnerConfig.isParallel() ? scripts.parallelStream() : scripts.stream();
      // @formatter:off
      List<ScriptRunner.ScriptResults> results =
                  stream
                     .map(f -> new ScriptRunner(runnerConfig, f).checkScript())
                     .sorted()
                     .collect(Collectors.toList());
      // @formatter:on
      long duration = System.currentTimeMillis() - start;
      return new TestResults(results, duration);
   }

   /**
    * Represents the results of running the Prolog tests.
    *
    * @see #assertSuccess()
    */
   public static class TestResults {
      private final StringBuilder summaries = new StringBuilder();
      private final StringBuilder errorMessages = new StringBuilder();
      private final int scriptsCount;
      private int queryCount;
      private int errorCount;
      private long duration;

      private TestResults(List<ScriptRunner.ScriptResults> results, long duration) {
         this.scriptsCount = results.size();
         this.duration = duration;
         for (ScriptRunner.ScriptResults r : results) {
            queryCount += r.getQueryCount();
            errorCount += r.getErrorCount();
            String s = r.getFile() + "\nCompleted " + r.getQueryCount() + " queries with " + r.getErrorCount() + " failures in: " + r.getDuration() + "ms\n";
            summaries.append(s);
            for (String error : r.getErrorMessages()) {
               errorMessages.append(r.getFile() + " " + error + "\n");
            }
         }
      }

      public int getScriptsCount() {
         return scriptsCount;
      }

      public int getQueryCount() {
         return queryCount;
      }

      public int getErrorCount() {
         return errorCount;
      }

      public boolean hasFailures() {
         return errorCount != 0;
      }

      public String getErrorMessages() {
         return errorMessages.toString();
      }

      /**
       * Throws an exception if any of the tests failed.
       *
       * @throws RuntimeException if any of the tests fail
       */
      public void assertSuccess() {
         if (hasFailures()) {
            throw new RuntimeException(errorCount + " test failures:\n" + errorMessages);
         }
      }

      public String getSummary() {
         StringBuilder sb = new StringBuilder();
         sb.append("Completed " + queryCount + " queries from " + scriptsCount + " files with " + errorCount + " failures in: " + duration + "ms\n\n");
         sb.append(summaries);
         if (hasFailures()) {
            sb.append("\n ***** Failed: " + errorCount + " tests!!! *****\n\n");
            sb.append(errorMessages);
         }
         return sb.toString();
      }
   }
}
