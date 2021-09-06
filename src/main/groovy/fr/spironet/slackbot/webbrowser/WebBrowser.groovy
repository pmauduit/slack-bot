package fr.spironet.slackbot.webbrowser

import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.imageio.ImageIO

class WebBrowser {

    private final static Logger logger = LoggerFactory.getLogger(WebBrowser.class)

    def kibanaUser     = System.getenv("KIBANA_USER")
    def kibanaPassword = System.getenv("KIBANA_PASSWORD")
    def kibanaUrl      = System.getenv("KIBANA_URL")
    def dashboardPath  = System.getenv("KIBANA_DASHBOARD")

    def grafanaUser = System.getenv("GRAFANA_USER")
    def grafanaPassword = System.getenv("GRAFANA_PASSWORD")
    def grafanaUrl = System.getenv("GRAFANA_URL")
    def grafanaDashboard = System.getenv("GRAFANA_DASHBOARD_PATH")

    static {
        def chromeDriverPath = System.getenv("BROWSER_CHROMEDRIVER")
        if (chromeDriverPath != null) {
            System.setProperty("webdriver.chrome.driver", chromeDriverPath)
        }
    }

    def getDriver() throws Exception {
        def options = new ChromeOptions()
        options.addArguments("headless")
        options.addArguments("window-size=1920,1080")
        return new ChromeDriver(options)
    }

    def visitKibanaDashboard() {
        def driver
        try {
            driver = getDriver()
        } catch (IllegalStateException e) {
            logger.error("Driver not available.")
            return null
        }
        try {
            if ((kibanaUser == null) || (kibanaPassword == null) || (kibanaUrl == null)) {
                return null
            }
            // makes sure to cleanup cookies before accessing the page
            driver.manage().deleteAllCookies()
            driver.get("https://${kibanaUser}:${kibanaPassword}@${kibanaUrl}/")
            System.sleep(1000)
            // ok, now we should have a cookie ?
            def finalUrl = "https://${kibanaUrl}${dashboardPath}"
            driver.get(finalUrl)
            // wait for long enough to let the time to draw the page
            System.sleep(15000)
            byte[] data = driver.getScreenshotAs(OutputType.BYTES)
            if (data == null) {
                return null
            }
            // crop the image a bit before sending it
            def im = ImageIO.read(new ByteArrayInputStream(data))
            // open gimp if you need to figure out relevant params here
            def cropped = im.getSubimage(184, 120, 1720, 760)
            def baos = new ByteArrayOutputStream(2048)
            ImageIO.write(cropped, "png", baos)
            return new ByteArrayInputStream(baos.toByteArray())
        } finally {
            driver.close()
            driver.quit()
        }
    }

    def visitGrafanaMonitoringDashboard() {
        def driver
        try {
            driver = getDriver()
        } catch (IllegalStateException e) {
            logger.error("Driver not available.")
            return null
        }
        try {
            if (!grafanaUser || !grafanaPassword || !grafanaUrl || !grafanaDashboard) {
                return null
            }

            driver.get(grafanaUrl)
            def userInput = driver.findElementByCssSelector("input[name='user']")
            def passwordInput = driver.findElementByCssSelector("input[name='password']")
            def loginButton = driver.findElementByCssSelector("button")

            userInput.sendKeys(grafanaUser)
            passwordInput.sendKeys(grafanaPassword)
            loginButton.click()
            // long enough to have a session
            System.sleep(500)
            // then visit the dashboard
            driver.get("${grafanaUrl}${grafanaDashboard}")
            def bodyPage = driver.findElementByCssSelector("body")
            bodyPage.sendKeys("dkdk")
            // long enough to draw the page
            System.sleep(5000)
            byte[] data = driver.getScreenshotAs(OutputType.BYTES)

            // returns the screenshot
            return  new ByteArrayInputStream(data)
        } finally {
            driver.close()
            driver.quit()
        }
    }
}
