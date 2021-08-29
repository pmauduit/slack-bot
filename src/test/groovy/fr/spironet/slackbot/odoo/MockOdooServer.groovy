package fr.spironet.slackbot.odoo

import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient

class MockOdooServer extends RESTClient {
    def sessionInvalidated = false
    def userExisting = true
    def badLoginCredentials = false

    def weeklyAttendance = true

    @Override
    public Object post(Map<String,?> args) {
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
        else if (model == "hr.attendance" && weeklyAttendance) {
            def ret = new File(this.getClass().getResource("hr-attendances-response-payload.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        }
        else if (model == "hr.attendance" && ! weeklyAttendance) {
            def ret = new File(this.getClass().getResource("hr-attendance-day-response-payload.json").toURI()).text
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
