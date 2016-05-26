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
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.{By, WebElement}
import org.scalatest._
import org.scalatest.concurrent.Eventually._
import spray.client.pipelining._
import spray.http.MediaTypes._
import spray.http.{HttpRequest, HttpResponse, _}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * Tests the SALSAH web interface using Selenium.
  */
class ResourceCreationSpec extends WordSpecLike with ShouldMatchers {
    /*

       We use the Selenium API directly instead of the ScalaTest wrapper, because the Selenium API is more
       powerful and more efficient.

       See https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html
       for more documentation.

     */

    val page = new SalsahPage

    // How long to wait for results obtained using the 'eventually' function
    implicit val patienceConfig = page.patienceConfig

    implicit val timeout = Timeout(180 seconds)

    implicit val system = ActorSystem()

    implicit val dispatcher = system.dispatcher

    val settings = new SettingsImpl(ConfigFactory.load())

    val rdfDataObjectsJsonList =
        """
            [
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/images-demo-onto.ttl", "name": "http://www.knora.org/ontology/images"},
                {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/images"},
                {"path": "_test_data/ontologies/anything-onto.ttl", "name": "http://www.knora.org/ontology/anything"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/anything"}
            ]
        """

    // In order to run these tests, start `webapi` using the option `allowResetTriplestoreContentOperationOverHTTP`

    "The SALSAH home page" should {
        "load test data" in {
            // define a pipeline function that gets turned into a generic [[HTTP Response]] (containing JSON)
            val pipeline: HttpRequest => Future[HttpResponse] = (
                addHeader("Accept", "application/json")
                    ~> sendReceive
                    ~> unmarshal[HttpResponse]
                )

            val loadRequest: HttpRequest = Post(s"${settings.baseKNORAUrl}/v1/store/ResetTriplestoreContent", HttpEntity(`application/json`, rdfDataObjectsJsonList))

            val loadRequestFuture: Future[HttpResponse] = for {
                postRequest <- Future(loadRequest)
                pipelineResult <- pipeline(postRequest)
            } yield pipelineResult

            val loadRequestResponse = Await.result(loadRequestFuture, Duration("180 seconds"))

            assert(loadRequestResponse.status == StatusCodes.OK)
        }



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


        "create a resource of type images:person" in {

            page.load()

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/images#person")

            val rows = page.getInputRowsForResourceCreationForm()

            val address = page.getInputForResourceCreationForm(rows(1))

            address.sendKeys("Musterstrasse 32")

            val place = page.getInputForResourceCreationForm(rows(2))

            place.sendKeys("Basel")

            val firstName = page.getInputForResourceCreationForm(rows(6))

            firstName.sendKeys("Testvorname")

            val familyName = page.getInputForResourceCreationForm(rows(9))

            familyName.sendKeys("Testperson")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)



        }

        "create a resource of type anything:thing" in {

            page.load()

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/anything#Thing")

            val rows = page.getInputRowsForResourceCreationForm()

            val floatVal =  page.getInputForResourceCreationForm(rows(2))

            floatVal.sendKeys("5.3")

            val textVal =  page.getInputForResourceCreationForm(rows(7))

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
