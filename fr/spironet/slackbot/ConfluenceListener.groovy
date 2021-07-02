package fr.spironet.slackbot

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
        return new SlackPreparedMessage.Builder().withMessage(this.usage).build()
    }

    SlackPreparedMessage search(def types, def topics) {
        def typesIn = types.collect{ "\"${it}\""}.join(",")
        def topicsFilter = "label = " + topics.collect{ "\"${it}\""}.join(" AND label = ")

        def cql = "type IN (${typesIn}) AND ${topicsFilter}"
        cql = "cql=" + java.net.URLEncoder.encode(cql, "UTF-8")
        cql += "&limit=10"
        def http = new RESTClient(this.confluenceServerUrl)
        def authorizationHeader = "Basic " + "${this.confluenceUsername}:${this.confluencePassword}".bytes.encodeBase64()
        def response = http.get(path:  "/confluence/rest/api/search",
                queryString: cql,
        headers: [Authorization: authorizationHeader])

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
        return new SlackPreparedMessage.Builder().withMessage(ret).build()
    }


    public ConfluenceListener() {
        this.confluenceServerUrl = System.getenv("CONFLUENCE_SERVER_URL")
        def propFile = System.getenv("JIRA_CLIENT_PROPERTY_FILE")
        def jiraProps = new Properties()
        new File(propFile).withInputStream {
            jiraProps.load(it)
        }
        this.confluenceUsername = jiraProps['jira.user.id']
        this.confluencePassword= jiraProps['jira.user.pwd']
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

    // Test
//    public static void main(String[] args) {
//        // usage
//        def tested = new ConfluenceListener()
//        def message = tested.processCommand("!confluence usage")
//        assert message.toString().contains("Usage: ")
//
//        // garbage
//        message = tested.processCommand("!confluence aaaaa")
//        assert message.toString().contains("Usage: ")
//
//        message = tested.processCommand("!confluence search georchestra lopocs")
//        assert message.toString().contains("Here are")
//
//        message = tested.processCommand("!confluence blogs geograndest")
//        assert message.toString().contains("Here are")
//    }
}
