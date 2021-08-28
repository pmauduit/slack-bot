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

    /**
     * Pretty-print a number of seconds to HH:mm:ss format.
     * Previous strategy was to use a calendar adding the number of seconds to epoch
     * but this was not satisfaying, as it was zeroed after 24h, and the number of days
     * was discarded. Also displaying "1 day x hours ..." is misleading, as a day of
     * work at C2C is 7h42m, not 24h.
     *
     * @param totalSeconds
     * @return a formatted string following  the "HH:mm:ss"
     */
    private def prettyPrintSeconds(def totalSeconds) {
      def numberOfHours = (totalSeconds / 3600) as int
      def numberOfMinutes = ((totalSeconds - (numberOfHours * 3600)) / 60) as int
      def numberOfSeconds = totalSeconds % 60

      return "${numberOfHours}:${numberOfMinutes.toString().padLeft(2,"0")}:${numberOfSeconds.toString().padLeft(2, "0")}"
    }

  /**
   * Returns the issues for the user's mail passed as argument.
   *
   * @param slackUserMail the user's email who one want the issues for.
   * @return a slack message with the issues (or a message telling that no issues are opened so far).
   */
    private SlackPreparedMessage myIssues(def slackUserMail) {
      def jql = "resolution = Unresolved AND assignee = '${slackUserMail}' ORDER BY priority DESC, updated DESC"
      def issues = issueService.getIssuesFromQuery(jql)
      if (issues.issues.size() == 0) {
          return SlackPreparedMessage.builder().message("No issues for this user").build()
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
      return SlackPreparedMessage.builder().message(ret).build()
    }

  /**
   * Prepares a slack message with the issues currently opened and displayed as highest prio on
   * our grafana dashboard.
   *
   * @return a slack message with the issues (or with no issue if there are not).
   */
  private SlackPreparedMessage issuesMonitoring() {
      // Same as filter here: https://jira.camptocamp.com/issues/?filter=12612
      def jql = "project = GEO AND priority = Highest AND created >= -24h AND NOT status = Resolved"
      def issues = issueService.getIssuesFromQuery(jql)
      if (issues.issues.size() == 0)
        return SlackPreparedMessage.builder().
                message(":warning: _There are no issue on the monitoring screen currently._").build()
      def ret = ":warning: Issues currently reported on the monitoring screen *(${issues.issues.size()})*:\n"
      issues.issues.each {
        def currentOrg = getRelatedOrg(it)
        ret += "• *<${this.jiraUrl}/browse/${it.key}|${it.key}>*"
        if (currentOrg != null) {
          ret += " - :office: ${currentOrg}"
        }
        ret += " - ${it.fields.summary}\n"
      }
      return SlackPreparedMessage.builder().message(ret).build()
    }

    /**
     * Prepares a slack message with the issues currently opened in the support queue, since
     * the begining of the current week.
     *
     * @return a slack message with the issues (or with no issue if there are not).
     */
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
      return SlackPreparedMessage.builder().message(ret).build()
    }

  /**
   * Gets the worklog entries for the issue given as argument.
   *
   * Note: C2C JIRA instance is using v2 of the JIRA API, a parameter "startedAfter"
   * is documented but in the v3.
   *
   * The same parameter does not seem to work on v2. We have no better option to grab
   * every worklog entries and filter the result from our side ...
   *
   * @param jiraIssue the JIRA issue key (or id) to get the worklog from.
   * @return the worklog entries.
   */
    private def getWorklog(def jiraIssue) {
        try {
            // lol: https://jira.atlassian.com/browse/JRACLOUD-73630
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

    /**
     * Prepares a slack message with the worklog entries summarized by users, over the last 4 weeks.
     *
     * @param jiraIssue
     * @return a slack message with the worklog summary.
     */
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
        def ret = "*:spiral_note_pad: Worklog for <${this.jiraUrl}/browse/${jiraIssue}|${jiraIssue}>* _(over the last 4 weeks)_:\n"
        timeByUsers.each {
          ret += "• ${it.name}: ${prettyPrintSeconds(it.timeSpent)}\n"
        }
        return SlackPreparedMessage.builder().message(ret).build()
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
            SlackPreparedMessage.builder().message(jiraIssueMessage).build()
          )
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            SlackPreparedMessage.builder().message(usage).build()
          )
        }
      }
    }

}
