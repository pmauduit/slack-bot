package fr.spironet.slackbot.odoo

import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class OdooClientTest {

    private def toTest

    @Before
    void setUp() {
        toTest = new OdooClient()
        toTest.http = new MockOdooServer()
    }

    @Test
    void testLogin() {
        toTest.http.badLoginCredentials = false
        def ret = toTest.login()

        assertTrue(ret.result.uid == 69 && ret.result.db == "odoo_db")
    }

    @Test(expected = AccessDeniedOdooException)
    void testBadLogin() {
        toTest.http.badLoginCredentials = true
        toTest.login()
    }

    @Test
    void testAttendanceState() {
        toTest.http.userExisting = true
        def state = toTest.getAttendanceState("jdoe")

        assertTrue(state in ["checked_out", "checked_in"])
    }

    @Test
    void testAttendanceStateNonExistingUser() {
        toTest.http.userExisting = false
        // checks the attendance of Pamela Anderson
        def state = toTest.getAttendanceState("panderson")

        assertTrue(state == null)
    }

    @Test
    void testGetComingLeavesForUser() {
        def ret = toTest.getComingLeavesForUser("jdoe")

        assertTrue(ret.size() == 4)
    }

    @Test(expected = DisconnectedFromOdooException)
    void testGetComingLeavesForUserButSessionExpired() {
        toTest.login()

        // then more than one week passes ...
        toTest.http.sessionInvalidated = true

        toTest.getComingLeavesForUser()
    }
}
