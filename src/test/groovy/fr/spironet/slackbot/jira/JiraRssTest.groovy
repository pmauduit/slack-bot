package fr.spironet.slackbot.jira

import groovy.xml.XmlSlurper
import org.junit.Test

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import static org.junit.Assert.assertTrue

class JiraRssTest {

    @Test
    void testConsumePersonalRss() {
        def toTest = new JiraRss("http://jira", "jdoe", "secret") {
            @Override
            def lastSunday() {
                def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                return LocalDateTime.parse("2021-09-12 20:35", formatter)
            }
        }
        toTest.http = new MockJiraRssResponse()
        def obj = toTest.rssGetMyActivity()

        assertTrue(obj.size() == 8)
    }

    @Test
    void testLastSunday() {
        def toTest = new JiraRss("http://jira", "jdoe", "secret")
        def today = Calendar.instance

        def lastSunday = toTest.lastSunday()

        def duration
        use(groovy.time.TimeCategory) {
            duration = today.toLocalDateTime().toDate() - lastSunday.toDate()
        }
        assertTrue(lastSunday.toCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        assertTrue(duration.days <= 8)

    }

    @Test
    void testGetIssuesWorkedOnByDate() {
        def toTest = new JiraRss("http://jira", "jdoe", "secret") {
            @Override
            def lastSunday() {
                def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                return LocalDateTime.parse("2021-09-12 20:35", formatter)
            }
        }
        toTest.http = new Object() {
            def get(def args) {
                def rssTxt = this.getClass().getResource("personal-rss-feed.xml").text
                return new XmlSlurper().parseText(rssTxt)
            }
        }
        def obj = toTest.rssGetMyActivity()
        def tested = toTest.getIssuesWorkedOnByDate(obj)

        assertTrue(tested.keySet().size() == 1 &&
            tested["2021-09-15"].size() == 5
        )

    }
}
