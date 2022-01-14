/*
/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.knora.webapi.E2ESimSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import scala.concurrent.duration._

/**
 * Simulation Scenario for testing the groups endpoints.
 *
 * This simulation scenario accesses the groups endpoint with
 * 1000 users concurrently.
 */
class GroupsADME2ESimSpec extends E2ESimSpec {

    override lazy val rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]

    val protobolBuilder = http
            .baseURL("http://localhost:3333")

    val groups = scenario("Users")
            .exec(
                http("Get all groups")
                .get("/admin/groups")
                .check(status.is(200))
            )

    val injections = Seq(rampUsers(1000) over 5.seconds)

    val assertions = Seq(
        global.responseTime.mean.lt(500)
        , forAll.failedRequests.count.lt(1)
    )

    setUp(
        groups.inject(injections).protocols(protobolBuilder)
    ).assertions(assertions)

}


 */
