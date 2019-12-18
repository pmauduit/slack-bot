package fr.spironet.slackbot

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener

import org.slf4j.LoggerFactory
import org.slf4j.Logger


import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Map
import java.util.TimeZone

import com.odoo.rpc.exception.OeRpcException
import com.odoo.rpc.json.OeExecutor
import com.odoo.rpc.util.OeConst.OeModel

/**
 * Odoo integration for slack-bot.
 * Note: this requires the following module:
 * https://bitbucket.org/qfast/odoo-jsonrpc/src/master/
 *
 * This it is not available on the official maven repositories, one need
 * to setup it by hand.
 */

class OdooListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(OdooListener.class)


    def usage = """
    Usage: !odoo <today>
    Returns time passed on the current day.
    """

    def oeExecutor


    public OdooListener() {
      def scheme   = System.getenv("ODOO_SCHEME")
      def host     = System.getenv("ODOO_HOST")
      def port     = System.getenv("ODOO_PORT") as int
      def username = System.getenv("ODOO_USERNAME")
      def password = System.getenv("ODOO_PASSWORD")
      def db       = System.getenv("ODOO_DB")

      this.oeExecutor  = OeExecutor.getInstance(scheme, host, port, db, username, password)
    }

    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
      SlackChannel channelOnWhichMessageWasPosted = event.getChannel()
      String messageContent = event.getMessageContent()
      SlackUser messageSender = event.getSender()

      if (messageSender.getUserName() == "georchestracicd") {
        return
      }

      if (messageContent.contains("!odoo")) {
        try {
          def match = messageContent =~ /\!odoo (\S+)/
          def arg = match[0][1]
          // Listing my issues
          if (arg == "today") {
            session.sendMessage(channelOnWhichMessageWasPosted,
                    totalTimeToday()
            )
            return
          }
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(usage).build()
          )
        }
      }
    }

   private def totalTimeToday() {
       Calendar calendar = Calendar.getInstance()
       Date today = calendar.getTime()
       calendar.add(Calendar.DAY_OF_YEAR, 1)
       Date tomorrow = calendar.getTime()
       
       SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
       SimpleDateFormat fromOdooDateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
       
       Object[] domain = [
           [ "check_in", ">=", format.format(today) + "T00:00:00.0Z"],
           [ "check_in", "<=", format.format(tomorrow) + "T00:00:00.0Z"]
         ]
       
       Map<String, Object>[] ret = oeExecutor.searchRead(OeModel.HR_ATTENDANCE.getName(),
           Arrays.asList(domain), (Integer) null, "check_in","check_out")
       
       SimpleDateFormat outputOdooFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
       outputOdooFormat.setTimeZone(TimeZone.getTimeZone("GMT"))
       
       long minutes = 0
       
       for (int i = 0 ; i < ret.length ; i ++) {
         Map current = (Map) ret[i]
           Object o = current.get("check_out")
       
           Date cIn  = outputOdooFormat.parse(current.get("check_in").toString().replaceAll("\"", ""))
           Date cOut
           if (! current.get("check_out").equals("false")) {
             cOut = outputOdooFormat.parse(current.get("check_out").toString().replaceAll("\"", ""))
           } else {
             cOut = new Date()
           }
       
         minutes += ChronoUnit.MINUTES.between(cIn.toInstant(), cOut.toInstant())
       }
      return String.format("""```
        done %02d:%02d over 07:42
        ```""", minutes / 60 as Integer, minutes % 60 as Integer)
   }
}
