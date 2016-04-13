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

import org.openqa.selenium.WebElement
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
    implicit val patienceConfig = PatienceConfig(timeout = scaled(2.seconds), interval = scaled(20.millis))

    val page = new SalsahPage

    "The SALSAH home page" should {

        "have the correct title" in {
            page.load()
            page.getPageTitle should be ("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "do a simple search" in {

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
        }

        // Uncomment this if you want the browser to close after the test completes.

        /*"close the browser" in {
            page.quit()
        }*/

    }
}
