package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.google.GCalendarApi
import fr.spironet.slackbot.jira.IssueDetailsResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat

class GCalListener  implements SlackMessagePostedListener {
    private final static Logger logger = LoggerFactory.getLogger(GCalListener.class)

    private GCalendarApi gcalApi
    def hourMinutes = new SimpleDateFormat("HH:mm")

    GCalListener() {
        this.gcalApi = new GCalendarApi()
    }

    GCalListener(def gcalApi) {
        this.gcalApi = gcalApi
    }

    private def usage = """
    Usage: `!gcal today|usage`
    • today: prints a summary of the day
    • usage: prints this help message
    """

    SlackPreparedMessage usage() {
        return SlackPreparedMessage.builder().message(this.usage).build()
    }

    def processCommand(def message) {
        try {
            def match = message =~ /\!gcal (\S+)+/
            def command = match[0][1]
            if (command == "usage") {
                return usage()
            } else if(command == "today") {
                def msg = ":spiral_calendar_pad: Here is a summary of your day, relying on your Google Calendar:\n"
                this.gcalApi.getTodaysEvents().each {
                    def timeStart = it.start.dateTime?.getValue()
                    def timeEnd = it.end.dateTime?.getValue()
                    if (timeStart != null && timeEnd != null) {
                        msg += "• From *${this.hourMinutes.format(timeStart)}* to *${this.hourMinutes.format(timeEnd)}*: _${it.summary}_\n"
                    } else {
                        msg += "• *The whole day*: _${it.summary}_\n"
                    }
                }
                return SlackPreparedMessage.builder().message(msg).build()
            } else {
                return usage()
            }
        } catch (def e) {
            logger.error("Error occurred processing the command, returning usage()", e)
            return usage()
        }
    }
    @Override
    void onEvent(SlackMessagePosted event, SlackSession slackSession) {
        SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
        String messageContent = event.getMessageContent()
        SlackUser messageSender = event.getSender()

        if (messageSender.getUserName() == "georchestracicd") {
            return
        }

        if (messageContent.contains("!gcal")) {
            def message = processCommand(messageContent)
            slackSession.sendMessage(channelOnWhichMessageWasPosted, message)
        }
    }
}
