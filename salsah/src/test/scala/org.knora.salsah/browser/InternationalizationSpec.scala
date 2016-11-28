package org.knora.salsah.browser

import akka.actor.ActorSystem
import akka.util.Timeout

import scala.concurrent.duration._

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
                {"path": "_test_data/ontologies/anything-onto.ttl", "name": "http://www.knora.org/ontology/anything"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/anything"}
            ]
        """

    // In order to run these tests, start `webapi` using the option `allowResetTriplestoreContentOperationOverHTTP`

    "The SALSAH home page" should {
        "load test data" in {
            loadTestData(rdfDataObjectsJsonList)
        }

        "change the user interface language" in {
            page.load()
            page.getSimpleSearchField.getAttribute("value") should be("Search")
            page.changeLanguage("fr")
            page.getSimpleSearchField.getAttribute("value") should be("Recherche")
        }
    }
}
