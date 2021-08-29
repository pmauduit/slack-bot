package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import groovyx.net.http.RESTClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ConfluenceListener  implements SlackMessagePostedListener {
    private final static Logger logger = LoggerFactory.getLogger(ConfluenceListener.class)

    private def confluenceServerUrl
    private def confluenceUsername
    private def confluencePassword

    private def http

    private def usage = """
    Usage: `!confluence (usage|blogs tag [tags...]|pages tag [tags...]|search tag [tags...])`
    • usage: shows this help message
    • blogs: search only the blogs tagged with the given tags as arguments
    • pages: search only the pages tagged with the given tags as arguments
    • search: search given the tags given as arguments
    Example: `!confluence search georchestra lopocs`
    
    _Note: Only the first 10 results are returned_.
    """

    public ConfluenceListener() {
        def propFile = System.getenv("JIRA_CLIENT_PROPERTY_FILE")
        if (propFile == null) {
            throw new RuntimeException("JIRA_CLIENT_PROPERTY_FILE must be set")
        }
        load(propFile)
    }

    public ConfluenceListener(def path) {
        load(path)
    }

    def load(def path) {

        def jiraProps = new Properties()
        new File(path).withInputStream {
            jiraProps.load(it)
        }
        this.confluenceServerUrl = System.getenv("CONFLUENCE_SERVER_URL")
        this.confluenceUsername = jiraProps['jira.user.id']
        this.confluencePassword= jiraProps['jira.user.pwd']
        this.http = new RESTClient(this.confluenceServerUrl)
    }


    def confluenceSearch(def cql, def authorizationHeader) {
        http.get(path:  "/confluence/rest/api/search",
                queryString: cql,
                headers: [Authorization: authorizationHeader])
    }

    SlackPreparedMessage usage() {
        return SlackPreparedMessage.builder().message(this.usage).build()
    }

    SlackPreparedMessage search(def types, def topics) {
        def typesIn = types.collect{ "\"${it}\""}.join(",")
        def topicsFilter = "label = " + topics.collect{ "\"${it}\""}.join(" AND label = ")

        def cql = "type IN (${typesIn}) AND ${topicsFilter}"
        cql = "cql=" + java.net.URLEncoder.encode(cql, "UTF-8")
        cql += "&limit=10"
        def authorizationHeader = "Basic " + "${this.confluenceUsername}:${this.confluencePassword}".bytes.encodeBase64()
        def response = confluenceSearch(cql, authorizationHeader)

        def ret = ""
        if (response.data.size == 0) {
            ret = ":ledger: No confluence documents found for topic *${topics}*\n"
        }
        else {
            ret = ":ledger: Here are the confluence documents found for topic ${topics} *(${response.data.size} / ${response.data.totalSize})*\n"
            response.data.results.each {
                def contentType = it.content.type
                def space = "<${this.confluenceServerUrl}/confluence${it.resultGlobalContainer.displayUrl}|${it.resultGlobalContainer.title}>"
                ret += "• *<${this.confluenceServerUrl}/confluence${it.url}|${it.title}>* - ${contentType} in space ${space}\n"
            }
            if (response.data.totalSize > response.data.size) {
                ret += "*partial results returned, use the <${this.confluenceServerUrl}|confluence search tool> to find the other ones.*"
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
