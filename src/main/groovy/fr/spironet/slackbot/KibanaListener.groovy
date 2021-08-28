package fr.spironet.slackbot

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.webbrowser.WebBrowser
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class KibanaListener implements SlackMessagePostedListener  {
    private final static Logger logger = LoggerFactory.getLogger(KibanaListener.class)

    private def usage = "Usage: !kibana weather: returns a screenshot of the kibana dashboard.\n"
    private def error = "I am not capable of browsing the web currently.\n${this.usage}"


    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
        SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
        String messageContent = event.getMessageContent()
        SlackUser messageSender = event.getSender()

        if (messageSender.getUserName() == "georchestracicd") {
            return
        }

        if (messageContent.contains("!kibana")) {
            try {
                def match = messageContent =~ /\!kibana (\S+)/
                def issueKey = match[0][1]
                // Getting the "météo des plateformes"
                if (issueKey == "weather") {
                    def wb = new WebBrowser()
                    def screenshot = wb.visitKibanaDashboard()
                    if (screenshot == null) {
                        throw new NullPointerException()
                    }
                    session.sendFile(
                            channelOnWhichMessageWasPosted,
                            screenshot,
                            "Weather report on our geOrchestra platforms on the past 7 days")
                    return
                } else {
                    session.sendMessage(channelOnWhichMessageWasPosted,
                            SlackPreparedMessage.builder().message(this.usage).build()
                    )
                }
            } catch (Exception e) {
                logger.error("Error occurred", e)
                session.sendMessage(channelOnWhichMessageWasPosted,
                        SlackPreparedMessage.builder().message(this.error).build()
                )
            }
        }
    }

}
