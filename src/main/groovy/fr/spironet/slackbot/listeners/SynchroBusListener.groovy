package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.synchrobus.SynchroBusSchedule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SynchroBusListener implements SlackMessagePostedListener {
    def synchro = new SynchroBusSchedule()

    def usage = """
    Usage: `!synchrobus to <destination>` where destination can be:
    • `technolac`
    • `cby`
    e.g.: `!synchrobus to technolac` will provide the next departures from Biollay bus stop to technolac.
    """

    private final static Logger logger = LoggerFactory.getLogger(SynchroBusListener.class)

    void onEvent(SlackMessagePosted event, SlackSession session) {
        SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
        String messageContent = event.getMessageContent()
        SlackUser messageSender = event.getSender()

        if (messageSender.getUserName() == "georchestracicd") {
            return
        }

        if (! messageContent.contains("!synchrobus")) {
            return
        }

        if (messageContent.contains("!synchrobus to cby")) {
            def message = processCommand("from 'INES sud' bus stop to Chambéry",
                    synchro.parseNextDeparturesToChambery())
            session.sendMessage(channelOnWhichMessageWasPosted, message)
        } else if (messageContent.contains("!synchrobus to technolac")) {
            def message = processCommand("from 'Biollay' bus stop to Technolac",
                    synchro.parseNextDeparturesToTechnolac())
            session.sendMessage(channelOnWhichMessageWasPosted, message)
        } else {
            session.sendMessage(channelOnWhichMessageWasPosted, SlackPreparedMessage.builder().message(this.usage).build())
        }
    }

    private def processCommand(def fromTo, def arrayNextdep) {
        def msg = ""
        if (arrayNextdep.isEmpty()) {
            msg = "*:bus: No departure ${fromTo} planned*\n"
        }
        else {
            msg = "*:bus: Next departures ${fromTo}:*\n"
            arrayNextdep.each {
                msg += "• in ${it}\n"
            }
        }
        return SlackPreparedMessage.builder().message(msg).build()
    }
}
