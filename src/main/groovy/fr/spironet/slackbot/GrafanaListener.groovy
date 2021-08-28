package fr.spironet.slackbot

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import fr.spironet.slackbot.webbrowser.WebBrowser
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GrafanaListener implements com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener  {
    private final static Logger logger = LoggerFactory.getLogger(GrafanaListener.class)

    private def usage = "Usage: !grafana monitoring: returns a screenshot of the current monitoring screen.\n"
    private def error = "I am not capable of browsing the web currently.\n${this.usage}"


    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
        SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
        String messageContent = event.getMessageContent()
        SlackUser messageSender = event.getSender()

        if (messageSender.getUserName() == "georchestracicd") {
            return
        }

        if (messageContent.contains("!grafana")) {
            try {
                def match = messageContent =~ /\!grafana (\S+)/
                def issueKey = match[0][1]
                // Getting the current state of the monitoring screen
                if (issueKey == "monitoring") {
                    def wb = new WebBrowser()
                    def screenshot = wb.visitGrafanaMonitoringDashboard()
                    if (screenshot == null) {
                        throw new NullPointerException()
                    }
                    session.sendFile(channelOnWhichMessageWasPosted, screenshot, "monitoring dashboard")
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
