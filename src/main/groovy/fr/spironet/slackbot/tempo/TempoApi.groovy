package fr.spironet.slackbot.tempo


import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

class TempoApi {
    private def username
    private def password
    private def jiraUrl

    def http = new RESTClient()

    static def createWorklogUrl = "/rest/tempo-timesheets/4/worklogs/"
    static def searchWorklogUrl = "/rest/tempo-timesheets/4/worklogs/search"

    def TempoApi(def username, def password, def jiraUrl) {
        this.username = username
        this.password = password
        this.jiraUrl = jiraUrl
    }

    /**
     * Converts a string given as argument to a number of minutes.
     *
     * @param str the string to parse, like "1h", "60m", "1h30", "1h30m" ...
     * @return an integer representing the number of minutes.
     * @throws Exception
     */
    def toMinutes(def str) throws Exception {
        def mPat = /^(\d+)m$/
        def match = str =~ mPat
        if (match.size() > 0) {
            return (match[0][1] as Integer)
        }
        def hPat = /^(\d+)h$/
        match = str =~ hPat
        if (match.size() > 0) {
            return (match[0][1] as Integer) * 60
        }

        def hmPat = /^(\d+)h(\d+)m?$/
        match = str =~ hmPat
        if (match.size() > 0) {
            return (match[0][1] as Integer) * 60 + (match[0][2] as Integer)
        }
        throw new Exception("Unable to parse the time spent ${str}")
    }

    /**
     * Creates a worklog entry using the Tempo API.
     *
     * @param message the message asociated with the worklog.
     * @param issueKey the issue key to timesheet on.
     * @param date the date.
     * @param timeSpent string representing the time spent.
     *
     * @return true if success, throws an exception otherwise.
     */
    def createWorklog(def message, def issueKey, def date, def timeSpent) {
        def worklogEntry = [
                attributes: [:],
                billableSeconds: "",
                comment: message,
                endDate: null,
                includeNonWorkingDays: false,
                originId: -1,
                originTaskId: issueKey,
                remainingEstimate: null,
                started: date,
                timeSpentSeconds: toMinutes(timeSpent) * 60,
                worker: this.username,
        ]

        def response = http.post(
                uri: this.jiraUrl,
                path: TempoApi.createWorklogUrl,
                requestContentType: ContentType.JSON,
                headers: [
                        Accept: "application/json",
                        Authorization: "Basic " + "${this.username}:${this.password}".bytes.encodeBase64()
                ],
                body: worklogEntry
        )
        return true
    }

    /**
     * Given a date span, returns the worklog recorded via Tempo for the current user
     * (or all the users the current user is allowed to query ?).
     *
     * @param from the start date in the form "YYYY-mm-dd"
     * @param to the end date in the form "YYYY-mm-dd"
     *
     * @return the known tempo records, as a groovy object.
     */
    def searchWorklog(def from, def to) {
        def payload = [
                from: from,
                to: to
                /* worker: ["username"] optional */
        ]
        def response = http.post(
                uri: this.jiraUrl,
                path: TempoApi.searchWorklogUrl,
                requestContentType: ContentType.JSON,
                headers: [
                        Accept: "application/json",
                        Authorization: "Basic " + "${this.username}:${this.password}".bytes.encodeBase64()
                ],
                body: payload
        )
        return response.data
    }

}
