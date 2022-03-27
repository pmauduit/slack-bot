package fr.spironet.slackbot.slack

class SlackWorkaround {

    public static def findPrivateMessageChannel(def session, def ownerEmail) {
        return session.getChannels().find {
            it.direct == true &&
                    it.getMembers().find { sl -> sl.userMail == ownerEmail } != null
        }
    }
}
