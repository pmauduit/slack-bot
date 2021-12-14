package fr.spironet.slackbot.scheduled


import com.google.common.util.concurrent.AbstractScheduledService
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import fr.spironet.slackbot.jira.JiraRss
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class JiraRssScheduledService extends AbstractScheduledService {

    private final static Logger logger = LoggerFactory.getLogger(JiraRssScheduledService.class)

    def jiraRss = new JiraRss()
    def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")
    def lastScrapeDate = LocalDateTime.now()
    def slackSession
    def onlyTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    public JiraRssScheduledService(def slackSession) {
        this.slackSession = slackSession
    }

    private SlackPreparedMessage prepareNotification(def issue, def issueChanges) {
        def message =":spiral_note_pad: Issue *<${jiraRss.jiraRssUrl}/browse/${issue}|${issue}>*:\n"

        issueChanges.each {updateDate, infos ->
            message += "• ${infos.title}"
            if (! infos.content.isEmpty()) {
                message += "\n```\n${infos.content}\n```"
            }
            message += "\nat ${updateDate.format(this.onlyTimeFormatter)}\n"
        }

        return SlackPreparedMessage.builder().message(message).build()
    }

    @Override
    protected void runOneIteration() throws Exception {
        try {
            def botOwner = slackSession.findUserByEmail(botOwnerEmail)
            if (botOwner == null) {
                logger.error("bot owner not found: ${botOwnerEmail}")
                return
            }
            def lmIssues = jiraRss.rssLatestModifiedIssues()
            // we do have the update date as value in the returned map
            // but the expired events will be filtered afterwards anyway.
            lmIssues.each { k,_ ->
                def issueId = jiraRss.extractIssueIdFromUrl(k)
                def changes = jiraRss.rssGetModificationDetails(issueId, this.lastScrapeDate).findAll {
                    it.value.author != this.botOwnerEmail
                }
                if (! changes.isEmpty()) {
                    def msg = this.prepareNotification(issueId, changes)
                    slackSession.sendMessageToUser(botOwner, msg)
                }
            }
        } catch (Exception e) {
            logger.error("Error occured while running", e)
        } finally {
            this.lastScrapeDate = LocalDateTime.now()
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 2, TimeUnit.MINUTES)
    }
}

