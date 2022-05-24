package fr.spironet.slackbot.listeners

import fr.spironet.slackbot.google.GCalendarApi
import org.junit.Test

import static org.junit.Assert.assertTrue

class GCalListenerTest {

    def client = new MockedCalendar()
    GCalendarApi mockedGcalApi = new GCalendarApi(client)
    GCalListener toTest = new GCalListener(mockedGcalApi)

    @Test
    public void testProcessCommand() {
        def ret = toTest.processCommand("!aaaa plop")
        assertTrue ret.getMessage().contains("Usage: `!gcal today|usage`")

        ret = toTest.processCommand("!gcal usage")
        assertTrue ret.getMessage().contains("Usage: `!gcal today|usage`")

        ret = toTest.processCommand("!gcal today")
        def retMsg = ret.getMessage()
        assertTrue retMsg.contains("• *The whole day*: _Vacations_") &&
                retMsg.contains("Here is a summary of your day") &&
                retMsg.contains("• From *") && retMsg.contains(": _Daily_")
    }

}

class MockedCalendar {
    def events() {
        return this
    }

    def list(def _) {
        return this
    }

    def setTimeMin(def _) {
        return this
    }

    def setTimeMax(def _) {
        return this
    }

    def setSingleEvents(def _) {
        return this
    }

    def setOrderBy(def _) {
        return this
    }

    def setPageToken(def _) {
        return this
    }

    def execute() {
        return new Object() {
            def getItems() {
                return [
                        [
                                start: [dateTime: new MockedDateTime()],
                                end: [dateTime: new MockedDateTime()],
                                status: "confirmed",
                                summary: "Meeting jardins partagés"
                        ],
                        [
                                start: [dateTime: new MockedDateTime()],
                                end: [dateTime: new MockedDateTime()],
                                status: "confirmed",
                                summary: "Daily"
                        ],
                        [
                                start: [dateTime: null],
                                end: [dateTime: null],
                                status: "confirmed",
                                summary: "Vacations"
                        ],
                        [
                                start: [dateTime: new MockedDateTime()],
                                end: [dateTime: new MockedDateTime()],
                                status: "unconfirmed",
                                summary: "Colloque travaux de voirie"
                        ]
                ]
            }

            def getNextPageToken() {
                return null
            }
        }
    }
}

class MockedDateTime {
    def getValue() {
        return new Date()
    }
}