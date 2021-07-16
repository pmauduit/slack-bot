package fr.spironet.slackbot.scheduled


import com.ullink.slack.simpleslackapi.SlackSession
import fr.spironet.slackbot.webbrowser.WebBrowser
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class KibanaJob implements Job
{
    static final def SLACK_SESSION_ID = "SLACK_SESSION_IDENTIFIER"

    private final static Logger logger = LoggerFactory.getLogger(KibanaJob.class)

    @Override
    void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        def ctx = jobExecutionContext.getScheduler().getContext()
        def slackSession = (SlackSession) ctx.get(KibanaJob.SLACK_SESSION_ID)
        if (slackSession == null) {
            logger.error("Unable to get a hand on the slack session, stopping execution")
            return
        }
        def wb = new WebBrowser()
        def kibanaScreenshot = wb.visitKibanaDashboard()

        if (kibanaScreenshot?.size() > 0) {
            def kibanaChannel = System.getenv("KIBANA_CHANNEL_TO_SEND_DASHBOARD")
            def chan = slackSession.findChannelByName(kibanaChannel)
            if (chan == null) {
                logger.error("Unable to find the channel where to post the dashboard")
                return
            }
/*            slackSession.sendFile(chan,
                    kibanaScreenshot,
                    "météo_des_plateformes_georchestra")*/
            // for now, send me the file, let's see if having it in the
            // expected channel is acceptable
            slackSession.sendFileToUser(
                    slackSession.findUserByEmail("pierre.mauduit@camptocamp.com"),
                    kibanaScreenshot,
                    "météo_des_plateformes_georchestra")
        }

    }
}

