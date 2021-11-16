package fr.spironet.slackbot.confluence

import com.google.common.cache.CacheBuilder
import groovyx.net.http.HTTPBuilder
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ConfluenceRss {
    def confluenceRssUrl
    def confluenceUser
    def confluencePassword
    def http

    def ConfluenceRss() {
        def jiraPropsFilePath = System.getenv("JIRA_CLIENT_PROPERTY_FILE")
        if (jiraPropsFilePath == null) {
            throw new RuntimeException("JIRA_CLIENT_PROPERTY_FILE env variable must be set")
        }
        loadParams(jiraPropsFilePath)
    }

    def ConfluenceRss(def path) {
        loadParams(path)
    }

    private def loadParams(def path) {
        def props = new Properties()
        File propertiesFile = new File(path)
        propertiesFile.withInputStream {
            props.load(it)
        }
        this.confluenceRssUrl = System.getenv("CONFLUENCE_SERVER_URL")
        this.confluenceUser = props.getProperty("jira.user.id")
        this.confluencePassword = props.getProperty("jira.user.pwd")
        this.http = new HTTPBuilder(this.confluenceRssUrl)
    }

    def parseRssDate(def date) {
        def calendar = Instant.parse(date).toCalendar()
        def zid = ZoneId.systemDefault()
        return LocalDateTime.ofInstant(calendar.toInstant(), zid)
    }

    def getRssFeed() {

        def authorizationHeader = "Basic " + "${confluenceUser}:${confluencePassword}".bytes.encodeBase64()

        def path = "/confluence/createrssfeed.action"

        def queryParams = [
                "types"           : ["page", "blogpost"],
                "pageSubTypes"    : ["comment", "attachment"],
                "blogpostSubTypes": ["comment", "attachment"],
                "spaces"          : "conf_favorites",
                "sort"            : "created",
                "maxResults"      : "10",
                "timeSpan"        : "5",
                "showContent"     : "false"
        ]
        def rssFeed
        http.get(path: path,
                query: queryParams,
                headers: ["Authorization": authorizationHeader]) { resp, xml ->
            rssFeed = xml
        }
        return rssFeed
    }

    def rssLatestModifiedItems() {
        def rssFeed = getRssFeed()

        def ret = []
        rssFeed.entry.each {
            def doc = Jsoup.parse(it.summary.text())
            def summary = Jsoup.clean(doc.getElementsByTag("p")[0].toString(), Whitelist.none())
            def url = it.link.@href.text()

            def id = it.id.text()
            def updatedDate = parseRssDate(it.updated.text())
            ret.add(["id": id, "title": it.title.text(), "summary": summary, "url": url, "updatedDate": updatedDate])
        }
        return ret
    }
}
