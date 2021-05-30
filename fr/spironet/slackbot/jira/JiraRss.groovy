package fr.spironet.slackbot.jira

import groovyx.net.http.HTTPBuilder
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class JiraRss {
    def jiraRssUrl
    def jiraUser
    def jiraPassword

    def http

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
            cleanedTitle = cleanedTitle.substring(0, cleanedTitle.indexOf(issueId) + issueId.size())
            def updatedAt = this.parseRssIsoInstantMsec(it.updated.text())
            if (updatedAt > lastScrapeDate) {
                ret.put(updatedAt, [ title: cleanedTitle, content: content ])
            }
        }
        return ret
    }
}
