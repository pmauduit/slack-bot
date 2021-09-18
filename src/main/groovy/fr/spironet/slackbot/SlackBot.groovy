package fr.spironet.slackbot

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import fr.spironet.slackbot.listeners.*
import fr.spironet.slackbot.scheduled.ConfluenceRssScheduledService
import fr.spironet.slackbot.scheduled.GithubScheduledService
import fr.spironet.slackbot.scheduled.JiraRssScheduledService
import fr.spironet.slackbot.scheduled.JiraScheduledService
import fr.spironet.slackbot.scheduled.KibanaJob
import fr.spironet.slackbot.scheduled.TempoTimesheetValidationScheduledService
import fr.spironet.slackbot.scheduled.TerraformScheduledService
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

session.addMessagePostedListener(new DefaultListener())
session.addMessagePostedListener(new JiraListener())
session.addMessagePostedListener(new ConfluenceListener())
session.addMessagePostedListener(new OdooListener())
session.addMessagePostedListener(new GithubListener())
session.addMessagePostedListener(new TempoListener())
session.addMessagePostedListener(new GrafanaListener())
session.addMessagePostedListener(new KibanaListener())

JiraScheduledService jiraService = new JiraScheduledService(session)
jiraService.startAsync()
JiraRssScheduledService jiraRssService = new JiraRssScheduledService(session)
jiraRssService.startAsync()
ConfluenceRssScheduledService confluenceService = new ConfluenceRssScheduledService(session)
confluenceService.startAsync()
GithubScheduledService githubService = new GithubScheduledService(session)
githubService.startAsync()
TerraformScheduledService terraformService = new TerraformScheduledService(session)
terraformService.startAsync()
TempoTimesheetValidationScheduledService tempoService = new TempoTimesheetValidationScheduledService(session)
tempoService.startAsync()


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
