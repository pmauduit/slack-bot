package fr.spironet.slackbot.tempo

import fr.spironet.slackbot.webbrowser.WebBrowser
import groovy.json.JsonOutput
import groovy.text.SimpleTemplateEngine
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

import javax.imageio.ImageIO
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat

class TempoApi {
    private def username
    private def password
    private def jiraUrl

    def http = new RESTClient()

    static def createWorklogUrl = "/rest/tempo-timesheets/4/worklogs/"
    static def searchWorklogUrl = "/rest/tempo-timesheets/4/worklogs/search"
    static def timeSheetApprovalStatus = "/rest/tempo-timesheets/4/timesheet-approval/approval-statuses"

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

        http.post(
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

    /**
     * Formats the worklogs from a week to an array, indexed by:
     *
     * day: mon, tue, wed, thu, fri, sat, sun.
     * time: number of time worked in seconds.
     *
     * The data structure passed as argument should be a one compatible
     *  to what the searchWorklogs() method returns.
     *
     * @param data an array of worklogs.
     * @return an array of map typed as e.g.: { day: 'Mon', time: 33 }
     */
    def worklogsWeekPerDay(def data) {
        def perDay = [:]
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        def dowf = new SimpleDateFormat("EEE")

        data.each {
            def date = sdf.parse(it.started)
            def dow = dowf.format(date)
            if (perDay[dow] == null) {
                perDay[dow] = it.timeSpentSeconds
            } else {
                perDay[dow] += it.timeSpentSeconds
            }
        }

        perDay.collect { k,v ->
            [ day: k, time: v ]
        }
    }

    /**
     * Given a data structure returned by searchWorklogs(), calculates
     * and returns the time spent in seconds per project.
     *
     * @param data the data returned by a searchWorklogs() call.
     * @return an array of map following this specification:
     * { project: "JIRAKEY", time: 22 }
     *
     */
    def worklogsWeekPerProject(def data) {
        def perProject = [:]
        data.each {
            def project = it.issue.projectKey
            if (perProject[project] == null) {
                perProject[project] = it.timeSpentSeconds
            } else {
                perProject[project] += it.timeSpentSeconds
            }
        }

        perProject.collect { k,v ->
            [ project: k, time: v ]
        }.sort {a,b -> b.time <=> a.time }
    }

    /**
     * Given a data structure returned by searchWorklogs(), calculates
     * and returns the time spent in seconds per issue.
     * @param data the data returned by a searchWorklogs() call.
     * @return  an array of map following this specification:
     * { project: "KEY-123", time: 22 }
     */
    def worklogsWeekPerIssue(def data) {
        def perIssue = [:]
        data.each {
            def issue = it.issue.key
            if (perIssue[issue] == null) {
                perIssue[issue] = it.timeSpentSeconds
            } else {
                perIssue[issue] += it.timeSpentSeconds
            }
        }

        perIssue.collect { k,v ->
            [ issue: k, time: v ]
        }.sort {a,b -> b.time <=> a.time }
    }
    /**
     * Calculates the date of the last sunday.
     * (if current day is monday, we want to get further than just "yesterday", though).
     * TODO: copy/pasted from JiraRss.
     * @return the date of the "last" sunday.
     */
    def lastSunday() {
        def cal = Calendar.instance
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_WEEK, -7)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_WEEK, -1)
        }
        cal.time.toLocalDateTime()
    }

    /**
     * Given a date as argument, calculates the next friday, relative
     * to this date.
     * @param relativeSunday
     * @return the date of the next friday.
     */
    def nextFriday(def relativeSunday) {
        def cal = Calendar.instance
        cal.setTime(relativeSunday.toDate())
        while(cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            cal.add(Calendar.DAY_OF_WEEK, 1)
        }
        return cal.time.toLocalDateTime()

    }

    /**
     * Given a date, returns if the timesheet the date belongs to
     * has been approved or not.
     *
     * @param date the date the period belongs to.
     * @return true if timesheet for this period has been approved,
     * false otherwize.
     *
     */
    def isTimesheetApproved(def date) {
        def response = http.get(
                uri: this.jiraUrl,
                path: TempoApi.timeSheetApprovalStatus,
                queryString:"userKey=${this.username}&periodStartDate=${date}",
                headers: [
                        Authorization: "Basic " + "${this.username}:${this.password}".bytes.encodeBase64()
                ],
        )
        return response.data[0].status == "approved"
    }

    /**
     * Given two dates, returns a report as a PNG with 3 graphs:
     *
     * * a graph giving the work time per day on the interval,
     * * a graph giving the work time per project, descending,
     * * a graph giving the work time per issues, descending.
     *
     * Note: a temporary website in d3js is created, and a WebBrowser
     * is invoked on it to generate the image sent as output. The
     * website files are removed at the end of the method.
     *
     * @param dateBegin the start date
     * @param dateEnd the end date
     *
     * @return a PNG as a ByteArrayOutputStream object.
     */
    def generateReport(def dateBegin, def dateEnd) {
        File reportDir = File.createTempDir()
        try {
            def index = this.getClass().getResourceAsStream("index.html")

            Path copiedIdx = Paths.get(reportDir.getAbsolutePath(), "index.html")
            copiedIdx.toFile().withOutputStream {
                it << index.getBytes()
            }
            index.close()

            def scriptJs = this.getClass().getResourceAsStream("script.js").getText()

            def weekData = this.searchWorklog(dateBegin, dateEnd)
            def perIssue = this.worklogsWeekPerIssue(weekData)
            def perProject = this.worklogsWeekPerProject(weekData)
            def perDay = this.worklogsWeekPerDay(weekData)

            def template = new SimpleTemplateEngine().createTemplate(scriptJs)
            // Groovy won't toString() valid JS, using JSON to replace JS variables
            // in our template.
            def jsonOutput = new JsonOutput()
            def templated = template.make([perIssue  : jsonOutput.toJson(perIssue),
                                           perProject: jsonOutput.toJson(perProject),
                                           perDay    : jsonOutput.toJson(perDay)])
            Paths.get(reportDir.getAbsolutePath(), "script.js").toFile().withOutputStream {
                it << templated
            }

            def screenshot = new WebBrowser().visit(copiedIdx.toUri().toString())

            def im = ImageIO.read(new ByteArrayInputStream(screenshot.getBytes()))
            def cropped = im.getSubimage(0, 0, 1220, 1055)
            def baos = new ByteArrayOutputStream(2048)
            ImageIO.write(cropped, "png", baos)
            return new ByteArrayInputStream(baos.toByteArray())
        } finally {
            reportDir.deleteDir()
        }
    }
}
