package fr.spironet.slackbot.google

/**
 * A class which interacts with the Google Spreadsheets API (https://developers.google.com/sheets/api).
 * to grab some informations about the current planning.
 *
 * This is specific to the C2C Geospatial department own organization: The project managers regularly
 * update a google spreadsheet, which contains a sheet per 2-week sprints, giving the allocated time
 * for each employee per project. The code contained in this class aims to get the data from this spreadsheets.
 *
 * It involves:
 *
 * * determining which sheet describes the current sprint (given the current week of the year + the sheet name),
 * * Checking in this sheet what is the allocated time per project for a collaborator (finding the correct trigram,
 *   and correlate with the first column containing the project descriptions).
 *
 * It could be interesting to be able to analyze the datas from JIRA/Tempo afterwards, and see if the
 * employee diverged from the expected planning.
 *
 * But for now, being able to consult the planning via Slack is already an acceptable goal.
 *
 */
class C2CGeospatialPlanning {
    /**
     * the spreadsheet id. It can be easily found by opening the spreadsheet
     * in google-drive, as it is part of the URL once the sheet has been opened.
     */
    private def spreadSheetId

    /**
     * a SpreadsheetsApi object, required to interact with Google Spreadsheets
     */
    private def spreadSheetsApi

    /**
     * Constructor
     * @param spreadSheetsApi a SpreadsheetsApi object
     * @param spreadSheetId the spreadsheet identifier
     */
    C2CGeospatialPlanning(def spreadSheetsApi, def spreadSheetId) {
        this.spreadSheetId = spreadSheetId
        this.spreadSheetsApi = spreadSheetsApi
    }

    /**
     * Gets the current week of the year.
     *
     * Note: It might depend on the locale being used. Some manual tests showed that a +1 offset
     * was observed, hence the `-1` in the code.
     *
     * @return an integer for the current week of the year.
     */
    def currentWeekOfYear() {
        Calendar.getInstance(new Locale("en", "US")).get(Calendar.WEEK_OF_YEAR) - 1
    }

    /**
     * Given a week number, returns the date of the first day for the considered week.
     *
     * @param weekNumber a week number.
     *
     * @return a Calendar object.
     */
    Calendar getDateFirstDayOfWeek(def weekNumber) {
        Calendar cal = Calendar.getInstance()
        cal.set(Calendar.WEEK_OF_YEAR, weekNumber)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek())
        cal.add(Calendar.DAY_OF_WEEK, 1)

        return cal
    }

    /**
     * Given the current date, tries to determine a portion of the sheet name
     * from the C2C geospatial planning spreadsheet.
     *
     * @return a String which should be part of the sheet name, having the following pattern:
     * `[week x&y]`
     */
    def guessCurrentSheetName() {
        def wn = currentWeekOfYear()
        if (wn % 2 == 0) {
            return "[week ${wn-1}&${wn}]"
        } else {
            return "[week ${wn}&${wn+1}]"
        }
    }

    /**
     * Given an employee trigramm (e.g. "PMT" for "Pierre Mauduit"), tries to guess the column being used
     * for the employee.
     *
     * It basically selects the first line of the guessed sheet, and finds the first index in the returned array
     * by the sheets API. Once the index found, we need to convert it to a sheet column (A-Z, AA-AZ, ...).
     *
     * @param sheetIdx the sheet identifier on which the lookup has to be made. Note: we could probably use
     * the default sheet of the document, but since it might evolve (newcomers, people having left), we are better
     * to use the supposed current sheet.
     *
     * @param employeeTrigramm the employee trigramm.
     * @return a String describing the supposed column dedicated to the employee.
     *
     */
    def findEmployeeColumn(def sheetIdx, def employeeTrigramm) {
        def employeesLine = spreadSheetsApi.service.spreadsheets().values().get(this.spreadSheetId,
                "${sheetIdx}!1:1").execute()
        def emplIdx = employeesLine.values[0].findIndexOf { it.contains(employeeTrigramm) }
        def columnChar
        if ((emplIdx + 65) < 90) {
            columnChar = (emplIdx + 65) as Character
        } else {
            columnChar = 'A'
            columnChar += ((emplIdx - 26 + 65) as Character)
        } // we can handle employees as long as we are less than 26x2 ...
        return columnChar
    }

    /**
     * Gets the project labels. This basically selects the values set in the first column of the sheet.
     *
     * @param sheetIdx the sheet identifier in the spreadsheet.
     *
     * @return the values from the column.
     */
    def getProjectLabels(sheetIdx) {
        def projectsLabels = spreadSheetsApi.service.spreadsheets().values().get(this.spreadSheetId,
                "${sheetIdx}!A:A").execute()
        return projectsLabels.values
    }

    /**
     * Given an employee trigramm, gets the allocation for the current sprint. the code does the following:
     * * guess the column of the employee (@see findEmployeeColumn())
     * * queries the spreadsheets API for the guessed employee column.
     *
     * @param sheetIdx the sheet describing the sprint to consider.
     * @param searchedTrigramm the employee trigramm.
     *
     * @return the values from the employee column.
     */
    def getDaysAllocated(sheetIdx, searchedTrigramm) {
        def employeeColumn = findEmployeeColumn(sheetIdx, searchedTrigramm)
        def allocatedTimeColumn = spreadSheetsApi.service.spreadsheets().values().get(this.spreadSheetId,
                "${sheetIdx}!${employeeColumn}:${employeeColumn}").execute()
        return allocatedTimeColumn.values
    }

    /**
     *
     * Gets the current planning for a given user.
     *
     * @param the trigramm of the employee (e.g. "PMT" for "Pierre Mauduit").
     *
     * @return a map containing:
     * * the allocations per project under the key 'allocations'
     * * the sheet identifier used to get the datas, under the key 'sheet'
     *
     */
    def getCurrentPlanningForUser(def trigramm) {
        def planningSheet =  spreadSheetsApi.service.spreadsheets().get(this.spreadSheetId).execute()
        def guessedSheetName = this.guessCurrentSheetName()

        // .reverse() because last sheets are the most recent ones
        // and more likely to be of interest
        def sheetIdx = planningSheet.sheets.reverse().find {
            it.properties.title.contains(guessedSheetName)
        }.properties.title

        def projectsLabels = this.getProjectLabels(sheetIdx)
        def daysAllocated = this.getDaysAllocated(sheetIdx, trigramm)

        def allocations = [:]
        daysAllocated.eachWithIndex { it, idx ->
            // first column is the employee Name / trigramm / indication if part-time
            // and we don't really care of "working days & total" cells either.
            if (idx < 3)
                return
            // no data
            if (it.size == 0)
                return
            def relatedProject = projectsLabels[idx][0]
            allocations[relatedProject] = it[0] as float
        }
        return [ 'allocations' : allocations, 'sheet': sheetIdx ]
    }
}
