/*
/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.knora.webapi.E2ESimSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.tags.E2ETest

import scala.concurrent.duration._

/**
 * Example Simulation Scenario:
 *
 * This simulation scenario accesses the users endpoint with
 * 1000 users concurrently.
 */
class ExampleE2ESimSpec extends E2ESimSpec {

    override lazy val rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]

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


 */
