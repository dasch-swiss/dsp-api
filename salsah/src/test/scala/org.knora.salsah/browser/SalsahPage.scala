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

import java.io.File

import scala.collection.JavaConversions._
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.concurrent.Eventually._
import java.io.FileNotFoundException
import java.util

import scala.concurrent.duration._


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

    // How long to wait for results obtained using the 'eventually' function
    implicit val patienceConfig = PatienceConfig(timeout = scaled(10.seconds), interval = scaled(20.millis))

    val pageUrl = "http://localhost:3335/index.html" // TODO: get this from application.conf

    val chromeDriverPath = "lib/chromedriver/chromedriver"

    // check if the Selenium driver for Chrome exists
    val SeleniumChromeDriver = new File(chromeDriverPath)
    if (!SeleniumChromeDriver.exists) throw new FileNotFoundException(s"${chromeDriverPath} could not be found. Please install Selenium Chrome Driver.")

    // Load the native Selenium driver for Chrome.
    System.setProperty("webdriver.chrome.driver", chromeDriverPath)
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

    /*

    Search

     */

    /**
      * Returns the SALSAH simple search field.
      */
    def getSimpleSearchField: WebElement = {
        driver.findElement(By.id("simplesearch"))
    }

    /**
      * Returns the header of a `div` representing search results.
      *
      * @return the contents of the header.
      */
    def getSearchResultHeader: String = {
        eventually {
            val searchResultDiv = driver.findElement(By.name("result"))
            searchResultDiv.findElement(By.xpath("div[1]")).getText
        }
    }

    /**
      * Returns a description of the first search result.
      *
      * @param searchResultRow a `tr` representing a search result row.
      * @return the contents of the last column of the first row of the search results table.
      */
    def getSearchResultRowText(searchResultRow: WebElement): String = {
        eventually {
            searchResultRow.findElement(By.xpath("td[3]")).getText
        }
    }

    /**
      * Clicks the SALSAH extended search button.
      */
    def clickExtendedSearchButton() = {
        driver.findElement(By.xpath("//div[@id='searchctrl']/img[2][@class='link']")).click()
    }

    /**
      * Select a restype to search for.
      *
      * @param restype the restype to be selected.
      */
    def selectRestype(restype: String) = {
        eventually {
            val restypeSelect = driver.findElement(By.name("selrestype"))
            new Select(restypeSelect).selectByValue(restype)
        }
    }

    /**
      *
      * Get a selection in extended search.
      *
      * @param propIndex indicate which (first, second, third etc. ) property field set to use: the user may perform a search involving several properties.
      * @param name the name of the selection ("selprop" or "compop")
      * @return a [[Select]] representing the HTML select.
      */
    def getExtendedSearchSelectionByName(propIndex: Int, name: String): Select = {
        eventually {
            val selection = driver.findElement(By.xpath(s"//div[$propIndex][contains(@class, 'selprop') and contains(@class, 'extsearch')]")).findElement(By.name(name))
            new Select(selection)
        }
    }

    /**
      * Get the value field to enter the value to search for.
      *
      * @param propIndex indicate which (first, second, third etc. ) property field set to use: the user may perform a search involving several properties.
      * @return a [[WebElement]] representing the value field.
      */
    def getValueField(propIndex: Int): WebElement = {
        eventually {
            driver.findElement(By.xpath(s"//div[$propIndex][contains(@class, 'selprop')]")).findElement(By.name("valfield")).findElement(By.xpath("input"))
        }
    }

    /**
      * Get the selects representing a hierarchical list in extended search form.
      *
      * @param propIndex indicate which (first, second, third etc. ) property field set to use: the user may perform a search involving several properties.
      * @return a list of [[Select]]
      */
    def getHierarchicalListSelectionsInExtendedSearch(propIndex: Int): List[Select] = {
        eventually {

            val selections = driver.findElement(By.xpath(s"//div[$propIndex][contains(@class, 'selprop')]")).findElement(By.name("valfield")).findElements(By.xpath("span[@class='propval']//select"))
            // make sure to find any selection (async)
            if (selections.length < 1) throw new Exception

            selections.map(new Select(_)).toList

        }
    }

    /**
      * Get the date form for a date value property.
      *
      * @param propIndex indicate which (first, second, third etc. ) property field set to use: the user may perform a search involving several properties.
      * @return the [[WebElement]] representing the date form.
      */
    def getDateFormInExtendedSearchForm(propIndex: Int): WebElement = {
        eventually {
            driver.findElement(By.xpath(s"//div[$propIndex][contains(@class, 'selprop')]")).findElement(By.name("valfield")).findElement(By.name("searchval"))
        }

    }

    /**
      * Get calendar type selection.
      *
      * @param dateForm the [[WebElement]] representing the date form.
      * @return a [[Select]] representing the calendar types.
      */
    def getCalSelection(dateForm: WebElement): Select = {

        eventually {
            new Select(dateForm.findElement(By.xpath("//select[contains(@class,'calsel')]")))
        }

    }

    /**
      * Hit checkbox to search for a date that is a period.
      *
      * @param dateForm the [[WebElement]] representing the date form.
      */
    def makePeriod(dateForm: WebElement) = {
        eventually {
            dateForm.findElement(By.xpath("//input[contains(@class,'periodsel')]")).click()
        }
    }

    /**
      * Get month selection in extended search form.
      *
      * @param dateForm the [[WebElement]] representing the date form.
      * @param index 1 indicates start date, 2 end date in case of a period.
      * @return a [[Select]] representing the month.
      */
    def getMonthSelectionInExtendedSearchForm(dateForm: WebElement, index: Int = 1) = {
        eventually {
            new Select(dateForm.findElement(By.xpath(s"span[$index]/select[contains(@class,'monthsel')]")))
        }
    }

    /**
      * Get the days in the extended search form.
      *
      * @param dateForm dateForm the [[WebElement]] representing the date form.
      * @param index 1 indicates start date, 2 end date in case of a period.
      * @return a list of `td` representing the days.
      */
    def getDaysInExtendedSearchForm(dateForm: WebElement, index: Int = 1): List[WebElement] = {
        eventually {
            val daysel = dateForm.findElement(By.xpath(s"span[$index]/input[contains(@class,'daysel')]"))

            daysel.click()

            val dayForm = dateForm.findElement(By.xpath(s"span[$index]/div[contains(@class,'daysel')]/table"))

            dayForm.findElements(By.xpath("tbody/tr/td[normalize-space(.)!='']")).toList



        }
    }

    /**
      * Get year entry field in extended search form.
      *
      * @param dateForm the [[WebElement]] representing the date form.
      * @param index 1 indicates start date, 2 end date in case of a period.
      * @return a `input` representing the year entry field.
      */
    def getYearFieldInExtendedSearchForm(dateForm: WebElement, index: Int = 1) = {
        eventually {
            dateForm.findElement(By.xpath(s"span[$index]/input[contains(@class,'yearsel')]"))
        }
    }

    /**
      * Add a new property to search for.
      *
      * @param propIndex the index that this property set will have. If this is the first additional set, the index will be 2.
      */
    def addPropertySetToExtendedSearch(propIndex: Int): WebElement = {
        eventually {
            driver.findElement(By.xpath("//div[contains(@class, 'propedit_frame')]//img[contains(@src,'add.png')]")).click()
        }

        eventually {
            // make sure that the new set is loaded
            driver.findElement(By.xpath(s"//div[$propIndex][contains(@class, 'selprop')]"))
        }
    }

    /**
      * Chooses an element from a searchbox.
      *
      * @param eleIndex the index of the element to choose (beginning with 1).
      */
    def chooseElementFromSearchbox(eleIndex: Int) = {
        val searchbox = eventually {
            driver.findElement(By.className("searchbox"))
        }

        eventually {
            searchbox.findElement(By.xpath(s"//div[$eleIndex][@class='searchboxItem']")).click()
        }
    }

    /**
      * Submit the extended search form.
      */
    def submitExtendedSearch = {
        eventually {
            driver.findElement(By.xpath("//input[@value='Search']")).click()
        }
    }

    /**
      * Returns the result rows.
      *
      * @return list of [[WebElement]]
      */
    def getExtendedSearchResultRows: List[WebElement] = {
        eventually {
            val table = driver.findElement(By.name("result")).findElement(By.xpath("//table"))

            val rows = table.findElements(By.xpath("//tr[@class='result_row']"))

            // make sure that rows are loaded
            if (rows.length < 1) throw new Exception

            rows.toList

        }
    }


    /*

    Window handling

    */


    /**
      * Get the window with the given id.
      *
      * @param winId the window's id.
      * @return a [[WebElement]] representing the window.
      */
    def getWindow(winId: Int): WebElement = {

        eventually {
            driver.findElement(By.id(winId.toString))
        }
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

    def getEditingFieldsFromMetadataSection(metadataSection: WebElement): List[WebElement] = {
        eventually {
            val editingFields = metadataSection.findElements(By.xpath("div[@class='propedit datafield_1 winid_1']")).toList
            if (editingFields.length < 1) throw new Exception
            editingFields
        }


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

    /*

    Edit Properties

     */

    /**
      * Gets the `iframe` representing CKEditor.
      *
      * @param field the editing field from [[getEditingFieldsFromMetadataSection]]
      * @return the `iframe` representing CKEditor.
      */
    def findCkeditor(field: WebElement): WebElement = {
        field.findElement(By.xpath("div//iframe"))
    }

    /**
      * Clicks the edit button in a editing field.
      *
      * @param field the editing field in question.
      */
    def clickEditButton(field: WebElement) = {
        eventually {
            field.findElement(By.xpath("div/img[contains(@src,'edit.png')]")).click()
        }
    }

    /**
      * Clicks the add button in a editing field.
      *
      * @param field the editing field in question.
      */
    def clickAddButton(field: WebElement) = {
        eventually {
            field.findElement(By.xpath("div/img[contains(@src,'add.png')]")).click()
        }
    }

    def clickSaveButton(field: WebElement) = {
        eventually {
            field.findElement(By.xpath("div/img[contains(@src,'save.png')]")).click()
        }
    }

    /**
      * Gets the value container to check for its contents
      *
      * @param field the editing field in question.
      * @param index specifiy which container to return (there may be several value instances for one prop).
      * @return the value container.
      */
    def getValueContainer(field: WebElement, index: Int = 1): WebElement = {
        eventually {
            val valueField = field.findElement(By.xpath(s"div[$index][contains(@class, 'value_container')]"))
            if (valueField.getText.isEmpty) throw new Exception
            valueField
        }
    }

    /**
      * Get input field.
      *
      * @param field the editing field.
      * @return the `input` representing the input field.
      */
    def getInputField(field: WebElement): WebElement = {
        eventually {
            field.findElement(By.xpath("//input[@class='propedit']"))
        }
    }

    /**
      * Get the input field of a searchbox.
      *
      * @param linkField the link editing field.
      * @return the `input` representing the input field.
      */
    def getSearchBoxInputField(linkField: WebElement): WebElement = {
        eventually {
            linkField.findElement(By.xpath("//input[@class='__searchbox']"))
        }
    }

    /**
      * Increases an integer value by one.
      *
      * @param integerField the editing field of an integer value.
      */
    def clickOnSpinboxUp(integerField: WebElement) = {
        eventually {
            integerField.findElement(By.xpath("//img[contains(@src,'spin-up.png')]")).click()
        }
    }

    /**
      * Get year entry field.
      *
      * @param dateForm the [[WebElement]] representing the date form.
      * @param index 1 indicates start date, 2 end date in case of a period.
      * @return a `input` representing the year entry field.
      */
    def getYearField(dateForm: WebElement, index: Int = 1) = {
        eventually {
            dateForm.findElement(By.xpath(s"//span[@class='propedit']/span[$index]/input[contains(@class,'yearsel')]"))
        }
    }

    /**
      * Get month selection.
      *
      * @param dateForm the [[WebElement]] representing the date form.
      * @param index 1 indicates start date, 2 end date in case of a period.
      * @return a [[Select]] representing the month.
      */
    def getMonthSelection(dateForm: WebElement, index: Int = 1) = {
        eventually {
            new Select(dateForm.findElement(By.xpath(s"//span[@class='propedit']/span[$index]/select[contains(@class,'monthsel')]")))
        }
    }

    /**
      * Get the days.
      *
      * @param dateForm dateForm the [[WebElement]] representing the date form.
      * @param index 1 indicates start date, 2 end date in case of a period.
      * @return a list of `td` representing the days.
      */
    def getDays(dateForm: WebElement, index: Int = 1): List[WebElement] = {
        eventually {
            val daysel = dateForm.findElement(By.xpath(s"//span[@class='propedit']/span[$index]/input[contains(@class,'daysel')]"))

            daysel.click()

            val dayForm = dateForm.findElement(By.xpath(s"//span[@class='propedit']/span[$index]/div[contains(@class,'daysel')]/table"))

            dayForm.findElements(By.xpath("tbody/tr/td[normalize-space(.)!='']")).toList


        }
    }

    /**
      * Get the selects representing a hierarchical list.
      *
      * @param selField selection property field.
      * @return a list of [[Select]]
      */
    def getHierarchicalListSelections(selField: WebElement): List[Select] = {
        eventually {

            val selections = selField.findElements(By.xpath("div//select"))
            // make sure to find any selection (async)
            if (selections.length < 1) throw new Exception

            selections.map(new Select(_)).toList

        }
    }

    /**
      * Returns the radio buttons.
      *
      * @param radioField radio property field.
      * @return a list of `input` representing the options.
      */
    def getRadioButtons(radioField: WebElement) = {
        eventually {
            val radios = radioField.findElements(By.xpath("div//input[@type='radio']"))

            if (radios.length < 1) throw new Exception

            radios

        }

    }


    /*

    Create Resource

     */


    def clickAddResourceButton() = {
        driver.findElement(By.xpath("//div[@id='addresctrl']/img")).click()
    }

    def getFormFieldByName(name: String): WebElement = {
        eventually {
            driver.findElement(By.xpath(s"//table[@class='propedit']//*[@name='$name']"))

        }

    }

    def clickSaveButtonForResourceCreationForm() = {

        eventually {
            driver.findElement(By.xpath("//form[@class='propedit']//input[@value='Save']")).click()
        }

    }

}
