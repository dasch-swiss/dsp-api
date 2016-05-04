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
import scala.collection.JavaConversions._
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
    implicit val patienceConfig = PatienceConfig(timeout = scaled(5.seconds), interval = scaled(20.millis))

    val page = new SalsahPage

    "The SALSAH home page" should {

        "have the correct title" in {
            page.load()
            page.getPageTitle should be ("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "log in as root" in {

            page.doLogin("root", "test")

            eventually {
                // check if login has succeeded
                // search for element with id 'dologout'
                page.driver.findElement(By.id("dologout"))
            }
        }

        "do a simple search for 'Zeitglöcklein' and open a search result row representing a page, and move it" in {

            val searchField: WebElement = page.getSimpleSearchField
            searchField.clear()
            searchField.sendKeys("Zeitglöcklein\n")

            // Use 'eventually' to test results that may take time to appear.
            val resultDiv: WebElement = eventually {
                page.getSearchResultDiv
            }

            val resultHeader: String = eventually {
                page.getSearchResultHeader(resultDiv)
            }

            assert(resultHeader.contains("Total of 3 hits"))

            val firstResult: String = eventually {
                page.getFirstSearchResult(resultDiv)
            }

            assert(firstResult.contains("Zeitglöcklein des Lebens und Leidens Christi"))

            eventually {
                // open second element in list
                page.openResult(resultDiv, 1)
            }

            val window = eventually {
                page.getWindow(1)
            }

            page.dragWindow(window, 90, 10)

        }

        /*"edit the description of a page" in {

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
        }*/


        // Uncomment this if you want the browser to close after the test completes.

        /*"close the browser" in {
            page.quit()
        }*/

    }
}
