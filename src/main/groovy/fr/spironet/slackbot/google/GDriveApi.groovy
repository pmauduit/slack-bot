package fr.spironet.slackbot.google

import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

class GDriveApi {

    Drive client

    GDriveApi() {
        if ((System.env["GDRIVE_OAUTH_CLIENT_ID"] == null) ||
                (System.env["GDRIVE_OAUTH_CLIENT_SECRET"] == null) ||
                (System.env["GDRIVE_OAUTH_ACCESS_TOKEN"] == null) ||
                (System.env["GDRIVE_OAUTH_REFRESH_TOKEN"] == null)) {
            throw new RuntimeException("Missing GDRIVE_OAUTH_* environment variables")
        }
        initialize(System.env["GDRIVE_OAUTH_CLIENT_ID"],
                System.env["GDRIVE_OAUTH_CLIENT_SECRET"],
                System.env["GDRIVE_OAUTH_ACCESS_TOKEN"],
                System.env["GDRIVE_OAUTH_REFRESH_TOKEN"])
    }

    def GDriveApi(def client) {
        this.client = client
    }

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
                DriveScopes.all())
                .setDataStoreFactory(dsFactory)
                .build()

        def creds = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user")

        this.client = new com.google.api.services.drive.Drive.Builder(
                httpTransport, JSON_FACTORY, creds)
                .setApplicationName("spironet/gdrive-slackbot-1.0")
                .build()
    }
}
