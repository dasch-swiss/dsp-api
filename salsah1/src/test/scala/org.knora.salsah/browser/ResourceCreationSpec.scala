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

import org.openqa.selenium.WebElement

/**
  * Tests the SALSAH web interface using Selenium.
  */
class ResourceCreationSpec extends SalsahSpec {
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
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/0803/incunabula"},
                {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/00FF/images"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/0001/anything"},
                {"path": "_test_data/all_data/biblio-data.ttl", "name": "http://www.knora.org/data/0802/biblio"}
            ]
        """

    private val anythingUserEmail = "anything.user01@example.org"
    private val anythingUserFullName = "Anything User01"

    private val imagesUserEmail = "user02.user@example.com"
    private val imagesUserFullName = "User02 User"

    private val multiUserEmail = "multi.user@example.com"
    private val multiUserFullName = "Multi User"

    private val testPassword = "test"

    private val anythingProjectIri = "http://rdfh.ch/projects/0001"
    private val incunabulaProjectIri = "http://rdfh.ch/projects/0803"

    // In order to run these tests, start `webapi` using the option `allowReloadOverHTTP`

    "The SALSAH home page" should {
        "load test data" in {
            loadTestData(rdfDataObjectsJsonList)
        }

        "have the correct title" in {
            page.open()
            page.getPageTitle should be("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "log in as anything user" in {

            page.open()
            page.doLogin(email = anythingUserEmail, password = testPassword, fullName = anythingUserFullName)
            page.doLogout()

        }

        "create a resource of type images:person" in {

            page.open()

            page.doLogin(email = imagesUserEmail, password = testPassword, fullName = imagesUserFullName)

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/00FF/images#person")

            val label: WebElement = page.getFormFieldByName("__LABEL__")

            label.sendKeys("Robin Hood")

            val firstname: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/00FF/images#firstname")

            firstname.sendKeys("Robin")

            val familyname: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/00FF/images#lastname")

            familyname.sendKeys("Hood")

            val address: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/00FF/images#address")

            address.sendKeys("Sherwood Forest")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindowByID(1)

            page.doLogout()

        }

        "create a resource of type anything:thing" in {

            page.open()

            page.doLogin(email = anythingUserEmail, password = testPassword, fullName = anythingUserFullName)

            page.clickAddResourceButton()

            page.selectVocabulary("0") // select all

            page.selectRestype("http://www.knora.org/ontology/0001/anything#Thing")

            val resource1Label: WebElement = page.getFormFieldByName("__LABEL__")

            try {
                resource1Label.sendKeys("Testding")
            } catch {
                case _: org.openqa.selenium.StaleElementReferenceException =>
                    val resource1Label: WebElement = page.getFormFieldByName("__LABEL__")
                    resource1Label.sendKeys("Testding")
            }

            val resource1FloatVal = page.getFormFieldByName("http://www.knora.org/ontology/0001/anything#hasDecimal")

            resource1FloatVal.sendKeys("5.3")

            val resource1TextVal = page.getFormFieldByName("http://www.knora.org/ontology/0001/anything#hasText")

            resource1TextVal.sendKeys("Dies ist ein Test")

            val resource1DateVal = page.getFormFieldByName("http://www.knora.org/ontology/0001/anything#hasDate")

            val resource1Monthsel1 = page.getMonthSelection(resource1DateVal, 1)

            // choose 15 March 44 BCE

            resource1Monthsel1.selectByValue("3")

            val resource1Days1 = page.getDays(resource1DateVal, 1)
            resource1Days1(15).click()

            val resource1Yearsel1 = page.getYearField(resource1DateVal, 1)
            resource1Yearsel1.clear()
            resource1Yearsel1.sendKeys("44")

            val resource1Erasel1 = page.getEraSelection(resource1DateVal, 1)
            resource1Erasel1.selectByIndex(1)

            page.clickSaveButtonForResourceCreationForm()

            val resource1Window = page.getWindowByID(1)

            page.closeWindow(resource1Window)

            page.doLogout()

        }

        "close the browser" in {
            page.quit()
        }
    }
}
