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
import org.openqa.selenium.By
import org.scalatest._
import org.scalatest.concurrent.Eventually._
import spray.client.pipelining._
import spray.http.MediaTypes._
import spray.http.{HttpRequest, HttpResponse, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
                {"path": "_test_data/ontologies/images-demo-onto.ttl", "name": "http://www.knora.org/ontology/images"},
                {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/images"},
                {"path": "_test_data/ontologies/beol-onto.ttl", "name": "http://www.knora.org/ontology/beol"},
                {"path": "_test_data/ontologies/anything-onto.ttl", "name": "http://www.knora.org/ontology/anything"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/anything"}
            ]
        """

    // In order to run these tests, start `webapi` using the option `allowResetTriplestoreContentOperationOverHTTP`

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


        "create a resource of type images:person" in {

            page.load()

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/images#person")

            val rows = page.getInputRowsForResourceCreationForm()

            val address = page.getInputForResourceCreationForm(rows(0))

            address.sendKeys("Musterstrasse 32")

            val place = page.getInputForResourceCreationForm(rows(1))

            place.sendKeys("Basel")

            val firstName = page.getInputForResourceCreationForm(rows(5))

            firstName.sendKeys("Testvorname")

            val familyName = page.getInputForResourceCreationForm(rows(8))

            familyName.sendKeys("Testperson")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)



        }

        "create a resource of type anything:thing" in {

            page.load()

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/anything#Thing")

            val rows = page.getInputRowsForResourceCreationForm()

            val floatVal =  page.getInputForResourceCreationForm(rows(1))

            floatVal.sendKeys("5.3")

            val textVal =  page.getInputForResourceCreationForm(rows(10))

            textVal.sendKeys("Dies ist ein Test")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)


        }


        // Uncomment this if you want the browser to close after the test completes.

        /*"close the browser" in {
            page.quit()
        }*/

    }
}
