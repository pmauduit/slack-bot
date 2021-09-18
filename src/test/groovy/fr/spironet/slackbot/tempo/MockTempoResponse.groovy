package fr.spironet.slackbot.tempo

import groovy.json.JsonSlurper

class MockTempoResponse {
    def post(def args) {
        if (args.path == TempoApi.searchWorklogUrl) {
            def txt = new File(
                    this.getClass().getResource("tempoSearch.json").toURI()
            ).text
            return [data: new JsonSlurper().parseText(txt)]
        } else if (args.path == TempoApi.createWorklogUrl && args.body.originTaskId == "NONEXISTING-1") {
            throw new groovyx.net.http.HttpResponseException("BAD RREQUEST")
        } else if (args.path == TempoApi.createWorklogUrl && args.body.originTaskId == "AGPRJ-1") {
            def txt = new File(
                    this.getClass().getResource("createdWorklogResponse.json").toURI()
            ).text
            return [data: new JsonSlurper().parseText(txt)]
        }
    }
    def get(def args) {
        if (args.queryString.contains("2021-09-13")) {
            def txt = new File(
                    this.getClass().getResource("tempo-not-approved-yet.json").toURI()
            ).text
            return [data: new JsonSlurper().parseText(txt)]
        } else if (args.queryString.contains("2021-09-06")) {
            def txt = new File(
                    this.getClass().getResource("tempo-approved.json").toURI()
            ).text
            return [data: new JsonSlurper().parseText(txt)]
        }
        return null
    }
}
