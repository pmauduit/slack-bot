package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import org.slf4j.LoggerFactory
import org.slf4j.Logger

/**
 * A simple default listener, just dumping infos onto stdout
 */
class DefaultListener implements SlackMessagePostedListener  {

    private final static Logger logger = LoggerFactory.getLogger(DefaultListener.class)

    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
      SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
      String messageContent = event.getMessageContent()
      SlackUser messageSender = event.getSender()
      logger.info("${channelOnWhichMessageWasPosted.getName()} @${messageSender.getUserName()}: \"${messageContent}\"")
    }

}
