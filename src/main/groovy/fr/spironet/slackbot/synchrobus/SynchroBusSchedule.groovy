package fr.spironet.slackbot.synchrobus

import org.jsoup.Jsoup

class SynchroBusSchedule {

    def synchroBusStopsBaseUrl = "https://start.synchro.grandchambery.fr/fr/map/stop/"

    def technolacToCbyBusStop = "INES1"
    def biollayToTechnolacBusStop = "BIOLL1"

    private def nextDepartures(def busStop) {
        def soup = Jsoup.connect(synchroBusStopsBaseUrl + busStop).get()
        def nextDepElem = soup.select("span[class='is-Schedule-Line-Directions-Item-Time-C2 is-realtime']")

        nextDepElem.collect{it.text() }
    }

    def parseNextDeparturesToChambery() {
        nextDepartures(technolacToCbyBusStop)
    }

    def parseNextDeparturesToTechnolac() {
        nextDepartures(biollayToTechnolacBusStop)
    }

    static void main(String[] args) {
        def sbs = new SynchroBusSchedule()
        def nextDepsToTechnolac = sbs.parseNextDeparturesToTechnolac()
        def nextDepsToChambery = sbs.parseNextDeparturesToChambery()
        println "${nextDepsToTechnolac}, ${nextDepsToChambery}"
    }

}
