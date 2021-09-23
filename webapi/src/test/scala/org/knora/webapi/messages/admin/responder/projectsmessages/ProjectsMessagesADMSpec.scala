/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.{BadRequestException, OntologyConstraintException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object ProjectsMessagesADMSpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test subclasses of the [[ProjectsResponderRequestADM]] trait.
 */
class ProjectsMessagesADMSpec extends CoreSpec(ProjectsMessagesADMSpec.config) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The CreateProjectApiRequestADM case class" should {

    "return a 'BadRequest' when project description is not supplied" in {
      val caught = intercept[BadRequestException](
        CreateProjectApiRequestADM(
          shortname = "newproject5",
          shortcode = "1114",
          longname = Some("project longname"),
          description = Seq.empty[StringLiteralV2],
          keywords = Seq("keywords"),
          logo = Some("/fu/bar/baz.jpg"),
          status = true,
          selfjoin = false
        )
      )
      assert(caught.getMessage === "Project description needs to be supplied.")
    }

    "return 'BadRequest' if the supplied project IRI is not a valid IRI" in {
      val caught = intercept[BadRequestException](
        CreateProjectApiRequestADM(
          id = Some("invalid-project-IRI"),
          shortname = "newprojectWithInvalidIri",
          shortcode = "2222",
          longname = Some("new project with a custom invalid IRI"),
          description = Seq(StringLiteralV2("a project created with an invalid custom IRI", Some("en"))),
          keywords = Seq("projectInvalidIRI"),
          logo = Some("/fu/bar/baz.jpg"),
          status = true,
          selfjoin = false
        ).validateAndEscape
      )
      assert(caught.getMessage === "Invalid project IRI")
    }

    "return 'BadRequestException' if project 'shortname' during creation is missing" in {

      val caught = intercept[BadRequestException](
        CreateProjectApiRequestADM(
          shortname = "",
          shortcode = "1114",
          longname = Some("project longname"),
          description = Seq(StringLiteralV2(value = "project description", language = Some("en"))),
          keywords = Seq("keywords"),
          logo = Some("/fu/bar/baz.jpg"),
          status = true,
          selfjoin = false
        ).validateAndEscape
      )
      assert(caught.getMessage === s"Project shortname must be supplied.")
    }

    "return 'BadRequestException' if project 'shortcode' during creation is missing" in {

      val caught = intercept[BadRequestException](
        CreateProjectApiRequestADM(
          shortname = "newproject4",
          shortcode = "",
          longname = Some("project longname"),
          description = Seq(StringLiteralV2(value = "project description", language = Some("en"))),
          keywords = Seq("keywords"),
          logo = Some("/fu/bar/baz.jpg"),
          status = true,
          selfjoin = false
        ).validateAndEscape
      )
      assert(caught.getMessage === "Project shortcode must be supplied.")
    }

    "return 'BadRequestException' if project 'shortname' is not NCName valid" in {
      val invalidShortName = "abd%2"
      val caught = intercept[BadRequestException](
        CreateProjectApiRequestADM(
          shortname = invalidShortName,
          shortcode = "1114",
          longname = Some("project longname"),
          description = Seq(StringLiteralV2(value = "project description", language = Some("en"))),
          keywords = Seq("keywords"),
          logo = Some("/fu/bar/baz.jpg"),
          status = true,
          selfjoin = false
        ).validateAndEscape
      )
      assert(caught.getMessage === s"The supplied short name: '$invalidShortName' is not valid.")
    }

    "return 'BadRequestException' if project 'shortname' is not URL safe" in {
      val invalidShortName = "äbd2"
      val caught = intercept[BadRequestException](
        CreateProjectApiRequestADM(
          shortname = invalidShortName,
          shortcode = "1114",
          longname = Some("project longname"),
          description = Seq(StringLiteralV2(value = "project description", language = Some("en"))),
          keywords = Seq("keywords"),
          logo = Some("/fu/bar/baz.jpg"),
          status = true,
          selfjoin = false
        ).validateAndEscape
      )
      assert(caught.getMessage === s"The supplied short name: '$invalidShortName' is not valid.")
    }
  }

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

  "The ProjectIdentifierADM class" should {
    "return without throwing when the project IRI is valid" in {
      ProjectIdentifierADM(maybeIri =
        Some(SharedTestDataADM.incunabulaProject.id)
      ).value shouldBe SharedTestDataADM.incunabulaProject.id
      ProjectIdentifierADM(maybeIri =
        Some(SharedTestDataADM.defaultSharedOntologiesProject.id)
      ).value shouldBe SharedTestDataADM.defaultSharedOntologiesProject.id
      ProjectIdentifierADM(maybeIri =
        Some(SharedTestDataADM.systemProject.id)
      ).value shouldBe SharedTestDataADM.systemProject.id
    }

    "return a 'BadRequestException' when the project IRI is invalid" in {
      assertThrows[BadRequestException] {
        ProjectIdentifierADM(maybeIri = Some("http://not-valid.org"))
      }
    }
  }
}
