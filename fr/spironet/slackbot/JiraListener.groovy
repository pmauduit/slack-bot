package fr.spironet.slackbot

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import com.lesstif.jira.services.ProjectService
import com.lesstif.jira.project.Project
import com.lesstif.jira.services.IssueService

import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import org.slf4j.LoggerFactory
import org.slf4j.Logger

class JiraListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(JiraListener.class)

    // Jira issue service, needed to query Jira
    def issueService

    def usage = """
    Usage: !jira (<jira-id>|mine|monitoring|worklog <jira-id>)
      mine returns your opened issues ordered by priority
      monitoring returns the issues currently reported on the monitoring screen
      worklog <jira-id> returns the worklog summarized by users of the provided JIRA issue
      <jira-id> returns the title and the description of the given JIRA issue

    Example: !jira GEO-2246
    """

    public JiraListener() {
      // This class expects a JIRA_CLIENT_PROPERTY_FILE env variable to be set
      // see env.dist at the root of the repository.
      System.setProperty("jira.client.property",System.getenv("JIRA_CLIENT_PROPERTY_FILE"))
      issueService = new IssueService()
    }

    private def getRelatedOrg(def issue) {
      try {
        issue.getFields().getCustomfield()["customfield_10900"]["name"][0]
      } catch (def _) {
        return null
      }
    }

    private SlackPreparedMessage myIssues(def slackUser) {
      def jql = "resolution = Unresolved AND assignee = '${slackUser.getUserMail()}' ORDER BY priority DESC, updated DESC"
      def issues = issueService.getIssuesFromQuery(jql)
      def ret = "Unresolved issues (${issues.issues.size()}):\n"
      issues.issues.each {
        def currentOrg = getRelatedOrg(it)
        ret += "${it.key}"
        if (currentOrg != null) {
          ret += " - ${currentOrg}"
        }
        ret += " - ${it.fields.summary} - https://jira.camptocamp.com/browse/${it.key}\n"
      }
      return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    private SlackPreparedMessage issuesMonitoring() {
      // Same as filter here: https://jira.camptocamp.com/issues/?filter=12612
      def jql = "project = GEO AND priority = Highest AND created >= -24h AND NOT status = Resolved"
      def issues = issueService.getIssuesFromQuery(jql)
      def ret = "Issues currently reported on the monitoring screen (${issues.issues.size()}):\n"
      issues.issues.each {
        ret += "${it.key} - ${it.fields.summary} - https://jira.camptocamp.com/browse/${it.key}\n"
      }
      return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    private SlackPreparedMessage issueWorklog(def jiraIssue) {
        def issue = issueService.getIssue(jiraIssue)
        def workLog = issue.getFields().getWorklog()
        def timeByUsers = [:]
        for (def wle : workLog.getWorklogs()) {
          def author = wle.getUpdateauthor().getDisplayname()
          def timeSpent = wle.getTimeSpentSeconds()
          if (timeByUsers[author] == null)
              timeByUsers[author] = 0
          timeByUsers[author] += timeSpent
        }
        def ret = "Worklog for ${jiraIssue}:\n```\n"
        timeByUsers.each { i,t ->
          // Morph seconds to hh:mm:ss
          def timeSpent =  new GregorianCalendar( 0, 0, 0, 0, 0, t, 0 ).time.format( 'HH:mm:ss' )
          ret += "${i}: ${timeSpent}\n"
        }
        ret += "```\n"
        return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
      SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
      String messageContent = event.getMessageContent()
      SlackUser messageSender = event.getSender()

      if (messageSender.getUserName() == "georchestracicd") {
        return
      }

      if (messageContent.contains("!jira")) {
        try {
          def match = messageContent =~ /\!jira (\S+)/
          def issueKey = match[0][1]
          // Listing my issues
          if (issueKey == "mine") {
            session.sendMessage(channelOnWhichMessageWasPosted,
              myIssues(messageSender)
            )
            return
          }
          if (issueKey == "monitoring") {
            session.sendMessage(channelOnWhichMessageWasPosted,
              issuesMonitoring()
            )
            return
          }
          if (issueKey == "worklog") {
            def issueKey2 = messageContent =~ /\!jira \S+ (\S+)/
            issueKey2 = issueKey2[0][1]
            session.sendMessage(channelOnWhichMessageWasPosted,
              issueWorklog(issueKey2)
            )
            return
          }
          // Describe a specific issue
          def issue = issueService.getIssue(issueKey)
          String jiraIssueMessage = "Issue ${issueKey}: ${issue.getFields().getSummary()}\n\n"+
          "${issue.getFields().getDescription()}\n\n"+
          "Reported by: ${issue.getFields().getReporter().getName()}\n"+
          "URL: https://jira.camptocamp.com/browse/${issueKey}"
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(jiraIssueMessage).build()
          )
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(usage).build()
          )
        }
      }
    }

}
