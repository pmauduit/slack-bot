package fr.spironet.slackbot.jira

import groovy.xml.XmlSlurper

class MockJiraRssResponse {
    def get(def args) {
        def rssTxt = this.getClass().getResource("personal-rss-feed.xml").text
        return new XmlSlurper().parseText(rssTxt)
    }
}
