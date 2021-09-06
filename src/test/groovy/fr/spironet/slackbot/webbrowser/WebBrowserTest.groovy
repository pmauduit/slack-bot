package fr.spironet.slackbot.webbrowser

import org.junit.Before
import org.junit.Test

import java.nio.file.Paths

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
class WebBrowserTest {
    def toTest

    @Before
    void setUp() {
        toTest = new WebBrowser()
        def driverPath = Paths.get(".", "drivers", "chromedriver-linux-64bit").toAbsolutePath().normalize().toString()
        System.setProperty("webdriver.chrome.driver", driverPath)
    }

    @Test
    void testWebBrowser() {
        def drv = toTest.getDriver()

        assertNotNull(drv)
    }

    @Test
    void testKibanaDashboardNoEnvVars() {
        def ret = toTest.visitKibanaDashboard()

        assertTrue(ret == null)
    }

    @Test
    void testVisitGrafanaMonitoringDashboardNoEnvVars() {
        def ret = toTest.visitGrafanaMonitoringDashboard()

        assertTrue(ret == null)
    }
}
