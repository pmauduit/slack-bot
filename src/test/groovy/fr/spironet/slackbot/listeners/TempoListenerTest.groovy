package fr.spironet.slackbot.listeners

import fr.spironet.slackbot.tempo.MockTempoResponse
import org.junit.Before
import org.junit.Test

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

}
