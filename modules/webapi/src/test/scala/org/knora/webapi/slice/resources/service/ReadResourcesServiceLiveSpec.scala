/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.Scope
import zio.ZIO
import zio.json.DecoderOps
import zio.json.ast.Json
import zio.test.*

import java.time.Instant
import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadLinkValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.RegionPreviewValueContentV2
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Longname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.standoff.service.StandoffMappingServiceFake
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataset
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.builtInNamedGraphs

@RunWith(classOf[DspZTestJUnitRunner])
class ReadResourcesServiceLiveSpec extends ZIOSpecDefault {

  given sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ")

  private val dataSets: Set[RdfDataObject] = builtInNamedGraphs ++ List(
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
    RdfDataObject(
      path = "test_data/project_data/admin-data.ttl",
      name = "http://www.knora.org/data/admin",
    ),
  ).toSet

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ReadResourcesServiceLive")(
      test("readResourcesSequence returns a ReadResourceV2 matching the triplestore data") {
        val projectADM = Project(
          id = KnoraProject.ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
          shortname = Shortname.unsafeFrom("anything"),
          shortcode = Shortcode.unsafeFrom("0001"),
          longname = Some(Longname.unsafeFrom("Anything Project")),
          description = Seq(PlainStringLiteralV2("Anything Project")),
          keywords = List("arbitrary test data", "things"),
          logo = None,
          ontologies = List.empty,
          status = Status.Active,
          selfjoin = SelfJoin.CannotJoin,
          allowedCopyrightHolders = Set.empty,
          enabledLicenses = Set.empty,
        )

        val nestedResource = ReadResourceV2(
          resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw"),
          label = "Uniform",
          resourceClassIri = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#Thing"),
          attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
          projectADM = projectADM,
          permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
          userPermission = Permission.ObjectAccess.ChangeRights,
          values = Map.empty,
          creationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
          lastModificationDate = None,
          versionDate = None,
          deletionInfo = None,
        )

        val linkValue = ReadLinkValueV2(
          valueIri = ValueIri.unsafeFrom("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ/values/xB88vMy-Tc2ZCVh9Km7rVw"),
          attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
          permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
          valueHasUUID = UUID.fromString("c41f3cbc-ccbe-4dcd-9909-587d2a6eeb57"),
          valueContent = LinkValueContentV2(
            ontologySchema = InternalSchema,
            referredResourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw"),
            referredResourceExists = true,
            isIncomingLink = false,
            nestedResource = Some(nestedResource),
            comment = None,
          ),
          valueHasRefCount = 1,
          previousValueIri = None,
          deletionInfo = None,
        )

        val expected = ReadResourceV2(
          resourceIri = resourceIri,
          label = "Sierra",
          resourceClassIri = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#Thing"),
          attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
          projectADM = projectADM,
          permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
          userPermission = Permission.ObjectAccess.ChangeRights,
          values = Map(
            sf.toSmartIri("http://www.knora.org/ontology/0001/anything#isPartOfOtherThingValue") -> Seq(linkValue),
          ),
          creationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
          lastModificationDate = None,
          versionDate = None,
          deletionInfo = None,
        )

        for {
          _        <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          sequence <- ZIO.serviceWithZIO[ReadResourcesService](
                        _.readResourcesSequence(
                          resourceIris = Seq(resourceIri),
                          targetSchema = ApiV2Complex,
                          requestingUser = TestDataFactory.User.rootUser,
                        ),
                      )
        } yield assertTrue(sequence.resources == Seq(expected))
      },
      test("readResourcesSequence augments a RegionPreviewValue with a IIIF URL computed from the region geometry") {
        val regionPreviewHostResourceIri =
          ResourceIri.unsafeFrom("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg")
        // pct:x,y,w,h is the bounding box of the region's geometry (in percent); /max/ serves it at the largest available size.
        val expectedIiifUrl =
          "http://0.0.0.0:1024/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2/pct:39.796687,24.423475,9.266166,17.576948/max/0/default.jpg"
        for {
          _        <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          sequence <- ZIO.serviceWithZIO[ReadResourcesService](
                        _.readResourcesSequence(
                          resourceIris = Seq(regionPreviewHostResourceIri),
                          targetSchema = ApiV2Complex,
                          requestingUser = TestDataFactory.User.rootUser,
                        ),
                      )
          iiifUrls = sequence.resources
                       .flatMap(_.values.values.flatten)
                       .map(_.valueContent)
                       .collect { case rp: RegionPreviewValueContentV2 => rp.iiifUrl }
        } yield assertTrue(iiifUrls == Seq(Some(expectedIiifUrl)))
      },
      test(
        "readResourcesSequence renders the RegionPreviewValue (including the computed IIIF URL) into the JSON-LD response body",
      ) {
        val regionPreviewHostResourceIri =
          ResourceIri.unsafeFrom("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg")
        // `knora-api:iiifUrl` is the only field computed at read time: pct:x,y,w,h is the bounding box of the region's geometry (in percent)
        val expectedRegionPreview =
          """{
            |  "@id": "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg/values/Hn3kAqXyTbiB1RkF0r5Q7w",
            |  "@type": "knora-api:RegionPreviewValue",
            |  "knora-api:isRegionPreviewOf": { "@id": "http://rdfh.ch/0001/A5NfXW4QRxOnBPULCTvH5w" },
            |  "knora-api:iiifUrl": "http://0.0.0.0:1024/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2/pct:39.796687,24.423475,9.266166,17.576948/max/0/default.jpg",
            |  "knora-api:valueHasUUID": "Hn3kAqXyTbiB1RkF0r5Q7w",
            |  "knora-api:valueCreationDate": { "@value": "2026-05-20T10:00:00Z", "@type": "xsd:dateTimeStamp" },
            |  "knora-api:attachedToUser": { "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q" },
            |  "knora-api:hasPermissions": "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
            |  "knora-api:userHasPermission": "CR",
            |  "knora-api:arkUrl": { "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/55UrkgTKR2SEQgnsLWI9mgR/Hn3kAqXyTbiB1RkF0r5Q7wB", "@type": "xsd:anyURI" },
            |  "knora-api:versionArkUrl": { "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/55UrkgTKR2SEQgnsLWI9mgR/Hn3kAqXyTbiB1RkF0r5Q7wB.20260520T100000Z", "@type": "xsd:anyURI" }
            |}""".stripMargin.fromJson[Json]
        for {
          appConfig <- ZIO.service[AppConfig]
          _         <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          sequence  <- ZIO.serviceWithZIO[ReadResourcesService](
                        _.readResourcesSequence(
                          resourceIris = Seq(regionPreviewHostResourceIri),
                          targetSchema = ApiV2Complex,
                          requestingUser = TestDataFactory.User.rootUser,
                        ),
                      )
          regionPreview = sequence
                            .format(JsonLD, ApiV2Complex, Set.empty, appConfig)
                            .fromJson[Json]
                            .flatMap {
                              case Json.Obj(fields) =>
                                fields.collectFirst { case ("anything:hasRegionPreview", v) => v }
                                  .toRight("no anything:hasRegionPreview field in response")
                              case other => Left(s"expected a JSON object, got: $other")
                            }
        } yield assertTrue(regionPreview == expectedRegionPreview)
      },
    ).provide(
      AppConfig.layer,
      AuthorizationRestService.layer,
      CacheManager.layer,
      CardinalityHandler.layer,
      CardinalityService.layer,
      ConstructResponseUtilV2.layer,
      emptyDataset,
      IriConverter.layer,
      IriService.layer,
      KnoraGroupRepoLive.layer,
      KnoraGroupService.layer,
      KnoraProjectRepoLive.layer,
      KnoraProjectService.layer,
      KnoraUserRepoLive.layer,
      KnoraUserService.layer,
      LicenseRepo.layer,
      ListsResponder.layer,
      OntologyCacheHelpers.layer,
      OntologyCacheLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV2.layer,
      OntologyTriplestoreHelpers.layer,
      PasswordService.layer,
      PredicateObjectMapper.layer,
      PredicateRepositoryLive.layer,
      ProjectService.layer,
      ReadResourcesServiceLive.layer,
      StandoffMappingServiceFake.layer,
      StandoffTagUtilV2Live.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.layer,
    )
}
