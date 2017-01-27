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
import org.openqa.selenium.{By, WebElement}
import org.scalatest.concurrent.Eventually._

import scala.concurrent.duration._

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

    private val page = new SalsahPage

    // How long to wait for results obtained using the 'eventually' function
    implicit private val patienceConfig = page.patienceConfig

    implicit private val timeout = Timeout(180.seconds)

    implicit private val system = ActorSystem()

    implicit private val dispatcher = system.dispatcher

    private val rdfDataObjectsJsonList: String =
        """
            [
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
                {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/images"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/anything"},
                {"path": "_test_data/all_data/biblio-data.ttl", "name": "http://www.knora.org/data/biblio"}
            ]
        """

    val rootEmail = "root@example.com"
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")

    val anythingUserEmail = "anything.user01@example.org"
    val anythingUserEmailEnc = java.net.URLEncoder.encode(anythingUserEmail, "utf-8")

    // In order to run these tests, start `webapi` using the option `allowResetTriplestoreContentOperationOverHTTP`

    "The SALSAH home page" should {
        "load test data" in {
            loadTestData(rdfDataObjectsJsonList)
        }


        "have the correct title" in {
            page.load()
            page.getPageTitle should be("System for Annotation and Linkage of Sources in Arts and Humanities")

        }


        "log in as anything user" in {

            page.load()

            page.doLogin(anythingUserEmail, "test")

            eventually {
                // check if login has succeeded

                page.checkForUserdata


            }
        }


        "create a resource of type images:person" in {

            page.load()

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/images#person")

            val label: WebElement = page.getFormFieldByName("__LABEL__")

            label.sendKeys("Robin Hood")

            val firstname = page.getFormFieldByName("http://www.knora.org/ontology/images#firstname")

            firstname.sendKeys("Robin")

            val familyname: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/images#lastname")

            familyname.sendKeys("Hood")

            val address: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/images#address")

            address.sendKeys("Sherwood Forest")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)


        }

        "create a resource of type anything:thing" in {

            page.load()

            page.clickAddResourceButton()

            page.selectVocabulary("0") // select all

            val restypes = page.selectRestype("http://www.knora.org/ontology/anything#Thing")

            val label: WebElement = page.getFormFieldByName("__LABEL__")

            label.sendKeys("Testding")

            val floatVal =  page.getFormFieldByName("http://www.knora.org/ontology/anything#hasDecimal")

            floatVal.sendKeys("5.3")

            val textVal =  page.getFormFieldByName("http://www.knora.org/ontology/anything#hasText")

            textVal.sendKeys("Dies ist ein Test")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)


        }

        "create another resource of type anything:thing" in {

            page.load()

            page.clickAddResourceButton()

            page.selectVocabulary("0") // select all

            val restypes = page.selectRestype("http://www.knora.org/ontology/anything#Thing")

            val label: WebElement = page.getFormFieldByName("__LABEL__")

            label.sendKeys("ein zweites Testding")

            val floatVal =  page.getFormFieldByName("http://www.knora.org/ontology/anything#hasDecimal")

            floatVal.sendKeys("5.7")

            val textVal =  page.getFormFieldByName("http://www.knora.org/ontology/anything#hasText")

            textVal.sendKeys("Dies ist auch ein Test")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)


        }

        "close the browser" in {
            page.quit()
        }

    }
}
