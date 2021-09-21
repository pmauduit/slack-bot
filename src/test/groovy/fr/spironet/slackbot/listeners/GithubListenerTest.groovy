package fr.spironet.slackbot.listeners

import groovy.json.JsonSlurper
import org.junit.Test

import static org.junit.Assert.assertTrue

class GithubListenerTest {

    @Test
    void testGithubListenerFindRepos() {
            def ghLstnr = new GithubListener() {
                @Override
                def doGithubSearch(def query) {
                    def ret = new File(this.getClass().getResource("github-api-query.json").toURI()).text
                    [ data: new JsonSlurper().parseText(ret) ]
                }
            }
            def msg = ghLstnr.findRepositories(["trifouillis"])
            assertTrue(msg.message.contains("myorg/georchestra-trifouillis-public-customizations") &&
                    msg.message.contains("*<https://github.com/myorg/terraform-trifouillismetropole/commits|history>* |") &&
                    msg.message.contains("*<https://github.com/myorg/terraform-trifouillismetropole/issues|issues> (2)* |") &&
                    msg.message.contains("*<https://github.com/myorg/terraform-trifouillismetropole/pulls|pull-requests>* |") &&
                    msg.message.contains("*<https://github.com/myorg/terraform-trifouillismetropole/actions|Github-Actions>*")
        )
    }
}
