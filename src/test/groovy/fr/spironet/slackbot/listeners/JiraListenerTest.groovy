package fr.spironet.slackbot.listeners


import com.ullink.slack.simpleslackapi.impl.SlackPersonaImpl
import com.ullink.slack.simpleslackapi.impl.SlackProfileImpl
import fr.spironet.slackbot.jira.IssueDetailsResolver
import fr.spironet.slackbot.jira.MockJiraResponse
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class JiraListenerTest {

    private JiraListener toTest
    private def event
    private def session


    @Before
    public void setUp() {
        this.toTest = new JiraListener("/dev/null", "", "")
        this.toTest.issueResolver.http = new MockJiraResponse()
        this.toTest.issueResolver.jiraUrl = "https://jira"

        this.event = new Object() {
            def messageContent = ""
            def getChannel() {
                return null
            }
            def getMessageContent() {
                return messageContent
            }
            def getSender() {
                return new SlackPersonaImpl.SlackPersonaImplBuilder()
                        .userName("jdoe")
                        .profile(new SlackProfileImpl.SlackProfileImplBuilder().email("john@doe.eu").build())
                        .build()
            }
        }
        this.session = new Object() {
            def sentMessages = []
            def sendMessage(def _, def message) {
                sentMessages << message
            }
        }

    }

    @Test
    void testJiraListenerDoOnEventEmptyMessageOrUnrelated() {
        this.session.sentMessages = []
        this.toTest.doOnEvent(event, session)

        // nothing is done
        assertTrue(session.sentMessages.size() == 0)

        event.messageContent = "!odoo help"
        this.toTest.doOnEvent(event, session)

        // nothing is done either / message discarded by the listener
        assertTrue(session.sentMessages.size() == 0)
    }

    @Test
    void testJiraListenerDoOnEventBadIssueKey() {
        this.session.sentMessages = []
        event.messageContent = "!jira help"
        this.toTest.doOnEvent(event, session)

        // a message should have been added with the help / usage
        // as the "help" issue is probably not found.
        assertTrue(session.sentMessages.size() == 1 &&
        session.sentMessages[0].message.contains("Usage: !jira"))
    }

    @Test
    void testJiraListenerDoOnEventGetIssue() {
        this.session.sentMessages = []
        event.messageContent = "!jira ABC-316"
        this.toTest.doOnEvent(event, session)

        // a message should have been added with the help / usage
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.contains("Migration à CAS 6 dans wonderProject"))
    }

    @Test
    void testJiraListenerDoOnEventGetWorklog() {
        this.session.sentMessages = []
        event.messageContent = "!jira worklog ABC-316"
        this.toTest.doOnEvent(event, session)

        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.contains("Worklog for <https://jira/browse/ABC-316|ABC-316>*" +
                        " _(over the last 4 weeks)_")
        )
    }

    @Test
    void testJiraListenerDoOnEventGetMyIssue() {
        this.session.sentMessages = []
        event.messageContent = "!jira mine"
        this.toTest.doOnEvent(event, session)

        assertTrue(session.sentMessages.size() == 1 &&
        session.sentMessages[0].message.startsWith(":warning: Unresolved issues *(4)*:"))
    }

    @Test
    void testJiraListenerDoOnEventGetPSebastienIssues() {
        this.session.sentMessages = []
        event.messageContent = "!jira user psebastien"
        this.toTest.doOnEvent(event, session)

        // Since the same sample dataset is returned, we can expect the same
        // result as the previous test.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.startsWith(":warning: Unresolved issues *(4)*:"))
    }

    @Test
    void testJiraListenerDoOnEventGetIssuesOnMonitoring() {
        this.session.sentMessages = []
        event.messageContent = "!jira monitoring"
        this.toTest.doOnEvent(event, session)

        // Since the same sample dataset is returned, we can expect the same
        // result as the previous tests.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.startsWith(":warning: Issues currently reported on the monitoring screen *(4)*:"))
    }

    @Test
    void testJiraListenerDoOnEventGetIssuesOnSupport() {
        this.session.sentMessages = []
        event.messageContent = "!jira support"
        this.toTest.doOnEvent(event, session)

        // Since the same sample dataset is returned, we can expect the same
        // result as the previous tests.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.startsWith(":warning: Unresolved issues in the GEO support project since the begining of the week *(4)*:"))
    }

}
