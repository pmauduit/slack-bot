package fr.spironet.slackbot.odoo

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

/**
 * The Odoo Java library I was using since then is horrible to use in Groovy:
 * there are too many uncertainty on which method should be called, the
 * argument types, ... and generally groovy will call the wrong one.
 *
 * So I recoded mine to do simpler things, as it is just about sending JSON
 * paylaods to an HTTP endpoint, basically.
 *
 */
class OdooClient {
    private def odooHost     = System.getenv("ODOO_HOST")
    private def odooPort     = System.getenv("ODOO_PORT")
    private def odooScheme   = System.getenv("ODOO_SCHEME")
    private def odooUser     = System.getenv("ODOO_USERNAME")
    private def odooPassword = System.getenv("ODOO_PASSWORD")
    private def odooDb       = System.getenv("ODOO_DB")

    private def http
    private def isLoggedIn = false
    public OdooClient() {
        this.http = new RESTClient()
    }

    public def isLoggedIn() {
        return this.isLoggedIn
    }
    public def login() {
        def odooLoginUrl = "${odooScheme}://${odooHost}:${odooPort}/web/session/authenticate"


        def response = http.post(
                    uri: odooLoginUrl,
                    body: [jsonrpc: "2.0",
                           method: "call",
                            params: [ db: odooDb, login: odooUser, password: odooPassword ]
                    ],
                    requestContentType: ContentType.JSON,
                    headers: [Accept: "application/json"]

        )
        this.isLoggedIn = true
        return response.data
    }

    public def getFields(def model) {
        def odooSearchReadUrl = "${odooScheme}://${odooHost}:${odooPort}/web/dataset/call_kw"
        def response = http.post(
                uri: odooSearchReadUrl,
                body: [
                        jsonrpc: "2.0",
                        method: "call",
                        params: [
                            model: model,
                            method: "fields_get",
                            args: [],
                            kwargs: [
                                    context: [lang: "fr_FR", tz: "Europe/Zurich", uid: 69, allowed_company_ids: [3]]
                            ]
                        ]
                ],
                requestContentType: ContentType.JSON,
                headers: [Accept: "application/json"]
        )
        return response.data.result.value
    }

    public def findUser(def user) {
        def odooSearchReadUrl = "${odooScheme}://${odooHost}:${odooPort}/web/dataset/search_read"
        def response = http.post(
                uri: odooSearchReadUrl,
                body: [
                        jsonrpc: "2.0",
                        method: "call",
                        params: [
                                model: "res.users",
                                domain: [[
                                                 "login",
                                                 "=",
                                                 user
                                         ]],
                                fields: [ "id"]
                        ]
                ],
                requestContentType: ContentType.JSON,
                headers: [Accept: "application/json"]
        )
        if (response.data.error?.message == "Odoo Session Expired") {
            this.isLoggedIn = false
            throw new DisconnectedFromOdooException();
        }
        return response.data.result.records[0]
    }

    /**
     * gets the current attendance state for the user given as argument.
     *
     * @param user the user whose attendance state has to be checked
     * @return null if user is not found, else the attendance state of the first user found
     * @throws RuntimeException if an error is returned by the Odoo server.
     */
    public def getAttendanceState(def user) {
        def odooSearchReadUrl = "${odooScheme}://${odooHost}:${odooPort}/web/dataset/search_read"
        def response = http.post(
                uri: odooSearchReadUrl,
                body: [
                       jsonrpc: "2.0",
                       method: "call",
                       params: [
                               model: "res.users",
                               domain: [[
                                       "login",
                                       "=",
                                       user
                               ]],
                               fields: ["id", "attendance_state"]
                       ]
                ],
                requestContentType: ContentType.JSON,
                headers: [Accept: "application/json"]
        )
        if (response.data.error?.message != null) {
            throw new RuntimeException(response.data.error.message)
        }
        if (response.data.result.length == 0) {
            return null
        }
        return response.data.result.records[0].attendance_state
    }

    public def getComingLeavesForUser(def user) {
        def userId = findUser(user)["id"]

        def odooSearchReadUrl = "${odooScheme}://${odooHost}:${odooPort}/web/dataset/search_read"
        def response = http.post(
                uri: odooSearchReadUrl,
                body: [
                        jsonrpc: "2.0",
                        method: "call",
                        params: [
                                model: "hr.leave",
                                domain: [
                                        ["user_id", "=", userId],
                                        ["date_from", ">=", new Date().format('YYYY-MM-dd 00:00:00')],
                                ],
                                fields: []
                        ]
                ],
                requestContentType: ContentType.JSON,
                headers: [Accept: "application/json"]
        )

        return response.data.result.records.sort {
            Date.parse('yyyy-MM-dd HH:mm:ss', it.date_from)
        }.findAll {
            it.state == "validate"
        }
    }

    public static void main(String[] args) {
        def toTest = new OdooClient()
        toTest.login()
        //def state = toTest.getAttendanceState("yjacolin")
//      //assert state in ["checked_out", "checked_in"]
//      // check the attendance of Pamela Anderson
//      def state = toTest.getAttendanceState("panderson")
//      assert state == null
//        toTest.findUser("yjacolin")
        //toTest.getColor()
        //toTest.getFields("res.users")
        //toTest.getLeaves()
        //toTest.getLeaves()
        //toTest.getLeads()
        //toTest.getFields("crm.lead")
        //toTest.getFields("res.users")
        def ret = toTest.getComingLeavesForUser("pmauduit")
        println ret
    }
}