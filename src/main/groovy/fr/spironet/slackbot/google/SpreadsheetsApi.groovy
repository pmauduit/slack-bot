package fr.spironet.slackbot.google

import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes

class SpreadsheetsApi {

    Sheets service

    /**
     * Default constructor. Expects some environment variables to be set,
     * so that we can interact with the Spreadsheets API.
     *
     * @throws a RuntimeException if some of the expected env variables
     * are missing.
     *
     */
    SpreadsheetsApi() {
        if ((System.env["SPREADSHEETS_OAUTH_CLIENT_ID"] == null) ||
                (System.env["SPREADSHEETS_OAUTH_CLIENT_SECRET"] == null) ||
                (System.env["SPREADSHEETS_OAUTH_ACCESS_TOKEN"] == null) ||
                (System.env["SPREADSHEETS_OAUTH_REFRESH_TOKEN"] == null)) {
            throw new RuntimeException("Missing SPREADSHEETS_OAUTH_* environment variables")
        }
        initialize(System.env["SPREADSHEETS_OAUTH_CLIENT_ID"],
                System.env["SPREADSHEETS_OAUTH_CLIENT_SECRET"],
                System.env["SPREADSHEETS_OAUTH_ACCESS_TOKEN"],
                System.env["SPREADSHEETS_OAUTH_REFRESH_TOKEN"])
    }

    /**
     * Constructor which allows to pass the required credentials by parameters.
     *
     * @param clientId the client identifier
     * @param clientSecret the client secret
     * @param accessToken the user access token
     * @param refreshToken the user refresh token
     */
    SpreadsheetsApi(def clientId, def clientSecret, def accessToken, def refreshToken) {
        initialize(clientId, clientSecret, accessToken, refreshToken)
    }

    /**
     * Initializes the spreadsheets API.
     *
     * @param clientId the client identifier
     * @param clientSecret the client secret
     * @param accessToken the user's access token
     * @param refreshToken the user's refresh token
     *
     */
    private def initialize(def clientId, def clientSecret, def accessToken, def refreshToken) {
        def gcs = new GoogleClientSecrets().setInstalled(new GoogleClientSecrets.Details()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setAuthUri("https://accounts.google.com/o/oauth2/auth")
                .setTokenUri("https://accounts.google.com/o/oauth2/token")
                .setRedirectUris([ "urn:ietf:wg:oauth:2.0:oob" ])
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
                Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY))
                //SheetsScopes.all())
                .setDataStoreFactory(dsFactory)
                .build()

        def creds = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user")

        this.service = new Sheets.Builder(httpTransport, JSON_FACTORY, creds)
                .setApplicationName("spironet/spreadsheets-slackbot-1.0")
                .build()
    }
}
