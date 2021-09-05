package fr.spironet.slackbot.terraform


import org.junit.Before
import org.junit.Test

import java.text.SimpleDateFormat

import static org.junit.Assert.assertTrue

class MockS3Object {
    def file
    def MockS3Object(def file) {
        this.file = file
    }
    def getObjectContent() {
        return this.getClass().getResourceAsStream(file)
    }
}

class TerraformStateTest {
    TerraformState toTest

    @Before
    void setUp() {
        toTest = new TerraformState(null, null  )
        toTest.s3 = new Object() {
            /** this one is called if no versionId is specified */
            def getObject(def arg1, def arg2) {
                return new MockS3Object("sample.tfstate.json")
            }
            /** this one is called when a versionId is specified */
            def getObject(def objectRequest) {
                return new MockS3Object("sample2.tfstate.json")
            }

            def listVersions(def req) {
                return [ versionSummaries:
                    [
                        [ versionId: "latest", lastModified: new Date() ],
                        [ versionId: "latest",
                          lastModified: new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                  .parse("2020-10-09 23:30:10")]
                    ]
                ]
            }
        }
    }

    /**
     * The only thing this test proves is that Groovy is able
     * to parse a well-formed json document from the classpath.
     */
    @Test
    void testGetState() {
        def state = toTest.getState("dudu")

        assertTrue(state.terraform_version == "0.11.15")
    }

    /**
     * Same remark for this one, it is just to ensure
     * the mock is functioning correctly for the coming tests.
     */
    @Test
    void testGetStateWithVersion() {
        def state = toTest.getState("dudu", "withversion")

        assertTrue(state.terraform_version == "0.11.15" && state.modules[0].resources.size() == 2)
    }

    /**
     * Test the listVersions() call against our mock.
     */
    @Test
    void testListVersions() {
        def versions = toTest.listObjectVersions("dudu")

        assertTrue(versions.size() == 2 && versions[0].id == "latest")
    }

    /**
     * Tests the compareStates() call.
     */
    @Test
    void testcompareStates() {
        def state1 = toTest.getState("dudu")
        def state2 = toTest.getState("dudu", "before :-)")

        def diff = toTest.compareStates(state1, state2)

        assertTrue(
                diff.keySet().size() == 3 &&
                /* the rancher-compose is different between both samples */
                diff["rancher_stack.georchestra-ldap"].differences.rancher_compose != null &&
                /* the georchestra-smtp stack does not exist anymore in state 2 */
                diff["rancher_stack.georchestra-smtp"].onlyOnLeft.size() == 18 &&
                diff["rancher_stack.georchestra-smtp"].onlyOnRight.size() == 0 &&
                diff["rancher_stack.georchestra-smtp"].onBoth.size() == 0 &&
                /* the monitoring-internal-services stack only exists on state 2 */
                diff["rancher_stack.monitoring-internal-services"].onlyOnLeft.size() == 0 &&
                diff["rancher_stack.monitoring-internal-services"].onlyOnRight.size() == 19 &&
                diff["rancher_stack.monitoring-internal-services"].onBoth.size() == 0
        )
    }

    /**
     * Tests the prettyPrintDiff() method against our mocked objects.
     *
     */
    @Test
    void testPrettyPrintDiff() {
        def state1 = toTest.getState("dudu")
        def state2 = toTest.getState("dudu", "before :-)")
        def diffs  = toTest.compareStates(state1, state2)

        def output = toTest.prettyPrintDiff(diffs)

        assertTrue(
                output.contains ("georchestra-ldap modified") &&
                output.contains ("georchestra-smtp removed")  &&
                output.contains ("monitoring-internal-services added")
        )
    }
}
