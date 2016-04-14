/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.salsah.browser

import scala.collection.JavaConversions._
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.concurrent.Eventually._

/**
  * Gives browser tests access to elements in a SALSAH HTML page, using the Selenium API. By using methods provided
  * here instead of doing their own queries, tests can be more readable, and can be protected from future changes
  * in the structure of the HTML.
  *
  * You need to download the [[https://sites.google.com/a/chromium.org/chromedriver/downloads Selenium driver for Chrome]]
  * and put it in `salsah/lib`.
  *
  * See [[https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html WebDriver]]
  * for more documentation.
  */
class SalsahPage {
    val pageUrl = "http://localhost:3335/index.html" // TODO: get this from application.conf

    // Load the native Selenium driver for Chrome.
    System.setProperty("webdriver.chrome.driver", "lib/chromedriver")
    implicit val driver: WebDriver = new ChromeDriver()

    /**
      * Loads the SALSAH home page.
      */
    def load(): Unit = {
        driver.get(pageUrl)
    }

    /**
      * Closes the web browser. Once this method has been called, this instance of `SalsahPage` can no longer be used.
      */
    def quit(): Unit = {
        driver.quit()
    }

    /**
      * Returns the title of the page.
      */
    def getPageTitle: String = {
        Option(driver.getTitle).getOrElse("")
    }

    /**
      * Does login with given credentials.
      *
      * @param user username
      * @param password password
      */
    def doLogin(user: String, password: String) = {
        val loginButton = driver.findElement(By.id("dologin"))

        loginButton.click()

        val userInput = driver.findElement(By.id("user_id"))
        val passwordInput = driver.findElement(By.id("password"))
        val sendCredentials = driver.findElement(By.id("login_button"))

        userInput.sendKeys("root")
        passwordInput.sendKeys("test")
        sendCredentials.click()

    }

    /**
      * Returns the SALSAH simple search field.
      */
    def getSimpleSearchField: WebElement = {
        driver.findElement(By.id("simplesearch"))
    }

    /**
      * Returns a `div` representing search results.
      */
    def getSearchResultDiv: WebElement = {
        driver.findElement(By.name("result"))
    }

    /**
      * Returns the header of a `div` representing search results.
      *
      * @param searchResultDiv a `div` representing search results.
      * @return the contents of the header.
      */
    def getSearchResultHeader(searchResultDiv: WebElement): String = {
        searchResultDiv.findElement(By.xpath("div[1]")).getText
    }

    /**
      * Returns a description of the first search result.
      *
      * @param searchResultDiv a `div` representing search results.
      * @return the contents of the last column of the first row of the search results table.
      */
    def getFirstSearchResult(searchResultDiv: WebElement): String = {
        searchResultDiv.findElement(By.xpath("table/tbody/tr[2]/td[4]")).getText
    }

    /**
      * Clicks on the specified search result row.
      *
      * @param searchResultDiv a `div` representing search results.
      * @param number          the search result row to be opened.
      */
    def openResult(searchResultDiv: WebElement, number: Int) = {
        val resultRows: Seq[WebElement] = searchResultDiv.findElements(By.xpath("table/tbody/tr[@class='result_row']"))

        val resultRow = resultRows.get(number)

        resultRow.click()


    }

    /**
      * Ensures the minimum overall amount of windows and returns a list of them.
      *
      * @param minSize the minimum amount of windows.
      * @return a list of [[WebElement]].
      */
    private def getWindows(minSize: Int): Seq[WebElement] = {

        val windows = driver.findElements(By.className("win"))
        if (windows.size < minSize) throw new Exception() else windows
    }

    /**
      * Get the window with the given id.
      *
      * @param winId the window's id.
      * @return a [[WebElement]] representing the window.
      */
    def getWindow(winId: Int): WebElement = {
        // make sure that the specified window is ready in the DOM
        val windows = getWindows(winId + 1)

        driver.findElement(By.id(winId.toString))
    }

    /**
      * Gets a window's metadata section.
      *
      * @param window a [[WebElement]] representing the window.
      * @return a [[WebElement]] representing the metadat section.
      */
    def getMetadataSection(window: WebElement) = {
        eventually {
            window.findElement(By.xpath("div[@class='content contentWithTaskbar']//div[contains(@class, 'metadata') and contains(@class, 'section') and not (contains(@class, 'sectionheader'))]"))
        }
    }

    def getEditingFieldsFromMetadataSection(metadataSection: WebElement) = {
        metadataSection.findElements(By.xpath("div[@class='propedit datafield_1 winid_1']")).toList
    }

    /**
      * Moves a window.
      *
      * @param window the [[WebElement]] representing the window.
      * @param offsetX horizontal moving distance.
      * @param offsetY vertical moving distance.
      */
    def dragWindow(window: WebElement, offsetX: Int, offsetY: Int) = {
        val titlebar = window.findElement(By.className("titlebar"))

        val builder = new Actions(driver)

        builder.clickAndHold(titlebar).moveByOffset(offsetX, offsetY).release().build.perform()

    }

    def findCkeditor(field: WebElement) = {
        field.findElement(By.xpath("div//iframe"))
    }
}
