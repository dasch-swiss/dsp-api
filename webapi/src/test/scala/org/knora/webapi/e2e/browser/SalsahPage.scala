package org.knora.webapi.e2e.browser

import org.openqa.selenium.{By, WebDriver, WebElement}

/**
  * Gives browser tests access to elements in a SALSAH HTML page, using the Selenium API. By using methods provided
  * here instead of doing their own queries, tests can be more readable, and can be protected from future changes
  * in the structure of the HTML.
  *
  * See [[https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html WebDriver]]
  * for more documentation.
  */
object SalsahPage {
    /**
      * Returns the title of the HTML page.
      */
    def getPageTitle(implicit driver: WebDriver): String = {
        Option(driver.getTitle).getOrElse("")
    }

    /**
      * Returns the SALSAH simple search field.
      */
    def getSimpleSearchField(implicit driver: WebDriver): WebElement = {
        driver.findElement(By.id("simplesearch"))
    }

    /**
      * Returns a `div` representing search results.
      */
    def getSearchResultDiv(implicit driver: WebDriver): WebElement = {
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
