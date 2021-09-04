package fr.spironet.slackbot.jira

import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class IssueDetailsResolverTest {
    def toTest
    @Before
    public void setUp() {
        toTest = new IssueDetailsResolver("/dev/null",
                "http://localhost/confluence",
                "ghp_aaaaaa")

        toTest.http = new RESTClient() {
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
                }
                return [:]
            }
        }
    }
    @Test
    void testResolve() {
        def issue = toTest.resolve("ABC-316")

        assertTrue(
                issue.issueKey == "ABC-316"           &&
                issue.organization == "super_project" &&
                "authentication" in issue.labels      &&
                "ldap" in issue.possibleLabels        &&
                issue.githubPullRequests.size() == 2  && // TODO: should be empty actually ...
                issue.githubActivity.detail[0].repositories[0].commits[0].id == "70b86f27a7fe6afe19845a37f9511e66b18b2d41"
        )
    }

        // Analyze the worklog per worker on an issue
        // worklog.json issued from https://jira.camptocamp.com/rest/api/2/issue/AGFR-1/worklog
        // Avoid loading the page, as it DoS a bit the jira instance ;-)
        /*
        def dudu = new File("/home/pmauduit/Documents/worklog.json").getText()
        def jsonSlurper = new JsonSlurper()
        def parsed = jsonSlurper.parseText(dudu)
        def processed = [:]
        parsed.worklogs.each {
            if (processed[it.author.displayName] == null)
                processed[it.author.displayName] = it.timeSpentSeconds
            else {
                processed[it.author.displayName] += it.timeSpentSeconds
            }
        }
        processed.sort{ it.value }
        print processed
         */
}
