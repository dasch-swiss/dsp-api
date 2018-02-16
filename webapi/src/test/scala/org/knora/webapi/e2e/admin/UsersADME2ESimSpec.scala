package org.knora.webapi.e2e.admin

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.knora.webapi.E2ESimSpec

import scala.concurrent.duration._

class UsersADME2ESimSpec extends E2ESimSpec {

    val protobolBuilder = http
            .baseURL("http://localhost:3333")

    val users = scenario("Users")
            .exec(
                http("Get all users")
                .get("/admin/users")
                .check(status.is(200))
            )

    val injections = Seq(rampUsers(1000) over 5.seconds)

    val assertions = Seq(
        global.responseTime.mean.lt(800)
        , forAll.failedRequests.count.lt(1)
    )

    setUp(
        users.inject(injections).protocols(protobolBuilder)
    ).assertions(assertions)

}


