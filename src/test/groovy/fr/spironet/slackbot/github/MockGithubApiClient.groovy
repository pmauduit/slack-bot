package fr.spironet.slackbot.github

import groovy.json.JsonSlurper

import java.text.SimpleDateFormat

class MockGithubApiClient {

    def fakedCreatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date())

    def getReceivedEventForUser(def usr) {
        def evts = new File(this.getClass().getResource("received-events.json").toURI()).text
        def ret = new JsonSlurper().parseText(evts)
        ret.each {
            it.created_at = this.fakedCreatedAt
        }
    }
}
