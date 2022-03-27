package fr.spironet.slackbot.scheduled

import fr.spironet.slackbot.github.MockGithubApiClient
import fr.spironet.slackbot.slack.MockSlackSession
import org.junit.Test
import org.junit.jupiter.api.BeforeEach

import java.text.SimpleDateFormat

import static org.junit.Assert.assertTrue

class GithubScheduledServiceTest extends GithubScheduledService {


    GithubScheduledServiceTest() {
        super(new MockSlackSession(), new MockGithubApiClient())
        this.botOwnerEmail = "pierre.mauduit@example.com"
    }

    @BeforeEach
    void setUpEach() {
        this.slackSession.reset()
        this.githubApiClient.fakedCreatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date())
        this.notificationMap.invalidateAll()
    }
    @Test
    void testRunOneIterationNoFilterOnDate() {

        this.runOneIteration()

        assertTrue(this.slackSession.messages.size() == 9)
    }

    @Test
    void testRunOneIterationAllExpired() {
        this.githubApiClient.fakedCreatedAt = "2021-08-26T15:34:23Z"

        this.runOneIteration()

        assertTrue(this.slackSession.messages.size() == 0)
    }

    @Test
    void testRunOneIterationSomeMessagesAlreadyNotified() {
        this.githubApiClient.fakedCreatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date())
        this.notificationMap.putAll([
                "18152539341": "18152539341",
                "18144431621":"18144431621"
        ])

        this.runOneIteration()

        assertTrue(this.slackSession.messages.size() == 7)
    }

    @Test
    void testDefaultCtor() {
        def toTest = new GithubScheduledService(new MockSlackSession(), new MockGithubApiClient())

        toTest.eventFilter instanceof fr.spironet.slackbot.github.EventFilter
    }
}


