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

import java.text.SimpleDateFormat

class TempoListener implements SlackMessagePostedListener {

    private final static Logger logger = LoggerFactory.getLogger(TempoListener.class)

    def usage = """
    Usage: !tempo create <date> <JIRA issue key> <time spent> "<message>"
    Creates a worklog
    • date should have the 'yyyy-MM-dd' format
    • JIRA issue key, e.g. "GSREN-22"
    • time spent could be in the following form: "1h30m", or "1h", or "30m", or "2h00"

    Usage: !tempo report <dateBegin> <dateEnd>
    Creates a graphical report from datebegin to dateend, sent to the user as an image over Slack.
    • dateBegin and dateEnd should have the 'yyyy-MM-dd' format

    Usage: !tempo history <date>
    Returns the worklog for the date given as argument.
    • date should have the 'yyyy-MM-dd' format
    """

    def messageWlPat    = /^\!tempo (.*) (.*) (.*) (.*) "(.*)"$/
    def messageRepPat   = /^\!tempo (.*) (.*) (.*)$/
    def messageHistoPat = /^\!tempo history (.*)$/

    def tempoDateFormat = new SimpleDateFormat("yyyy-MM-dd")

    def tempoApi

    TempoListener() {
        // This class expects a JIRA_CLIENT_PROPERTY_FILE env variable to be set
        // see env.dist at the root of the repository.
        if (System.getenv("JIRA_CLIENT_PROPERTY_FILE") == null) {
            throw new RuntimeException("JIRA_CLIENT_PROPERTY_FILE env variable is not set")
        }
        String propsFile = System.getenv("JIRA_CLIENT_PROPERTY_FILE")
        Properties properties = new Properties()
        File propertiesFile = new File(propsFile)
        propertiesFile.withInputStream {
            properties.load(it)
        }
        this.tempoApi = new TempoApi(properties."jira.user.id", properties."jira.user.pwd",
                properties."jira.server.url")
    }

    /**
     * Constructor used for the unit tests, not meant to be used.
     * @param jiraUser the JIRA login/username
     * @param jiraPassword the JIRA password
     * @param jiraUrl the JIRA URL.
     *
     */
    TempoListener(def jiraUser, def jiraPassword, def jiraUrl) {
        this.tempoApi = new TempoApi(jiraUser, jiraPassword, jiraUrl)
    }

    /**
     * Tests if the date given as argument is invalid or not, given
     * what the tempo API expects as a date format.
     *
     * @param date the date to test.
     * @return true if unparseable, false if the date is correct.
     */
    def isUnparseableDate(def date) {
        try {
            tempoDateFormat.parse(date)
        } catch (Exception _) {
            return true
        }
        return false
    }

    /**
     * Given a message, an issue key, a date and a string representing the time spent, creates
     * a worklog using the JIRA tempo API.
     *
     * @param message the message describing the worklog
     * @param issueKey the JIRA issue key to create a worklog on
     * @param date the date as a string describing the day on which the worklog has to be created on.
     * @param timeSpent a string describing the time spent on the issue.
     *
     * @return a SlackPreparedMessage object describing if the command succeeded of failed.
     */
    private SlackPreparedMessage createWorklog(def message, def issueKey, def date, def timeSpent) {
        def ret
        try {
            tempoApi.createWorklog(message, issueKey, date, timeSpent)
            ret = ":calendar: worklog entry created on issue ${issueKey}"
        } catch (Exception e) {
            logger.error("Error creating the worklog entry", e)
            ret = "*An error occured while creating the worklog*"
        }
        return SlackPreparedMessage.builder().message(ret).build()
    }

    /**
     * Searches for the worklog entries of the given date in argument.
     *
     * @param date the date as a string.
     * @return a SlackPreparedMessage object describing the worklog entries for the expected date.
     */
    private SlackPreparedMessage worklogHistoryMesage(def date) {
        def ret = ""
        def wl = tempoApi.searchWorklog(date, date)
        if (wl.size() > 0) {
            ret += ":calendar: Here are the worklog entries in Jira/Tempo for *${date}*:\n"
            wl.each {
                def comment = it.comment
                comment = comment.replace("\n", " ")
                if (comment.size() > 60) {
                    comment = comment.substring(0,60) << "..."
                }
                ret += "• *${it.issue.projectKey}* - *${it.timeSpent}* on ${it.issue.key} (_\"${comment}\"_)\n"
            }
        } else {
            ret += ":calendar: No worklog entries found for *${date}*."
        }
        return SlackPreparedMessage.builder().message(ret).build()
    }

    /**
     * Given the string message caught by the listener, tries to parse the "!tempo" command arguments.
     *
     * @param str the string message received by the listener.
     *
     * @return a map with the parsed arguments.
     * @throws Exception if the code is unable to parse, an Exception is thrown.
     */
    def parseCommand(def str) throws Exception {
        def match = str =~ messageWlPat
        if (match.size() > 0) {
            def date = match[0][2]
            if (date == "today") {
                date = this.tempoApi.dateFormat.format(new Date())
            } else if (date == "yesterday") {
                def cal = Calendar.instance
                cal.add(Calendar.DATE, -1)
                date = this.tempoApi.dateFormat.format(cal.time)
            }
            if ((match[0][1] != "create") || isUnparseableDate(date)) {
                throw new Exception("unable to parse tempo create command")
            }
            return ['command'    : match[0][1],
                    'date'       : date,
                    'issueKey'   : match[0][3],
                    'timeMinutes': match[0][4],
                    'message'    : match[0][5]
            ]
        }
        match = str =~ messageRepPat
        if (match.size() > 0) {
            if ((match[0][1] != "report") || isUnparseableDate(match[0][2])
                    || isUnparseableDate(match[0][3])) {
                throw new Exception("unable to parse tempo report command")
            }
            return ['command'  : match[0][1],
                    'dateBegin': match[0][2],
                    'dateEnd'  : match[0][3]
            ]
        }
        match  = str =~ messageHistoPat
        if (match.size() > 0) {
            if (isUnparseableDate(match[0][1])) {
                throw new Exception("unable to parse tempo history command")
            }
            return ['command': 'history',
                    'date'   : match[0][1]
            ]
        }
        throw new Exception("unable to parse tempo command")
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
                if (params.command == "create") {
                    def mesg = createWorklog(params.message, params.issueKey, params.date, params.timeMinutes)
                    session.sendMessage(channelOnWhichMessageWasPosted, mesg)
                } else if (params.command == "report") {
                    def report = tempoApi.generateReport(params.dateBegin, params.dateEnd)
                    session.sendFile(channelOnWhichMessageWasPosted, report, "Timesheet report " +
                            "from ${params.dateBegin} to ${params.dateEnd}")
                } else if (params.command == "history") {
                    def mesg = worklogHistoryMessage(params.date)
                    session.sendMessage(channelOnWhichMessageWasPosted, mesg)
                }
            } catch (Exception e) {
                logger.error("Error occured", e)
                session.sendMessage(channelOnWhichMessageWasPosted,
                        SlackPreparedMessage.builder().message(usage).build()
                )
            }
        }
    }
}
