package fr.spironet.slackbot.listeners


import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import okhttp3.Request
import okhttp3.Response
import okhttp3.Credentials
import okhttp3.RequestBody
import okhttp3.MediaType
import okhttp3.OkHttpClient

import groovy.json.JsonOutput


class TempoListener implements SlackMessagePostedListener  {

    private final static Logger logger = LoggerFactory.getLogger(TempoListener.class)

    def usage = """
    Usage: !tempo <date> <JIRA issue key> <time spent> "message"
    * date should have the 'YYYY-mm-dd' format
    * JIRA issue key, e.g. "GSREN-22"
    * time spent could be in the following form: "1h30m", or "1h", or "30m", or "2h00"
    """

    private def username
    private def password
    private def tempoUrl

    public TempoListener() {
      // This class expects a JIRA_CLIENT_PROPERTY_FILE env variable to be set
      // see env.dist at the root of the repository.
      String propsFile = System.getenv("JIRA_CLIENT_PROPERTY_FILE")
      Properties properties = new Properties()
      File propertiesFile = new File(propsFile)
      propertiesFile.withInputStream {
          properties.load(it)
      }
      this.username = properties."jira.user.id"
      this.password = properties."jira.user.pwd"
      this.tempoUrl = properties."jira.server.url" + "/rest/tempo-timesheets/4/worklogs/"
    }

    def toMinutes(def str) throws Exception {
        def mPat = /^(\d+)m$/
        def match = str =~ mPat
        if (match.size() > 0) {
            return (match[0][1] as Integer)
        }
        def hPat = /^(\d+)h$/
        match = str =~ hPat
        if (match.size() > 0) {
            return (match[0][1] as Integer) * 60
        }

        def hmPat = /^(\d+)h(\d+)m?$/
        match = str =~ hmPat
        if (match.size() > 0) {
            return (match[0][1] as Integer) * 60 + (match[0][2] as Integer)
        }
        throw new Exception("Unable to parse the time spent ${str}")
    }
    /**
      previous method tested with the following:
      println toMinutes("1h30m")
      println toMinutes("3h")
      println toMinutes("50m")
      println toMinutes("1h30")
    */

    private SlackPreparedMessage createWorklog(def message, def issueKey, def date, def timeSpent)  {
      def worklogEntry = [
          attributes: [:],
          billableSeconds: "",
          comment: message,
          endDate: null,
          includeNonWorkingDays: false,
          originId: -1,
          originTaskId: issueKey,
          remainingEstimate: null,
          started: date,
          timeSpentSeconds: timeSpent * 60,
          worker: this.username,
      ]

      def JSON = MediaType.parse("application/json; charset=utf-8")
      def body = JsonOutput.toJson(worklogEntry).toString()
      RequestBody rb = RequestBody.create(JSON, body)

      def credentials = Credentials.basic(this.username, this.password)
      Request http = new Request.Builder().url(this.tempoUrl)
          .header("content-type", "application/json")
          .post(rb)
          .header("Authorization", credentials)
          .build()

      def client = new OkHttpClient()
      Response response = client.newCall(http).execute()
      response.close()
      def ret = ""
      if (response.code == 200) {
        ret = "worklog entry created"
      } else {
        ret = "*An error occured*, code: ${response.code}"
      }
      return SlackPreparedMessage.builder().message(ret).build()
    }

    def parseCommand(def str) throws Exception {
        def messagePat = /^\!tempo (.*) (.*) (.*) "(.*)"$/
        def match = str =~ messagePat
        if (match.size() > 0) {
            return [ 'date': match[0][1], 'issueKey': match[0][2], 'timeMinutes': toMinutes(match[0][3]), 'message': match[0][4]]
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
