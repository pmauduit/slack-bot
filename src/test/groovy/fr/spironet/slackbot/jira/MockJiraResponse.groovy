package fr.spironet.slackbot.jira

import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient

class MockJiraResponse extends RESTClient {
    @Override
    public Object post( Map<String,?> args ) {
        return [:]
    }
    @Override
    public Object get( Map<String,?> args ) {
        if (args.path == "/rest/api/2/issue/ABC-316") {
            def ret = new File(this.getClass().getResource("ABC-316.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if (args.path == "/rest/api/2/issue/ABC-316/worklog") {
            def ret = new File(this.getClass().getResource("ABC-316-worklog.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if (args.path == "/rest/api/2/issue/ABC-316/watchers") {
            def ret = new File(this.getClass().getResource("ABC-316-watchers.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if (args.path == "/rest/api/2/issue/ABC-316/comment") {
            def ret = new File(this.getClass().getResource("ABC-316-comment.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if (args.path == "/rest/api/2/issue/ABC-316/remotelink") {
            def ret = new File(this.getClass().getResource("ABC-316-remotelink.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if ((args.path == "/rest/dev-status/1.0/issue/detail")
                &&
                args.queryString.contains("dataType=repository"))
        {
            def ret = new File(this.getClass().getResource("ABC-316-issuedetail-gh-repository.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if ((args.path == "/rest/dev-status/1.0/issue/detail")
                &&
                args.queryString.contains("dataType=pullrequest"))
        {
            def ret = new File(this.getClass().getResource("ABC-316-issuedetail-gh-pr.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if (args.path == "/rest/api/2/project") {
            def ret = new File(this.getClass().getResource("list-projects.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if ((args.path == "/rest/api/2/search") && (args.queryString.contains("startAt=0"))) {
            def ret = new File(this.getClass().getResource("compute-labels-1.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if ((args.path == "/rest/api/2/search") && (args.queryString.contains("startAt=1000"))) {
            def ret = new File(this.getClass().getResource("compute-labels-2.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if ((args.path == "/rest/api/2/search") && (args.queryString.contains("startAt=2000"))) {
            def ret = new File(this.getClass().getResource("compute-labels-3.json").toURI()).text
            return [ data: new JsonSlurper().parseText(ret) ]
        } else if ((args.path == "/rest/api/2/search") && (args.queryString.startsWith("jql="))) {
            def ret = new File(this.getClass().getResource("search-issues-response.json").toURI()).text
            return [ data: [ issues: new JsonSlurper().parseText(ret) ]]
        }
        return [:]
    }
}
