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

    @Test
    void testAnalyzeWorklog() {
        def issue = toTest.resolve("ABC-316")

        assertTrue(issue.worklogsPerUser.size() == 1 &&
        issue.worklogsPerUser["jdoe"].beginDate < issue.worklogsPerUser["jdoe"].endDate &&
        issue.worklogsPerUser["jdoe"].timeSpent == 43200)
    }

    @Test
    void testgetProjects() {
        def ret = toTest.loadProjects()

        assertTrue(ret.size() == 3 &&
            "super_project_3" in ret)
    }

    @Test(expected = RuntimeException)
    void testCtorNoEnvVariables() {
        def toTest = new IssueDetailsResolver()
    }

    @Test(expected = RuntimeException)
    void testLoadLabels() {
        toTest.loadLabels()
    }

    @Test
    void testLoadLabelsForceTrue() {
        def ret = toTest.loadLabels(true)

        assertTrue(ret.size == 5 &&
                "worldcompany" in ret
        )
    }


}
