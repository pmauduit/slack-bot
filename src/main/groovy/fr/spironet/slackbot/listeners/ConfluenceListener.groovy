package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.jira.IssueDetailsResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ConfluenceListener  implements SlackMessagePostedListener {
    private final static Logger logger = LoggerFactory.getLogger(ConfluenceListener.class)

    private IssueDetailsResolver issueResolver

    ConfluenceListener() {
        this.issueResolver = new IssueDetailsResolver()
    }

    ConfluenceListener(def jiraPropsFile, confluenceServerUrl, githubToken) {
        this.issueResolver = new IssueDetailsResolver(jiraPropsFile, confluenceServerUrl, githubToken)
    }

    private def usage = """
    Usage: `!confluence (usage|blogs tag [tags...]|pages tag [tags...]|search tag [tags...])`
    • usage: shows this help message
    • blogs: search only the blogs tagged with the given tags as arguments
    • pages: search only the pages tagged with the given tags as arguments
    • search: search given the tags given as arguments
    Example: `!confluence search georchestra lopocs`
    
    _Note: Only the first 10 results are returned_.
    """

    SlackPreparedMessage usage() {
        return SlackPreparedMessage.builder().message(this.usage).build()
    }

    SlackPreparedMessage search(def types, def topics) {
        def response  = this.issueResolver.searchConfluenceDocuments(types, topics)

        def ret = ""
        if (response.results.size() == 0) {
            ret = ":ledger: No confluence documents found for topic *${topics}*\n"
        }
        else {
            ret = ":ledger: Here are the confluence documents found for topic ${topics} *(${response.results.size} / ${response.totalSize})*\n"
            response.results.each {
                def contentType = it.content.type
                def space = "<${this.issueResolver.confluenceUrl}/confluence${it.resultGlobalContainer.displayUrl}|${it.resultGlobalContainer.title}>"
                ret += "• *<${this.issueResolver.confluenceUrl}/confluence${it.url}|${it.title}>* - ${contentType} in space ${space}\n"
            }
            if (response.totalSize > response.results.size()) {
                ret += "*partial results returned, use the <${this.issueResolver.confluenceUrl}|confluence search tool> to find the other ones.*"
            }
        }
        return SlackPreparedMessage.builder().message(ret).build()
    }

    def processCommand(def message) {
        try {
            def match = message =~ /\!confluence (\S+)+/
            def command = match[0][1]
            if (command == "usage") {
                return usage()
            }
            def topics = message.split(/\s+/)[2..-1]
            if (command == "blogs") {
                return search(["blogpost"], topics)
            } else if (command == "pages") {
                return search(["page"], topics)
            } else if (command == "search") {
                return search(["page", "blogpost"], topics)
            } else {
                return usage()
            }
        } catch (def e) {
            logger.error("Error occurred processing the command, returning usage()", e)
            return usage()
        }
    }
    @Override
    void onEvent(SlackMessagePosted event, SlackSession slackSession) {
        SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
        String messageContent = event.getMessageContent()
        SlackUser messageSender = event.getSender()

        if (messageSender.getUserName() == "georchestracicd") {
            return
        }

        if (messageContent.contains("!confluence")) {
            def message = processCommand(messageContent)
            slackSession.sendMessage(channelOnWhichMessageWasPosted, message)
        }
    }
}
