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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.projog.test.ProjogTestUtils.readText;
import static org.projog.test.ProjogTestUtils.toUnixLineEndings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

@RunWith(DataProviderRunner.class)
public class ProjogTestRunnerTest {
   private static final String TEST_RESOURCES_DIR_NAME = "src/test/resources/";
   private static final File TEST_RESOURCES_DIR = new File(TEST_RESOURCES_DIR_NAME);

   @Test
   public void testSimple() throws IOException {
      File f = File.createTempFile(getClass().getName(), ".pl", new File("target"));
      Files.write(f.toPath(), "%?- X is 1 + Y\n%ERROR Cannot get Numeric for term: Y of type: VARIABLE".getBytes());
      ProjogTestRunner.TestResults r = ProjogTestRunner.runTests(f, new ProjogTestRunnerConfig() {
      });
      r.assertSuccess();
      assertFalse(r.hasFailures());
      assertEquals(1, r.getScriptsCount());
      assertEquals(1, r.getQueryCount());
      assertEquals(0, r.getErrorCount());
      assertTrue(r.getErrorMessages().isEmpty());
   }

   @Test
   @DataProvider({"true", "false"})
   public void testDoNotIgnoreFailedRetries(boolean isParallel) {
      final String expectedErrorMessages = readText(new File(TEST_RESOURCES_DIR, "ProjogTestRunnerTest_DoNotIgnoreFailedRetries_ExpectedErrors.txt"));
      final String expectedSummary = readText(new File(TEST_RESOURCES_DIR, "ProjogTestRunnerTest_DoNotIgnoreFailedRetries_ExpectedSummary.txt"));
      final int expectedScriptsCount = 4;
      final int expectedQueryCount = 41;
      final int expectedErrorCount = 29;

      ProjogTestRunner.TestResults r = ProjogTestRunner.runTests(TEST_RESOURCES_DIR, new ProjogTestRunnerConfig() {
         @Override
         public boolean isParallel() {
            return isParallel;
         }
      });
      assertIgnoringPlatformDifferences(expectedErrorMessages, r.getErrorMessages());
      assertIgnoringPlatformDifferences(expectedSummary, r.getSummary().replaceAll("\\d+ms", "???ms"));
      assertEquals(expectedScriptsCount, r.getScriptsCount());
      assertEquals(expectedQueryCount, r.getQueryCount());
      assertEquals(expectedErrorCount, r.getErrorCount());
      assertTrue(r.hasFailures());

      try {
         r.assertSuccess();
         fail();
      } catch (RuntimeException e) {
         assertIgnoringPlatformDifferences(expectedErrorCount + " test failures:\n" + expectedErrorMessages, e.getMessage());
      }
   }

   @Test
   @DataProvider({"true", "false"})
   public void testDoIgnoreFailedRetries(boolean isParallel) {
      final String expectedErrorMessages = readText(new File(TEST_RESOURCES_DIR, "ProjogTestRunnerTest_DoIgnoreFailedRetries_ExpectedErrors.txt"));
      final String expectedSummary = readText(new File(TEST_RESOURCES_DIR, "ProjogTestRunnerTest_DoIgnoreFailedRetries_ExpectedSummary.txt"));
      final int expectedScriptsCount = 4;
      final int expectedQueryCount = 41;
      final int expectedErrorCount = 27;

      ProjogTestRunner.TestResults r = ProjogTestRunner.runTests(TEST_RESOURCES_DIR, new ProjogTestRunnerConfig() {
         @Override
         public boolean doIgnoreFailedRetries() {
            return true;
         }

         @Override
         public boolean isParallel() {
            return isParallel;
         }
      });
      assertIgnoringPlatformDifferences(expectedErrorMessages, r.getErrorMessages());
      assertIgnoringPlatformDifferences(expectedSummary, r.getSummary().replaceAll("\\d+ms", "???ms"));
      assertEquals(expectedScriptsCount, r.getScriptsCount());
      assertEquals(expectedQueryCount, r.getQueryCount());
      assertEquals(expectedErrorCount, r.getErrorCount());
      assertTrue(r.hasFailures());

      try {
         r.assertSuccess();
         fail();
      } catch (RuntimeException e) {
         assertIgnoringPlatformDifferences(expectedErrorCount + " test failures:\n" + expectedErrorMessages, e.getMessage());
      }
   }

   /** Assert the two given Strings are equal, ignoring platform-specific line-endings and directory-separators. */
   private void assertIgnoringPlatformDifferences(String expected, String actual) {
      assertEquals(toPlatformIndependant(expected), toPlatformIndependant(actual));
   }

   /** Replace windows style line-endings and directory-separators with unix versions. */
   private String toPlatformIndependant(String input) {
      String windowsStylePathName = TEST_RESOURCES_DIR_NAME.replace('/', '\\');
      return toUnixLineEndings(input).replace(windowsStylePathName, TEST_RESOURCES_DIR_NAME);
   }
}
