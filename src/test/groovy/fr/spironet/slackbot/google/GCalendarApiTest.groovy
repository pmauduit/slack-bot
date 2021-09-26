package fr.spironet.slackbot.google

import org.junit.Test
import static org.junit.Assert.assertTrue
import static org.junit.Assume.assumeTrue

class GCalendarApiTest {

    GCalendarApi toTest = new GCalendarApi(new MockGCalendarClient())

    @Test
    void testGetTodaysEvents() {
        def ret = toTest.getTodaysEvents()

        assertTrue(ret.size() == 9 &&
        ret[0].organizer.email == "user1@company.com")
    }

    @Test(expected = RuntimeException.class)
    void testDefaultConstructor() {
        assumeTrue(System.env["GCAL_OAUTH_CLIENT_ID"] == null)
        assumeTrue(System.env["GCAL_OAUTH_CLIENT_SECRET"] == null)
        assumeTrue(System.env["GCAL_OAUTH_ACCESS_TOKEN"] == null)
        assumeTrue(System.env["GCAL_OAUTH_REFRESH_TOKEN"] == null)

        new GCalendarApi()
        // expected a RuntimeException to be thrown
    }
}
