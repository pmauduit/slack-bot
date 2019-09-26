package fr.spironet.slackbot

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import com.offbytwo.jenkins.JenkinsServer
import com.offbytwo.jenkins.model.FolderJob


class JenkinsListener implements SlackMessagePostedListener  {

  static final def USAGE = """Usage: !jenkins <cmd> <args>
  Examples:
    !jenkins start-build cadastrapp/master
    !jenkins start-build georchestra-pigma-configuration/jenkins-18.06
  or equivalent:
    !jenkins geor-build (pigma|gge|hdf|georhena)
  """
  private def georMapping = [
    "pigma"    : ["georchestra-pigma-configuration", "jenkins-18.06"],
    "gge"      : ["georchestra-grandest-configuration", "jenkins"],
    "hdf"      : ["georchestra-geo2france-configuration", "jenkins"],
    "georhena" : ["georchestra-georhena-configuration", "jenkins-18.06"],
  ]
  private def jenkinsServer

    public JenkinsListener() {
      jenkinsServer = new JenkinsServer(new URI(System.getenv("JENKINS_URL")),
       System.getenv("JENKINS_USERNAME"),
       System.getenv("JENKINS_TOKEN"))
    }

    private void triggerBuild(def prj, def branch) {
      FolderJob multibranchRoot = new FolderJob(prj, "https://ci.camptocamp.com/job/geospatial/job/${prj}/")
      def job = jenkinsServer.getJob(multibranchRoot, branch)
      job.build()
    }

    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
      SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
      String messageContent = event.getMessageContent()
      SlackUser messageSender = event.getSender()

      // Do not react to own events
      if (messageSender.getUserName() == "georchestracicd") {
        return
      }
      if (messageContent.contains("!jenkins")) {
        println "Jenkins request detected"
        try {
          // !jenkins <command> <args>
          // !jenkins launch-build <repo/branch>
          def match = messageContent =~ /\!jenkins (\S+) (\S+)/
          def jenkinsCmd = match[0][1]

          def prj, branch

          if (jenkinsCmd == "start-build") {
              def args = match[0][2]
              prj = args.split("/")[0]
              branch = args.split("/")[1]
          } else if (jenkinsCmd == "geor-build") {
             prj = georMapping[match[0][2]][0]
             branch = georMapping[match[0][2]][1]
          }
          triggerBuild(prj, branch)
          String jenkinsMessage = "Build requested: https://ci.camptocamp.com/job/geospatial/job/${prj}/job/${branch}/"
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(jenkinsMessage).build()
          )
        } catch (Exception e) {
          println e
          e.printStackTrace()
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(USAGE).build()
          )
        }
      }
    }

}
