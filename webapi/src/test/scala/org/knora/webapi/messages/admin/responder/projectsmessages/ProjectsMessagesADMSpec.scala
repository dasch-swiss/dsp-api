/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import dsp.errors.BadRequestException
import dsp.errors.OntologyConstraintException
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

/**
 * This spec is used to test subclasses of the [[ProjectsResponderRequestADM]] trait.
 */
class ProjectsMessagesADMSpec extends CoreSpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The ChangeProjectApiRequestADM case class" should {
    "return a 'BadRequest' when everything is 'None" in {
      assertThrows[BadRequestException](
        ChangeProjectApiRequestADM(
          shortname = None,
          longname = None,
          description = None,
          keywords = None,
          logo = None,
          status = None,
          selfjoin = None
        )
      )
    }
  }

  "The ProjectADM case class" should {
    "return a 'OntologyConstraintException' when project description is not supplied" in {
      assertThrows[OntologyConstraintException](
        ProjectADM(
          id = "id",
          shortcode = "1111",
          shortname = "shortname",
          longname = None,
          description = Seq.empty[StringLiteralV2],
          keywords = Seq.empty[String],
          logo = None,
          ontologies = Seq.empty[IRI],
          status = true,
          selfjoin = false
        )
      )
    }
  }
}
