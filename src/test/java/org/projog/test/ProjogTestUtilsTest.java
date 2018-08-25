package org.projog.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

public class ProjogTestUtilsTest {
   @Test
   public void testLineEndings() {
      assertEquals("", ProjogTestUtils.toUnixLineEndings(""));
      assertEquals("\nabc\nq\rw\ne\t\n", ProjogTestUtils.toUnixLineEndings("\r\nabc\nq\rw\r\ne\t\r\n"));
   }

   @Test
   public void testConcatLines() {
      final String actual = ProjogTestUtils.concatLines(Arrays.asList("Lorem ipsum", "", " dolor ", "sit amet,", "\tconsectetur adipiscing elit"));
      assertEquals("Lorem ipsum\n\n dolor \nsit amet,\n\tconsectetur adipiscing elit\n", actual);
   }

   @Test
   public void testIsPrologScript() {
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.pl")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.PL")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.pro")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.PRO")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.p")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.P")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.prolog")));
      assertTrue(ProjogTestUtils.isPrologScript(new File("test.PROLOG")));

      assertTrue(ProjogTestUtils.isPrologScript(new File("test.xyz.pl")));

      assertFalse(ProjogTestUtils.isPrologScript(new File("test.pdf")));
      assertFalse(ProjogTestUtils.isPrologScript(new File("test.java")));
      assertFalse(ProjogTestUtils.isPrologScript(new File("test.pl.tmp")));
   }
}
