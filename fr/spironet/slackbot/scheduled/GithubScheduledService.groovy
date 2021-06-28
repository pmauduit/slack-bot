    package fr.spironet.slackbot.scheduled

    import com.google.common.cache.CacheBuilder
    import com.google.common.util.concurrent.AbstractScheduledService
    import com.ullink.slack.simpleslackapi.SlackPreparedMessage
    import fr.spironet.slackbot.github.DefaultEventFilter
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
        private def eventFilter = new DefaultEventFilter()

        private final static Logger logger = LoggerFactory.getLogger(GithubScheduledService.class)

        private def notificationMap = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(4, TimeUnit.HOURS)
                .build()

        public GithubScheduledService(def slackSession) {
            this.slackSession = slackSession
            def filterCls = Class.forName(System.getenv("GITHUB_EVENT_FILTER_CLASS"))
            this.eventFilter = filterCls.newInstance()
        }

        @Override
        protected void runOneIteration() throws Exception {
            def botOwner = slackSession.findUserByEmail(this.botOwnerEmail)

            if (botOwner == null) {
                logger.info("botOwner not found, skipping iteration")
                return
            }
            try {
                this.callGithubReceivedEventApiForUser(this.githubWatchedUser, botOwner)
            } catch (Exception e) {
                logger.error("error occured while getting the notifications from Github", e)
            }
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedRateSchedule(0, 2, TimeUnit.MINUTES)
        }

        private def notifyEvent(def event, def botOwner) {
            def phrase = ""
            if (event.type == "PushEvent") {
                phrase = ":arrow_up: ${event.actor.login} pushed ${event.payload.size} commits onto <https://github.com/${event.repo.name}|${event.repo.name}> in ${event.payload.ref}\n"
            }
            else if (event.type == "PullRequestEvent") {
                // url of the PR will be in event.payload.pull_request.url
                phrase = ":git-pull-request: ${event.actor.login} ${event.payload.action} <https://github.com/${event.repo.name}/pull/${event.payload.number}|PR #${event.payload.number}> onto <https://github.com/${event.repo.name}|${event.repo.name}>"
            }
            else if (event.type == "CreateEvent") {
                if (event.payload.ref_type == "repository")
                    phrase = ":factory: ${event.actor.login} created ${event.payload.ref_type} <https://github.com/${event.repo.name}|${event.repo.name}>"
                else
                    phrase = ":git-compare: ${event.actor.login} created ${event.payload.ref_type} ${event.payload.ref} onto <https://github.com/${event.repo.name}|${event.repo.name}>"
            }
            else if (event.type == "DeleteEvent") {
                if (event.payload.ref_type == "repository")
                    phrase = ":x: ${event.actor.login} deleted ${event.payload.ref_type} <https://github.com/${event.repo.name}|${event.repo.name}>"
                else
                    phrase = ":x: ${event.actor.login} deleted ${event.payload.ref_type} ${event.payload.ref} onto <https://github.com/${event.repo.name}|${event.repo.name}>"
            }
            else if(event.type == "IssuesEvent") {
                // url of the issue into event.payload.issue.url
                // repository url into event.payload.issue.repository_url
                phrase = ":spiral_note_pad: ${event.actor.login} ${event.payload.action} issue <${event.payload.issue.url}|#${event.payload.issue.number}> onto <https://github.com/${event.repo.name}|${event.repo.name}>:\n"
                phrase += "```${event.payload.issue.title}```"
            }
            else if (event.type == "IssueCommentEvent") {
                phrase = ":spiral_note_pad: ${event.actor.login} ${event.payload.action} a comment on issue <https://github.com/${event.repo.name}/issues/${event.payload.issue.number}|${event.repo.name}#${event.payload.issue.number}>:\n"
                phrase+= "```${event.payload.comment.body}```\n"
            }
            else if (event.type == "PullRequestReviewCommentEvent") {
                phrase = ":git-pull-request: ${event.actor.login} ${event.payload.action} a comment on PR <https://github.com/${event.repo.name}/pull/${event.payload.pull_request.number}|${event.repo.name}#${event.payload.pull_request.number}>:\n"
                phrase+= "```${event.payload.comment.body}```\n"
            }
            else if (event.type == "GollumEvent") {
                def actorLogin = event.actor.login
                def repoName = event.repo.name
                event.payload.pages.each { p ->
                    phrase += ":page_facing_up: ${actorLogin} ${p.action} Wiki page *<${p.html_url}|${p.page_name}>* on *${repoName}*\n"
                }
            }
            else {
                phrase = ":interrobang: I don't know how to handle github events of type '${event.type}' yet, see <https://docs.github.com/en/developers/webhooks-and-events/events/github-event-types|github documentation>"
            }
            def msg = new SlackPreparedMessage.Builder().withMessage(phrase).build()

            this.slackSession.sendMessageToUser(botOwner, msg)
        }

        def callGithubReceivedEventApiForUser(def user, def botOwner) {
            def actualPath = String.format("/users/%s/received_events", user)
            def http = new RESTClient("https://api.github.com")
            def response = http.get(path: actualPath,
                    headers: ["Authorization": "Bearer ${this.githubToken}",
                              "User-Agent": "groovyx.net.http.RESTClient"])
            response.data.each {
                if (this.eventFilter.doFilter(it)) {
                    return
                }
                // each event have an unique id (it.id)
                // skip the event if already in the notificationMap
                if (this.notificationMap.getIfPresent(it.id)) {
                    return
                }
                // date of the event is it.created_at
                def calendar = Instant.parse(it.created_at).toCalendar()
                def eventLocalDt = LocalDateTime.ofInstant(calendar.toInstant(), zid)
                def nowMinus2Hours = LocalDateTime.now().minusHours(2)
                def expired =  (eventLocalDt < nowMinus2Hours)
                if (expired) {
                    return
                }
                // else send the notification to the bot owner
                this.notifyEvent(it, botOwner)
                this.notificationMap.put(it.id, it.id)
            }
        }
    }
