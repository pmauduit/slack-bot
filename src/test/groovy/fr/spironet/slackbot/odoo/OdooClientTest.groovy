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
        toTest.http = new RESTClient() {

            def sessionInvalidated = false
            def userExisting = true
            def badLoginCredentials = false

            @Override
            public Object post( Map<String,?> args ) {
                def model = args.body.params.model
                if (args.uri.endsWith("/web/session/authenticate") && ! badLoginCredentials) {
                    def ret = new File(this.getClass().getResource("login-response-payload.json").toURI()).text
                    return [ data: new JsonSlurper().parseText(ret) ]
                }
                else if (args.uri.endsWith("/web/session/authenticate") && badLoginCredentials) {
                    def ret = new File(this.getClass().getResource("bad-login-response-payload.json").toURI()).text
                    return [ data: new JsonSlurper().parseText(ret) ]
                }
                // It does not matter if we return more fields than the Odoo server should do
                // in this mock
                else if (args.uri.endsWith("/web/dataset/search_read") && model == "res.users"
                        && ! sessionInvalidated && userExisting) {
                    def ret = new File(this.getClass().getResource("attendance-response-payload.json").toURI()).text
                    return [ data: new JsonSlurper().parseText(ret) ]
                }
                else if (args.uri.endsWith("/web/dataset/search_read") && model == "res.users"
                        && ! sessionInvalidated && ! userExisting) {
                    def ret = new File(this.getClass().getResource("attendance-empty-response.json").toURI()).text
                    return [ data: new JsonSlurper().parseText(ret) ]
                }
                else if (model == "hr.leave" && ! sessionInvalidated) {
                    def ret = new File(this.getClass().getResource("hr-leaves-response-payload.json").toURI()).text
                    return [ data: new JsonSlurper().parseText(ret) ]
                }
                // catch-all case, returning as if the session was invalid
                def ret = new File(this.getClass().getResource("session-expired-response-payload.json").toURI()).text
                return [ data: new JsonSlurper().parseText(ret) ]
            }
        }
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
