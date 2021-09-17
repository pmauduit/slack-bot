package fr.spironet.slackbot.tempo

import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Paths

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

    @Test
    void testWorklogsWeekPerDay() {
        def data = toTest.searchWorklog("aaa", "bbb")
        def perDay = toTest.worklogsWeekPerDay(data)

        assertTrue(perDay.size() == 5 &&
        perDay.collect { it.day } == ["Mon", "Tue", "Wed", "Thu", "Fri"])
    }

    @Test
    void testWorklogsWeekPerProject() {
        def data = toTest.searchWorklog("aaa", "bbb")
        def perProject = toTest.worklogsWeekPerProject(data)

        assertTrue(perProject.size() == 5 &&
                perProject.collect { it.project } == ["PROJ6",
        "ADM", "PROJ2", "PROJ1", "PROJ4"])
    }

    @Test
    void testWorklogsWeekPerIssue() {
        def data = toTest.searchWorklog("aaa", "bbb")
        def perIssue = toTest.worklogsWeekPerIssue(data)

        assertTrue(perIssue.size() == 6 &&
                perIssue.collect { it.issue } == ["PROJ6-5",
        "ADM-1", "PROJ2-7", "PROJ1-316", "PROJ4-4647","PROJ4-4723"])
    }

    @Test
    void testGenerateReport() {
        def driverPath = Paths.get(".", "drivers", "chromedriver-linux-64bit").
                toAbsolutePath().normalize().toString()
        System.setProperty("webdriver.chrome.driver", driverPath)

        def img = toTest.generateReport("2021-09-01", "2021-09-05")

        BufferedImage imgRead = ImageIO.read(new ByteArrayInputStream(img.getBytes()))
        assertTrue(imgRead.getHeight() == 1055 &&
                imgRead.getWidth() == 1220)
    }

}
