package fr.spironet.slackbot.google

import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import groovy.json.JsonSlurper

class MockGCalendarClient {

    MockGCalendarClient() {
    }

    def events() {
        return new MockEvents()
    }
}

class MockEvents {
    def list(def calId)         { return this }
    def setTimeMin(def timeMin) { return this }
    def setTimeMax(def timeMax) { return this }
    def setFields(def fields)   { return this }
    def setPageToken(def token) { return this }
    def setSingleEvents(def b)  { return this }
    def setOrderBy(def ob)      { return this }

    def execute() {
      return new Object() {
          def getItems() {
              def ret = new File(this.getClass().getResource("gcaleventresponse.json").toURI()).text
              return new JsonSlurper().parseText(ret)
          }
          def getNextPageToken() { return null }
      }
    }
}
