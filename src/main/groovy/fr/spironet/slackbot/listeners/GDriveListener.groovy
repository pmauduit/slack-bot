package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.google.C2CGeospatialPlanning
import fr.spironet.slackbot.google.SpreadsheetsApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GDriveListener implements SlackMessagePostedListener {
    private final static Logger logger = LoggerFactory.getLogger(GDriveListener.class)

    private C2CGeospatialPlanning planningApi
    private SpreadsheetsApi spreadsheetsApi
    private String spreadsheetId

    private def usage = """
    Usage: `!gdrive planning <trigramm>`
    • planning: prints out <trigramm>'s planning for the current sprint. e.g.
    `!gdrive planning PMT` returns Pierre Mauduit's planning.
    """

    /**
     * Default constructor. Requires several environment variables to be defined,
     * to be able to correctly instanciate itself.
     */
    GDriveListener() {
        this.spreadsheetsApi = new SpreadsheetsApi()
        this.spreadsheetId = System.env['C2C_GEOSPATIAL_PLANNING_SPREADSHEET_ID']
        if (this.spreadsheetsApi == null) {
            throw new RuntimeException("C2C_GEOSPATIAL_PLANNING_SPREADSHEET_ID env variable not set.")
        }
        this.planningApi = new C2CGeospatialPlanning(this.spreadsheetsApi, this.spreadsheetId)
    }

    /**
     * Constructor
     * @param spreadSheetsApi a spreadSheetsApi object
     * @param spreadsheetId an spreadsheet identifier on gdrive, which points to the C2C geospatial planning.
     */
    GDriveListener(def spreadSheetsApi, def spreadsheetId) {
        this.spreadsheetId = spreadsheetId
        this.spreadsheetsApi = spreadsheetsApi
        this.planningApi = new C2CGeospatialPlanning(this.spreadsheetsApi, this.spreadsheetId)
    }

    SlackPreparedMessage usage() {
        return SlackPreparedMessage.builder().message(this.usage).build()
    }

    def processCommand(def message) {
        try {
            def match = message =~ /\!gdrive (\S+)+/
            def command = match[0][1]
            if (command == "usage" || command == "help") {
                return usage()
            } else if (command == "planning") {
                def trigramm = message =~ /\!gdrive planning (\S+)/
                trigramm = trigramm[0][1]
                def alloc = this.planningApi.getCurrentPlanningForUser(trigramm)

                def msg = ":spiral_calendar_pad: Here is _${trigramm}_'s current GS planning (*${alloc.sheet}*):\n"
                alloc.allocations.each {k,v ->
                    msg = msg.concat("• *${k}*: _${v}_ days\n")
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

        if (messageContent.contains("!gdrive")) {
            def message = processCommand(messageContent)
            slackSession.sendMessage(channelOnWhichMessageWasPosted, message)
        }
    }
}