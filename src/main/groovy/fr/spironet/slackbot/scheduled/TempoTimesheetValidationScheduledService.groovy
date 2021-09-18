package fr.spironet.slackbot.scheduled

import com.google.common.util.concurrent.AbstractScheduledService
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import fr.spironet.slackbot.tempo.TempoApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class TempoTimesheetValidationScheduledService  extends AbstractScheduledService {
    def slackSession
    def tempoApi
    def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")
    def lastWeekReported

    private final static Logger logger = LoggerFactory.getLogger(TerraformScheduledService.class)

    def TempoTimesheetValidationScheduledService(def session) {
        this.slackSession = session
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

        this.lastWeekReported = lastWeekNumber()
    }

    def TempoTimesheetValidationScheduledService(def session, def user, def password, def jiraBaseUrl) {
        this.slackSession = session
        this.tempoApi  = new TempoApi(user, password, jiraBaseUrl)
        this.lastWeekReported = lastWeekNumber()
    }

    /**
     * Gets the last week's number. What the java.util.Calendar API returns
     * depends on the locale. But whether the week starts on sunday or monday
     * does not change that much, we just need an Integer as a basis to follow
     * the weeks evolutions.
     *
     * @return a integer representing previous' week number
     */
    def lastWeekNumber() {
        def cal = Calendar.instance
        cal.add(Calendar.DAY_OF_WEEK, -7)
        cal.get(Calendar.WEEK_OF_YEAR)
    }

    /**
     * Gets the date from one week ago.
     *
     * @return a String representing 7 days ago's date.
     */
    def lastWeek() {
        def cal = Calendar.instance
        cal.add(Calendar.DAY_OF_WEEK, -7)
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        sdf.format(cal.getTime())
    }

    /**
     * the runOneIteration corpus, in an other accessible method
     * to allow testing.
     *
     */
    def doRunOneIteration() {
        def lastWeekNumber = lastWeekNumber()
        if (lastWeekNumber == this.lastWeekReported) {
            logger.info("Report for the previous week has already been reported, no need to check the status.")
            return
        }
        if (tempoApi.isTimesheetApproved(lastWeek())) {
            logger.info("Timesheet approval detected, generating a report")

            def botOwner = slackSession.findUserByEmail(this.botOwnerEmail)
            def dateBegin = tempoApi.lastSunday()
            def dateEnd = tempoApi.nextFriday(dateBegin)

            def report = tempoApi.generateReport(dateBegin, dateEnd)

            slackSession.sendMessageToUser(botOwner, SlackPreparedMessage.builder().message(
                    "It looks like your timesheet from the previous week has " +
                            "been approved, I am going to generate a report from it, please wait ...").build())

            slackSession.sendFileToUser(botOwner, report, "Timesheet report " +
                    "from ${dateBegin} to ${dateEnd}")

            /* updates the variable, and wait for next week */
            this.lastWeekReported = lastWeekNumber
        }
    }

    @Override
    protected void runOneIteration() throws Exception {
        this.doRunOneIteration()
    }

    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 5, TimeUnit.MINUTES)
    }
}
