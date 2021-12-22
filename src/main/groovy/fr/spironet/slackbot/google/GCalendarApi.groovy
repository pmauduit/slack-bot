package fr.spironet.slackbot.google

import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.calendar.CalendarScopes

import java.text.SimpleDateFormat

class GCalendarApi {

    def client

    GCalendarApi() {
        if ((System.env["GCAL_OAUTH_CLIENT_ID"] == null) ||
                (System.env["GCAL_OAUTH_CLIENT_SECRET"] == null) ||
                (System.env["GCAL_OAUTH_ACCESS_TOKEN"] == null) ||
                (System.env["GCAL_OAUTH_REFRESH_TOKEN"] == null)) {
            throw new RuntimeException("Missing GCAL_OAUTH_* environment variables")
        }
        initialize(System.env["GCAL_OAUTH_CLIENT_ID"],
                System.env["GCAL_OAUTH_CLIENT_SECRET"],
                System.env["GCAL_OAUTH_ACCESS_TOKEN"],
                System.env["GCAL_OAUTH_REFRESH_TOKEN"])
    }

    /**
     * Constructor used for testing purposes.
     * @param client a Google API Client object (more likely a mock).
     */
    GCalendarApi(def client) {
        this.client = client
    }

    /**
     * Initializes the Calendar client object, using the credentials passed as argument.
     *
     * @param clientId the google client ID
     * @param clientSecret the google client secret
     * @param accessToken the private user access token
     * @param refreshToken the private user refresh token
     *
     * @return nothing ; after having called the method, the client member variable is
     * expected to be ready.
     */
    private def initialize(def clientId, def clientSecret, def accessToken, def refreshToken) {
        def gcs = new GoogleClientSecrets().setInstalled(new GoogleClientSecrets.Details()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setAuthUri("https://accounts.google.com/o/oauth2/auth")
                .setTokenUri("https://accounts.google.com/o/oauth2/token")
                .setRedirectUris(["urn:ietf:wg:oauth:2.0:oob"])
        )
        def dsFactory = MemoryDataStoreFactory.getDefaultInstance()
        dsFactory.getDataStore("StoredCredential").set("user", new StoredCredential()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setExpirationTimeMilliseconds(1491480728089L))
        def JSON_FACTORY = JacksonFactory.getDefaultInstance()
        def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        def flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                gcs,
                Collections.singleton(CalendarScopes.CALENDAR))
                .setDataStoreFactory(dsFactory)
                .build()
        def creds = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user")
        this.client = new com.google.api.services.calendar.Calendar.Builder(
                httpTransport, JSON_FACTORY, creds).setApplicationName("spironet/gcal-slackbot-1.0").build()
    }


    def getTodaysEvents() {
        // google API expects RFC3339, e.g. 2002-10-02T15:00:00.05Z
        def todayMidnight = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00.0").format(new Date())
        def today23h59m59s = new SimpleDateFormat("yyyy-MM-dd'T'23:59:59.99").format(new Date())

        def pageToken
        def items = []
        do {
            def events = client.events().list('primary')
                    .setTimeMin(new DateTime(todayMidnight))
                    .setTimeMax(new DateTime(today23h59m59s))
                    //.setFields("items(summary,organizer,start,end,status)")
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setPageToken(pageToken).execute()
            items.addAll(events.getItems().findAll { it.status == "confirmed"})
            pageToken = events.getNextPageToken()
        } while (pageToken != null)

        return items
    }
}
