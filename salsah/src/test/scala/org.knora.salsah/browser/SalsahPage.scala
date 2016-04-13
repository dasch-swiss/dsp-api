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

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.{By, WebDriver, WebElement}

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
}
