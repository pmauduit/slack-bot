package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.jira.IssueDetailsResolver
import fr.spironet.slackbot.jira.JiraRss
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat

class JiraListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(JiraListener.class)

    def issueResolver
    JiraRss jiraRss

    def jiraUser
    def jiraPassword
    def jiraUrl

    def dateFormat = new SimpleDateFormat("yyyy-MM-dd")

    def usage = """
    Usage: !jira (<jira-id>|mine|user <username>|monitoring|support|worklog <jira-id>|activity)
      mine returns your opened issues ordered by priority
      support returns the opened issues for the support since the begining of the current week
      monitoring returns the issues currently reported on the monitoring screen
      worklog <jira-id> returns the worklog summarized by users of the provided JIRA issue
      <jira-id> returns the title and the description of the given JIRA issue
      activity returns the activity from the last week, relying on our personal RSS feed

    Example: !jira GEO-2246
    """

    public JiraListener() {
      this.issueResolver = new IssueDetailsResolver()
      this.jiraRss = new JiraRss()
    }

    public JiraListener(def jiraPropsFile, def confluenceServerUrl, def ghToken) {
      this.issueResolver = new IssueDetailsResolver(jiraPropsFile, confluenceServerUrl, ghToken)
      this.jiraRss = new JiraRss(jiraPropsFile)
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

      def ppMin = numberOfMinutes.toString().padLeft(2,"0")
      def ppSec = numberOfSeconds.toString().padLeft(2, "0")
      return "${numberOfHours}:${ppMin}:${ppSec}"
    }

  /**
   * Returns the issues for the user's mail passed as argument.
   *
   * @param slackUserMail the user's email who one want the issues for.
   * @return a slack message with the issues (or a message telling that no issues are opened so far).
   */
    private SlackPreparedMessage issuesForUser(def slackUserMail) {
      def jql = "resolution = Unresolved AND assignee = '${slackUserMail}' ORDER BY priority DESC, updated DESC"
      def issues = issueResolver.searchJiraIssues(jql)
      if (issues.size() == 0) {
          return SlackPreparedMessage.builder().message("No issues for this user").build()
      }
      def ret = ":warning: Unresolved issues *(${issues.size()})*:\n"
      issues.each {
        def currentOrg = this.issueResolver.computeOrganization(it)
        ret += "• *<${this.issueResolver.jiraUrl}/browse/${it.key}|${it.key}>*"
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
      def issues = this.issueResolver.searchJiraIssues(jql)
      if (issues.size() == 0)
        return SlackPreparedMessage.builder().
                message(":warning: _There are no issue on the monitoring screen currently._").build()
      def ret = ":warning: Issues currently reported on the monitoring screen *(${issues.size()})*:\n"
      issues.each {
        def currentOrg = issueResolver.computeOrganization(it)
        ret += "• *<${this.issueResolver.jiraUrl}/browse/${it.key}|${it.key}>*"
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
      def jql = "project = GEO AND NOT status = Resolved AND created >= startOfWeek()"
      def issues = this.issueResolver.searchJiraIssues(jql)
      def ret = ":warning: Unresolved issues in the GEO support project since the begining of the week *(${issues.size()})*:\n"
      issues.each {
        def currentOrg = this.issueResolver.computeOrganization(it)
        ret += "• *<${this.issueResolver.jiraUrl}/browse/${it.key}|${it.key}>*"
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
     * Prepares a slack message with the worklog entries summarized by users, over the last 4 weeks.
     *
     * @param jiraIssue
     * @return a slack message with the worklog summary.
     */
    private SlackPreparedMessage issueWorklog(def jiraIssue) {
        def wl = this.issueResolver.loadIssueWorklog(jiraIssue, 4)
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
        def ret = "*:spiral_note_pad: Worklog for <${this.issueResolver.jiraUrl}/browse/${jiraIssue}|${jiraIssue}>* _(over the last 4 weeks)_:\n"
        timeByUsers.each {
          ret += "• ${it.name}: ${prettyPrintSeconds(it.timeSpent)}\n"
        }
        return SlackPreparedMessage.builder().message(ret).build()
    }

    SlackPreparedMessage activity() {
      def items = jiraRss.rssGetMyActivity()
      def issuesByDate = jiraRss.getIssuesWorkedOnByDate(items)

      if (issuesByDate.keySet().size() == 0)
        return SlackPreparedMessage.builder().message(":computer: No activity recorded from the JIRA RSS endpoint.").build()
      def ret = ":computer: Activity from the JIRA RSS endpoint (back to last sunday):"

      issuesByDate.each { k,v ->
          ret += "\n*• ${k}:* _${v}_\n"
      }
      return SlackPreparedMessage.builder().message(ret).build()
    }
    /**
     * Untyped version of the `onEvent()` call to allow testing.
     *
     * @param event the event object, normally of type SlackMessagePosted
     * @param session the session object, of type SlackSession
     *
     * @return void
     */
    def doOnEvent(def event, def session) {
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
                    issuesForUser(messageSender.getUserMail())
            )
            return
          }
          if (issueKey == "user") {
            def userName = messageContent =~ /\!jira \S+ (\S+)/
            userName = userName[0][1]
            session.sendMessage(channelOnWhichMessageWasPosted, issuesForUser(userName))
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
          if (issueKey == "activity") {
            session.sendMessage(channelOnWhichMessageWasPosted,
                    activity()
            )
            return
          }
          // Describe a specific issue
          def issue = issueResolver.loadIssue(issueKey)
          String jiraIssueMessage = "*Issue <${this.jiraUrl}/browse/${issueKey}|${issueKey}>*: ${issue.fields.summary}\n\n"+
                  "${issue.fields.description}\n\n"+
                  "Reported by: ${issue.fields.reporter.name}"
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

    @Override
    void onEvent(SlackMessagePosted event, SlackSession session) {
      this.doOnEvent(event, session)
    }

}
