package fr.spironet.slackbot.scheduled

import fr.spironet.slackbot.tempo.TempoApi
import org.junit.Before
import org.junit.Test

import java.text.SimpleDateFormat

import static org.junit.Assert.assertTrue
import static org.junit.Assume.assumeTrue

class TempoTimesheetValidationScheduledServiceTest {

    TempoTimesheetValidationScheduledService toTest

    @Before
    void setUp() {
        this.toTest = new TempoTimesheetValidationScheduledService(null, null, null, null)
    }

    @Test(expected = RuntimeException)
    void testConstructorWithoutEnvVariable() {
        assumeTrue(System.env["JIRA_CLIENT_PROPERTY_FILE"] == null)
        new TempoTimesheetValidationScheduledService(null)
    }

    @Test
    void testLastWeekNumber() {
        def lwn = this.toTest.lastWeekNumber()

        assertTrue(lwn instanceof Integer &&
        lwn >= 1 && lwn <= 52)
    }

    @Test
    void testLastWeek() {
        def lw = this.toTest.lastWeek()

        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        def reparsed = sdf.parse(lw)
        def dateDiff = use(groovy.time.TimeCategory) { Calendar.instance.getTime() - reparsed }.days

        assertTrue(dateDiff == 7)
    }

    @Test
    void testDoRunOneIterationSameWeekAsInstanciation() {
        this.toTest.doRunOneIteration()
        // nothing thrown, great
    }

    @Test
    void testDoRunOneIterationNotTheSameWeekTimesheetNotApprovedYet() {
        this.toTest.lastWeekReported -= 1

        // first iteration: timesheet not approved yet
        this.toTest.tempoApi = new TempoApi(null, null, null) {
            def isTimesheetApproved(def date) {
                return false
            }
        }
        this.toTest.doRunOneIteration()
        // still nothing thrown, great
    }

    @Test
    void testDoRunOneIterationNotTheSameWeekTimesheetApproved() {
        def previousReported = this.toTest.lastWeekReported
        this.toTest.lastWeekReported -= 1

        this.toTest.tempoApi = new TempoApi(null, null, null) {
            def reportGenerated = false
            // timesheet has now been approved
            def isTimesheetApproved(def date) {
                return true
            }
            def generateReport(def begDate, def endDate) {
                reportGenerated = true
            }
        }
        this.toTest.slackSession = new Object() {
            def messageToUserSent = false
            def fileToUserSent = false
            def findUserByEmail(def botOwnerEmail) {
                return "aaaaa"
            }
            def sendMessageToUser(def user, def message) {
                this.messageToUserSent = true
            }
            def sendFileToUser(def user, def file, def desc) {
                this.fileToUserSent = true
            }
        }

        this.toTest.doRunOneIteration()

        assertTrue(this.toTest.slackSession.messageToUserSent == true &&
                this.toTest.slackSession.messageToUserSent == true    &&
                this.toTest.tempoApi.reportGenerated == true          &&
                this.toTest.lastWeekReported == previousReported
        )
    }
}
