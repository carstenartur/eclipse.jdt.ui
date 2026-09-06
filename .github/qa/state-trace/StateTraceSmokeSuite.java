// Copyright (c) 2026 Carsten Hammer.
// SPDX-License-Identifier: EPL-2.0
package org.eclipse.jdt.ui.tests;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({StateTraceSmokeSuite.LegacyProbe.class, StateTraceSmokeSuite.NestedSuite.class})
public class StateTraceSmokeSuite {
    static int suiteSetups;

    @org.junit.jupiter.api.BeforeEach
    void notAnInheritedSetup() { suiteSetups++; }

    @FixMethodOrder(MethodSorters.NAME_ASCENDING)
    public static class LegacyProbe {
        static final String KEY = "jdt.stateTrace.smoke";
        static Object original;
        static boolean present;

        @BeforeClass
        public static void save() {
            present = System.getProperties().containsKey(KEY);
            original = System.getProperties().get(KEY);
        }

        @Test
        public void test01Write() {
            assertEquals(0, suiteSetups);
            System.setProperty(KEY, "deliberate-observer-sentinel");
            // Intentional carry-over to test02Read; restored at the enclosing class boundary.
        }

        @Test
        public void test02Read() {
            assertEquals("deliberate-observer-sentinel", System.getProperty(KEY));
            assertEquals(0, suiteSetups);
        }

        @AfterClass
        public static void restore() {
            if (present) System.getProperties().put(KEY, original);
            else System.clearProperty(KEY);
        }
    }

    @Suite
    @SelectClasses(JupiterProbe.class)
    public static class NestedSuite { }

    public static class JupiterProbe {
        @org.junit.jupiter.api.Test
        public void jupiterInsideNestedSuite() {
            assertEquals(0, suiteSetups);
        }
    }
}
