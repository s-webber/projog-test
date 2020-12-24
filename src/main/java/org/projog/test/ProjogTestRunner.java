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
import static org.projog.test.ProjogTestUtils.toUnixLineEndings;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projog.api.Projog;
import org.projog.api.QueryResult;
import org.projog.api.QueryStatement;
import org.projog.core.ProjogException;
import org.projog.core.SpyPoints.SpyPointEvent;
import org.projog.core.SpyPoints.SpyPointExitEvent;
import org.projog.core.event.ProjogListener;
import org.projog.core.term.Atom;
import org.projog.core.term.Term;

/**
 * Runs tests defined in Prolog files and compares actual output against expected results.
 * <p>
 * Projog can operate in two modes - interpreted and compiled. Although they should give the same result there are some
 * subtle differences about how the results may be presented. As the expected output specified by the tests always
 * refers to what the compiled version should do, there are some "workarounds" for confirming the output of running in
 * interpreted mode.
 *
 * @see ProjogTestParser
 * @see ProjogTestExtractor
 */
public final class ProjogTestRunner implements ProjogListener {
   private static final boolean DEBUG = false;

   private final File redirectedOutputFile = new File("ProjogTestRunnerOutput_" + hashCode() + ".tmp");
   private final Map<Object, Integer> spypointSourceIds = new HashMap<>();
   private final ProjogSupplier projogFactory;
   private Projog projog;
   private TestResults testResults;

   /**
    * Run the Prolog tests contained in the given file.
    *
    * @param testResources If {@code testResources} is a directory then all test scripts in the directory, and its
    * sub-directories, will be run. If {@code testResources} is a file then all tests contained in the file will be run.
    * @return the results of running the tests
    */
   public static TestResults runTests(File testResources) {
      return runTests(testResources, new ProjogSupplier() {
         @Override
         public Projog get() {
            return new Projog();
         }
      });
   }

   /**
    * Run the Prolog tests contained in the given file.
    *
    * @param testResources If {@code testResources} is a directory then all test scripts in the directory, and its
    * sub-directories, will be run. If {@code testResources} is a file then all tests contained in the file will be run.
    * @param projogFactory Used to obtain the {@link Projog} instance to run the tests against.
    * @return the results of running the tests
    */
   public static TestResults runTests(File testResources, ProjogSupplier projogFactory) {
      List<File> scripts = getScriptsToRun(testResources);
      return new ProjogTestRunner(projogFactory).checkScripts(scripts);
   }

   private ProjogTestRunner(ProjogSupplier projogFactory) {
      this.projogFactory = projogFactory;
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
      testResults = new TestResults(scripts.size());
      for (File script : scripts) {
         // create new Projog each time so rules added from previous scripts
         // don't interfere with results of the script about to be checked
         projog = projogFactory.get();
         projog.addListener(this);
         checkScript(script);
      }
      testResults.duration = System.currentTimeMillis() - start;
      return testResults;
   }

   private void checkScript(File f) {
      try {
         consultFile(f);
         List<ProjogTestQuery> queries = ProjogTestParser.getQueries(f);
         checkQueries(f, queries);
      } catch (Exception e) {
         debug(e);
         testResults.addError(f, "Error checking Prolog test script: " + f.getPath() + " " + e);
      }
   }

   private void consultFile(File script) {
      println("Checking script: " + script.getPath());
      projog.consultFile(script);
   }

   private void checkQueries(File f, List<ProjogTestQuery> queries) {
      for (ProjogTestQuery query : queries) {
         try {
            checkQuery(query);
         } catch (Exception e) {
            debug(e);
            testResults.addError(f, query.getPrologQuery() + " " + e.getClass() + " " + e.getMessage());
         }
      }
   }

   private void checkQuery(ProjogTestQuery query) {
      debug("QUERY: " + query.getPrologQuery());
      testResults.queryCount++;

      Iterator<ProjogTestAnswer> expectedAnswers = null;
      Term redirectedOutputFileHandle = null;
      boolean parsedQuery = false;
      try {
         redirectedOutputFileHandle = redirectOutput();

         // TODO make it configurable to do either ".createPlan().createStatement()" or just ".createStatement()"
         QueryStatement stmt = projog.createPlan(query.getPrologQuery() + ".").createStatement();
         QueryResult result = stmt.getResult();
         parsedQuery = true;
         expectedAnswers = query.getAnswers().iterator();

         boolean isExhausted = result.isExhausted();
         spypointSourceIds.clear();
         while (result.next()) {
            if (isExhausted) {
               throw new RuntimeException("isExhausted() was true when there were still more answers available");
            }
            debug("ANSWERS:");
            if (!expectedAnswers.hasNext()) {
               if (query.doesQuitBeforeFindingAllAnswers()) {
                  return;
               } else {
                  throw new RuntimeException("More answers than expected");
               }
            }
            ProjogTestAnswer correctAnswer = expectedAnswers.next();
            checkOutput(correctAnswer);
            checkAnswer(result, correctAnswer);

            isExhausted = result.isExhausted();

            closeOutput(redirectedOutputFileHandle);
            redirectedOutputFileHandle = redirectOutput();
            spypointSourceIds.clear();
         }
         if (query.doesQuitBeforeFindingAllAnswers()) {
            throw new RuntimeException("Found all answers before quit");
         }
         if (isExhausted == query.isContinuesUntilFails()) {
            throw new RuntimeException("isExhausted was: " + isExhausted + " but query.isContinuesUntilFails was: " + query.isContinuesUntilFails());
         }
         if (query.isContinuesUntilFails()) {
            checkOutput(query.getExpectedOutput());
         }
      } catch (ProjogException pe) {
         String actual = pe.getMessage();
         String expected = query.getExpectedExceptionMessage();
         if (actual.equals(expected) == false) {
            throw new RuntimeException("Expected: >" + expected + "< but got: >" + actual + "<", pe);
         }
      } finally {
         closeOutput(redirectedOutputFileHandle);
      }
      if (parsedQuery && expectedAnswers.hasNext()) {
         throw new RuntimeException("Less answers than expected for: " + query.getPrologQuery());
      }
   }

   /**
    * Redirect output to a file, rather than <code>System.out</code>, so it can be checked against the expectations.
    *
    * @return a reference to the newly opened file so it can be closed and read from after the tests have run
    * @see #closeOutput(Term)
    */
   private Term redirectOutput() {
      redirectedOutputFile.delete();
      QueryStatement openStmt = projog.createStatement("open('" + redirectedOutputFile.getName() + "',write,Z).");
      QueryResult openResult = openStmt.getResult();
      if (!openResult.next()) {
         throw new IllegalStateException();
      }
      Term redirectedOutputFileHandle = openResult.getTerm("Z");
      QueryStatement setOutputStmt = projog.createStatement("set_output(Z).");
      setOutputStmt.setTerm("Z", redirectedOutputFileHandle);
      QueryResult setOutputResult = setOutputStmt.getResult();
      if (!setOutputResult.next()) {
         throw new IllegalStateException();
      }
      return redirectedOutputFileHandle;
   }

   private void checkOutput(ProjogTestAnswer answer) {
      checkOutput(answer.getExpectedOutput());
   }

   private void checkOutput(String expected) {
      byte[] redirectedOutputFileContents = readAllBytes(redirectedOutputFile);
      String actual = new String(redirectedOutputFileContents);
      if (!equalExcludingLineTerminators(expected, actual)) {
         throw new RuntimeException("Expected: >\n" + expected + "\n< but got: >\n" + actual + "\n<");
      }
   }

   public static byte[] readAllBytes(File f) {
      try {
         return Files.readAllBytes(f.toPath());
      } catch (Exception e) {
         throw new RuntimeException("could not read file: " + f, e);
      }
   }

   private static boolean equalExcludingLineTerminators(String expected, String actual) {
      return toUnixLineEndings(expected).equals(toUnixLineEndings(actual));
   }

   /**
    * Close the file that was used to redirect output from the tests.
    *
    * @param redirectedOutputFileHandle reference to the file to close
    * @see #redirectOutput()
    */
   private void closeOutput(Term redirectedOutputFileHandle) {
      QueryStatement closeStmt = projog.createStatement("close(Z).");
      closeStmt.setTerm("Z", redirectedOutputFileHandle);
      QueryResult closeResult = closeStmt.getResult();
      if (!closeResult.next()) {
         throw new IllegalStateException();
      }
      redirectedOutputFile.delete();
   }

   private void checkAnswer(QueryResult result, ProjogTestAnswer correctAnswer) {
      Set<String> variableIds = result.getVariableIds();
      if (variableIds.size() != correctAnswer.getAssignmentsCount()) {
         throw new RuntimeException("Different number of variables than expected. Actual: " + variableIds + " Expected: " + correctAnswer.getAssignments());
      }

      for (String variableId : variableIds) {
         Term variable = result.getTerm(variableId);
         String actualTerm;
         if (variable.getType().isVariable()) {
            actualTerm = "UNINSTANTIATED VARIABLE";
         } else {
            actualTerm = projog.formatTerm(variable);
         }

         String expectedTerm = correctAnswer.getAssignedValue(variableId);
         if (expectedTerm == null) {
            throw new RuntimeException(variableId
                                       + " ("
                                       + variable
                                       + ") was not expected to be assigned to anything but was to: "
                                       + actualTerm
                                       + " "
                                       + correctAnswer.getAssignments());
         } else if (!actualTerm.equals(expectedTerm)) {
            throw new RuntimeException(variableId + " (" + variable + ") assigned to: " + actualTerm + " not: " + expectedTerm + " " + correctAnswer.getAssignments());
         }
      }
   }

   @Override
   public void onCall(SpyPointEvent event) {
      update("CALL", event);
   }

   @Override
   public void onRedo(SpyPointEvent event) {
      update("REDO", event);
   }

   @Override
   public void onExit(SpyPointExitEvent event) {
      update("EXIT", event);
   }

   @Override
   public void onFail(SpyPointEvent event) {
      update("FAIL", event);
   }

   @Override
   public void onWarn(String message) {
      // system tests do not include expectations about WARN or INFO events - so don't check them TODO start including WARN and INFO events in test expectations
      println(message);
   }

   @Override
   public void onInfo(String message) {
      // system tests do not include expectations about WARN or INFO events - so don't check them TODO start including WARN and INFO events in test expectations
      println(message);
   }

   /**
    * Writes to the output stream {@code ProjogEvent}s generated by running the tests.
    * <p>
    * Notified of events as is registered as an observer of {@link #projog}
    */
   private void update(String level, SpyPointEvent event) {
      String message = generateLogMessageForEvent(level, event);
      writeLogMessage(message);
   }

   /**
    * @return e.g. <code>[2] CALL testCalculatables( X, 3, 7 )</code>
    */
   private String generateLogMessageForEvent(String level, SpyPointEvent event) {
      String actualSourceId = Integer.toString(event.getSourceId());
      Integer i = spypointSourceIds.get(actualSourceId);
      if (i == null) {
         i = spypointSourceIds.size() + 1;
         spypointSourceIds.put(actualSourceId, i);
      }
      return "[" + i + "] " + level + " " + event.toString();
   }

   private void writeLogMessage(String message) {
      QueryStatement openStmt = projog.createStatement("write(Message), nl.");
      openStmt.setTerm("Message", new Atom(message));
      QueryResult openResult = openStmt.getResult();
      if (!openResult.next()) {
         throw new IllegalStateException();
      }
   }

   private static void debug(String s) {
      if (DEBUG) {
         println(s);
      }
   }

   private void debug(Exception e) {
      if (DEBUG) {
         e.printStackTrace(System.out);
      }
   }

   private static void println(String s) {
      System.out.println(s);
   }

   /**
    * Represents the results of running the Prolog tests.
    *
    * @see #assertSuccess()
    */
   public static class TestResults {
      private final StringBuilder errorMessages = new StringBuilder();
      private final int scriptsCount;
      private int queryCount;
      private int errorCount;
      private long duration;

      private TestResults(int scriptsCount) {
         this.scriptsCount = scriptsCount;
      }

      private void addError(File f, String errorDescription) {
         errorMessages.append(f.getName() + " " + errorDescription + "\n");
         errorCount++;
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
       * @throws RuntimeException if any of the tests failes
       */
      public void assertSuccess() {
         if (hasFailures()) {
            throw new RuntimeException(errorCount + " test failures:\n" + errorMessages);
         }
      }

      public String getSummary() {
         StringBuilder sb = new StringBuilder();
         sb.append("Completed " + queryCount + " queries from " + scriptsCount + " files with " + errorCount + " failures in: " + duration + "ms");
         if (hasFailures()) {
            sb.append("\n\n\n ***** Failed: " + errorCount + " tests!!! *****\n\n\n");
            sb.append(errorMessages);
         }
         return sb.toString();
      }
   }

   /**
    * Creates the {@link Projog} instance that tests will be run against.
    * <p>
    * TODO if this project is upgraded from Java 7 then this interface can be replaced with: java.util.function.Supplier
    */
   public interface ProjogSupplier {
      Projog get();
   }
}
