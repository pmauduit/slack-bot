package fr.spironet.slackbot.listeners

import fr.spironet.slackbot.odoo.MockOdooServer
import fr.spironet.slackbot.odoo.OdooClient
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class OdooListenerTest {

    private def toTest

    @Before
    void setUp() {
        def mockedOdooClient = new OdooClient()
        mockedOdooClient.http = new MockOdooServer()
        toTest = new OdooListener(mockedOdooClient)
        toTest.username = "pmauduit"
    }

    @Test
    void testTotalTimeWeek() {
        toTest.odooClient.http.weeklyAttendance = true
        def ret = toTest.totalTimeWeek()

        assertTrue(ret.contains("*40:11*"))
    }

    @Test
    void testTotalTimeToday() {
        toTest.odooClient.http.weeklyAttendance = false
        def ret = toTest.totalTimeToday()

        assertTrue(ret.contains("* over *07:42*"))
    }

}
