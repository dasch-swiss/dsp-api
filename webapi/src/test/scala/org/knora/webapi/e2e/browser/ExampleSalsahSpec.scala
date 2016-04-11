package org.knora.webapi.e2e.browser

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.scalatest._
import org.scalatest.concurrent.Eventually._
import scala.concurrent.duration._

/**
  * Illustrates how to write a browser-based test using Selenium.
  *
  * You need to download the Selenium driver for Chrome and put it in `webapi/lib`.
  *
  * See [[https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html WebDriver]]
  * for more documentation.
  *
  * This example uses the Selenium API directly instead of the ScalaTest wrapper, because the Selenium API is more
  * powerful and more efficient.
  */
class ExampleSalsahSpec extends WordSpecLike with ShouldMatchers {

    val pageUrl = "http://localhost:3335/index.html" // TODO: get this from application.conf

    // Load the native Selenium driver for Chrome.
    System.setProperty("webdriver.chrome.driver", "lib/chromedriver")
    implicit val driver: WebDriver = new ChromeDriver()

    // How long to wait for results obtained using the 'eventually' function
    implicit val patienceConfig = PatienceConfig(timeout = scaled(2.seconds), interval = scaled(20.millis))

    "The SALSAH home page" should {

        "have the correct title" in {

            driver.get(pageUrl)
            SalsahPage.getPageTitle should be ("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "do a simple search" in {

            val searchField = SalsahPage.getSimpleSearchField
            searchField.clear()
            searchField.sendKeys("Zeitglöcklein\n")

            // Use 'eventually' to test results that may take time to appear.
            val resultDiv = eventually {
                SalsahPage.getSearchResultDiv
            }

            val resultHeader = eventually {
                SalsahPage.getSearchResultHeader(resultDiv)
            }

            assert(resultHeader.contains("Total of 3 hits"))

            val firstResult = eventually {
                SalsahPage.getFirstSearchResult(resultDiv)
            }

            assert(firstResult.contains("Zeitglöcklein des Lebens und Leidens Christi"))
        }

        "close the browser" in {
            // Uncomment this if you want the browser to close after the test completes.
            // driver.quit()
        }
    }
}
