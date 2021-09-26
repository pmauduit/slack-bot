package fr.spironet.slackbot.jira

import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assume.assumeTrue

class IssueDetailsResolverTest {
    def toTest
    @Before
    public void setUp() {
        toTest = new IssueDetailsResolver("/dev/null",
                "http://localhost/confluence",
                "ghp_aaaaaa")

        toTest.http = new MockJiraResponse()
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
        assumeTrue(System.env["JIRA_CLIENT_PROPERTY_FILE"] == null)
        new IssueDetailsResolver()
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

    @Test
    void testSearchJiraIssues() {
        def ret = toTest.searchJiraIssues("project = GEO AND priority = Highest")

        assertTrue(ret.size() == 4 &&
                    ret[0].key == "ABC-4739")
    }

    @Test
    void testSearchConfluence() {
        def ret = toTest.searchConfluenceDocuments(["page", "blogpost"], ["chambery"], 3)

        assertTrue(ret.results.size() == 3 &&
        ret.results.collect { it.content.type }.unique().sort() == ["blogpost", "page"])
    }


}
