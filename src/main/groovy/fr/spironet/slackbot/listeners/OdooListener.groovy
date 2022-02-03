package fr.spironet.slackbot.listeners

import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import fr.spironet.slackbot.odoo.DisconnectedFromOdooException
import fr.spironet.slackbot.odoo.OdooClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit

/**
 * Odoo listener for slack-bot.
 */
class OdooListener implements SlackMessagePostedListener  {

  private final static Logger logger = LoggerFactory.getLogger(OdooListener.class)

    def usage = """
    :page_facing_up: Usage: !odoo <today|week|ts|presence [user]|vacations>
    • today|week: Returns time passed on the current day / week.
    • ts: Displays a summary of the attendances suitable to fill in my timesheet.
    • presence: Tries to detect if the user is signed in (note: does not work for people in other locations than Chambéry's office).
    • vacations: prints my coming validated vacations (note: works only for mine, I don't have access to others' ones).
    """

    def odooClient = new OdooClient()
    /**
     * Even if the exchanges with Odoo are managed with the OdooClient class,
     * we still need the Odoo user login, so that we are able to limit the
     * objects requested to our user.
     */
    def username = System.getenv("ODOO_USERNAME")

    OdooListener() {
        if (! odooClient.isLoggedIn()) {
            odooClient.login()
        }
    }

    OdooListener(OdooClient odooClient) {
        this.odooClient = odooClient
    }

    /**
     * Prepares a slack message listing the coming accepted vacations
     * for the user being connected onto Odoo.
     *
     * Note: being able to view leaves from another user is not possible
     * with a simple / developer account as mine, one probably need to at least
     * be project manager.
     *
     * @return a SlackPreparedMessage instance.
     */
    def vacations() {
        def plannedVacations = []
        def message = ""
        try {
            plannedVacations = odooClient.getComingLeavesForUser(this.username)
        } catch (DisconnectedFromOdooException e) {
            // then retry
            try {
                odooClient.login()
                plannedVacations = odooClient.getComingLeavesForUser(this.username)
            } catch (RuntimeException e2) {
                logger.error("Tried logging in again on Odoo, no luck, giving up", e2)
                message = ":desert_island: Unable to get the vacations for now (check the bot logs)"
                return SlackPreparedMessage.builder().message(message).build()
            }
        }
        def inFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        def outFormat = new SimpleDateFormat("yyyy-MM-dd")
        if (plannedVacations.size() > 0) {
            message = ":desert_island: Here are your currently accepted leaves *(${plannedVacations.size()})*:\n"
            plannedVacations.each {
                // Parses the date to discard the time, keeps only the 'yyyy-MM-dd' part
                def from = outFormat.format(inFormat.parse(it.date_from))
                def to   = outFormat.format(inFormat.parse(it.date_to))
                message += "• *from* ${from} *to* ${to} - ${it.name} _(${it.number_of_days} days)_\n"
            }
        } else {
            message = ":desert_island: No accepted vacations planned yet."
        }
        return SlackPreparedMessage.builder().message(message).build()
    }

    /**
     * Given a user, checks ones attendance state.
     *
     * @param user the user's login on Odoo.
     *
     * @return a SlackPreparedMessage object.
     */
    def getUserAttendanceState(def user) {
        def attendance
        def message = ""
        try {
            attendance = odooClient.getAttendanceState(user)
        } catch (DisconnectedFromOdooException _) {
            // second chance ?
            odooClient.login()
            try {
                attendance = odooClient.getAttendanceState(user)
            } catch (RuntimeException e2) {
                logger.error("Tried logging in again on Odoo, no luck, giving up", e2)
                message = ":interrobang: Unable to get the current state for the user ${user}"
                return SlackPreparedMessage.builder().message(message).build()
            }
        }
        if (attendance == null) {
            message = ":interrobang: user *${user}* not found."
        }
        else if (attendance == false) {
            message = ":interrobang: user *${user}* found, but Odoo refused to give me the attendance state."
        }
        else if (attendance == "checked_out") {
            message = ":zzz: relying on Odoo, *${user}* is currently *signed out*."
        } else {
            message = ":gear: relying on Odoo, *${user}* is currently *signed in*."
        }
        return SlackPreparedMessage.builder().message(message).build()
    }

    /**
     * Formats a string, giving the current working time since
     * the begining of the week.
     *
     * @return a String with the expected info, if no error occured.
     */
    def totalTimeWeek() {
        Calendar calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        Date tomorrow = calendar.getTime()

        def cal = Calendar.instance
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_WEEK, -1)
        }
        Date lastMonday = cal.time

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
        def from = format.format(lastMonday) + "T00:00:00.0Z"
        def to   = format.format(tomorrow)  + "T00:00:00.0Z"

        def ret = odooClient.getAttendances(this.username, from, to)

        SimpleDateFormat outputOdooFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        outputOdooFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

        long minutes = 0

        ret.each {
            def cIn = outputOdooFormat.parse(it.check_in)
            def cOut = it.check_out != false ? outputOdooFormat.parse(it.check_out) : new Date()
            minutes += ChronoUnit.MINUTES.between(cIn.toInstant(), cOut.toInstant())
        }
        def progress = 100 * minutes / 2310 as float // 2310 minutes = 38h30m
        progress /= 5 as int
        if (progress > 20) progress = 20
        return String.format(":clock4: Done *%02d:%02d* over *38:30* " +
                "`[${"▓".multiply(progress)}${" ".multiply(20 - progress)}]`",
                minutes / 60 as Integer, minutes % 60 as Integer)
    }

    /**
     * Formats a String, giving the current working time since
     * the begining of the day.
     *
     * @return a String with the expected info, if no error occured.
     */
    def totalTimeToday() {
        Calendar calendar = Calendar.getInstance()
        Date today = calendar.getTime()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        Date tomorrow = calendar.getTime()

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
        def from = format.format(today) + "T00:00:00.0Z"
        def to   = format.format(tomorrow) + "T00:00:00.0Z"
        def ret
        def message
        try {
            ret = odooClient.getAttendances(this.username, from, to)
        } catch (DisconnectedFromOdooException _) {
            // second chance ?
            odooClient.login()
            try {
                ret = odooClient.getAttendances(this.username, from, to)
            } catch (RuntimeException e2) {
                logger.error("Tried logging in again on Odoo, no luck, giving up", e2)
                message = ":interrobang: Unable to get the attendances for the moment (check the bot logs)"
                return SlackPreparedMessage.builder().message(message).build()
            }
        }

        SimpleDateFormat outputOdooFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        outputOdooFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

        long minutes = 0

        ret.each {
            def cIn = outputOdooFormat.parse(it.check_in)
            def cOut = it.check_out != false ? outputOdooFormat.parse(it.check_out) : new Date()
            minutes += ChronoUnit.MINUTES.between(cIn.toInstant(), cOut.toInstant())
        }

        def progress = 100 * minutes / 462 as float // 462 minutes = 7h42m
        progress /= 5 as int
        if (progress > 20) progress = 20

        return String.format(":clock4: Done *%02d:%02d* over *07:42* `[${"▓".multiply(progress)}${" ".multiply(20 - progress)}]`",
                minutes / 60 as Integer, minutes % 60 as Integer)
    }

    /**
     * Formats a String, giving a listing of the working time over the last
     * 8 days, useful to fill in ones timesheet.
     *
     * @return a String with the expected infos, if no error occured.
     */
    def timeSheet() {
        Calendar cal = Calendar.getInstance()
        Date now = cal.getTime()
        cal.add(Calendar.DAY_OF_YEAR, -10) // ten days actually, but we don't use to work on the weekend.
        Date lastWeek = cal.getTime()

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")

        def from = format.format(lastWeek) + "T00:00:00.0Z"
        def to   = format.format(now)   + "T00:00:00.0Z"

        def ret
        def message

        try {
            ret = odooClient.getAttendances(this.username, from, to)
        } catch (DisconnectedFromOdooException _) {
            // second chance ?
            odooClient.login()
            try {
                ret = odooClient.getAttendances(this.username, from, to)
            } catch (RuntimeException e2) {
                logger.error("Tried logging in again on Odoo, no luck, giving up", e2)
                message = ":interrobang: Error while trying to interact with Odoo (check the bot logs)"
                return SlackPreparedMessage.builder().message(message).build()
            }
        }
        SimpleDateFormat outputOdooFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        outputOdooFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

        long minutes = 0
        def  perDay = [:]
        def currentDay = null

        ret.each {
            def cIn = outputOdooFormat.parse(it.check_in)
            def nd = format.format(cIn)
            if (currentDay == null) {
                currentDay = nd
            }
            if (nd != currentDay) {
                perDay[currentDay] = minutes
                currentDay = nd
                minutes = 0
            }
            Date cOut = it.check_out != false ? outputOdooFormat.parse(it.check_out) : new Date()
            minutes += ChronoUnit.MINUTES.between(cIn.toInstant(), cOut.toInstant())
        }
        def retstr = ""
        perDay.reverseEach { k,v ->
            def h = v / 60 as Integer
            def m = v % 60 as Integer
            def timeSpent = String.format("%02d:%02d", h, m)
            retstr += "${k}: ${timeSpent}\n"
        }
        return ":spiral_calendar_pad: Attendances on Odoo for the last 8 days:\n```${retstr}```"
    }

    @Override
    void onEvent(SlackMessagePosted event, SlackSession session) {
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
          else if (arg == "vacations") {
              session.sendMessage(channelOnWhichMessageWasPosted,
                      vacations()
              )
              return
          }
          else if (arg == "presence") {
              def user = messageContent =~ /\!odoo presence (\S+)$/
              user = user[0][1]
              session.sendMessage(channelOnWhichMessageWasPosted,
                      getUserAttendanceState(user)
              )
              return
          } else {
              session.sendMessage(channelOnWhichMessageWasPosted,
                      SlackPreparedMessage.builder().message(usage).build()
              )
          }
        } catch (Exception e) {
          logger.error("Error occured", e)
          session.sendMessage(channelOnWhichMessageWasPosted,
            SlackPreparedMessage.builder().message(usage).build()
          )
        }
      }
    }
}
