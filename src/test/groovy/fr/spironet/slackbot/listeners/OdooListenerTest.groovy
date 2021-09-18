package fr.spironet.slackbot.listeners

import fr.spironet.slackbot.odoo.MockOdooServer
import fr.spironet.slackbot.odoo.OdooClient
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class OdooListenerTest {

    private OdooListener toTest

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

    @Test
    void testVacations() {
        def ret = toTest.vacations()

        assertTrue(ret.message.startsWith(":desert_island: Here are your currently accepted leaves *(4)*:"))
    }

    @Test
    void testGetUserAttendanceState() {
        def ret = toTest.getUserAttendanceState("jdoe")

        assertTrue(ret.message.startsWith(":zzz: relying on Odoo, *jdoe* is currently *signed out*."))
    }
}
