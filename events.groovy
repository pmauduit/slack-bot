@Grapes([
    @Grab(group='com.ullink.slack', module='simpleslackapi', version='1.2.0'),
    @Grab(group='com.lesstif', module='jira-rest-api', version='0.8.3'),
    @Grab(group='com.sun.jersey', module='jersey-core', version='1.19.4'),
    @Grab(group='com.offbytwo.jenkins', module='jenkins-client', version='0.3.8'),
])

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.Appender

import fr.spironet.slackbot.*

LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()
for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
      log.setLevel(Level.INFO)
}

def botToken = System.getenv("BOT_TOKEN")
SlackSession session = SlackSessionFactory.createWebSocketSlackSession(botToken)
session.connect()

session.addMessagePostedListener(new DefaultListener())
session.addMessagePostedListener(new JiraListener())
session.addMessagePostedListener(new JenkinsListener())
