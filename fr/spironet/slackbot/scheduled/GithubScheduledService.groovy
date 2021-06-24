    package fr.spironet.slackbot.scheduled

    import com.google.common.cache.CacheBuilder
    import com.google.common.util.concurrent.AbstractScheduledService
    import com.ullink.slack.simpleslackapi.SlackPreparedMessage
    import groovyx.net.http.RESTClient
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory

    import java.time.Instant
    import java.time.LocalDateTime
    import java.time.ZoneId
    import java.util.concurrent.TimeUnit

    class GithubScheduledService extends AbstractScheduledService {

        private def slackSession
        private def zid = ZoneId.systemDefault()
        private def githubWatchedUser = System.getenv("GITHUB_WATCHED_USER")
        private def botOwnerEmail = System.getenv("BOT_OWNER_EMAIL")
        private def githubToken = System.getenv("GITHUB_TOKEN")

        private final static Logger logger = LoggerFactory.getLogger(GithubScheduledService.class)

        private def notificationMap = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(4, TimeUnit.HOURS)
                .build()

        public GithubScheduledService(def slackSession) {
            this.slackSession = slackSession
        }

        @Override
        protected void runOneIteration() throws Exception {
            def botOwner = slackSession.findUserByEmail(this.botOwnerEmail)

            if (botOwner == null) {
                logger.info("botOwner not found, skipping iteration")
                return
            }
            try {
                this.callGithubReceivedEventApiForUser(this.githubWatchedUser)
            } catch (Exception e) {
                logger.error("error occured while getting the notifications from Github", e)
            }
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedRateSchedule(0, 2, TimeUnit.MINUTES)
        }

        private def notifyEvent(def event) {
            def phrase = ""
            if (event.type == "PushEvent") {
                phrase = "${event.actor.login} pushed ${event.payload.size} commits onto ${event.repo.name} in ${event.payload.ref}:\n"
                event.payload.commits.each { commit ->
                    phrase += "\t${commit.message}\n"
                }
            } else if (event.type == "PullRequestEvent") {
                // url of the PR will be in event.payload.pull_request.url
                phrase = "${event.actor.login} ${event.payload.action} PR #${event.payload.number} onto ${event.repo.name}"
            } else if (event.type == "CreateEvent") {
                phrase = "${event.actor.login} created ${event.payload.ref_type} ${event.payload.ref} onto ${event.repo.name}"
            } else if(event.type == "IssuesEvent") {
                // url of the issue into event.payload.issue.url
                // repository url into event.payload.issue.repository_url
                phrase = "${event.actor.login} ${event.payload.action} issue ${event.payload.issue.number} onto ${event.repo.name}:\n" +
                        "\t${event.payload.issue.title}"
            } else {
                phrase = "I don't know how to handle github events of type '${event.type}' yet"
            }
            def msg = new SlackPreparedMessage.Builder().withMessage(phrase).build()
            this.slackSession.sendMessageToUser(this.botOwner, msg)
        }

        def callGithubReceivedEventApiForUser(def user) {
            def actualPath = String.format("/users/%s/received_events", user)
            def http = new RESTClient("https://api.github.com")
            def response = http.get(path: actualPath,
                    headers: ["Authorization": "Bearer ${this.githubToken}",
                              "User-Agent": "groovyx.net.http.RESTClient"])
            response.data.each {
                // each event have an unique id (it.id)
                // skip the event if already in the notificationMap
                if (this.notificationMap.getIfPresent(it.id)) {
                    return
                }
                // date of the event is it.created_at
                def calendar = Instant.parse(it.created_at).toCalendar()
                def eventLocalDt = LocalDateTime.ofInstant(calendar.toInstant(), zid)
                def expired = LocalDateTime.now().minusMinutes(5) > eventLocalDt
                if (expired) {
                    return
                }
                // else send the notification to the bot owner
                this.notifyEvent(it)
                this.notificationMap.put(it.id, it.id)
            }
        }
    }
