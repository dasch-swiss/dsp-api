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

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.knora.salsah.SettingsImpl
import org.openqa.selenium.{By, WebElement}
import org.scalatest._
import org.scalatest.concurrent.Eventually._
import spray.client.pipelining._
import spray.http.MediaTypes._
import spray.http.{HttpRequest, HttpResponse, _}

import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * Tests the SALSAH web interface using Selenium.
  */
class SearchAndEditSpec extends SalsahSpec {
    /*

       We use the Selenium API directly instead of the ScalaTest wrapper, because the Selenium API is more
       powerful and more efficient.

       See https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html
       for more documentation.

     */

    private val page = new SalsahPage

    // How long to wait for results obtained using the 'eventually' function
    implicit private val patienceConfig = page.patienceConfig

    implicit private val timeout = Timeout(180.seconds)

    implicit private val system = ActorSystem()

    implicit private val dispatcher = system.dispatcher

    private val rdfDataObjectsJsonList: String =
        """
            [
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
                {"path": "_test_data/ontologies/images-demo-onto.ttl", "name": "http://www.knora.org/ontology/images"},
                {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/images"},
                {"path": "_test_data/ontologies/beol-onto.ttl", "name": "http://www.knora.org/ontology/beol"},
                {"path": "_test_data/ontologies/anything-onto.ttl", "name": "http://www.knora.org/ontology/anything"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/anything"},
                {"path": "_test_data/all_data/biblio-data.ttl", "name": "http://www.knora.org/data/biblio"},
                {"path": "_test_data/ontologies/biblio-onto.ttl", "name": "http://www.knora.org/ontology/biblio"}
            ]
        """

    // In order to run these tests, start `webapi` using the option `allowResetTriplestoreContentOperationOverHTTP`


    def doZeitgloeckleinSearch() = {

        val searchField: WebElement = page.getSimpleSearchField
        searchField.clear()
        searchField.sendKeys("Zeitglöcklein\n")

        val header = page.getSearchResultHeader

        assert(header.contains("Total of 3 hits"))

        val rows = page.getExtendedSearchResultRows

        val row1Text = page.getSearchResultRowText(rows.head)

        assert(row1Text.contains("Zeitglöcklein des Lebens und Leidens Christi"))

    }

    "The SALSAH home page" should {
        "load test data" in {
            loadTestData(rdfDataObjectsJsonList)
        }

        "have the correct title" in {
            page.load()
            page.getPageTitle should be ("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "log in as root" in {

            page.load()

            page.doLogin("root", "test")

            eventually {
                // check if login has succeeded
                // search for element with id 'dologout'
                page.driver.findElement(By.id("dologout"))
            }
        }


        "do a simple search for 'Zeitglöcklein' and open a search result row representing a page" in {

            page.load()

            doZeitgloeckleinSearch()

            val rows = page.getExtendedSearchResultRows

            // open the second row representing a page
            rows(1).click()

            val window = page.getWindow(1)


            // drag and drop the window
            page.dragWindow(window, 90, 10)


        }

        "do a simple search for 'Zeitglöcklein' and open a search result row representing a book" in {

            page.load()

            doZeitgloeckleinSearch()

            val rows = page.getExtendedSearchResultRows

            // open the first search result representing a book
            rows.head.click()

            val window = page.getWindow(1)


            page.dragWindow(window, 90, 10)

            // click next page twice
            for (i <- 1 to 2) {
                eventually {
                    window.findElement(By.xpath("//div[@class='nextImage']")).click()
                }

            }

        }

        "do an extended search for restype book containing 'Zeitglöcklein in the title'" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/incunabula#book")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#title")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("LIKE")

            page.getValueField(1).sendKeys("Zeitglöcklein")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 2, "There should be two result rows")

        }


        "do an extended search for restype region and open a region" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/knora-base#Region")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            rows(1).click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)


        }

        "do an extended search for restype page with seqnum 1 belonging to a book containing 'Narrenschiff' in its title" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/incunabula#page")

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

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/images#bild")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/images#titel")

            var selections = page.getHierarchicalListSelectionsInExtendedSearch(1)

            val firstSel = selections.head

            // list node from selection "HIERARCHICAL LISTS" in images-demo-data
            val listNodeSport = "http://data.knora.org/lists/71a1543cce"

            firstSel.selectByValue(listNodeSport)

            // refresh the selection
            selections = page.getHierarchicalListSelectionsInExtendedSearch(1)

            val secondSel = selections(1)

            // list node from selection "HIERARCHICAL LISTS" in images-demo-data
            val listNodeFliegen = "http://data.knora.org/lists/d1fa87bbe3"

            secondSel.selectByValue(listNodeFliegen)

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 5, "There should be five result rows")

        }

        "do an extended search for a book with the exact publication date Julian 1497-08-01" in {
            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/incunabula#book")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#pubdate")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("EQ")

            val dateForm = page.getDateFormInExtendedSearchForm(1)

            val calsel = page.getCalSelection(dateForm)

            calsel.selectByValue("JULIAN")

            val monthsel = page.getMonthSelectionInExtendedSearchForm(dateForm)

            monthsel.selectByValue("8")

            val days = page.getDaysInExtendedSearchForm(dateForm = dateForm)

            days.head.click()

            val yearsel = page.getYearFieldInExtendedSearchForm(dateForm)

            yearsel.clear()
            yearsel.sendKeys("1497")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 2, "There should be two result rows")


        }

        "do an extended search for a book with the period Julian 1495 as publication date" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/incunabula#book")

            page.getExtendedSearchSelectionByName(1, "selprop").selectByValue("http://www.knora.org/ontology/incunabula#pubdate")

            page.getExtendedSearchSelectionByName(1, "compop").selectByValue("EQ")

            val dateForm = page.getDateFormInExtendedSearchForm(1)

            page.makePeriod(dateForm)

            val calsel = page.getCalSelection(dateForm)

            calsel.selectByValue("JULIAN")

            //
            // start date
            //
            val monthsel1 = page.getMonthSelectionInExtendedSearchForm(dateForm, 1)

            // choose January
            monthsel1.selectByValue("1")

            val days1 = page.getDaysInExtendedSearchForm(dateForm, 1)

            // choose the first day of the month
            days1.head.click()

            val yearsel1 = page.getYearFieldInExtendedSearchForm(dateForm, 1)

            yearsel1.clear()
            yearsel1.sendKeys("1495")


            //
            // end date
            //
            val monthsel2 = page.getMonthSelectionInExtendedSearchForm(dateForm, 2)

            monthsel2.selectByValue("12")

            val days2 = page.getDaysInExtendedSearchForm(dateForm, 2)

            // choose the last day of the month (31st)
            days2(30).click()

            val yearsel2 = page.getYearFieldInExtendedSearchForm(dateForm, 2)

            yearsel2.clear()
            yearsel2.sendKeys("1495")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            assert(rows.length == 3, "There should be three result rows")

        }

        "change the publication date of a book" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/incunabula#book")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            // open page of a book
            rows.head.click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val pubdateField = editFields(10)

            page.clickEditButton(pubdateField)

            //
            // start date
            //
            val monthsel1 = page.getMonthSelection(pubdateField, 1)

            // choose February
            monthsel1.selectByValue("2")

            val days1 = page.getDays(pubdateField, 1)

            // choose the second day of the month
            days1(1).click()

            val yearsel1 = page.getYearField(pubdateField, 1)

            yearsel1.clear()
            yearsel1.sendKeys("1495")

            page.clickSaveButton(pubdateField)


            // use eventually here because it requires several attempts to get the up to date value container
            eventually {
                // read the new value back
                val pubdateValueContainer = page.getValueContainer(pubdateField)

                val pubdateValue = pubdateValueContainer.getText

                val found = pubdateValue.contains("Sat 2. Feb 1495")

                if (!found) throw new Exception

            }



        }

        "edit the seqnum and the pagenumber of a page" in {

            page.load()

            doZeitgloeckleinSearch()

            val rows = page.getExtendedSearchResultRows

            // open page of a book
            rows(1).click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            //
            // seqnum
            //

            // get the field representing the seqnum of the page
            val seqnumField = editFields(10)

            page.clickEditButton(seqnumField)

            page.clickOnSpinboxUp(seqnumField)

            page.clickSaveButton(seqnumField)

            // read the new value back
            val seqnumValueContainer = page.getValueContainer(seqnumField)

            val seqnumValue = seqnumValueContainer.getText

            assert(seqnumValue.contains("2"), s"seqnum should be 2, but is $seqnumValue")


            //
            // pagenum
            //

            // get the field representing the pagenum of the page
            val pagenumField = editFields(2)

            page.clickEditButton(pagenumField)

            // get the input field
            val input = page.getInputField(pagenumField)

            input.clear()

            input.sendKeys("test")

            page.clickSaveButton(pagenumField)

            // read the new value back
            val pagenumValueContainer = page.getValueContainer(pagenumField)

            val pagenumValue = pagenumValueContainer.getText

            assert(pagenumValue.contains("test"), s"pagnum should be 'test', but is $pagenumValue")

        }

        "add a new creator to a book" in {

            page.load()

            doZeitgloeckleinSearch()

            val rows = page.getExtendedSearchResultRows

            // open a book
            rows.head.click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            // get the field representing the seqnum of the page
            val creatorField = editFields(0)

            page.clickAddButton(creatorField)

            // get the input field
            val input = page.getInputField(creatorField)

            input.clear()

            input.sendKeys("Tobiasus")

            page.clickSaveButton(creatorField)

            // read the new value back
            // get the second container because there is already one existing value
            val creatorValueContainer = page.getValueContainer(creatorField, 2)

            val creatorValue = creatorValueContainer.getText

            assert(creatorValue.contains("Tobiasus"), s"$creatorValue")

        }

        "edit the description of a page" in {

            page.load()

            doZeitgloeckleinSearch()

            val rows = page.getExtendedSearchResultRows

            // open a page
            rows(1).click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val descriptionField = editFields(7)

            page.clickEditButton(descriptionField)

            val ckeditor = eventually {
                page.findCkeditor(descriptionField)
            }

            ckeditor.sendKeys("my text")

            page.clickSaveButton(descriptionField)

            // read the new value back
            val seqnumValueContainer = page.getValueContainer(descriptionField)


            assert(descriptionField.getText.substring(0, 7) == "my text")
        }

        "change the partof property of a page" in {

            page.load()

            doZeitgloeckleinSearch()

            val rows = page.getExtendedSearchResultRows

            // open a page
            rows(1).click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val partOfField = editFields(1)

            page.clickEditButton(partOfField)

            val input = page.getSearchBoxInputField(partOfField)

            input.sendKeys("Narrenschiff")

            page.chooseElementFromSearchbox(2)

            page.clickSaveButton(partOfField)

            // read the new value back
            val partOfValueContainer = page.getValueContainer(partOfField)

            val partOfValue = partOfValueContainer.getText

            assert(partOfValue.contains("Narrenschiff"), s"$partOfValue")

        }

        "change the season property of a image:bild to summer" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/images#bild")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            rows(4).click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val seasonField = editFields(14)

            page.clickEditButton(seasonField)

            val seasons = page.getRadioButtons(seasonField)

            // summer is the first item
            seasons.head.click()

            page.clickSaveButton(seasonField)

            // read the new value back
            val seasonValueContainer = page.getValueContainer(seasonField)

            val seasonValue = seasonValueContainer.getText

            assert(seasonValue.contains("Sommer"), s"$seasonValue")

        }

        "add a season to a image:bild" in {

            page.load()

            page.clickExtendedSearchButton()

            page.selectRestype("http://www.knora.org/ontology/images#bild")

            page.submitExtendedSearch

            val rows = page.getExtendedSearchResultRows

            rows(4).click()

            val window = page.getWindow(1)


            // get metadata section
            val metadataSection: WebElement = page.getMetadataSection(window)

            // get a list of editing fields
            val editFields = page.getEditingFieldsFromMetadataSection(metadataSection)

            val seasonField = editFields(14)

            page.clickAddButton(seasonField)

            val seasons = page.getRadioButtons(seasonField)

            // winter is the second element in the list
            seasons(1).click()

            page.clickSaveButton(seasonField)

            // read the new value back
            val seasonValueContainer = page.getValueContainer(seasonField, 2)

            val seasonValue = seasonValueContainer.getText

            assert(seasonValue.contains("Winter"), s"$seasonValue")



        }


        // Uncomment this if you want the browser to close after the test completes.

        /*"close the browser" in {
            page.quit()
        }*/

    }
}
