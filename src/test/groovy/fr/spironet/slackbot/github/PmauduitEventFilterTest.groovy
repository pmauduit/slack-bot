package fr.spironet.slackbot.github

import org.junit.Test

import static org.junit.jupiter.api.Assertions.assertTrue
import static org.testng.AssertJUnit.assertFalse


class PmauduitEventFilterTest {

    @Test
    void testPmauduitEventFilter() {
            def filterCls = Class.forName("fr.spironet.slackbot.github.PmauduitEventFilter")
            def filter = filterCls.newInstance()

            def result = filter.doFilter([actor: [login: "sbrunner"]])

            assertTrue(result)

            result = filter.doFilter([:])

            assertFalse(result)
    }
}
