package fr.spironet.slackbot.scheduled

import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.AbstractScheduledService
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import fr.spironet.slackbot.github.DefaultEventFilter
import fr.spironet.slackbot.github.GithubApiClient
import groovyx.net.http.RESTClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class GithubScheduledService extends AbstractScheduledService {

    private def githubWatchedUser = System.getenv("GITHUB_WATCHED_USER")
    private def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")

    def slackSession
    def zid = ZoneId.systemDefault()
    def eventFilter
    def githubApiClient
    def notificationMap = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(4, TimeUnit.HOURS)
            .build()

    private final static Logger logger = LoggerFactory.getLogger(GithubScheduledService.class)



    /**
     * Constructor
     *
     * @param slackSession a slack session object to interact with slack.
     *
     */
    public GithubScheduledService(def slackSession) {
        this.githubApiClient = new GithubApiClient()
        this.slackSession = slackSession
        def filterCls = Class.forName(System.getenv("GITHUB_EVENT_FILTER_CLASS") ?:
                "fr.spironet.slackbot.github.DefaultEventFilter")
        this.eventFilter = filterCls.newInstance()
    }
    /**
     * Constructor
     * @param slackSession a slack session object to interact with slack.
     * @param githubApiClient a GithubApiClient object.
     */
    public GithubScheduledService(def slackSession, def githubApiClient) {
        this.githubApiClient = githubApiClient
        this.slackSession = slackSession
        def filterCls = Class.forName(System.getenv("GITHUB_EVENT_FILTER_CLASS") ?:
                "fr.spironet.slackbot.github.DefaultEventFilter")
        this.eventFilter = filterCls.newInstance()
    }

    /**
     * Calculates the time 2 hours ago.
     *
     * @return a LocalDateTime object representing the expected date.
     */
    def twoHoursAgo() {
        LocalDateTime.now().minusHours(2)
    }

    @Override
    protected void runOneIteration() throws Exception {
        def botOwner = slackSession.findUserByEmail(this.botOwnerEmail)
        if (botOwner == null) {
            logger.warn("botOwner not found, skipping iteration")
            return
        }
        try {

            def evts = this.githubApiClient.getReceivedEventForUser(this.githubWatchedUser)
            evts.findResults {
                // skip if the event is filtered
                if (eventFilter.doFilter(it)) {
                    return null
                }
                // skip if the event is considered expired ( < 2 hours)
                def calendar = Instant.parse(it.created_at).toCalendar()
                def eventLocalDt = LocalDateTime.ofInstant(calendar.toInstant(), zid)
                def expired = (eventLocalDt < twoHoursAgo())
                if (expired) {
                    return null
                }
                // skip if event has already been sent to the user
                if (this.notificationMap.getIfPresent(it.id)) {
                    return
                }
                it
            }.each  {
                this.notifyEvent(it, botOwner)
                this.notificationMap.put(it.id, it.id)
            }
        } catch (Exception e) {
            logger.error("error occured while getting the notifications from Github", e)
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 2, TimeUnit.MINUTES)
    }

    def notifyEvent(def event, def botOwner) {
        def phrase = ""
        if (event.type == "PushEvent") {
            def reference = event.payload.ref - "refs/heads/"
            def historyUrl = "https://github.com/${event.repo.name}/commits/${reference}"
            phrase = ":arrow_up: ${event.actor.login} pushed ${event.payload.size} commits onto <${historyUrl}|${event.repo.name}> in ${event.payload.ref}\n"
        } else if (event.type == "PullRequestEvent") {
            // url of the PR will be in event.payload.pull_request.url
            phrase = ":git-pull-request: ${event.actor.login} ${event.payload.action} <https://github.com/${event.repo.name}/pull/${event.payload.number}|PR #${event.payload.number}> onto <https://github.com/${event.repo.name}|${event.repo.name}>"
        } else if (event.type == "CreateEvent") {
            if (event.payload.ref_type == "repository")
                phrase = ":factory: ${event.actor.login} created ${event.payload.ref_type} <https://github.com/${event.repo.name}|${event.repo.name}>"
            else
                phrase = ":git-compare: ${event.actor.login} created ${event.payload.ref_type} ${event.payload.ref} onto <https://github.com/${event.repo.name}|${event.repo.name}>"
        } else if (event.type == "DeleteEvent") {
            if (event.payload.ref_type == "repository")
                phrase = ":x: ${event.actor.login} deleted ${event.payload.ref_type} <https://github.com/${event.repo.name}|${event.repo.name}>"
            else
                phrase = ":x: ${event.actor.login} deleted ${event.payload.ref_type} ${event.payload.ref} onto <https://github.com/${event.repo.name}|${event.repo.name}>"
        } else if (event.type == "IssuesEvent") {
            // url of the issue into event.payload.issue.url
            // repository url into event.payload.issue.repository_url
            phrase = ":spiral_note_pad: ${event.actor.login} ${event.payload.action} issue <https://github.com/${event.repo.name}/issues/${event.payload.issue.number}|#${event.payload.issue.number}> onto <https://github.com/${event.repo.name}|${event.repo.name}>:\n"
            phrase += "```${event.payload.issue.title}```"
        } else if (event.type == "IssueCommentEvent") {
            def payloadBody = event.payload.comment.body.replace("```", "").take(250) + (event.payload.comment.body.size() > 250 ? "..." : "")
            phrase = ":spiral_note_pad: ${event.actor.login} ${event.payload.action} a comment on issue <https://github.com/${event.repo.name}/issues/${event.payload.issue.number}|${event.repo.name}#${event.payload.issue.number}>:\n"
            phrase += "```${payloadBody}```\n"
        } else if (event.type == "PullRequestReviewCommentEvent") {
            def payloadBody = event.payload.comment.body.replace("```", "").take(250) + (event.payload.comment.body.size() > 250 ? "..." : "")
            phrase = ":git-pull-request: ${event.actor.login} ${event.payload.action} a comment on PR <https://github.com/${event.repo.name}/pull/${event.payload.pull_request.number}|${event.repo.name}#${event.payload.pull_request.number}>:\n"
            phrase += "```${payloadBody}```\n"
        } else if (event.type == "GollumEvent") {
            def actorLogin = event.actor.login
            def repoName = event.repo.name
            event.payload.pages.each { p ->
                phrase += ":page_facing_up: ${actorLogin} ${p.action} Wiki page *<${p.html_url}|${p.page_name}>* on *${repoName}*\n"
            }
        } else if (event.type == "WatchEvent") {
            phrase = ":eyes: ${event.actor.login} ${event.payload.action} watching *<https://github.com/${event.repo.name}|${event.repo.name}>*\n"
        } else if (event.type == "ReleaseEvent") {
            phrase = ":label: ${event.actor.login} ${event.payload.action} *<${event.payload.release.html_url}|${event.payload.release.name}>*\n"
        } else {
            phrase = ":interrobang: I don't know how to handle github events of type '${event.type}' yet, see <https://docs.github.com/en/developers/webhooks-and-events/events/github-event-types|github documentation>"
        }
        def msg = SlackPreparedMessage.builder().message(phrase).build()

        this.slackSession.sendMessageToUser(botOwner, msg)
    }

}
