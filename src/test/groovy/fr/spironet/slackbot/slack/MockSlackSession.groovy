package fr.spironet.slackbot.slack

class MockSlackSession {

    def messages = []
    def files = []

    def reset() { this.messages = [] }

    def findUserByEmail(def botOwnerEmail) {
        return new Object()
    }

    def sendMessageToUser(def user, def message) {
        messages << message
    }

    def sendMessage(def channel, def message) {
        messages << message
    }
    def sendFile(def channel, def file, def filename) {
        files << filename
    }

    def messageToUserSent() {
        return messages.size > 0
    }
    def fileSent() {
        return files.size > 0
    }

    def getChannels() {
        [
               new MockSlackSession.MockChannel()
        ]
    }

    public class MockChannel {
        def direct = true
        def MockChannel() {}

        def getMembers() {
            [ new MockSlackSession.MockMember("pierre.mauduit@example.com" )]
        }
    }

    public static class MockMember {
        def userMail
        def MockMember(def userMail) {
            this.userMail = userMail
        }
    }
}