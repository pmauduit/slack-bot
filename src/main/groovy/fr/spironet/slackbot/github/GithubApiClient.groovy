package fr.spironet.slackbot.github

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

import java.time.Instant
import java.time.LocalDateTime

class GithubApiClient {

    private def githubToken
    def http = new RESTClient("https://api.github.com")

    /**
     * Default constructor. Expects the GITHUB_TOKEN environment variable
     * to be defined, and will throw a RuntimeException if it is not the case.
     *
     */
    def GithubApiClient() {
        if (System.env["GITHUB_TOKEN"] == null) {
            throw new RuntimeException("Expected GITHUB_TOKEN to be defined.")
        }
        initialize(System.env["GITHUB_TOKEN"])
    }
    /**
     * Constructor with a github token manually (e.g. not coming from the environment variables)
     * set.
     *
     * @param token the token to use for authentication on the Github API.
     */
    def GithubApiClient(def token) {
        initialize(token)
    }

    private def initialize(def token) {
        this.githubToken = token
    }

    private def graphQlTopicsPerRepoPerOrg = '''
        query($cursor: String, $query: String!)
        {
          search(first: 100, after: $cursor, type: REPOSITORY, query: $query) {
            pageInfo {
              hasNextPage
              endCursor
            }
            repos: edges {
              repo: node {
                ... on Repository {
                  nameWithOwner
                  repositoryTopics(first: 100) {
                    topics: nodes {
                      topic {
                        name
                      }
                    }
                  }
                }
              }
            }
          }
        }
    '''

    /**
     * Given a query, returns the repository topics associated with each repositories
     * returned.
     *
     * @param repoQuery the query (e.g. "org: camptocamp topic: geospatial")
     * @return an array of the following form: `[[ name: "org/name", topics: ["aaa", "bbb", ... ]], ...]
     */
    def getRepositoryTopics(def repoQuery) {
        def ret = []
        def hasNextPage, nextCursor
        do {
            def payload = [
                    "query"    : graphQlTopicsPerRepoPerOrg,
                    "variables": ["cursor": nextCursor, "query": repoQuery]
            ]
            def response = http.post(path: "/graphql",
                    body: payload,
                    requestContentType: ContentType.JSON,
                    headers: [
                            Accept       : "application/json",
                            Authorization: "Bearer ${this.githubToken}",
                            "User-Agent" : "groovyx.net.http.RESTClient"
                    ])

            hasNextPage = response.data.data.search.pageInfo.hasNextPage
            nextCursor = response.data.data.search.pageInfo.endCursor

            ret.addAll(response.data.data.search.repos.collect {
                [
                        "name"  : it.repo.nameWithOwner,
                        "topics": it.repo.repositoryTopics.topics.collect { t -> t.topic.name }
                ]
            })

        } while (hasNextPage == true)
        ret
    }

    /**
     * Gets the received event for the user given as argument.
     *
     * @param user the user to query github for.
     *
     * @return a list of events
     */
    def getReceivedEventForUser(def user) {
        def actualPath = String.format("/users/%s/received_events", user)
        def http = new RESTClient("https://api.github.com")
        def response = http.get(path: actualPath,
                headers: ["Authorization": "Bearer ${this.githubToken}",
                          "User-Agent": "groovyx.net.http.RESTClient"])
        response.data
    }

    /**
     * List of topics used in our organization on Github.
     *
     * This list can be easily computed using the getRepositoryTopics(def repoQuery)
     * call, then something like:
     *
     * ```
     * def obj = ghApi.getRepositoryTopics("org:camptocamp archived:false topic:geospatial")
     * obj.collect { it.topics }.flatten().unique().sort().collect {"\"${it}\"" }.join("\n")
     * ```
     */
     static def knownTopics = [
             "ansible",
             "configuration",
             "corse",
             "deployment",
             "docker",
             "flask",
             "geo2france",
             "georchestra",
             "georhena",
             "geospatial",
             "github",
             "grandest",
             "hauteloire",
             "java",
             "rancher",
             "rennes",
             "runtime",
             "terraform",
             "test",
             "webhooks",
             "zaseine"
    ]
}
