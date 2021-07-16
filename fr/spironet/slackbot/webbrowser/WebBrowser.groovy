package fr.spironet.slackbot.webbrowser

import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WebBrowser {

    private final static Logger logger = LoggerFactory.getLogger(WebBrowser.class)

    static {
        def chromeDriverPath = System.getenv("BROWSER_CHROMEDRIVER")
        System.setProperty("webdriver.chrome.driver", chromeDriverPath)
    }

    public def visitGrafanaMonitoringDashboard() {
        def driver
        def options = new ChromeOptions()
        options.addArguments("headless")
        options.addArguments("window-size=1920,1080")
        try {
            driver = new ChromeDriver(options)
        } catch (IllegalStateException e) {
            logger.error("Driver not available.")
            return null
        }
        def grafanaUser = System.getenv("GRAFANA_USER")
        def grafanaPassword = System.getenv("GRAFANA_PASSWORD")
        def grafanaUrl = System.getenv("GRAFANA_URL")
        def grafanaDashboard = System.getenv("GRAFANA_DASHBOARD_PATH")

        if (!grafanaUser  || !grafanaPassword || !grafanaUrl || !grafanaDashboard ) {
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
        return data
    }
}
