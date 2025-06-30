/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import sttp.client4.*
import sttp.model.*
import zio.json.*

import org.knora.webapi.E2ESpec
import org.knora.webapi.IRI
import org.knora.webapi.LanguageCode.DE
import org.knora.webapi.LanguageCode.EN
import org.knora.webapi.messages.admin.responder.IntegrationTestAdminJsonProtocol.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.model.*
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.*
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.MutableTestIri

class AdminProjectsEndpointsE2ESpec extends E2ESpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  s"The Projects Route 'admin/projects'" when {
    "used to query for project information" should {
      "return all projects excluding built-in system projects" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[ProjectsGetResponse](uri"/admin/projects").flatMap(_.assert200),
        )
        response.projects.size should be(6)
      }
      "return the information for a single project identified by iri" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[ProjectGetResponse](uri"/admin/projects/iri/$imagesProjectIri", rootUser),
        )
        assert(response.code === StatusCode.Ok)
      }
      "return the information for a single project identified by shortname" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[ProjectGetResponse](uri"/admin/projects/shortname/$imagesProjectShortname", rootUser),
        )
        assert(response.code === StatusCode.Ok)
      }
      "return the information for a single project identified by shortcode" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[ProjectGetResponse](uri"/admin/projects/shortcode/$imagesProjectShortcode", rootUser),
        )
        assert(response.code === StatusCode.Ok)
      }
      "return the project's restricted view settings using its IRI" in {
        val settings = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectRestrictedViewSettingsGetResponseADM](
              uri"/admin/projects/iri/$imagesProjectIri/RestrictedViewSettings",
            )
            .flatMap(_.assert200)
            .map(_.settings),
        )
        settings.size should be(Some("!512,512"))
        settings.watermark should be(false)
      }

      "return the project's restricted view settings using its shortname" in {
        val settings = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectRestrictedViewSettingsGetResponseADM](
              uri"/admin/projects/shortname/$imagesProjectShortname/RestrictedViewSettings",
            )
            .flatMap(_.assert200)
            .map(_.settings),
        )
        settings.size should be(Some("!512,512"))
        settings.watermark should be(false)
      }

      "return the project's restricted view settings using its shortcode" in {
        val settings = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectRestrictedViewSettingsGetResponseADM](
              uri"/admin/projects/shortcode/$imagesProjectShortcode/RestrictedViewSettings",
            )
            .flatMap(_.assert200)
            .map(_.settings),
        )
        settings.size should be(Some("!512,512"))
        settings.watermark should be(false)
      }
    }

    "given a custom Iri" should {
      val customProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/wahxssy1TDqPuSk6ee8EdQ")
      "CREATE a new project with the provided custom Iri" in {
        val createReq = ProjectCreateRequest(
          Some(customProjectIri),
          Shortname.unsafeFrom("newprojectWithIri"),
          Shortcode.unsafeFrom("3333"),
          Some(Longname.unsafeFrom("new project with a custom IRI")),
          List(Description.unsafeFrom(StringLiteralV2.from("a project created with a custom IRI", EN))),
          List(Keyword.unsafeFrom("projectIRI")),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
          None,
          Some(Set(LicenseIri.CC_BY_4_0)),
        )

        val result = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
              uri"/admin/projects",
              createReq,
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.project),
        )

        // check that the custom IRI is correctly assigned
        result.id should be(customProjectIri)

        // check the rest of project info
        result.shortcode.value should be("3333")
        result.shortname.value should be("newprojectWithIri")
        result.longname.map(_.value) should be(Some("new project with a custom IRI"))
        result.keywords should be(Seq("projectIRI"))
        result.description should be(Seq(StringLiteralV2.from("a project created with a custom IRI", Some("en"))))
        result.enabledLicenses should be(Set(LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0")))
        result.allowedCopyrightHolders.map(_.value) should be(
          Set("AI-Generated Content - Not Protected by Copyright", "Public Domain - Not Protected by Copyright"),
        )
      }

      "return 'BadRequest' if the supplied project IRI is not unique" in {
        val createReq = ProjectCreateRequest(
          Some(customProjectIri),
          Shortname.unsafeFrom("newWithDuplicateIri"),
          Shortcode.unsafeFrom("2222"),
          Some(Longname.unsafeFrom("new project with a duplicate custom invalid IRI")),
          List(Description.unsafeFrom(StringLiteralV2.from("a project created with a duplicate custom IRI", EN))),
          List(Keyword.unsafeFrom("projectDuplicateIRI")),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
          None,
        )
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
              uri"/admin/projects",
              createReq,
              rootUser,
            ),
        )

        response.code should be(StatusCode.BadRequest)
        response.body should be(Left(s"""{"message":"IRI: '$customProjectIri' already exists, try another one."}"""))
      }
    }

    "used to modify project information" should {
      val newProjectIri = new MutableTestIri

      "CREATE a new project and return the project info if the supplied shortname is unique" in {
        val createReq = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("newproject"),
          Shortcode.unsafeFrom("1111"),
          Some(Longname.unsafeFrom("project longname")),
          List(Description.unsafeFrom(StringLiteralV2.from("project description", EN))),
          List(Keyword.unsafeFrom("keywords")),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
          None,
        )
        val result = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
              uri"/admin/projects",
              createReq,
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.project),
        )

        result.shortname.value should be("newproject")
        result.shortcode.value should be("1111")
        result.longname.map(_.value) should be(Some("project longname"))
        result.description should be(Seq(StringLiteralV2.from(value = "project description", language = Some("en"))))
        result.keywords should be(Seq("keywords"))
        result.logo.map(_.value) should be(Some("/fu/bar/baz.jpg"))
        result.status should be(Status.Active)
        result.selfjoin should be(SelfJoin.CannotJoin)

        newProjectIri.set(result.id.value)
      }

      "return a 'BadRequest' if the supplied project shortname during creation is not unique" in {
        val createReq = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("newproject"),
          Shortcode.unsafeFrom("1112"),
          Some(Longname.unsafeFrom("project longname")),
          List(Description.unsafeFrom(StringLiteralV2.from("project description", EN))),
          List(Keyword.unsafeFrom("keywords")),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
          None,
        )
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
              uri"/admin/projects",
              createReq,
              rootUser,
            ),
        )
        response.code should be(StatusCode.BadRequest)
      }

      "UPDATE a project" in {
        val updateReq = ProjectUpdateRequest(
          longname = Some(Longname.unsafeFrom("updated project longname")),
          description = Some(List(Description.unsafeFrom(StringLiteralV2.from("updated project description", EN)))),
          keywords = Some(List(Keyword.unsafeFrom("updated"), Keyword.unsafeFrom("keywords"))),
          logo = Some(Logo.unsafeFrom("/fu/bar/baz-updated.jpg")),
          status = Some(Status.Active),
          selfjoin = Some(SelfJoin.CanJoin),
        )
        val result = UnsafeZioRun.runOrThrow(
          TestApiClient
            .putJson[ProjectOperationResponseADM, ProjectUpdateRequest](
              uri"/admin/projects/iri/${newProjectIri.asProjectIri}",
              updateReq,
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.project),
        )
        result.longname.map(_.value) should be(Some("updated project longname"))
        result.description should be(
          Seq(StringLiteralV2.from(value = "updated project description", language = Some("en"))),
        )
        result.keywords.sorted should be(Seq("updated", "keywords").sorted)
        result.logo.map(_.value) should be(Some("/fu/bar/baz-updated.jpg"))
        result.status should be(Status.Active)
        result.selfjoin should be(SelfJoin.CanJoin)
      }

      "UPDATE a project with multi-language description" in {
        val updateReq = ProjectUpdateRequest(
          description = Some(
            List(
              Description.unsafeFrom(StringLiteralV2.from("Test Project", EN)),
              Description.unsafeFrom(StringLiteralV2.from("Test Projekt", DE)),
            ),
          ),
        )
        val result = UnsafeZioRun.runOrThrow(
          TestApiClient
            .putJson[ProjectOperationResponseADM, ProjectUpdateRequest](
              uri"/admin/projects/iri/$imagesProjectIri",
              updateReq,
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.project),
        )
        result.description.size should be(2)
        result.description should contain(StringLiteralV2.from(value = "Test Project", language = Some("en")))
        result.description should contain(StringLiteralV2.from(value = "Test Projekt", language = Some("de")))
      }

      "DELETE a project" in {
        val result = UnsafeZioRun.runOrThrow(
          TestApiClient
            .deleteJson[ProjectOperationResponseADM](uri"/admin/projects/iri/$imagesProjectIri", rootUser)
            .flatMap(_.assert200)
            .map(_.project),
        )
        result.status should be(Status.Inactive)
      }
    }

    "used to query members [FUNCTIONALITY]" should {
      "return all members of a project identified by iri" in {
        val members = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectMembersGetResponseADM](
              uri"/admin/projects/iri/$imagesProjectIri/members",
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.members),
        )
        members.size should be(4)
      }

      "return all members of a project identified by shortname" in {
        val members = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectMembersGetResponseADM](
              uri"/admin/projects/shortname/$imagesProjectShortname/members",
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.members),
        )
        members.size should be(4)
      }

      "return all members of a project identified by shortcode" in {
        val members = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectMembersGetResponseADM](
              uri"/admin/projects/shortcode/$imagesProjectShortcode/members",
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.members),
        )
        members.size should be(4)
      }

      "return all admin members of a project identified by iri" in {
        val members = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectAdminMembersGetResponseADM](
              uri"/admin/projects/iri/$imagesProjectIri/admin-members",
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.members),
        )
        members.size should be(2)
      }

      "return all admin members of a project identified by shortname" in {
        val members = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectAdminMembersGetResponseADM](
              uri"/admin/projects/shortname/$imagesProjectShortname/admin-members",
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.members),
        )
        members.size should be(2)
      }

      "return all admin members of a project identified by shortcode" in {
        val members = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectAdminMembersGetResponseADM](
              uri"/admin/projects/shortcode/$imagesProjectShortcode/admin-members",
              rootUser,
            )
            .flatMap(_.assert200)
            .map(_.members),
        )
        members.size should be(2)
      }
    }

    "used to query members [PERMISSIONS]" should {
      "return members of a project to a SystemAdmin" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectMembersGetResponseADM](uri"/admin/projects/iri/$imagesProjectIri/members", rootUser),
        )
        assert(response.code === StatusCode.Ok)
      }

      "return members of a project to a ProjectAdmin" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectMembersGetResponseADM](uri"/admin/projects/iri/$imagesProjectIri/members", imagesUser01),
        )
        assert(response.code === StatusCode.Ok)
      }

      "return `Forbidden` for members of a project to a normal user" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectMembersGetResponseADM](uri"/admin/projects/iri/$imagesProjectIri/members", imagesUser02),
        )
        assert(response.code === StatusCode.Forbidden)
      }

      "return admin-members of a project to a SystemAdmin" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectAdminMembersGetResponseADM](
              uri"/admin/projects/iri/$imagesProjectIri/admin-members",
              rootUser,
            ),
        )
        assert(response.code === StatusCode.Ok)
      }

      "return admin-members of a project to a ProjectAdmin" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectAdminMembersGetResponseADM](
              uri"/admin/projects/iri/$imagesProjectIri/admin-members",
              imagesUser01,
            ),
        )
        assert(response.code === StatusCode.Ok)
      }

      "return `Forbidden` for admin-members of a project to a normal user" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectAdminMembersGetResponseADM](
              uri"/admin/projects/iri/$imagesProjectIri/admin-members",
              imagesUser02,
            ),
        )
        assert(response.code === StatusCode.Forbidden)
      }
    }

    "used to query keywords" should {
      "return all unique keywords for all projects" in {
        val keywords = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/Keywords", rootUser)
            .flatMap(_.assert200)
            .map(_.keywords),
        )
        keywords.size should be(21)
      }

      "return all keywords for a single project" in {
        val keywords = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/iri/$incunabulaProjectIri/Keywords", rootUser)
            .flatMap(_.assert200)
            .map(_.keywords),
        )
        keywords should be(incunabulaProject.keywords)
      }

      "return empty list for a project without keywords" in {
        val keywords = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/iri/$dokubibProjectIri/Keywords")
            .flatMap(_.assert200)
            .map(_.keywords),
        )
        keywords should be(Seq.empty)
      }

      "return 'NotFound' when the project IRI is unknown" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/iri/${ProjectIri.makeNew}/Keywords"),
        )
        assert(response.code === StatusCode.NotFound)
      }
    }

    "used to dump project data" should {
      "return a TriG file containing all data from a project" in {
        val rdfModel = UnsafeZioRun.runOrThrow(
          TestApiClient
            .getJsonLd(uri"/admin/projects/iri/$anythingProjectIri/AllData", rootUser)
            .flatMap(_.assert200)
            .mapAttempt(RdfModel.fromTriG),
        )
        assert(
          rdfModel.getContexts == Set(
            "http://www.knora.org/ontology/0001/something",
            "http://www.knora.org/ontology/0001/anything",
            "http://www.knora.org/data/0001/anything",
            "http://www.knora.org/data/permissions",
            "http://www.knora.org/data/admin",
          ),
        )
      }
    }

    "used to set RestrictedViewSize by project IRI" should {
      "return requested value to be set with 200 Response Status" in {
        val newSize = Some(RestrictedView.Size.unsafeFrom("pct:1"))
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
              uri"/admin/projects/iri/$imagesProjectIri/RestrictedViewSettings",
              SetRestrictedViewRequest(newSize, None),
              rootUser,
            )
            .flatMap(_.assert200),
        )
        assert(response === RestrictedViewResponse(newSize, None))
      }

      "return `Forbidden` for the user who is not a system nor project admin" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
              uri"/admin/projects/iri/$imagesProjectIri/RestrictedViewSettings",
              SetRestrictedViewRequest(Some(RestrictedView.Size.unsafeFrom("pct:1")), None),
              imagesUser02,
            ),
        )
        assert(response.code === StatusCode.Forbidden)
      }
    }

    "used to set RestrictedViewSize by project Shortcode" should {
      "when setting watermark to false return default size with 200 Response Status" in {
        val updateRequest = SetRestrictedViewRequest(None, Some(RestrictedView.Watermark.Off))
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
              uri"/admin/projects/shortcode/${imagesProject.shortcode}/RestrictedViewSettings",
              updateRequest,
              rootUser,
            )
            .flatMap(_.assert200),
        )
        assert(response === RestrictedViewResponse(Some(RestrictedView.Size.default), None))
      }

      "return `Forbidden` for the user who is not a system nor project admin" in {
        val updateRequest = SetRestrictedViewRequest(Some(RestrictedView.Size.unsafeFrom("pct:1")), None)
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient
            .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
              uri"/admin/projects/shortcode/${imagesProject.shortcode}/RestrictedViewSettings",
              updateRequest,
              imagesUser02,
            ),
        )
        assert(response.code === StatusCode.Forbidden)
      }
    }
  }
}
