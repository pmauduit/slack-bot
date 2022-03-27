package fr.spironet.slackbot.scheduled

import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.AbstractScheduledService
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import fr.spironet.slackbot.jira.IssueDetailsResolver
import fr.spironet.slackbot.slack.SlackWorkaround
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

class JiraScheduledService extends AbstractScheduledService
{
    private final static Logger logger = LoggerFactory.getLogger(JiraScheduledService.class)

    SlackSession slackSession
    IssueDetailsResolver issueResolver
    def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")

    def issueCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(7, TimeUnit.DAYS)
            .build()

    public JiraScheduledService(def session) {
        this.slackSession = session
        this.issueResolver = new IssueDetailsResolver()
    }

    public JiraScheduledService(def session, def issueResolver) {
        this.slackSession = session
        this.issueResolver = issueResolver
    }

    SlackPreparedMessage getIssuesToNotify() {
        def jql = "project = GEO and created <= now() and created  >= startOfWeek() and component = georchestra"
        def issues = issueResolver.searchJiraIssues(jql)
        def issuesToNotify = []
        issues.each {
            if (issueCache.getIfPresent(it.key) == null) {
                issuesToNotify.add(it)
                issueCache.put(it.key, it)
            }
        }
        if (issuesToNotify.size() == 0) {
            return null
        }
        def message = ":warning: New issues coming from JIRA *(${issuesToNotify.size()})*:\n"
        issuesToNotify.each {
            def currentOrg = this.issueResolver.computeOrganization(it)
            message += "• *<${this.issueResolver.jiraUrl}/browse/${it.key}|${it.key}>*"
            if (currentOrg != null) {
                message += " - :office: *${currentOrg}*"
            }
            message += " - ${it.fields.summary}\n"
        }
        return SlackPreparedMessage.builder().message(message).build()
    }

    @Override
    protected void runOneIteration() throws Exception {
        try {
            def msg = getIssuesToNotify()
            if (msg != null) {
                def botOwner = SlackWorkaround.findPrivateMessageChannel(slackSession, this.botOwnerEmail)
                if (botOwner == null) {
                    logger.error("Unable to find the channel in which to send the notification, giving up")
                    return
                }
                slackSession.sendMessage(botOwner, msg)
            }
        } catch (Exception e) {
            logger.error("Error occured while running", e)
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 2, TimeUnit.MINUTES)
    }
}
