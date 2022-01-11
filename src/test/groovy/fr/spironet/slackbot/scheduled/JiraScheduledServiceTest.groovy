package fr.spironet.slackbot.scheduled


import fr.spironet.slackbot.jira.IssueDetailsResolver
import groovy.json.JsonSlurper
import org.junit.Test
import static org.junit.Assert.assertTrue

class JiraScheduledServiceTest {

    def toTest = new JiraScheduledService(null, new MockIssueResolver("/dev/null", null, null))
    @Test
    public void testGetIssuesToNotify() {
        def msg = toTest.getIssuesToNotify().toString()


        assertTrue(msg.contains("New issues coming from JIRA *(4)*:")
          && msg.contains("*<http://myjira/browse/ABC-4739|ABC-4739>* - :office: *chambery* - Add Jane to the SSH env")
        )
    }

    class MockIssueResolver extends IssueDetailsResolver {
        def jiraUrl = "http://myjira"
        public MockIssueResolver(def a, def b, def c) {
            super(a,b,c)
        }

        public searchJiraIssues(def jql) {
            def ret = new File(this.getClass().getResource("/fr/spironet/slackbot/jira/search-issues-response.json").toURI()).text
            return new JsonSlurper().parseText(ret)
        }
    }
}
