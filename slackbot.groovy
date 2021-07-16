@Grapes([
        @Grab(group = 'com.ullink.slack', module = 'simpleslackapi', version = '1.2.0'),
        @Grab(group = 'com.lesstif', module = 'jira-rest-api', version = '0.8.3'),
        @Grab(group = 'com.sun.jersey', module = 'jersey-core', version = '1.19.4'),
        @Grab(group = 'com.offbytwo.jenkins', module = 'jenkins-client', version = '0.3.8'),
        @Grab('org.qfast.odoo-rpc:odoo-jsonrpc:1.0'),
        @Grab(group = 'org.kohsuke', module = 'github-api', version = '1.90'),
        @Grab(group = 'com.squareup.okhttp3', module = 'okhttp', version = '4.9.1'),
        @Grab(group = 'com.google.guava', module = 'guava', version = '25.0-jre'),
        @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
        @Grab(group='org.jsoup', module='jsoup', version='1.13.1'),
        @Grab(group='org.seleniumhq.selenium', module='selenium-java', version='3.141.59'),
        @Grab(group='org.quartz-scheduler', module='quartz', version='2.3.2')
])
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import fr.spironet.slackbot.*
import fr.spironet.slackbot.scheduled.*
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.LoggerFactory

import static org.quartz.CronScheduleBuilder.cronSchedule
import static org.quartz.JobBuilder.newJob
import static org.quartz.TriggerBuilder.newTrigger

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
session.addMessagePostedListener(new ConfluenceListener())
session.addMessagePostedListener(new JenkinsListener())
session.addMessagePostedListener(new OdooListener())
session.addMessagePostedListener(new GithubListener())
session.addMessagePostedListener(new TempoListener())
session.addMessagePostedListener(new GrafanaListener())
session.addMessagePostedListener(new KibanaListener())

JiraScheduledService jiraService = new JiraScheduledService(session, jiraListener.issueService)
jiraService.startAsync()
JiraRssScheduledService jiraRssService = new JiraRssScheduledService(session)
jiraRssService.startAsync()
ConfluenceRssScheduledService confluenceService = new ConfluenceRssScheduledService(session)
confluenceService.startAsync()
GithubScheduledService githubService = new GithubScheduledService(session)
githubService.startAsync()

// Prepare "cron-like" services
// kibana
def trigger = newTrigger()
        // every mondays at 10:15:00 AM
        .withSchedule(cronSchedule("0 15 10 ? * MON")
            .inTimeZone(TimeZone.getTimeZone("Europe/Paris")))
        .build()
def job = newJob(KibanaJob.class).build()
def scheduler = new StdSchedulerFactory().getScheduler()
scheduler.getContext().put(KibanaJob.SLACK_SESSION_ID, session)
scheduler.scheduleJob(job, trigger)
scheduler.start()
