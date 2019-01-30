/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

/**
  * Tests the internationalization of the SALSAH GUI.
  */
class InternationalizationSpec extends SalsahSpec {
    /*

       We use the Selenium API directly instead of the ScalaTest wrapper, because the Selenium API is more
       powerful and more efficient.

       See https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html
       for more documentation.

     */

    private val headless = settings.headless
    private val pageUrl = s"http://${settings.hostName}:${settings.httpPort}/index.html"
    private val page = new SalsahPage(pageUrl, headless)

    // How long to wait for results obtained using the 'eventually' function
    implicit private val patienceConfig = page.patienceConfig

    private val rdfDataObjectsJsonList: String =
        """
            [
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/0001/anything"}
            ]
        """

    // In order to run these tests, start `webapi` using the option `allowReloadOverHTTP`

    "The SALSAH home page" should {
        "load test data" in {
            loadTestData(rdfDataObjectsJsonList)
        }

        "change the user interface language" in {
            page.open()
            page.getSimpleSearchField.getAttribute("value") should be("Search")
            page.changeLanguage("fr")
            page.getSimpleSearchField.getAttribute("value") should be("Recherche")
        }

        "close the browser" in {
            page.quit()
        }

    }
}
