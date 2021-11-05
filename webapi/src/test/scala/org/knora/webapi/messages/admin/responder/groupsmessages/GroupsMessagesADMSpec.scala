/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.groupsmessages

import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object GroupsMessagesADMSpec {
  val config = ConfigFactory.parseString("""
              akka.loglevel = "DEBUG"
              akka.stdout-loglevel = "DEBUG"
            """.stripMargin)
}

/**
 * This spec is used to test 'GroupAdminMessages'.
 */
class GroupsMessagesADMSpec extends CoreSpec(GroupsMessagesADMSpec.config) {

  "The CreateGroupsApiRequestADM case class" should {
    "return 'BadRequest' if the supplied 'id' is not a valid IRI" in {
      val caught = intercept[BadRequestException](
        CreateGroupApiRequestADM(
          id = Some("invalid-group-IRI"),
          name = "NewGroupWithInvalidCustomIri",
          descriptions = Seq(StringLiteralV2("A new group created with an invalid custom IRI")),
          project = SharedTestDataADM.IMAGES_PROJECT_IRI,
          status = true,
          selfjoin = false
        )
      )
      assert(caught.getMessage === "Invalid group IRI")
    }
  }
}
