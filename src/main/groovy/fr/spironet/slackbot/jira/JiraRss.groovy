package fr.spironet.slackbot.jira

import com.google.common.cache.CacheBuilder
import groovy.xml.XmlSlurper
import groovyx.net.http.HTTPBuilder
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class JiraRss {
    def jiraRssUrl
    def jiraUser
    def jiraPassword

    def http

    def notificationMap = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(4, TimeUnit.HOURS)
            .build()

    def simpleDate = new SimpleDateFormat("yyyy-MM-dd")
    def jiraIssueKeyRegex = /((?<!([A-Z]{1,10})-?)[A-Z]+-\d+)/

    def JiraRss() {
        def props = new Properties()
        File propertiesFile = new File(System.getenv("JIRA_CLIENT_PROPERTY_FILE"))
        propertiesFile.withInputStream {
            props.load(it)
        }
        this.jiraRssUrl   = props.getProperty("jira.server.url")
        this.jiraUser     = props.getProperty("jira.user.id")
        this.jiraPassword = props.getProperty("jira.user.pwd")
        this.http         = new HTTPBuilder(this.jiraRssUrl)
    }

    def JiraRss(def propsFile) {
        def props = new Properties()
        File propertiesFile = new File(propsFile)
        propertiesFile.withInputStream {
            props.load(it)
        }
        this.jiraRssUrl   = props.getProperty("jira.server.url")
        this.jiraUser     = props.getProperty("jira.user.id")
        this.jiraPassword = props.getProperty("jira.user.pwd")
        this.http         = new HTTPBuilder(this.jiraRssUrl)
    }

    def JiraRss(def jiraRssUrl, def jiraUser, def jiraPassword) {
        this.jiraRssUrl   = jiraRssUrl
        this.jiraUser     = jiraUser
        this.jiraPassword = jiraPassword
        this.http         = new HTTPBuilder(this.jiraRssUrl)
    }

    def parseRssDate(def date) {
        return LocalDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME)
    }

    def parseRssIsoInstantMsec(def date) {
        def calendar = Instant.parse(date).toCalendar()
        def zid = ZoneId.systemDefault()
        return LocalDateTime.ofInstant(calendar.toInstant(), zid)
    }

    def rssLatestModifiedIssues() {

        def authorizationHeader = "Basic " + "${jiraUser}:${jiraPassword}".bytes.encodeBase64()
        def path = "/sr/jira.issueviews:searchrequest-rss/temp/SearchRequest.xml"

        def rssTxt = ""
        http.get(path: path,
                queryString: "jqlQuery=watcher%20=%20currentUser()%20ORDER%20BY%20updated&tempMax=10",
                headers: ["Authorization": authorizationHeader]) { resp, reader ->
            rssTxt = reader.getText()
        }
        def rssFeed = new XmlSlurper().parseText(rssTxt)

        def ret = [:]
        rssFeed.channel.item.each {
            def issueId = it.link.text()
            def modifiedDate = parseRssDate(it.pubDate.text())
            ret.put(issueId, modifiedDate)
        }
        return ret
    }

    def extractIssueIdFromUrl(def url) {
        return url - this.jiraRssUrl - "/browse/"
    }

    def rssGetModificationDetails(def issueId, def lastScrapeDate) {
        def authorizationHeader = "Basic " + "${jiraUser}:${jiraPassword}".bytes.encodeBase64()
        def projectId = issueId.split("-")[0]
        def path = "/activity"
        def rssFeed = http.get(path: path,
                queryString: "maxResults=20&streams=issue-key+IS+${issueId}&streams=key+IS+${projectId}&os_authType=basic" as String,
                headers: ["Authorization": authorizationHeader])

        def ret = [:]
        rssFeed.entry.each {
            def cleanedTitle = Jsoup.clean(it.title.text(), Whitelist.none())
            def content = Jsoup.clean(it.content.text(), Whitelist.none())
            // removing from stuff from the title
            if (cleanedTitle.indexOf(issueId) > 0) {
                cleanedTitle = cleanedTitle.substring(0, cleanedTitle.indexOf(issueId) + issueId.size())
            }
            def updatedAt = this.parseRssIsoInstantMsec(it.updated.text())
            def itemId = it.id.text()
            // if id is empty, try something else
            if (itemId?.isEmpty()) {
                itemId = it.object.id.text()
            }
            def author = it.author.email?.text()

            // notifies only if the event has not been sent already
            // limit to results newer than 2 hours before last scrape
            def itemIdNotEmpty = ! itemId?.isEmpty()
            def notNotifiedYet = ! this.notificationMap.getIfPresent(itemId)
            def notExpired     = updatedAt > lastScrapeDate.minusHours(2)
            if (itemIdNotEmpty && notNotifiedYet  && notExpired) {
                ret.put(updatedAt, [ title: cleanedTitle, content: content, author: author ])
                this.notificationMap.put(itemId, itemId)
            }
        }
        return ret
    }

    /**
     * Calculates the date of the last sunday.
     * (if current day is monday, we want to get further than just "yesterday", though).
     *
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
     * Gets the rss feed of "my" (relative to the jira user login) activity.
     * @param maxResults the max results to return. Generally 250 (the default)
     * is sufficient to cover a whole week.
     *
     * @return an array of map having the following fields:
     *   * desc: description of the event,
     *   * date: the LocalDateTimeObject.
     */
    def rssGetMyActivity(def maxResults = 250) {
        def authorizationHeader = "Basic " + "${jiraUser}:${jiraPassword}".bytes.encodeBase64()

        def rssFeed = http.get(path: "/activity",
                queryString: "maxResults=${maxResults}&streams=user+IS+${this.jiraUser}" as String,
                headers: ["Authorization": authorizationHeader])

        def lastSunday = lastSunday()

        def entries = rssFeed.entry.findResults {
            def date = parseRssIsoInstantMsec(it.published.text())
            if (date < lastSunday)
                return null
            def summary = it.content.text().isEmpty() ? it.title.text() : it.title.text() + it.content.text()
            def doc = Jsoup.parse(summary).text()
            if (doc.contains(" logged ") || doc.contains(" updated 2 fields "))
                return null
            if (doc.length() > 152)
                doc = doc[0..150] + "…"

            [desc: doc , date: date]
        }
        return entries
    }

    /**
     * given an array of map returned by rssGetMyActivity(), returns a map indexed
     * by date (format "yyyy-MM-dd"), and a set of issues.
     *
     * @param entries the array of event entries
     * @return a map with the following specification:
     *   * key: the date "yyy-MM-dd" format
     *   * value: a set of detected JIRA issue keys.
     */
    def getIssuesWorkedOnByDate(def entries) {
        def ret = [:]
        entries.each {
            def date = simpleDate.format(it.date.toDate())
            if (! (date in ret)) {
                ret[date] = []
            }
            def detectedIssuesKeys = it.desc.findAll(jiraIssueKeyRegex) as Set
            if (detectedIssuesKeys.size() > 0) {
                ret[date] = (ret[date] + detectedIssuesKeys) as Set
            }

        }
        return ret
    }
}
