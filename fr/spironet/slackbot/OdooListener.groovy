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
    Usage: !odoo <today|week|ts>
    today|week: Returns time passed on the current day / week.
    ts: Displays a summary of the attendances suitable to fill in my timesheet.
    """

    def oeExecutor
    def scheme
    def host
    def port
    def username
    def password
    def db

    public OdooListener() {
        scheme   = System.getenv("ODOO_SCHEME")
        host     = System.getenv("ODOO_HOST")
        port     = System.getenv("ODOO_PORT") as int
        username = System.getenv("ODOO_USERNAME")
        password = System.getenv("ODOO_PASSWORD")
        db       = System.getenv("ODOO_DB")

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
          else if (arg == "week") {
            session.sendMessage(channelOnWhichMessageWasPosted,
                    totalTimeWeek()
            )
            return
          }
          else if (arg == "ts") {
            session.sendMessage(channelOnWhichMessageWasPosted,
                    timeSheet()
            )
            return
          }

        } catch (Exception e) {
          logger.error("Error occured", e)
          if (e.getMessage().contains("Odoo Session Expired")) {
             this.oeExecutor.logout()
             this.oeExecutor = OeExecutor.getInstance(scheme, host, port, db, username, password)
             // retry
             onEvent(event, session)
             return
          }
          session.sendMessage(channelOnWhichMessageWasPosted,
            new SlackPreparedMessage.Builder().withMessage(usage).build()
          )
        }
      }
    }
   private def timeSheet() {
       Calendar cal = Calendar.getInstance()
       Date now = cal.getTime()
       cal.add(Calendar.DAY_OF_YEAR, -10)
       Date lastWeek = cal.getTime()

       SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
       SimpleDateFormat fromOdooDateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

       Object[] domain = [
           [ "check_in", ">=", format.format(lastWeek) + "T00:00:00.0Z"],
           [ "check_in", "<=", format.format(now)   + "T00:00:00.0Z"]
         ]

       Map<String, Object>[] ret = oeExecutor.searchRead(OeModel.HR_ATTENDANCE.getName(),
           Arrays.asList(domain), (Integer) null, "check_in","check_out")

       SimpleDateFormat outputOdooFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
       outputOdooFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

       long minutes = 0
       def  perDay = [:]
       def currentDay = null
       for (int i = 0 ; i < ret.length ; i ++) {
         Map current = (Map) ret[i]
           Object o = current.get("check_out")
           Date cIn  = outputOdooFormat.parse(current.get("check_in").toString().replaceAll("\"", ""))
           def nd = format.format(cIn)
           if (currentDay == null) {
             currentDay = nd
           }
           if (nd != currentDay) {
             perDay[currentDay] = minutes
             currentDay = nd
             minutes = 0
           }
           Date cOut
           if (! current.get("check_out").equals("false")) {
             cOut = outputOdooFormat.parse(current.get("check_out").toString().replaceAll("\"", ""))
           } else {
             cOut = new Date()
           }
         minutes += ChronoUnit.MINUTES.between(cIn.toInstant(), cOut.toInstant())
       }
      def retstr = ""
      perDay.reverseEach { k,v ->
        def h = v / 60 as Integer
        def m = v % 60 as Integer
        def timeSpent = String.format("%02d:%02d", h, m)
        retstr += "${k}: ${timeSpent}\n"
      }
      return "Attendances on Odoo for the last 8 days:\n```${retstr}```"
   }


   private def totalTimeWeek() {
       Calendar calendar = Calendar.getInstance()
       calendar.add(Calendar.DAY_OF_YEAR, 1)
       Date tomorrow = calendar.getTime()

       def cal = Calendar.instance
       while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
           cal.add(Calendar.DAY_OF_WEEK, -1)
       }
       Date lastMonday = cal.time

       SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
       SimpleDateFormat fromOdooDateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

       Object[] domain = [
           [ "check_in", ">=", format.format(lastMonday) + "T00:00:00.0Z"],
           [ "check_in", "<=", format.format(tomorrow)   + "T00:00:00.0Z"]
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
      def progress = 100 * minutes / 2310 as float
      progress /= 5 as int
      if (progress > 20) progress = 20
      return String.format("done %02d:%02d over 38:30\n```\n[${"▓".multiply(progress)}${" ".multiply(20 - progress)}] ```\n", minutes / 60 as Integer, minutes % 60 as Integer)
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
      def progress = 100 * minutes / 462 as float
      progress /= 5 as int
      if (progress > 20) progress = 20

      return String.format("done %02d:%02d over 07:42\n```\n[${"▓".multiply(progress)}${" ".multiply(20 - progress)}] ```\n", minutes / 60 as Integer, minutes % 60 as Integer)
   }
}
