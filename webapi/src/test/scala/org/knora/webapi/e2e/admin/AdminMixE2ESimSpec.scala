/*
/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import io.gatling.core.Predef.{forAll, global, rampUsers, scenario, _}
import io.gatling.http.Predef.{http, status}
import org.knora.webapi.E2ESimSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import scala.concurrent.duration._

/**
 * Simulation Scenario for testing the admin endpoints.
 *
 * This simulation scenario accesses the users, groups, and projects endpoint.
 */
class AdminMixE2ESimSpec extends E2ESimSpec {

    override lazy val rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]

    val protobolBuilder = http
            .baseURL("http://localhost:3333")

    val users = scenario("Users")
            .exec(
                http("Get all users")
                        .get("/admin/users")
                        .check(status.is(200))
            )

    val groups = scenario("Groups")
            .exec(
                http("Get all groups")
                        .get("/admin/groups")
                        .check(status.is(200))
            )

    val projects = scenario("Projects")
            .exec(
                http("Get all projects")
                        .get("/admin/projects")
                        .check(status.is(200))
            )

    val injections = Seq(rampUsers(500) over 5.seconds)

    val assertions = Seq(
        global.responseTime.mean.lt(1500)
        , forAll.failedRequests.count.lt(1)
    )

    setUp(
        users.inject(injections).protocols(protobolBuilder),
        groups.inject(injections).protocols(protobolBuilder),
        projects.inject(injections).protocols(protobolBuilder)
    ).assertions(assertions)

}
 */
