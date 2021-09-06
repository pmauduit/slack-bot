package fr.spironet.slackbot.tempo

import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertTrue

class TempoApiTest {
    def toTest = new TempoApi(null, null, null)

    @Before
    void setUp() {
        toTest.http = new Object() {
            def post(def args) {
                if (args.path == TempoApi.searchWorklogUrl) {
                    def txt = new File(
                            this.getClass().getResource("tempoSearch.json").toURI()
                    ).text
                    return [ data: new JsonSlurper().parseText(txt) ]
                } else if (args.path == TempoApi.createWorklogUrl && args.body.originTaskId == "NONEXISTING-1") {
                    throw new groovyx.net.http.HttpResponseException("BAD RREQUEST")
                } else if (args.path == TempoApi.createWorklogUrl && args.body.originTaskId == "AGPRJ-1") {
                    def txt = new File(
                            this.getClass().getResource("createdWorklogResponse.json").toURI()
                    ).text
                    return [ data: new JsonSlurper().parseText(txt) ]
                }
            }
        }
    }
    @Test
    void testToMinutes() {
        def ret = toTest.toMinutes("1h30m")
        assertTrue(ret == 90)

        ret = toTest.toMinutes("3h")
        assertTrue(ret == 180)

        ret = toTest.toMinutes("1h60m")
        assertTrue(ret == 120)

        ret = toTest.toMinutes("50m")
        assertTrue(ret == 50)

        ret = toTest.toMinutes("3h30")
        assertTrue(ret == 210)
    }

    @Test(expected = Exception)
    void testToMinutesInvalid() {
        def ret = toTest.toMinutes("213xyz")
    }

    @Test(expected = Exception)
    void testToMinutesInvalidDay() {
        def ret = toTest.toMinutes("1d")
    }

    @Test
    void testSearchWorklogs() {
        def ret = toTest.searchWorklog("2021-08-30", "2021-09-05")

        assertTrue(ret.size() == 7)
        /** more to test later on */
    }

    @Test
    void testCreateWorklog() {
        def ret = toTest.createWorklog("test 2", "AGPRJ-1", "2021-09-06", "1h")

        assertTrue(ret)
    }

    @Test(expected = Exception)
    void testCreateWorklogNonExistingIssue() {
        toTest.createWorklog("working on a non existing issue",
                "NONEXISTING-1", "2021-09-01", "1h")
    }
}
