package fr.spironet.slackbot.confluence

import groovy.xml.XmlSlurper
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

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
}
