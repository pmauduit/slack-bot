@Grapes([
        @Grab(group = 'com.ullink.slack', module = 'simpleslackapi', version = '1.2.0'),
        @Grab(group = 'com.lesstif', module = 'jira-rest-api', version = '0.8.3'),
        @Grab(group = 'com.sun.jersey', module = 'jersey-core', version = '1.19.4'),
        @Grab(group = 'com.offbytwo.jenkins', module = 'jenkins-client', version = '0.3.8'),
        @Grab('org.qfast.odoo-rpc:odoo-jsonrpc:1.0'),
        @Grab(group = 'org.kohsuke', module = 'github-api', version = '1.90'),
        @Grab(group = 'com.squareup.okhttp3', module = 'okhttp', version = '4.9.1'),
        @Grab(group = 'com.google.guava', module = 'guava', version = '30.1.1-jre'),
        @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
        @Grab(group='org.jsoup', module='jsoup', version='1.13.1')
])
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import fr.spironet.slackbot.*
import fr.spironet.slackbot.scheduled.JiraRssScheduledService
import fr.spironet.slackbot.scheduled.JiraScheduledService
import org.slf4j.LoggerFactory

LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()
for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
      log.setLevel(Level.INFO)
}

def botToken = System.getenv("BOT_TOKEN")
SlackSession session = SlackSessionFactory.createWebSocketSlackSession(botToken)
session.connect()

def jiraListener = new JiraListener()

session.addMessagePostedListener(new DefaultListener())
session.addMessagePostedListener(jiraListener)
session.addMessagePostedListener(new JenkinsListener())
session.addMessagePostedListener(new OdooListener())
session.addMessagePostedListener(new GithubListener())
session.addMessagePostedListener(new TempoListener())

JiraScheduledService jiraService = new JiraScheduledService(session, jiraListener.issueService)
jiraService.startAsync()
JiraRssScheduledService jiraRssService = new JiraRssScheduledService(session)
jiraRssService.startAsync()
