package fr.spironet.slackbot.jira

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.util.Version

/**
 * A convenient class to manage the interactions with different systems
 * (mainly JIRA) and being able to analyze a JIRA issue.
 */
class IssueDetailsResolver {

    private def jiraUrl
    private def jiraUser
    private def jiraPassword

    private def confluenceUrl
    // reusing the same credentials as on JIRA

    private def githubToken

    private def http

    public IssueDetailsResolver() {
        if (System.getenv("JIRA_CLIENT_PROPERTY_FILE") == null) {
            throw new RuntimeException("expected JIRA_CLIENT_PROPERTY_FILE env variable")
        }
        File propertiesFile = new File(System.getenv("JIRA_CLIENT_PROPERTY_FILE"))
        def props = new Properties()

        propertiesFile.withInputStream {
            props.load(it)
        }
        this.jiraUrl      = props.getProperty("jira.server.url")
        this.jiraUser     = props.getProperty("jira.user.id")
        this.jiraPassword = props.getProperty("jira.user.pwd")

        this.http = new RESTClient(this.jiraUrl)

        this.confluenceUrl = System.getenv("CONFLUENCE_SERVER_URL")
        if (this.confluenceUrl == null) {
            throw new RuntimeException("expected CONFLUENCE_SERVER_URL env variable")
        }
    }

    /**
     * Confluence search by tag.
     * Copy-pasted / inspired from the ConfluenceListener class.
     *
     * @param tags a collection of tags to search.
     *
     * @See ConfluenceListener.search().
     */
    public def confluenceSearch(def tags) {
        def topicsFilter = "label = " + tags.collect{ "\"${it}\""}.join(" AND label = ")

        def cql = "${topicsFilter}"
        cql = "cql=" + java.net.URLEncoder.encode(cql, "UTF-8")
        def http = new RESTClient(this.confluenceUrl)
        def authorizationHeader = "Basic " + "${this.jiraUser}:${this.jiraPassword}".bytes.encodeBase64()
        def response = http.get(
                uri: this.confluenceUrl,
                path:  "/confluence/rest/api/search",
                queryString: cql,
                headers: [Authorization: authorizationHeader])
        return response.data.results
    }

    /**
     * Generic call to any JIRA endpoint
     *
     * @param path the path to query.
     * @return the groovy object resulting from the JSON returned.
     */
    private def loadJira(def path, def queryString = '') {
        def response = http.get(
                path: path,
                queryString: queryString as String,
                requestContentType: ContentType.JSON,
                headers: [
                        Accept: "application/json",
                        Authorization: "Basic " + "${this.jiraUser}:${this.jiraPassword}".bytes.encodeBase64()
                ]
        )
        return response.data
    }

    /**
     * Loads the general infos for an issue.
     * @param key either the issue key (GEO-1234) or its internal numeric identifier.
     *
     * @return the resulting object.
     */
    private def loadIssue(def key) {
        return loadJira("/rest/api/2/issue/${key}")
    }

    /**
     * Loads the worklog for an issue.
     *
     * Be aware that we are using api v2 of JIRA, where we cannot filter on the date,
     * so we are receiving the whole worklog since epoch ... On widely used issues (like
     * AGFR-1), this returns a 12MB JSON document.
     *
     * The code here does not filter anyway.
     *
     * @param key either the issue key (GEO-1234) or its internal numeric identifier.
     *
     * @return the resulting object.
     */
    private def loadIssueWorklog(def key) {
        return loadJira("/rest/api/2/issue/${key}/worklog")
    }

    /**
     * Loads the issue watchers.
     *
     * @param key either the issue key or its internal numeric identifier.
     *
     * @return the resulting watchers as an object.
     */
    private def loadIssueWatchers(def key) {
        return loadJira("/rest/api/2/issue/${key}/watchers")
    }

    /**
     * Loads the issue comments.
     *
     * @param key either the issue key or its internal numeric identifier.
     *
     * @return the resulting comments as an object.
     */
    private def loadIssueComments(def key) {
        return loadJira("/rest/api/2/issue/${key}/comment")
    }

    /**
     * Loads the issue remote links.
     *
     * @param key either the issue key or its internal numeric identifier.
     *
     * @return the resulting links as an object.
     */
    private def loadIssueRemoteLinks(def key) {
        return loadJira("/rest/api/2/issue/${key}/remotelink")
    }

    /**
     * Loads the pull requests from Github logged by JIRA.
     * Note: this requires to tag the PR with "[issuekey]". We
     * avoid to do so on public repositories.
     *
     * @param issueId the internal numeric identifier of the issue.
     *
     * @return the resulting GitHub PRs as an object.
     */
    private def loadGithubPullRequests(def issueId) {
        return loadJira("/rest/dev-status/1.0/issue/detail", "issueId=${issueId}&applicationType=github&dataType=pullrequest")
    }

    /**
     * Loads the Github activity logged by JIRA.
     * Note: this requires to tag the commits with "[issuekey]". We
     * avoid to do so on public repositories. JIRA returns a JSON
     * object describing commits per repositories.
     *
     * @param issueId the internal numeric identifier of the issue.
     *
     * @return the resulting GitHub activity as an object.
     */
    private def loadGithubActivity(def issueId) {
        return loadJira("/rest/dev-status/1.0/issue/detail", "issueId=${issueId}&applicationType=github&dataType=repository")
    }
    /**
     * Loads every JIRA projects.
     * @return the list of projects
     */
    private def loadProjects() {
        return loadJira("/rest/api/2/project").collect { it.name }.sort(false)

    }
    public def loadLabels() {
        throw new RuntimeException("don't use me ! I'm resource intensive !")

        def ret = []
        ret << loadJira("/rest/api/2/search",
                "jql=labels%20is%20not%20EMPTY%20and%20project%20%3D%20support-geospatial&maxResults=1000").
                issues.fields.labels.flatten().unique()
        ret << loadJira("/rest/api/2/search", "jql=labels%20is%20not%20EMPTY%20and%20project%20%3D%20support-geospatial&maxResults=1000&startAt=1000").
                issues.fields.labels.flatten().unique()
        return ret.flatten().unique()
    }

    /**
     * From a corpus (the issue description + the comments), tries
     * to build a list of candidates labels for this issue.
     */
    private void computePossibleLabels(def issue) {
        if (issue.description == null || issue.comments == null) {
            return
        }
        def corpus = issue.description + "\n" + issue.comments.comments.collect { it.body }.join("\n")
        def analyzer= new StandardAnalyzer(Version.LUCENE_36)
        def keywords = []
        def stream = analyzer.tokenStream("contents", new StringReader(corpus))
        try {
            stream.reset()
            while(stream.incrementToken()) {
                String kw = stream.getAttribute(CharTermAttribute.class).toString()
                def elem = keywords.find { it.keyword == kw }
                if (elem == null) {
                    keywords.add([keyword: kw, weight: 1])
                } else {
                    elem.weight += 1
                }
            }
        } finally {
            try {
                stream.end()
                stream.close()
            } catch (Exception e) {}
        }
        keywords.sort{ a,b -> b.weight <=> a.weight }
        keywords = keywords.findAll { it.keyword in IssueDetails.knownLabels }.collect{ it.keyword }
        issue.possibleLabels = keywords.findAll { ! (it in issue.labels) }
    }

    /**
     * Actually does the work of gathering all the infos from everywhere.
     * @param issueKey
     */
    public def resolve(def issueKey) {
        def issue = new IssueDetails(issueKey)
        issue.rawIssueInfo = loadIssue(issueKey)

        issue.issueId      = issue.rawIssueInfo.id
        issue.issueAuthor  = issue.rawIssueInfo.fields.reporter
        issue.assignee     = issue.rawIssueInfo.fields.assignee
        issue.labels       = issue.rawIssueInfo.fields.labels
        issue.description  = issue.rawIssueInfo.fields.description
        issue.organization = issue.rawIssueInfo.fields.customfield_10900?[0]?.name ?:
                issue.rawIssueInfo.fields.project.name

        // coming from the customfield / this is probably a GEO-* support issue
        if (issue.organization.startsWith("org-")) {
            issue.organization -= "org-"
        }
        // coming without a customfield, we have to guess the org from the project name
        else {
            IssueDetails.meaninglessSuffixes.each {
                issue.organization -= it
            }
        }

        issue.worklogs    = loadIssueWorklog(issueKey)
        issue.watchers    = loadIssueWatchers(issueKey)
        issue.comments    = loadIssueComments(issueKey)
        issue.remoteLinks = loadIssueRemoteLinks(issueKey)

        issue.githubActivity     = loadGithubActivity(issue.issueId)
        issue.githubPullRequests = loadGithubPullRequests(issue.issueId)

        this.computePossibleLabels(issue)

        return issue
    }

    public static void main(String[] args) {
        def toTest = new IssueDetailsResolver()
        //def ret = toTest.resolve("GSEST-405")
        //def ret = toTest.resolve("GSGGE2019-22")
       // def ret = toTest.resolve("GEO-4638")
        //def ret = toTest.confluenceSearch(["rennes"])
        //def ret = toTest.resolve("GSREN-13")
        //println ret
        //def ret = toTest.resolve("GSREN-17")
        //println ret
        def issue = toTest.resolve("GEO-4712")
        println issue.possibleLabels


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

}
