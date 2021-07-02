package fr.spironet.slackbot.scheduled

import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.AbstractScheduledService
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import fr.spironet.slackbot.confluence.ConfluenceRss
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ConfluenceRssScheduledService  extends AbstractScheduledService {

    private final static Logger logger = LoggerFactory.getLogger(ConfluenceRssScheduledService.class)

    def slackSession
    def confluenceRss = new ConfluenceRss()
    def confluenceUrl = System.getenv("CONFLUENCE_SERVER_URL")
    def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")
    def lastScrapeDate = LocalDateTime.now()

    def notificationMap = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(4, TimeUnit.HOURS)
            .build()

    ConfluenceRssScheduledService(def slackSession) {
        this.slackSession = slackSession
    }
     def emojiMapping = [
             "Page"      : ":page_with_curl:",
             "Comment"   : ":speech_balloon:",
             "Blog post" : ":mailbox_with_mail:",
             "File"      : ":open_file_folder:"
     ]

    private def findEmoji (String txt) {
        this.emojiMapping.find { k,v ->
            txt.startsWith(k)
        }?.value ?: ":interrobang:"
    }

    @Override
    protected void runOneIteration() throws Exception {
        try {
            def botOwner = slackSession.findUserByEmail(botOwnerEmail)
            if (botOwner == null) {
                logger.error("bot owner not found: ${botOwnerEmail}")
                return
            }
            def confluenceEvents = confluenceRss.rssLatestModifiedItems().findAll {
                 (
                   (! it.id?.isEmpty() &&                                   // event id not empty
                   ! this.notificationMap.getIfPresent(it.id)) &&           // event id not in the notificationMap
                   (it.updatedDate > lastScrapeDate.minusHours(2))   // event not expired
                 )
            }
            // nothing to report
             if (confluenceEvents.isEmpty()) {
                return
            }
            def message = "*Here are the new events coming from <${this.confluenceUrl}|Confluence> (${confluenceEvents.size()}):*\n"
            confluenceEvents.each {
                def emoji = findEmoji(it.summary)
                message += "${emoji} ${it.summary}: <${it.url}|${it.title}>\n"
                this.notificationMap.put(it.id, it.id)
            }
            def slackMessage = new SlackPreparedMessage.Builder().withMessage(message).build()
            slackSession.sendMessageToUser(botOwner, slackMessage)
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
