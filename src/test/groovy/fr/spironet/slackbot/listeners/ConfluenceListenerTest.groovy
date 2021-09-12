package fr.spironet.slackbot.listeners

import fr.spironet.slackbot.jira.MockJiraResponse
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class ConfluenceListenerTest {
    private tested
    @Before
    void setUp() {
        this.tested = new ConfluenceListener("/dev/null",
        "http://localhost/confluence", "ghp_aaaa")
        this.tested.issueResolver.http = new MockJiraResponse() {
            @Override
            public Object get( Map<String,?> args ) {
                if (args.queryString.contains("noresult")) {
                    return [data: [results: [:]]]
                }
                if (args.uri == "http://localhost/confluence" &&
                        args.path == "/confluence/rest/api/search" &&
                        args.queryString.contains("type+IN+%28%22page%22%2C%22blogpost%22%29")) {
                    def ret = new File(this.getClass().getResource("page-blogpost.json").toURI()).text
                    return [data: new JsonSlurper().parseText(ret)]
                } else if (args.uri == "http://localhost/confluence" &&
                        args.path == "/confluence/rest/api/search" &&
                        args.queryString.contains("type+IN+%28%22blogpost%22%29")) {
                    def ret = new File(this.getClass().getResource("only-blogs.json").toURI()).text
                    return [data: new JsonSlurper().parseText(ret)]
                } else if (args.uri == "http://localhost/confluence" &&
                        args.path == "/confluence/rest/api/search" &&
                        args.queryString.contains("type+IN+%28%22page%22%29")) {
                    def ret = new File(this.getClass().getResource("page-blogpost.json").toURI()).text
                    def parsed = new JsonSlurper().parseText(ret)
                    parsed.results = parsed.results.findAll { it.content.type == "page" }
                    return [ data: parsed ]
                }
                return [:]
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

    @Test
    void testConfluenceListenerSearchOnlyPages() {
        def message = tested.processCommand("!confluence pages georchestra lopocs")

        assertTrue(message.toString().contains("Here are"))
        assertTrue(message.message.split("• *").size() == 3)
    }

    @Test
    void testConfluenceListenerSearchNoResult() {
        def message = tested.processCommand("!confluence pages noresult")

        assertTrue(message.toString().contains(":ledger: No confluence documents found for topic *[noresult]*"))
    }

    @Test
    void testConfluenceListenerSearchInvalidSearch() {
        def message = tested.processCommand("!confluence dudupopo noresult")

        assertTrue(message.toString().contains("Usage: "))
    }

}
