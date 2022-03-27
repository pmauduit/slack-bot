package fr.spironet.slackbot.scheduled

import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import fr.spironet.slackbot.google.GCalendarApi
import fr.spironet.slackbot.slack.SlackWorkaround
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat

class GoogleCalendarJob implements Job
{
    static final def SLACK_SESSION_ID = "SLACK_SESSION_IDENTIFIER"
    def gcalApi = new GCalendarApi()
    def hourMinutes = new SimpleDateFormat("HH:mm")
    def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")

    private final static Logger logger = LoggerFactory.getLogger(GoogleCalendarJob.class)

    @Override
    void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        def ctx = jobExecutionContext.getScheduler().getContext()
        def slackSession = (SlackSession) ctx.get(GoogleCalendarJob.SLACK_SESSION_ID)
        if (slackSession == null) {
            logger.error("Unable to get a hand on the slack session, stopping execution")
            return
        }

        def msg = ":spiral_calendar_pad: Good morning ${botOwner.profile.realName}, here is a summary of your day, relying on your Google Calendar:\n"
        this.gcalApi.getTodaysEvents().each {
            def timeStart = it.start.dateTime?.getValue()
            def timeEnd = it.end.dateTime?.getValue()
            if (timeStart != null && timeEnd != null) {
                msg += "• From *${this.hourMinutes.format(timeStart)}* to *${this.hourMinutes.format(timeEnd)}*: _${it.summary}_\n"
            } else {
                msg += "• *The whole day*: _${it.summary}_\n"
            }
        }
        def botOwner = SlackWorkaround.findPrivateMessageChannel(slackSession, this.botOwnerEmail)
        if (botOwner == null) {
            logger.error("Unable to find the channel in which to send the notification, giving up")
            return
        }
        slackSession.sendMessage(botOwner, SlackPreparedMessage.builder().message(msg).build())

    }

}

