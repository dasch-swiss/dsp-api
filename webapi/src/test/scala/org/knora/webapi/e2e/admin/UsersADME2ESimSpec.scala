package org.knora.webapi.e2e.admin

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.knora.webapi.E2ESimSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import scala.concurrent.duration._
import scala.util.Random


/**
  * Simulation Scenario for testing the users endpoint.
  *
  * In this simulation scenario, concurrently, 1000 users retrieve a list of all users,
  * while additional 20 users register themselves. The mean response time should be less
  * then 300 ms (according to a quick google search, 200 ms are standard).
  */
class UsersADME2ESimSpec extends E2ESimSpec {

    // need to override this. before each test, the triplestore is automatically reloaded.
    // if you want more then the base data, then add here
    override val rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]

    val protocolBuilder = http.baseURL("http://localhost:3333")

    val feeder = Iterator.continually(
        Map(
            "email" -> (Random.alphanumeric.take(20).mkString + "@foo.com"),
            "givenName" -> Random.alphanumeric.take(10).mkString,
            "familyName" -> Random.alphanumeric.take(10).mkString,
            "password" -> Random.alphanumeric.take(35).mkString
        )
    )

    val getAllUsers = scenario("GetUsers")
            .exec(
                http("Get all users")
                .get("/admin/users")
                .check(status.is(200))
            )

    val createUser = scenario("CreateUser")
            .feed(feeder)
            .exec(
                http("Create a user")
                .post("/admin/users")
                .body(StringBody("""{ "email": "${email}", "givenName": "${givenName}", "familyName": "${familyName}", "password": "${password}", "status": true, "lang": "en", "systemAdmin": false }""")).asJSON
                .check(status.is(200))
            )

    val largeAndFast = Seq(rampUsers(1000) over 5.seconds)
    val lowAndSlow = Seq(rampUsers(25) over 10.seconds)

    val assertions = Seq(
        global.responseTime.mean.lt(300)
        , forAll.failedRequests.count.lt(1)
    )

    setUp(
        getAllUsers.inject(largeAndFast).protocols(protocolBuilder),
        createUser.inject(lowAndSlow).protocols(protocolBuilder)
    ).assertions(assertions)

}


