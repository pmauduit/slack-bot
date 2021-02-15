package fr.spironet.slackbot

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import org.kohsuke.github.GitHub
import org.kohsuke.github.GHIssueState

import org.slf4j.LoggerFactory
import org.slf4j.Logger

class GithubListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(JiraListener.class)

    def gh_login
    def gh_password

    def usage = """
    Usage: !github <command> <args...>
    Where command can be:
    * prs <repository>: lists the opened PRs on the given repository
    examples:
    * `!github prs georchestra-rennes-configuration` will return the opened PRs on `camptocamp/georchestra-rennes-configuration`
    * `!github prs geor` will return the opened PRs on each c2c georchstra repositories
    """

    def  geor_repos = [
        "terraform-georchestra",
        "georchestra-rennes-configuration",
        "georchestra-pigma-configuration",
        "georchestra-georhena-configuration",
        "georchestra-grandest-configuration",
        "georchestra-hauteloire-ansible",
        "georchestra-hauteloire-configuration",
        "georchestra-hauteloire-docker",
        "georchestra-drealcorse-ansible",
        "georchestra-drealcorse-configuration",
        "georchestra-drealcorse-docker",
        "terraform-georchestra",
        "terraform-rennesmetropole"
    ]

    public GithubListener() {
       gh_login = System.getenv("GITHUB_USERNAME")
       gh_password = System.getenv("GITHUB_PASSWORD")
    }

    private def getOpenedPrsFromGithub(def gh, def repo) {
      def curRepo = gh.getRepository("camptocamp/${repo}")
      return curRepo.getPullRequests(GHIssueState.OPEN)

    }

    private SlackPreparedMessage openedGeorchestraPrs() {
      def issues = [:]
      def gh = GitHub.connect(gh_login, gh_password)

      geor_repos.each {
        issues[it] = getOpenedPrsFromGithub(gh, it)
      }

      def ret = ""
      def totalIssues = issues.collect { it.value.size()  }.sum()
      if (totalIssues == 0) {
        ret = "*No opened PRs on the georchestra repositories as for now.*"
      } else {
          issues.each { k,v ->
              if (v.size() > 0) {
                  ret += "List of opened PR on repository ${k}:\n"
                      v.each {
                          ret += "* ${it.getTitle()} - ${it.getHtmlUrl()} (author: ${it.getUser().getLogin()})\n"
                      }
              }
              ret += "\n"
          }
      }
      return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }

    private SlackPreparedMessage openedPrs(def repo) {
      def ret = "List of opened PR on repository ${repo}:\n"

      def gh = GitHub.connect(gh_login, gh_password)
      def prs = getOpenedPrsFromGithub(gh, repo)
      if (prs.size() == 0) {
        ret += "*none*\n"
      } else {
        prs.each { pr ->
           ret += "* ${pr.getTitle()} - ${pr.getHtmlUrl()} (author: ${pr.getUser().getLogin()})\n"
        }
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

      if (messageContent.contains("!github")) {
        try {
          def match = messageContent =~ /\!github (.*)$/
          def args = match[0][1].split(" ")
          if (args[0] == "prs") {
                def repository = args[1]
                if (repository == "geor") {
                  session.sendMessage(channelOnWhichMessageWasPosted, openedGeorchestraPrs())
                } else {
                  session.sendMessage(channelOnWhichMessageWasPosted, openedPrs(repository))
                }
          }
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(usage).build()
          )
        }
      }
    }
}
