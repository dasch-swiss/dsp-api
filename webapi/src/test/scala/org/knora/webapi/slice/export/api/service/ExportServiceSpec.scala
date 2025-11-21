/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.test.*

import java.time.Instant

import org.knora.webapi.GoldenTest
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resources.service.ReadResourcesServiceFake
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.slice.admin.domain.model.Permission
import java.util.UUID
import org.knora.webapi.messages.v2.responder.valuemessages.ReadTextValueV2
import org.knora.webapi.slice.common.KnoraIris.PropertyIri

object ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
  override val rewriteAll: Boolean = true

  UnsafeZioRun.runOrThrow(ZIO.service[StringFormatter].provide(StringFormatter.test))(zio.Runtime.default)
  given sf: StringFormatter = StringFormatter.getGeneralInstance

  def resourceClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing")(using sf)

  val user       = TestDataFactory.User.rootUser
  val project    = TestDataFactory.someProject
  val projectADM = TestDataFactory.someProjectADM

  val TextValueSmartIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri.toInternalSchema

  // NOTE: at this point I think it might be easier to use 
  // TriplestoreServiceInMemory and a wall of triples from incunabula-data.ttl
  def readTextValue(content: String): ReadValueV2 =
    ReadTextValueV2(
      valueIri = "",
      attachedToUser = "",
      permissions = "",
      userPermission = Permission.ObjectAccess.maxPermission,
      valueCreationDate = Instant.now,
      valueHasUUID = UUID.randomUUID(),
      valueContent = TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some(content),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = Some("en"),
      ),
      valueHasMaxStandoffStartIndex = None,
      previousValueIri = None,
      deletionInfo = None,
    )

  def makeResource(
    label: String,
    values: Map[SmartIri, Seq[ReadValueV2]] = Map.empty,
  ): ReadResourceV2 = {
    "[ ]".r.findFirstIn(label).map(_ => throw new Exception("simple labels only"))
    ReadResourceV2(
      s"http://rdfh.ch/0001/$label",
      label,
      resourceClassIri.smartIri,
      user.id,
      projectADM,
      "",
      ObjectAccess.maxPermission,
      values, // values
      Instant.now,
      Some(Instant.now),
      None,
      None,
    )
  }

  val readResources =
    Seq(
      makeResource("r2", values = Map(TextValueSmartIri -> Seq(readTextValue("seven\nthree\nseven")))),
      makeResource("r1"),
      makeResource("r3"),
      makeResource("r4"),
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportServiceSpec")(
      test("basic") {
        for {
          exportService <- ZIO.service[ExportService]
          exportedCsv <-
            exportService.exportResources(
              project,
              resourceClassIri,
              List(PropertyIri.from(TextValueSmartIri).toOption.get),
              user,
              LanguageCode.EN,
              includeResourceIri = true,
            )
          csv <- exportService.toCsv(exportedCsv)
        } yield assertGolden(csv, "basic")
      },
    ).provide(
      StringFormatter.test,
      IriConverter.layer,
      findAllResourcesServiceEmptyLayer,
      OntologyCacheFake.emptyCache,
      OntologyRepoLive.layer,
      CsvService.layer,
      ZLayer.succeed(ReadResourcesServiceFake(readResources)),
      ExportService.layer,
    )

  private val findAllResourcesServiceEmptyLayer: ZLayer[Any, Nothing, FindAllResourcesService] =
    ZLayer.succeed[FindAllResourcesService]((_: KnoraProject, _: ResourceClassIri) => ZIO.succeed(Seq.empty))
}
