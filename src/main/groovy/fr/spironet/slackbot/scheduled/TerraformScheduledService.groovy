package fr.spironet.slackbot.scheduled

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.google.common.util.concurrent.AbstractScheduledService
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import fr.spironet.slackbot.terraform.TerraformState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

class TerraformScheduledService extends AbstractScheduledService {
    SlackSession slackSession
    TerraformState tfStateAnalyzer
    def keysToSpy
    private final static Logger logger = LoggerFactory.getLogger(TerraformScheduledService.class)
    def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")
    def lastKnownVersion = [:]


    TerraformScheduledService(def session) {
        this.slackSession = session
        def bucket     = System.env["TERRAFORM_AWS_BUCKET"]
        def s3key      = System.env["TERRAFORM_AWS_CLIENT_KEY"]
        def s3secret   = System.env["TERRAFORM_AWS_SECRET_KEY"]
        def s3region   = System.env["TERRAFORM_AWS_ZONE"]
        this.keysToSpy = System.env["TERRAFORM_STATES_TO_SPY"].split(",")
        this.keysToSpy.each {
            lastKnownVersion[it] = null
        }

        def credentials = new BasicAWSCredentials(s3key, s3secret)
        def credsProvider = new StaticCredentialsProvider(credentials)
        def s3client = AmazonS3ClientBuilder.standard().withCredentials(credsProvider)
                        .withRegion(s3region)
                        .build()
        this.tfStateAnalyzer = new TerraformState(s3client, bucket)
    }

    protected void runOneIteration() throws Exception {
        def botOwner = slackSession.findUserByEmail(botOwnerEmail)
        if (botOwner == null) {
            logger.error("bot owner not found: ${botOwnerEmail}")
            return
        }
        this.keysToSpy.each {
            // first version is the ost recent one
            def knownVersion = this.lastKnownVersion[it]
            def versions = tfStateAnalyzer.listObjectVersions(it)
            if (knownVersion == null) {
                /* initialization */
                this.lastKnownVersion[it] = versions[0].id
            } else if (versions[0].id != knownVersion) {
                /** a change has been detected, we need to analyze the state for changes */
                def previousState = tfStateAnalyzer.getState(it, knownVersion)
                def lastState = tfStateAnalyzer.getState(it, versions[0].id)
                def diff = tfStateAnalyzer.compareStates(previousState, lastState)
                if (diff.size() > 0) {
                    def message = ":terraform: *Changes detected* on state ${it}:\n"
                    message += tfStateAnalyzer.prettyPrintDiff(diff)
                    def slackMessage = SlackPreparedMessage.builder().message(message).build()
                    slackSession.sendMessageToUser(botOwner, slackMessage)
                }
                this.lastKnownVersion[it] = versions[0].id
            }
        } // keysToSpy.each()
    }

    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 5, TimeUnit.MINUTES)
    }
}
