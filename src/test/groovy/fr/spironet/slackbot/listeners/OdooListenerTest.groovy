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
        toTest.odooClient.http.attendanceType = "weekly"
        def ret = toTest.totalTimeWeek()

        assertTrue(ret.contains("*40:11*"))
    }

    @Test
    void testTotalTimeToday() {
        toTest.odooClient.http.attendanceType = "daily"
        def ret = toTest.totalTimeToday()

        assertTrue(ret.contains("* over *07:42*"))
    }

    @Test
    void testTimeSheet() {
        toTest.odooClient.http.attendanceType = "ts"
        def ret = toTest.timeSheet()

        assertTrue(ret.contains("2021-08-25: 08:00"))
    }
}
