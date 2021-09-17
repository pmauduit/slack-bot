package fr.spironet.slackbot.github

import org.junit.Before
import org.junit.Test

import static org.junit.jupiter.api.Assertions.assertTrue
import static org.testng.AssertJUnit.assertFalse


class PmauduitEventFilterTest {
    def filter

    @Before
    void setUp() {
        def filterCls = Class.forName("fr.spironet.slackbot.github.PmauduitEventFilter")
        filter = filterCls.newInstance()
    }

    @Test
    void testPmauduitEventFilter() {
            def result = filter.doFilter([actor: [login: "sbrunner"]])

            assertTrue(result)

            result = filter.doFilter([:])

            assertFalse(result)
    }

    @Test
    void testReposFiltered() {
        def result = filter.doFilter([actor: [login: "pmauduit"], repo: [name: "camptocamp/helm-geomapfish"]])

        assertTrue(result)

        result = filter.doFilter([actor: [login: "pmauduit"], repo: [name:  "camptocamp/terraform-trifouillismetropole"]])

        assertFalse(result)

        result = filter.doFilter([actor: [login: "pmauduit"], repo: [name:  "camptocamp/odoo-worldcompany"]])

        assertTrue(result)
    }
}
