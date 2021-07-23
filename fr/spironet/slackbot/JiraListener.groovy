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
import groovyx.net.http.HTTPBuilder
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class JiraListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(JiraListener.class)

    // Jira issue service, needed to query Jira
    def issueService

    def jiraUser
    def jiraPassword
    def jiraUrl

    def usage = """
    Usage: !jira (<jira-id>|mine|user <username>|monitoring|support|worklog <jira-id>)
      mine returns your opened issues ordered by priority
      support returns the opened issues for the support since the begining of the current week
      monitoring returns the issues currently reported on the monitoring screen
      worklog <jira-id> returns the worklog summarized by users of the provided JIRA issue
      <jira-id> returns the title and the description of the given JIRA issue

    Example: !jira GEO-2246
    """

    public JiraListener() {
      // This class expects a JIRA_CLIENT_PROPERTY_FILE env variable to be set
      // see env.dist at the root of the repository.
      System.setProperty("jira.client.property",System.getenv("JIRA_CLIENT_PROPERTY_FILE"))
      def props = new Properties()
      // ... but we actually also need to do requests by ourselves
      // outside of the JIRA Java library used here.
      File propertiesFile = new File(System.getenv("JIRA_CLIENT_PROPERTY_FILE"))
      propertiesFile.withInputStream {
        props.load(it)
      }
      this.jiraUrl      = props.getProperty("jira.server.url")
      this.jiraUser     = props.getProperty("jira.user.id")
      this.jiraPassword = props.getProperty("jira.user.pwd")
      issueService = new IssueService()
    }

    private def getRelatedOrg(def issue) {
      try {
        issue.getFields().getCustomfield()["customfield_10900"]["name"][0]
      } catch (def _) {
        return null
      }
    }

    private SlackPreparedMessage myIssues(def slackUserMail) {
      def jql = "resolution = Unresolved AND assignee = '${slackUserMail}' ORDER BY priority DESC, updated DESC"
      def issues = issueService.getIssuesFromQuery(jql)
      if (issues.issues.size() == 0) {
          return new SlackPreparedMessage.Builder().withMessage("No issues for this user").build()
      }
      def ret = ":warning: Unresolved issues *(${issues.issues.size()})*:\n"
      issues.issues.each {
        def currentOrg = getRelatedOrg(it)
        ret += "• *<${this.jiraUrl}/browse/${it.key}|${it.key}>*"
        if (currentOrg != null) {
          ret += " - :office: *${currentOrg}*"
        }
        ret += " - ${it.fields.summary}\n"
      }
      return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    private SlackPreparedMessage issuesMonitoring() {
      // Same as filter here: https://jira.camptocamp.com/issues/?filter=12612
      def jql = "project = GEO AND priority = Highest AND created >= -24h AND NOT status = Resolved"
      def issues = issueService.getIssuesFromQuery(jql)
      if (issues.issues.size() == 0)
        return new SlackPreparedMessage.Builder().
                withMessage(":warning: _There are no issue on the monitoring screen currently._").build()
      def ret = ":warning: Issues currently reported on the monitoring screen *(${issues.issues.size()})*:\n"
      issues.issues.each {
        def currentOrg = getRelatedOrg(it)
        ret += "• *<${this.jiraUrl}/browse/${it.key}|${it.key}>*"
        if (currentOrg != null) {
          ret += " - :office: ${currentOrg}"
        }
        ret += " - ${it.fields.summary}\n"
      }
      return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    private SlackPreparedMessage issuesSupport() {
      def jql = "project = GEO and created <= now() and created  >= startOfWeek()"
      def issues = issueService.getIssuesFromQuery(jql)
      def ret = ":warning: Issues currently opened in GEO support *(${issues.issues.size()})*:\n"
      issues.issues.each {
        def currentOrg = getRelatedOrg(it)
        ret += "• *<${this.jiraUrl}/browse/${it.key}|${it.key}>*"
        if (currentOrg != null) {
          ret += " - :office: ${currentOrg}"
        }
        ret += " - ${it.fields.summary}\n"
      }
      if (issues.issues.size() <= 0) {
        ret += "*none.*\n"
      }
      return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    private def getWorklog(def jiraIssue) {
        try {
            // lol: https://jira.atlassian.com/browse/JRACLOUD-73630
            // anyway, even if documented, it seems that this startedAfter parameter
            // won't work with API v2, which is the latest available on the c2c instance ...
            // guess I'll filter dates by hand, and too bad if it stresses our servers.
            def fourWeeksAgo = use (groovy.time.TimeCategory) {
                4.weeks.ago
            }

          def wl = new HTTPBuilder(this.jiraUrl).get(path: "/rest/api/latest/issue/${jiraIssue}/worklog",
                    query: [
                            startedAfter: fourWeeksAgo
                    ],
                    headers: [
                            Authorization: "Basic " + "${this.jiraUser}:${this.jiraPassword}".bytes.encodeBase64(),
                            Accept       : "application/json"
                    ])

            return wl.worklogs.findAll {
              new java.text.SimpleDateFormat("yyyy-MM-dd").parse(it.started) > fourWeeksAgo
            }
        } catch (groovyx.net.http.HttpResponseException e) {
            return null
        }

    }

    private SlackPreparedMessage issueWorklog(def jiraIssue) {
        def wl = getWorklog(jiraIssue)
        def timeByUsers = []
        wl.each {
          def author = it.author.displayName
          def timeSpent = it.timeSpentSeconds
          if (timeByUsers.find { it.name == author } == null) {
            timeByUsers << [ name: author, timeSpent: timeSpent ]
          } else {
            timeByUsers.find { it.name == author }.timeSpent += timeSpent
          }
        }
        timeByUsers.sort { a,b -> a.timeSpent <=> b.timeSpent }
        def ret = ":spiral_note_pad: Worklog for *<${this.jiraUrl}/browse/${jiraIssue}|${jiraIssue}>* (over the last 4 weeks):\n"
        timeByUsers.each {
          // Morph seconds to hh:mm:ss
          def numberOfDays = (it.timeSpent / (3600 * 24)) as int
          def timeSpent =  new GregorianCalendar( 0, 0, 0, 0, 0, it.timeSpent, 0 )
                  .time.format("HH:mm:ss")
          ret += "• ${it.name}: ${numberOfDays} days, ${timeSpent}\n"
        }
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
              myIssues(messageSender.getUserMail())
            )
            return
          }
          if (issueKey == "user") {
             def userName = messageContent =~ /\!jira \S+ (\S+)/
             userName = userName[0][1]
             session.sendMessage(channelOnWhichMessageWasPosted, myIssues(userName))
            return
          }
          if (issueKey == "monitoring") {
            session.sendMessage(channelOnWhichMessageWasPosted,
              issuesMonitoring()
            )
            return
          }
          if (issueKey == "support") {
            session.sendMessage(channelOnWhichMessageWasPosted,
              issuesSupport()
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
          String jiraIssueMessage = "*Issue <${this.jiraUrl}/browse/${issueKey}|${issueKey}>*: ${issue.getFields().getSummary()}\n\n"+
          "${issue.getFields().getDescription()}\n\n"+
          "Reported by: ${issue.getFields().getReporter().getName()}"
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
