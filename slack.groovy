@Grapes(
    @Grab(group='com.ullink.slack', module='simpleslackapi', version='1.2.0')
)

import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackAttachment

def botToken = System.getenv("BOT_TOKEN")
if (System.getenv("BOT_TOKEN") != "notset") {
    SlackSession session = SlackSessionFactory.createWebSocketSlackSession(botToken)
    session.connect()
    def channelName = System.getenv("CHANNEL_NAME") != null ? System.getenv("CHANNEL_NAME") : "gs-georchestra"
    def channel = session.findChannelByName("gs-georchestra")
    // need to set FAILURE as an env variable to explicitely a red ribbon
    def failure = System.getenv("FAILURE")
    def color = ((failure != null) && (failure.toBoolean())) ? "#d92d2d" : "#36a64f"
    def rawMsg = System.getenv("MESSAGE")
    def rawDesc = System.getenv("RAWDESC")
    def url = System.getenv("URL")

    def attachment = new SlackAttachment()
    attachment.setColor(color)
    if (failure != null) {
      attachment.setTitle(rawMsg)
    } else {
      attachment.setTitle(rawMsg)
    }
    if (url != null) {
      attachment.setTitleLink(url)
    }
    attachment.setText(rawDesc)

    def preparedMessage = new SlackPreparedMessage.Builder()
                .addAttachment(attachment)
                .build()

    session.sendMessage(channel, preparedMessage)
}

System.exit(0)
