package fr.spironet.slackbot.listeners

import fr.spironet.slackbot.tempo.MockTempoResponse
import org.junit.Before
import org.junit.Test

import java.text.SimpleDateFormat

import static org.junit.Assert.assertTrue
import static org.junit.Assume.assumeTrue

class TempoListenerTest {

    private TempoListener toTest

    @Before
    void setUp() {
        this.toTest = new TempoListener(null, null, null)
        this.toTest.tempoApi.http = new MockTempoResponse()
    }

    @Test
    void testParseCommandLegitCommands() {
        def ret = toTest.parseCommand("!tempo report 2021-09-12 2021-09-18")

        assertTrue(ret.size() == 3 &&
            ret["command"] == "report")

        ret = toTest.parseCommand("!tempo create 2021-09-12 AGFR-1 1h \"ts w/33\"")
        assertTrue(ret.size() == 5 &&
                ret["command"] == "create")
    }

    @Test
    void testParseCommandCreateTodayYesterday() {

        def ret = toTest.parseCommand("!tempo create today AGFR-1 1h \"ts w/33\"")

        assertTrue(ret.size() == 5 &&
                ret["command"] == "create" &&
                ret["date"] == new SimpleDateFormat("yyyy-MM-dd").format(new Date())
        )

        def yesterday = Calendar.instance
        yesterday.add(Calendar.DATE, -1)

        ret = toTest.parseCommand("!tempo create yesterday AGFR-1 1h \"ts w/33\"")

        assertTrue(ret.size() == 5 &&
                ret["command"] == "create" &&
                ret["date"] == new SimpleDateFormat("yyyy-MM-dd").format(yesterday.time)
        )

    }

    @Test
    void testParseCommandHisto() {
        def ret = toTest.parseCommand("!tempo history 2020-10-02")

        assertTrue(ret["command"] == 'history' && ret["date"] == "2020-10-02")
    }

    @Test(expected = Exception)
    void testParseCommandHistoWrongDate() {
        toTest.parseCommand("!tempo history blah")
    }

    @Test(expected = Exception)
    void testParseCommandWrongReportBadDatest1Commands() {
        toTest.parseCommand("!tempo report dudu popo")
    }

    @Test(expected = Exception)
    void testParseCommandWrongReportBadDatest2Commands() {
        toTest.parseCommand("!tempo report dudu 2021-09-20")
    }

    @Test(expected = Exception)
    void testParseCommandWrongReportBadDatest3Commands() {
        toTest.parseCommand("!tempo report 2021-09-20 dudu")
    }

    @Test(expected = Exception)
    void testParseCommandWrongReportCommandsExtraData() {
        toTest.parseCommand("!tempo report 2021-09-20 2021-09-27 aaaaa doekpdok")
    }

    @Test(expected = Exception)
    void testParseCommandWrongCreateCommandsEWrongDate() {
        toTest.parseCommand("!tempo create unparseableDate AGFR-1 1h \"ts w/33\"")
    }

    @Test(expected = Exception)
    void testParseCommandWrongNumberOfArgs() {
        toTest.parseCommand("!tempo aaa bbb")
    }

    @Test(expected = RuntimeException)
    void testDefaultConstructor() {
        assumeTrue(System.env["JIRA_CLIENT_PROPERTY_FILE"] == null)
        toTest = new TempoListener()
    }

    @Test
    void testCreateWorklog() {
        def ret = toTest.createWorklog("TS w/33", "PRJADM-1", "2021-02-15", "1h")

        assertTrue(ret.message == ":calendar: worklog entry created on issue PRJADM-1")
    }

    @Test
    void testCreateWorklogFailure() {
        def ret = toTest.createWorklog("TS w/33", "NONEXISTING-1", "2021-02-15", "1h")

        assertTrue(ret.message == "*An error occured while creating the worklog*")
    }

    @Test
    void testWorklogHistory() {
        def ret = toTest.worklogHistoryMesage("2021-02-15")

        assertTrue(ret.message.startsWith(":calendar: Here are the worklog entries in Jira/Tempo for *2021-02-15*:\n") &&
        ret.message.contains("ADM-1") && ret.message.contains("meeting weekly PROJ4"))
    }

    @Test
    void testWorklogHistoryNoEntry() {
        def ret = toTest.worklogHistoryMesage("1994-02-15")

        assertTrue(ret.message == ":calendar: No worklog entries found for *1994-02-15*.")
    }
}
