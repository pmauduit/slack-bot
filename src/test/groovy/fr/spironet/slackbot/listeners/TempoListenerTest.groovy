package fr.spironet.slackbot.listeners

import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class TempoListenerTest {

    private def toTest

    @Before
    void setUp() {
        this.toTest = new TempoListener(null, null, null)
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

}
