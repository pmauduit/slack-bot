package fr.spironet.slackbot.listeners


import com.ullink.slack.simpleslackapi.impl.SlackPersonaImpl
import com.ullink.slack.simpleslackapi.impl.SlackProfileImpl
import fr.spironet.slackbot.jira.JiraRss
import fr.spironet.slackbot.jira.MockJiraResponse
import fr.spironet.slackbot.jira.MockJiraRssResponse
import org.junit.Before
import org.junit.Test

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

        this.toTest.jiraRss = new JiraRss("http://jira", "jdoe", "secret") {
            @Override
            def lastSunday() {
                def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                return LocalDateTime.parse("2021-09-12 20:35", formatter)
            }
        }
        this.toTest.jiraRss.http = new MockJiraRssResponse()
    }

    @Test
    void testDoOnEventEmptyMessageOrUnrelated() {
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
    void testDoOnEventProjectKey() {
        this.session.sentMessages = []
        event.messageContent = "!jira GEO"
        this.toTest.doOnEvent(event, session)

        // a message should have been added with the help / usage
        // as the "help" issue is probably not found.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.contains("Opened issues found for project *GEO*:"))
    }

    @Test
    void testDoOnEventBadIssueKey() {
        this.session.sentMessages = []
        event.messageContent = "!jira help"
        this.toTest.doOnEvent(event, session)

        // a message should have been added with the help / usage
        // as the "help" issue is probably not found.
        assertTrue(session.sentMessages.size() == 1 &&
        session.sentMessages[0].message.contains("Usage: !jira"))
    }

    @Test
    void testDoOnEventGetIssue() {
        this.session.sentMessages = []
        event.messageContent = "!jira ABC-316"
        this.toTest.doOnEvent(event, session)

        // a message should have been added with the help / usage
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.contains("Migration à CAS 6 dans wonderProject"))
    }

    @Test
    void testDoOnEventGetWorklog() {
        this.session.sentMessages = []
        event.messageContent = "!jira worklog ABC-316"
        this.toTest.doOnEvent(event, session)

        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.contains("Worklog for <https://jira/browse/ABC-316|ABC-316>*" +
                        " _(over the last 4 weeks)_")
        )
    }

    @Test
    void testDoOnEventGetMyIssue() {
        this.session.sentMessages = []
        event.messageContent = "!jira mine"
        this.toTest.doOnEvent(event, session)

        assertTrue(session.sentMessages.size() == 1 &&
        session.sentMessages[0].message.startsWith(":warning: Unresolved issues *(4)*:"))
    }

    @Test
    void testDoOnEventGetPSebastienIssues() {
        this.session.sentMessages = []
        event.messageContent = "!jira user psebastien"
        this.toTest.doOnEvent(event, session)

        // Since the same sample dataset is returned, we can expect the same
        // result as the previous test.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.startsWith(":warning: Unresolved issues *(4)*:"))
    }

    @Test
    void testDoOnEventGetIssuesOnMonitoring() {
        this.session.sentMessages = []
        event.messageContent = "!jira monitoring"
        this.toTest.doOnEvent(event, session)

        // Since the same sample dataset is returned, we can expect the same
        // result as the previous tests.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.startsWith(":warning: Issues currently reported on the monitoring screen *(4)*:"))
    }

    @Test
    void testDoOnEventGetIssuesOnSupport() {
        this.session.sentMessages = []
        event.messageContent = "!jira support"
        this.toTest.doOnEvent(event, session)

        // Since the same sample dataset is returned, we can expect the same
        // result as the previous tests.
        assertTrue(session.sentMessages.size() == 1 &&
                session.sentMessages[0].message.startsWith(":warning: Unresolved issues in the GEO support project since the begining of the week *(4)*:"))
    }

    @Test
    void testDoOnEventActivity() {
        this.session.sentMessages = []
        event.messageContent = "!jira activity"

        this.toTest.doOnEvent(event, session)

        assertTrue(this.session.sentMessages.size() == 1 &&
                this.session.sentMessages[0].message.contains(
                        ":computer: Activity from the JIRA RSS endpoint (back to last sunday):"
                ) &&
                this.session.sentMessages[0].message.contains("*2021-09-15:*\n") &&
                this.session.sentMessages[0].message.contains("• *<https://jira/browse/ABC-316|ABC-316>* - :office: *super_project* - _(In Progress)_ - Migration à CAS 6 dans wonderProject")
        )
    }

}
