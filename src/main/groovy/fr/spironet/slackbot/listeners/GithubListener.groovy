package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.listeners.JiraListener
import groovyx.net.http.RESTClient
import org.kohsuke.github.GitHub
import org.kohsuke.github.GHIssueState

import org.slf4j.LoggerFactory
import org.slf4j.Logger

class GithubListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(JiraListener.class)

    def gh_token
    def http

    def usage = """
    Usage: !github <command> <args...>
    Where command can be:
    * prs <repository>: lists the opened PRs on the given repository
    * find topic [topics...]: lists the repositories which relate to the given topics
    examples:
    * `!github prs georchestra-rennes-configuration` will return the opened PRs on `camptocamp/georchestra-rennes-configuration`
    * `!github prs geor` will return the opened PRs on each c2c georchstra repositories
    * `!github find rennes terraform` will return the git repositories related to the rennes project, having also the topic 'terraform'
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
       http = new RESTClient("https://api.github.com")
       gh_token = System.getenv("GITHUB_TOKEN")
    }

    private def getOpenedPrsFromGithub(def gh, def repo) {
      def curRepo = gh.getRepository("camptocamp/${repo}")
      return curRepo.getPullRequests(GHIssueState.OPEN)

    }

    private SlackPreparedMessage openedGeorchestraPrs() {
      def issues = [:]
      def gh = GitHub.connectUsingOAuth(gh_token)

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
                  ret += ":git-pull-request: List of opened PR on repository *<https://github.com/camptocamp/${k}|${k}>*:\n"
                      v.each {
                          ret += "• *<${it.getHtmlUrl()}|${it.getTitle()}>* - (author: ${it.getUser().getLogin()})\n"
                      }
              }
              ret += "\n"
          }
      }
      return SlackPreparedMessage.builder().message(ret).build()
    }

    private SlackPreparedMessage openedPrs(def repo) {
      def ret = "List of opened PR on repository ${repo}:\n"

      def gh = GitHub.connectUsingOAuth(gh_token)
      def prs = getOpenedPrsFromGithub(gh, repo)
      if (prs.size() == 0) {
        ret += "*none*\n"
      } else {
        prs.each { pr ->
           ret += "• *<${pr.getHtmlUrl()}|${pr.getTitle()}>* - (author: ${pr.getUser().getLogin()})\n"
        }
      }
      return SlackPreparedMessage.builder().message(ret).build()
    }

    def doGithubSearch(def query) {
      http.get(path: "/search/repositories",
              queryString: query,
              headers: ["Authorization": "Bearer ${this.gh_token}",
                        "User-Agent": "groovyx.net.http.RESTClient"])
    }

    SlackPreparedMessage findRepositories(def topics) {
      def topicsQuery = topics.collect {
        "topic:${it}"
      }.join('+')

      def actualQuery = String.format("q=org:camptocamp+${topicsQuery}")
      def response = doGithubSearch(actualQuery)
      def ret = ""
      if (response.data.total_count == 0) {
        ret = ":ledger: No github repository found in Camptocamp with topic *${topics}*\n"
      }
      else {
        ret = ":ledger: Here are the github repositories in the Camptocamp organization with topic ${topics} *(${response.data.total_count})*\n"
        response.data.items.each {
          def urlToIssues  = "${it.html_url}/issues"
          def urlToPrs     = "${it.html_url}/pulls"
          def urlToActions = "${it.html_url}/actions"
          ret += "• *<${it.html_url}|${it.full_name}>* - ${it.description ? it.description : "_No description_"} |" +
                  " *<${urlToIssues}|issues>* | *<${urlToPrs}|pull-requests>* | *<${urlToActions}|Github-Actions>*\n"
        }
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
          } else if (args[0] == "find") {
            def topics = args[1..-1]
            session.sendMessage(channelOnWhichMessageWasPosted, findRepositories(topics))
          }
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            SlackPreparedMessage.builder().message(usage).build()
          )
        }
      }
    }
}
