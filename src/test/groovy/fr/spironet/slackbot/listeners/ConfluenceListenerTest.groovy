package fr.spironet.slackbot.listeners

import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class ConfluenceListenerTest {
    private tested
    @Before
    void setUp() {
        this.tested = new ConfluenceListener("/dev/null") {
            private def confluenceServerUrl = "http://localhost:8080"
            @Override
            def confluenceSearch(def cql, def authorizationHeader) {
                if (cql.contains("type+IN+%28%22page%22%2C%22blogpost%22%29")) {
                    def ret = new File(this.getClass().getResource("page-blogpost.json").toURI())
                    return [ data : new JsonSlurper().parseText(ret.text) ]
                } else if (cql.contains("type+IN+%28%22blogpost%22%29")) {
                    def ret = new File(this.getClass().getResource("only-blogs.json").toURI())
                    return [ data : new JsonSlurper().parseText(ret.text) ]
                }
            }
        }
    }

    @Test
    void testConfluenceListenerUsage() {
        def message = tested.processCommand("!confluence usage")

        assertTrue(message.toString().contains("Usage: "))
    }

    @Test
    void testConfluenceListenerNonExistingCommand() {
        def message = tested.processCommand("!confluence aaaaa")

        assertTrue(message.toString().contains("Usage: "))
    }

    @Test
    void testConfluenceListenerSearch() {
        def message = tested.processCommand("!confluence search georchestra lopocs")

        assertTrue(message.toString().contains("Here are"))
        assertTrue(message.message.split("• *").size() == 4)
    }

    @Test
    void testConfluenceListenerSearchOnlyBlogs() {
        def message = tested.processCommand("!confluence blogs georchestra lopocs")

        assertTrue(message.toString().contains("Here are"))
        assertTrue(message.message.split("• *").size() == 2)
    }

}
