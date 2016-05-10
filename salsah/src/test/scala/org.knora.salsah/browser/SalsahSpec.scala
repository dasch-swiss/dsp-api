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

import org.openqa.selenium.{By, WebElement}
import org.scalatest._
import org.scalatest.concurrent.Eventually._

import scala.concurrent.duration._

/**
  * Tests the SALSAH web interface using Selenium.
  */
class SalsahSpec extends WordSpecLike with ShouldMatchers {
    /*

       We use the Selenium API directly instead of the ScalaTest wrapper, because the Selenium API is more
       powerful and more efficient.

       See https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html
       for more documentation.

     */

    // How long to wait for results obtained using the 'eventually' function
    implicit val patienceConfig = PatienceConfig(timeout = scaled(10.seconds), interval = scaled(20.millis))

    val page = new SalsahPage

    def doZeitgloeckleinSearch = {

        val searchField: WebElement = page.getSimpleSearchField
        searchField.clear()
        searchField.sendKeys("Zeitglöcklein\n")

        val header = page.getSearchResultHeader

        assert(header.contains("Total of 3 hits"))

        val rows = page.getExtendedSearchResultRows

        val row1Text = page.getSearchResultRowText(rows(0))

        assert(row1Text.contains("Zeitglöcklein des Lebens und Leidens Christi"))

    }

    "The SALSAH home page" should {

        "have the correct title" in {
            page.load()
            page.getPageTitle should be ("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "log in as root" in {

            page.load

            page.doLogin("root", "test")

            eventually {
                // check if login has succeeded
                // search for element with id 'dologout'
                page.driver.findElement(By.id("dologout"))
            }
        }

        "do a simple search for 'Zeitglöcklein' and open a search result row representing a page" in {

            page.load()

            doZeitgloeckleinSearch

            val rows = page.getExtendedSearchResultRows

            // open the second row representing a page
            /*rows(1).click()

            val window = eventually {
                page.getWindow(1)
            }

            // drag and drop the window
            page.dragWindow(window, 90, 10)
            */

        }

        "do a simple search for 'Zeitglöcklein' and open a search result row representing a book" in {

            page.load()

            doZeitgloeckleinSearch

            val rows = page.getExtendedSearchResultRows

            // open the first search result representing a book
            /*rows(0).click()

            val window = eventually {
                page.getWindow(1)
            }

            page.dragWindow(window, 90, 10)

            // click next page twice
            for (i <- 1 to 2) {
                eventually {
                    window.findElement(By.xpath("//div[@class='nextImage']")).click()
                }

            }*/

        }

        "do an extended search for restype book containing 'Zeitglöcklein in the title'" in {

            page.load()

            page.clickExtendedSearchButton

            page.selectExtendedSearchRestype("http://www.knora.org/ontology/incunabula#book")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#title")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("LIKE")

            page.getValueField(1).sendKeys("Zeitglöcklein")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 2, "There should be two result rows")

        }

        "do an extended search for restype page with seqnum 1 belonging to a book containing 'Narrenschiff' in its title" in {

            page.load

            page.clickExtendedSearchButton

            page.selectExtendedSearchRestype("http://www.knora.org/ontology/incunabula#page")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#seqnum")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("EQ")

            page.getValueField(1).sendKeys("1")

            page.addPropertySetToExtendedSearch(2)

            page.getExtendedSearchSelectionByName(2, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#partOf")

            page.getExtendedSearchSelectionByName(2, "compop").selectByValue("EQ")

            page.getValueField(2).sendKeys("Narrenschiff")

            page.chooseElementFromSearchbox(1)

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 1, "There should be one result row")

        }

        "do an extended search for images:bild involving a hierarchical list selection for its title" in {

            page.load

            page.clickExtendedSearchButton

            page.selectExtendedSearchRestype("http://www.knora.org/ontology/images#bild")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/images#titel")

            var selections = page.getHierarchicalListSelections(1)

            val firstSel = selections(0)

            firstSel.selectByValue("http://data.knora.org/lists/71a1543cce")

            // refresh the selection
            selections = page.getHierarchicalListSelections(1)

            val secondSel = selections(1)

            secondSel.selectByValue("http://data.knora.org/lists/d1fa87bbe3")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 5, "There should be five result rows")

        }

        "do an extended search for a book with the exact publication date Julian 1497-08-01" in {
            page.load

            page.clickExtendedSearchButton

            page.selectExtendedSearchRestype("http://www.knora.org/ontology/incunabula#book")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#pubdate")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("EQ")

            val dateForm = page.getDateForm(1)

            val calsel = page.getCalSelection(dateForm)

            calsel.selectByValue("JULIAN")

            val monthsel = page.getMonthSelection(dateForm)

            monthsel.selectByValue("8")

            val days = page.getDays(dateForm = dateForm)

            days(0).click()

            val yearsel = page.getYearField(dateForm)

            yearsel.clear
            yearsel.sendKeys("1497")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 2, "There should be two result rows")


        }

        "do an extended search for a book with the period Julian 1495 as publication date" in {

            page.load

            page.clickExtendedSearchButton

            page.selectExtendedSearchRestype("http://www.knora.org/ontology/incunabula#book")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#pubdate")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("EQ")

            val dateForm = page.getDateForm(1)

            page.makePeriod(dateForm)

            val calsel = page.getCalSelection(dateForm)

            calsel.selectByValue("JULIAN")

            //
            // start date
            //
            val monthsel1 = page.getMonthSelection(dateForm, 1)

            // choose January
            monthsel1.selectByValue("1")

            val days1 = page.getDays(dateForm, 1)

            // choose the first day of the month
            days1(0).click()

            val yearsel1 = page.getYearField(dateForm, 1)

            yearsel1.clear
            yearsel1.sendKeys("1495")


            //
            // end date
            //
            val monthsel2 = page.getMonthSelection(dateForm, 2)

            monthsel2.selectByValue("12")

            val days2 = page.getDays(dateForm, 2)

            // choose the last day of the month (31st)
            days2(30).click()

            val yearsel2 = page.getYearField(dateForm, 2)

            yearsel2.clear
            yearsel2.sendKeys("1495")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 3, "There should be three result rows")

        }

        "edit the seqnum and the pagenumber of a page" in {

            page.load()

            doZeitgloeckleinSearch

            val rows = page.getExtendedSearchResultRows

            // open page of a book
            rows(1).click()

            val window = eventually {
                page.getWindow(1)
            }

            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            // get the field representing the seqnum of the page
            val seqnumField = editFields(10)

            // get the edit button for this field (pen)
            val editButton = seqnumField.findElement(By.xpath("div//img[1]"))

            editButton.click()

            // click spin box to increase value by one
            seqnumField.findElement(By.xpath("div/span/span/img[1]")).click()

            // get save button (disk)
            val saveButton = seqnumField.findElement(By.xpath("div/img[1]"))

            saveButton.click()

            // read the new value back
            val value = eventually {
                val value = seqnumField.findElement(By.xpath("div[contains(@class, 'value_container')]"))
                if (value.getText.isEmpty) throw new Exception
                value
            }

            val seqnumValue = value.getText

            assert(seqnumValue.contains("2"), s"$seqnumValue")

            // get the field representing the pagenum of the page
            val pagenumField = editFields(3)

            // get the edit button for this field (pen)
            val editButton2 = pagenumField.findElement(By.xpath("div//img[1]"))

            editButton2.click()

            // get the input field
            val input = eventually {
                pagenumField.findElement(By.xpath("//input[@class='propedit']"))
            }

            input.clear

            input.sendKeys("test")

            // get save button (disk)
            val saveButton2 = pagenumField.findElement(By.xpath("div/img[1]"))

            saveButton2.click()

            // read the new value back
            val value2 = eventually {
                val value = pagenumField.findElement(By.xpath("div[contains(@class, 'value_container')]"))
                if (value.getText.isEmpty) throw new Exception
                value
            }

            val pagenumValue = value2.getText

            assert(pagenumValue.contains("test"), s"$pagenumValue")


        }

        "add a new creator to a book" in {

            page.load()

            doZeitgloeckleinSearch

            val rows = page.getExtendedSearchResultRows

            // open a book
            rows(0).click()

            val window = eventually {
                page.getWindow(1)
            }

            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            // get the field representing the seqnum of the page
            val creatorField = editFields(1)

            // get the edit button for this field (pen)
            val addButton = creatorField.findElement(By.xpath("div[2]/img[1]"))

            addButton.click()

            val input = eventually {
                creatorField.findElement(By.xpath("//input[@class='propedit']"))
            }

            input.sendKeys("Tobiasus")

            // get save button (disk)
            val saveButton = creatorField.findElement(By.xpath("div[2]/img[1]"))

            saveButton.click()

            // read the new value back
            val value = eventually {
                val value = creatorField.findElement(By.xpath("div[2][contains(@class, 'value_container')]"))
                if (value.getText.isEmpty) throw new Exception
                value
            }

            val creatorValue = value.getText

            assert(creatorValue.contains("Tobiasus"), s"$creatorValue")

        }

        "edit the description of a page" in {

            page.load()

            doZeitgloeckleinSearch

            val rows = page.getExtendedSearchResultRows

            // open a page
            rows(1).click()

            val window = eventually {
                page.getWindow(1)
            }

            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val descriptionField = editFields(7)

            val editButton = descriptionField.findElement(By.xpath("div//img[1]"))

            editButton.click()

            val ckeditor = eventually {
                page.findCkeditor(descriptionField)
            }

            ckeditor.sendKeys("my text")

            val saveButton = descriptionField.findElement(By.xpath("div//img[1]"))

            saveButton.click()

            val paragraph = eventually {
                descriptionField.findElement(By.xpath("div//p"))
            }

            assert(paragraph.getText.substring(0, 7) == "my text")
        }

        "change the partof property of a page" in {

            page.load()

            doZeitgloeckleinSearch

            val rows = page.getExtendedSearchResultRows

            // open a page
            rows(1).click()

            val window = eventually {
                page.getWindow(1)
            }

            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val partOfField = editFields(2)

            val editButton = partOfField.findElement(By.xpath("div//img[contains(@src,'edit.png')]"))

            editButton.click()

            val input = eventually {
                partOfField.findElement(By.xpath("//input[@class='__searchbox']"))
            }

            input.sendKeys("Narrenschiff")

            page.chooseElementFromSearchbox(2)

            val saveButton = partOfField.findElement(By.xpath("div//img[1]"))

            saveButton.click()

            // read the new value back
            val value = eventually {
                val value = partOfField.findElement(By.xpath("div[contains(@class, 'value_container')]"))
                if (value.getText.isEmpty) throw new Exception
                value
            }

            val partOfValue = value.getText

            assert(partOfValue.contains("Narrenschiff"), s"$partOfValue")

        }


        // Uncomment this if you want the browser to close after the test completes.

        /*"close the browser" in {
            page.quit()
        }*/

    }
}
