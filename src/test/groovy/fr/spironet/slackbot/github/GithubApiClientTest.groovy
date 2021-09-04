package fr.spironet.slackbot.github

import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class GithubApiClientTest {
    private def toTest

    @Before
    void setUp() {
        toTest = new GithubApiClient(null)
        toTest.http = new RESTClient() {
                @Override
                public Object post( Map<String,?> args ) {
                    def ret = new File(this.getClass().getResource("repositorytopics.json").toURI()).text
                    return [ data: new JsonSlurper().parseText(ret) ]
                }
        }
    }

    @Test
    void testGetRepositoryTopics() {
        def ret = toTest.getRepositoryTopics("org:myorg topic:chambery archived:false")

        assertTrue(ret.size() == 6 && "chambery" in ret[0].topics)
    }
}
