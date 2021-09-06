package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.tempo.TempoApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TempoListener implements SlackMessagePostedListener  {

    private final static Logger logger = LoggerFactory.getLogger(TempoListener.class)

    def usage = """
    Usage: !tempo <date> <JIRA issue key> <time spent> "message"
    * date should have the 'YYYY-mm-dd' format
    * JIRA issue key, e.g. "GSREN-22"
    * time spent could be in the following form: "1h30m", or "1h", or "30m", or "2h00"
    """

    def tempoApi

    public TempoListener() {
      // This class expects a JIRA_CLIENT_PROPERTY_FILE env variable to be set
      // see env.dist at the root of the repository.
      String propsFile = System.getenv("JIRA_CLIENT_PROPERTY_FILE")
      Properties properties = new Properties()
      File propertiesFile = new File(propsFile)
      propertiesFile.withInputStream {
          properties.load(it)
      }
        this.tempoApi = new TempoApi(properties."jira.user.id", properties."jira.user.pwd",
                properties."jira.server.url")
    }

    private SlackPreparedMessage createWorklog(def message, def issueKey, def date, def timeSpent)  {
        def ret
        try {
            tempoApi.createWorklog(message, issueKey, date, timeSpent)
            ret = ":calendar: worklog entry created on issue ${issueKey}"
        } catch (Exception e) {
            ret = "*An error occured while creating the worklog*"
      }
      return SlackPreparedMessage.builder().message(ret).build()
    }

    def parseCommand(def str) throws Exception {
        def messagePat = /^\!tempo (.*) (.*) (.*) "(.*)"$/
        def match = str =~ messagePat
        if (match.size() > 0) {
            return [ 'date': match[0][1], 'issueKey': match[0][2], 'timeMinutes': match[0][3], 'message': match[0][4]]
        }
        throw new Exception ("unable to parse tempo command")
    }

    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
      SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
      String messageContent = event.getMessageContent()
      SlackUser messageSender = event.getSender()

      if (messageSender.getUserName() == "georchestracicd") {
        return
      }

      if (messageContent.contains("!tempo")) {
        try {
          def params = parseCommand(messageContent)
          def mesg   = createWorklog(params.message, params.issueKey, params.date, params.timeMinutes)
          session.sendMessage(channelOnWhichMessageWasPosted, mesg)
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            SlackPreparedMessage.builder().message(usage).build()
          )
        }
      }
    }

}
