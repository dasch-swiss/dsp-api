/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.model.*
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.*
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.MutableTestIri

object AdminProjectsEndpointsE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  private val customProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/wahxssy1TDqPuSk6ee8EdQ")
  private val newProjectIri    = new MutableTestIri

  val e2eSpec = suite(s"The Projects Route 'admin/projects'")(
    suite("used to query for project information")(
      test("return all projects excluding built-in system projects") {
        TestApiClient
          .getJson[ProjectsGetResponse](uri"/admin/projects")
          .flatMap(_.assert200)
          .flatMap(response => assertTrue(response.projects.size == 6))
      },
      test("return the information for a single project identified by iri") {
        TestApiClient
          .getJson[ProjectGetResponse](uri"/admin/projects/iri/$imagesProjectIri", rootUser)
          .flatMap(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return the information for a single project identified by shortname") {
        TestApiClient
          .getJson[ProjectGetResponse](uri"/admin/projects/shortname/$imagesProjectShortname", rootUser)
          .flatMap(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return the information for a single project identified by shortcode") {
        TestApiClient
          .getJson[ProjectGetResponse](uri"/admin/projects/shortcode/$imagesProjectShortcode", rootUser)
          .flatMap(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return the project's restricted view settings using its IRI") {
        TestApiClient
          .getJson[ProjectRestrictedViewSettingsGetResponseADM](
            uri"/admin/projects/iri/$imagesProjectIri/RestrictedViewSettings",
          )
          .flatMap(_.assert200)
          .map(_.settings)
          .map(settings => assertTrue(settings.size.contains("!512,512"), !settings.watermark))
      },
      test("return the project's restricted view settings using its shortname") {
        TestApiClient
          .getJson[ProjectRestrictedViewSettingsGetResponseADM](
            uri"/admin/projects/shortname/$imagesProjectShortname/RestrictedViewSettings",
          )
          .flatMap(_.assert200)
          .map(_.settings)
          .map(settings => assertTrue(settings.size.contains("!512,512"), !settings.watermark))
      },
      test("return the project's restricted view settings using its shortcode") {
        TestApiClient
          .getJson[ProjectRestrictedViewSettingsGetResponseADM](
            uri"/admin/projects/shortcode/$imagesProjectShortcode/RestrictedViewSettings",
          )
          .flatMap(_.assert200)
          .map(_.settings)
          .map(settings => assertTrue(settings.size.contains("!512,512"), !settings.watermark))
      },
    ),
    suite("given a custom Iri")(
      test("CREATE a new project with the provided custom Iri") {
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
        TestApiClient
          .postJson[ProjectOperationResponseADM, ProjectCreateRequest](uri"/admin/projects", createReq, rootUser)
          .flatMap(_.assert200)
          .map(_.project)
          .map(result =>
            assertTrue(
              // check that the custom IRI is correctly assigned
              result.id == customProjectIri,
              // check the rest of project info
              result.shortcode.value == "3333",
              result.shortname.value == "newprojectWithIri",
              result.longname.map(_.value).contains("new project with a custom IRI"),
              result.keywords == Seq("projectIRI"),
              result.description == Seq(StringLiteralV2.from("a project created with a custom IRI", EN)),
              result.enabledLicenses == Set(LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0")),
              result.allowedCopyrightHolders.map(_.value) == Set(
                "AI-Generated Content - Not Protected by Copyright",
                "Public Domain - Not Protected by Copyright",
              ),
            ),
          )
      },
      test("return 'BadRequest' if the supplied project IRI is not unique") {
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
        TestApiClient
          .postJson[ProjectOperationResponseADM, ProjectCreateRequest](uri"/admin/projects", createReq, rootUser)
          .map(response =>
            assertTrue(
              response.code == StatusCode.BadRequest,
              response.body == Left(s"""{"message":"IRI: '$customProjectIri' already exists, try another one."}"""),
            ),
          )
      },
    ),
    suite("used to modify project information")(
      test("CREATE a new project and return the project info if the supplied shortname is unique") {
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
        TestApiClient
          .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
            uri"/admin/projects",
            createReq,
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.project)
          .tap(result => ZIO.succeed(newProjectIri.set(result.id.value)))
          .map(result =>
            assertTrue(
              result.shortname.value == "newproject",
              result.shortcode.value == "1111",
              result.longname.map(_.value) == Some("project longname"),
              result.description == Seq(StringLiteralV2.from("project description", EN)),
              result.keywords == Seq("keywords"),
              result.logo.map(_.value) == Some("/fu/bar/baz.jpg"),
              result.status == Status.Active,
              result.selfjoin == SelfJoin.CannotJoin,
            ),
          )
      },
      test("return a 'BadRequest' if the supplied project shortname during creation is not unique") {
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
        TestApiClient
          .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
            uri"/admin/projects",
            createReq,
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("UPDATE a project") {
        val updateReq = ProjectUpdateRequest(
          longname = Some(Longname.unsafeFrom("updated project longname")),
          description = Some(List(Description.unsafeFrom(StringLiteralV2.from("updated project description", EN)))),
          keywords = Some(List(Keyword.unsafeFrom("updated"), Keyword.unsafeFrom("keywords"))),
          logo = Some(Logo.unsafeFrom("/fu/bar/baz-updated.jpg")),
          status = Some(Status.Active),
          selfjoin = Some(SelfJoin.CanJoin),
        )
        TestApiClient
          .putJson[ProjectOperationResponseADM, ProjectUpdateRequest](
            uri"/admin/projects/iri/${newProjectIri.asProjectIri}",
            updateReq,
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.project)
          .map(result =>
            assertTrue(
              result.longname.map(_.value) == Some("updated project longname"),
              result.description == Seq(
                StringLiteralV2.from(value = "updated project description", EN),
              ),
              result.keywords.sorted == Seq("updated", "keywords").sorted,
              result.logo.map(_.value) == Some("/fu/bar/baz-updated.jpg"),
              result.status == Status.Active,
              result.selfjoin == SelfJoin.CanJoin,
            ),
          )
      },
      test("UPDATE a project with multi-language description") {
        val updateReq = ProjectUpdateRequest(
          description = Some(
            List(
              Description.unsafeFrom(StringLiteralV2.from("Test Project", EN)),
              Description.unsafeFrom(StringLiteralV2.from("Test Projekt", DE)),
            ),
          ),
        )
        TestApiClient
          .putJson[ProjectOperationResponseADM, ProjectUpdateRequest](
            uri"/admin/projects/iri/$imagesProjectIri",
            updateReq,
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.project)
          .map(result =>
            assertTrue(
              result.description.size == 2,
              result.description.contains(StringLiteralV2.from("Test Project", EN)),
              result.description.contains(StringLiteralV2.from("Test Projekt", DE)),
            ),
          )
      },
      test("DELETE a project") {
        TestApiClient
          .deleteJson[ProjectOperationResponseADM](uri"/admin/projects/iri/$imagesProjectIri", rootUser)
          .flatMap(_.assert200)
          .map(_.project)
          .map(result => assertTrue(result.status == Status.Inactive))
      },
    ),
    suite("used to query members [FUNCTIONALITY]")(
      test("return all members of a project identified by iri") {
        TestApiClient
          .getJson[ProjectMembersGetResponseADM](
            uri"/admin/projects/iri/$imagesProjectIri/members",
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.members)
          .map(members => assertTrue(members.size == 4))
      },
      test("return all members of a project identified by shortname") {
        TestApiClient
          .getJson[ProjectMembersGetResponseADM](
            uri"/admin/projects/shortname/$imagesProjectShortname/members",
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.members)
          .map(members => assertTrue(members.size == 4))
      },
      test("return all members of a project identified by shortcode") {
        TestApiClient
          .getJson[ProjectMembersGetResponseADM](
            uri"/admin/projects/shortcode/$imagesProjectShortcode/members",
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.members)
          .map(members => assertTrue(members.size == 4))
      },
      test("return all admin members of a project identified by iri") {
        TestApiClient
          .getJson[ProjectAdminMembersGetResponseADM](
            uri"/admin/projects/iri/$imagesProjectIri/admin-members",
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.members)
          .map(members => assertTrue(members.size == 2))
      },
      test("return all admin members of a project identified by shortname") {
        TestApiClient
          .getJson[ProjectAdminMembersGetResponseADM](
            uri"/admin/projects/shortname/$imagesProjectShortname/admin-members",
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.members)
          .map(members => assertTrue(members.size == 2))
      },
      test("return all admin members of a project identified by shortcode") {
        TestApiClient
          .getJson[ProjectAdminMembersGetResponseADM](
            uri"/admin/projects/shortcode/$imagesProjectShortcode/admin-members",
            rootUser,
          )
          .flatMap(_.assert200)
          .map(_.members)
          .map(members => assertTrue(members.size == 2))
      },
    ),
    suite("used to query members [PERMISSIONS]")(
      test("return members of a project to a SystemAdmin") {
        TestApiClient
          .getJson[ProjectMembersGetResponseADM](uri"/admin/projects/iri/$imagesProjectIri/members", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return members of a project to a ProjectAdmin") {
        TestApiClient
          .getJson[ProjectMembersGetResponseADM](uri"/admin/projects/iri/$imagesProjectIri/members", imagesUser01)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return `Forbidden` for members of a project to a normal user") {
        TestApiClient
          .getJson[ProjectMembersGetResponseADM](uri"/admin/projects/iri/$imagesProjectIri/members", imagesUser02)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("return admin-members of a project to a SystemAdmin") {
        TestApiClient
          .getJson[ProjectAdminMembersGetResponseADM](
            uri"/admin/projects/iri/$imagesProjectIri/admin-members",
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return admin-members of a project to a ProjectAdmin") {
        TestApiClient
          .getJson[ProjectAdminMembersGetResponseADM](
            uri"/admin/projects/iri/$imagesProjectIri/admin-members",
            imagesUser01,
          )
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return `Forbidden` for admin-members of a project to a normal user") {
        TestApiClient
          .getJson[ProjectAdminMembersGetResponseADM](
            uri"/admin/projects/iri/$imagesProjectIri/admin-members",
            imagesUser02,
          )
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
    suite("used to query keywords")(
      test("return all unique keywords for all projects") {
        TestApiClient
          .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/Keywords", rootUser)
          .flatMap(_.assert200)
          .map(_.keywords)
          .map(keywords => assertTrue(keywords.size == 21))
      },
      test("return all keywords for a single project") {
        TestApiClient
          .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/iri/$incunabulaProjectIri/Keywords", rootUser)
          .flatMap(_.assert200)
          .map(_.keywords)
          .map(keywords => assertTrue(keywords == incunabulaProject.keywords))
      },
      test("return empty list for a project without keywords") {
        TestApiClient
          .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/iri/$dokubibProjectIri/Keywords")
          .flatMap(_.assert200)
          .map(_.keywords)
          .map(keywords => assertTrue(keywords == Seq.empty))
      },
      test("return 'NotFound' when the project IRI is unknown") {
        TestApiClient
          .getJson[ProjectsKeywordsGetResponse](uri"/admin/projects/iri/${ProjectIri.makeNew}/Keywords")
          .map(response => assertTrue(response.code == StatusCode.NotFound))
      },
    ),
    suite("used to dump project data")(
      test("return a TriG file containing all data from a project") {
        TestApiClient
          .getJsonLd(uri"/admin/projects/iri/$anythingProjectIri/AllData", rootUser)
          .flatMap(_.assert200)
          .mapAttempt(RdfModel.fromTriG)
          .map(rdfModel =>
            assertTrue(
              rdfModel.getContexts == Set(
                "http://www.knora.org/ontology/0001/something",
                "http://www.knora.org/ontology/0001/anything",
                "http://www.knora.org/data/0001/anything",
                "http://www.knora.org/data/permissions",
                "http://www.knora.org/data/admin",
              ),
            ),
          )
      },
    ),
    suite("used to set RestrictedViewSize by project IRI")(
      test("return requested value to be set with 200 Response Status") {
        val newSize = Some(RestrictedView.Size.unsafeFrom("pct:1"))
        TestApiClient
          .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
            uri"/admin/projects/iri/$imagesProjectIri/RestrictedViewSettings",
            SetRestrictedViewRequest(newSize, None),
            rootUser,
          )
          .flatMap(_.assert200)
          .map(response => assertTrue(response == RestrictedViewResponse(newSize, None)))
      },
      test("return `Forbidden` for the user who is not a system nor project admin") {
        TestApiClient
          .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
            uri"/admin/projects/iri/$imagesProjectIri/RestrictedViewSettings",
            SetRestrictedViewRequest(Some(RestrictedView.Size.unsafeFrom("pct:1")), None),
            imagesUser02,
          )
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
    suite("used to set RestrictedViewSize by project Shortcode")(
      test("when setting watermark to false return default size with 200 Response Status") {
        val updateRequest = SetRestrictedViewRequest(None, Some(RestrictedView.Watermark.Off))
        TestApiClient
          .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
            uri"/admin/projects/shortcode/${imagesProject.shortcode}/RestrictedViewSettings",
            updateRequest,
            rootUser,
          )
          .flatMap(_.assert200)
          .map(response => assertTrue(response == RestrictedViewResponse(Some(RestrictedView.Size.default), None)))
      },
      test("return `Forbidden` for the user who is not a system nor project admin") {
        val updateRequest = SetRestrictedViewRequest(Some(RestrictedView.Size.unsafeFrom("pct:1")), None)
        TestApiClient
          .postJson[RestrictedViewResponse, SetRestrictedViewRequest](
            uri"/admin/projects/shortcode/${imagesProject.shortcode}/RestrictedViewSettings",
            updateRequest,
            imagesUser02,
          )
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
  )
}
