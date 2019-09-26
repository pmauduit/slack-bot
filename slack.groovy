@Grapes(
    @Grab(group='com.ullink.slack', module='simpleslackapi', version='1.2.0')
)

import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory

def botToken = System.getenv("BOT_TOKEN")
if (System.getenv("BOT_TOKEN") != "notset") {
    SlackSession session = SlackSessionFactory.createWebSocketSlackSession(botToken)
    session.connect()
    def channel = session.findChannelByName("gs-georchestra")
    session.sendMessage(channel, System.getenv("MESSAGE"))
}

System.exit(0)
