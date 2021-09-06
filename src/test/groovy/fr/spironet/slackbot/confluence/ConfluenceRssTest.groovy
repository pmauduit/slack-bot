package fr.spironet.slackbot.confluence

import groovy.xml.XmlSlurper
import org.junit.Assume
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assume.assumeTrue
class ConfluenceRssTest {

    private def toTest

    @Before
    void setUp() {

        this.toTest = new ConfluenceRss("/dev/null") {
            @Override
            def getRssFeed() {
                def content = new File(this.getClass().getResource("rssfeed.xml").toURI())
                return new XmlSlurper().parseText(content.text)
            }
        }
    }

    @Test
    void testRss() {
        def items = toTest.rssLatestModifiedItems()
        assertTrue(items.size() == 8)
    }

    @Test(expected = RuntimeException)
    void testConfluenceRssMissingEnvVariable() {
        assumeTrue(System.env["JIRA_CLIENT_PROPERTY_FILE"] == null)

        def toTest = new ConfluenceRss()
    }

    @Test
    void testConfluenceRssGetRss() {
        def toTest2 = new ConfluenceRss("/dev/null")
        toTest2.http = new Object() {
            def get(def args, def closure) {
                def ret = new File(
                        this.getClass().getResource("rssfeed.xml").toURI()
                ).text
                closure.call(null, new XmlSlurper().parseText(ret))
            }
        }
        def ret = toTest2.getRssFeed()

        assertTrue(ret[0].name == "feed")
    }
}
