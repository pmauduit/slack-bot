package fr.spironet.slackbot.jira

import fr.spironet.slackbot.github.GithubApiClient
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.util.Version

import java.text.SimpleDateFormat

/**
 * A convenient class to manage the interactions with different systems
 * (mainly JIRA) and being able to analyze a JIRA issue.
 */
class IssueDetailsResolver {

    // both following fields are public because of external access in the listeners
    def jiraUrl
    def confluenceUrl
    // it is assumed that we are reusing the same credentials as on JIRA

    private def jiraUser
    private def jiraPassword


    def githubApi

    def http

    public IssueDetailsResolver() {
        if (System.getenv("JIRA_CLIENT_PROPERTY_FILE") == null) {
            throw new RuntimeException("expected JIRA_CLIENT_PROPERTY_FILE env variable")
        }
        if (System.getenv("GITHUB_TOKEN") == null) {
            throw new RuntimeException("expected GITHUB_TOKEN env variable")
        }
        if (System.getenv("CONFLUENCE_SERVER_URL") == null) {
            throw new RuntimeException("expected CONFLUENCE_SERVER_URL env variable")
        }
        initialize(System.getenv("JIRA_CLIENT_PROPERTY_FILE"),
                System.getenv("CONFLUENCE_SERVER_URL"),
                System.getenv("GITHUB_TOKEN")
        )
    }

    public IssueDetailsResolver(def jiraPropsFile, def confluenceServerUrl, def ghToken) {
        initialize(jiraPropsFile, confluenceServerUrl, ghToken)
    }

    private def initialize(def jiraPropsFile, def confluenceServerUrl, def ghToken) {
        File propertiesFile = new File(jiraPropsFile)
        def props = new Properties()
        propertiesFile.withInputStream {
            props.load(it)
        }
        this.jiraUrl      = props.getProperty("jira.server.url")
        this.jiraUser     = props.getProperty("jira.user.id")
        this.jiraPassword = props.getProperty("jira.user.pwd")

        this.http = new RESTClient(this.jiraUrl)

        this.confluenceUrl = confluenceServerUrl
        this.githubApi = new GithubApiClient(ghToken)
    }

    /**
     * Confluence search by tags & types of document.
     * Copy-pasted / inspired from the ConfluenceListener class.
     *
     * @param types an array of types ("page","blogpost","space","user","attachment","comment") to search.
     * @param tags a collection of tags to search. Note that contrary to the confluence UI,
     *  we want to search for documents tagged with ALL the tags given as argument (not "IN(...)").
     * @param limit the max number of documents to get, defaults to 10.
     *
     * @See ConfluenceListener.search().
     */
    def searchConfluenceDocuments(def types, def topics, def limit = 10) {
        def typesIn = types.collect{ "\"${it}\""}.join(",")
        def topicsFilter = "label = " + topics.collect{ "\"${it}\""}.join(" AND label = ")

        def cql = "type IN (${typesIn}) AND ${topicsFilter}"
        cql = "cql=" + java.net.URLEncoder.encode(cql, "UTF-8")
        cql += "&limit=${limit}"

        def authorizationHeader = "Basic " + "${this.jiraUser}:${this.jiraPassword}".bytes.encodeBase64()
        def response = http.get(
                uri: this.confluenceUrl,
                path:  "/confluence/rest/api/search",
                queryString: cql,
                headers: [Authorization: authorizationHeader])

        return response.data
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
     * Searches for JIRA issues, using the search API endpoint.
     *
     * @param jql the query, following Jira Query Language (JQL) syntax.
     *
     * @return a Groovy list, corresponding to the JSON deserialization of the API call's response.
     */
    def searchJiraIssues(def jql) {
        def jqlEncoded = java.net.URLEncoder.encode(jql, "UTF-8")
        return loadJira("/rest/api/2/search", "jql=${jqlEncoded}").issues
    }
    /**
     * Loads the general infos for an issue.
     * @param key either the issue key (GEO-1234) or its internal numeric identifier.
     *
     * @return the resulting object.
     */
    def loadIssue(def key) {
        return loadJira("/rest/api/2/issue/${key}")
    }

    /**
     * Loads the worklog for an issue.
     *
     * Note: the C2C JIRA instance is using v2 of the JIRA API, a parameter "startedAfter"
     * is documented but in the v3.
     *
     * The same parameter does not seem to work on v2. We have no better option than to grab
     * every worklog entries and filter the result from our side ... On widely used issues (like
     * "AGFR-1"), this represents a > 12MB JSON document.
     *
     * @param key either the issue key (GEO-1234) or its internal numeric identifier.
     * @param nthofweek the last number of week to return the worklogs from, -1
     * for considering every worklogs of the issue (e.g. no filtering).
     *
     * @return the resulting object.
     */
    def loadIssueWorklog(def key, def nthofweek = -1) {
        def ret = loadJira("/rest/api/2/issue/${key}/worklog")
        if (nthofweek == -1) {
            return ret
        }
        def nWeeksBefore = use (groovy.time.TimeCategory) {
            nthofweek.weeks.ago
        }
        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
        return ret.worklogs.findAll {
            sdf.parse(it.started) > nWeeksBefore
        }
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
    /**
     * Computes a list of labels used on JIRA.
     *
     * @param force indicates if we really need to perform the queries.
     * @throws RuntimeException if force is not set to true.
     *
     * @return the list of labels. This would probably require a human qualification / validation.
     */
    public def loadLabels(def force = false) {
        if (force == false)
            throw new RuntimeException("don't use me ! I'm resource intensive !")

        def ret = []
        def startAt = 0
        // gets issues by batch of 1k records
        while (true) {
            def singleResult = loadJira("/rest/api/2/search",
                    "jql=labels%20is%20not%20EMPTY%20and%20project%20%3D%20support-geospatial&maxResults=1000&startAt=${startAt}")

            ret << singleResult.
                    issues.fields.labels.flatten().unique()
            startAt += 1000
            if (singleResult.issues.size() == 0) {
                break
            }
        }

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
     * Analyzes the worklog, and computes a summary per user, onto the
     * issue.worklogsPerUser field of the issue passed as argument.
     *
     * @param issue the issue to analyze the worklog for.
     *
     */
    private def analyzeWorklogs(def issue) {
        def sdf = new SimpleDateFormat("yyyy-MM-dd")

        issue.worklogs.worklogs.each {
            def user = it.author.name
            def parsedDate = sdf.parse(it.started)
            def timeSpent = it.timeSpentSeconds

            def wpu = issue.worklogsPerUser[user]

            if (wpu == null) {
                issue.worklogsPerUser[user] = [
                        beginDate: parsedDate,
                        endDate  : parsedDate,
                        timeSpent: timeSpent
                ]
            } else {
                wpu.beginDate = parsedDate < wpu.beginDate ? parsedDate : wpu.beginDate
                wpu.endDate = parsedDate > wpu.endDate ? parsedDate : wpu.endDate
                wpu.timeSpent += timeSpent
                issue.worklogsPerUser[user] = wpu
            }
        }
    }

    /**
     * Given the organization field of an issue, computes the "real" organization.
     *
     * For support issues at C2C, the organization is in the form "org-<customer>".
     * Else, the organization can be guessed from the project name, but we have
     * to remove a suffix ("_geomaintenance", "_assistance" ...).
     * See @IssueDetails.meaninglessSuffixes field for a list.
     *
     * @param issue the issue coming from the API.
     * @return the "sanitized" organization.
     */
    def computeOrganization(def issue) {
        def org = issue.fields.customfield_10900?[0]?.name ?:
                issue.fields.project.name
        // coming from the customfield / this is probably a GEO-* support issue
        if (org.startsWith("org-")) {
            org -= "org-"
        }
        // coming without a customfield, we have to guess the org from the project name
        else {
            IssueDetails.meaninglessSuffixes.each {
                org -= it
            }
        }
        return org
    }

    /**
     * Actually does the work of gathering all the infos from everywhere.
     * @param issueKey
     */
    def resolve(def issueKey) {
        def issue = new IssueDetails(issueKey)
        issue.rawIssueInfo = loadIssue(issueKey)

        issue.issueId      = issue.rawIssueInfo.id
        issue.issueAuthor  = issue.rawIssueInfo.fields.reporter
        issue.assignee     = issue.rawIssueInfo.fields.assignee
        issue.labels       = issue.rawIssueInfo.fields.labels
        issue.description  = issue.rawIssueInfo.fields.description
        issue.organization = this.computeOrganization(issue.rawIssueInfo)

        issue.worklogs    = loadIssueWorklog(issueKey)
        issue.watchers    = loadIssueWatchers(issueKey)
        issue.comments    = loadIssueComments(issueKey)
        issue.remoteLinks = loadIssueRemoteLinks(issueKey)

        issue.githubActivity     = loadGithubActivity(issue.issueId)
        issue.githubPullRequests = loadGithubPullRequests(issue.issueId)

        this.computePossibleLabels(issue)
        this.analyzeWorklogs(issue)

        return issue
    }
}
