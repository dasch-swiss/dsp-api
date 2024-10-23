/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.api.CompactionApi
import com.apicatalog.jsonld.api.ExpansionApi
import com.apicatalog.jsonld.api.FlatteningApi
import com.apicatalog.jsonld.document.JsonDocument
import zio.ZIO
import zio.test.Spec
import zio.test.TestResult
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType.UnformattedText
import org.knora.webapi.routing.v2.AssetIngestState.AssetIngested
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import zio.test.Gen
import zio.test.check

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8

object CreateValueV2Spec extends ZIOSpecDefault {

  private val unformattedTextValueWithLanguage =
    """
      |{
      |  "@id": "http://rdfh.ch/0001/a-thing",
      |  "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
      |  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText":{
      |    "@type":"http://api.knora.org/ontology/knora-api/v2#TextValue",
      |    "http://api.knora.org/ontology/knora-api/v2#valueAsString":"This is English",
      |    "http://api.knora.org/ontology/knora-api/v2#textValueHasLanguage":"en"
      |  }
      |}""".stripMargin

  private val rootUser =
    User(
      id = "http://rdfh.ch/users/root",
      username = "root",
      email = "root@example.com",
      givenName = "System",
      familyName = "Administrator",
      status = true,
      lang = "de",
      password = Option("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          KnoraProjectRepo.builtIn.SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value),
        ),
        administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]],
      ),
    )

  private val expand: String => String = (jsonLd: String) => {
    val d: JsonDocument   = JsonDocumentOps.of(jsonLd)
    val api: ExpansionApi = JsonLd.expand(d)
    api.get().toString
  }

  private val flatten: String => String = (jsonLd: String) => {
    val d: JsonDocument    = JsonDocumentOps.of(jsonLd)
    val api: FlatteningApi = JsonLd.flatten(d)
    api.get().toString
  }

  private val compact: String => String = (jsonLd: String) => {
    val d: JsonDocument = JsonDocumentOps.of(jsonLd)
    val api: CompactionApi =
      JsonLd.compact(
        d,
        JsonDocumentOps.of("""{
                             |  "api": "http://api.knora.org/ontology/knora-api/v2#",
                             |  "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                             |}""".stripMargin),
      )
    api.get().toString
  }

  private val noOp = (jsonLd: String) => jsonLd

  private val transformations: Seq[String => String] = Seq(expand, compact, flatten, noOp)

  object JsonDocumentOps {
    def of(str: String): JsonDocument = JsonDocument.of(ByteArrayInputStream(str.getBytes(UTF_8)))
  }

  override def spec: Spec[Any, Throwable] =
    suite("CreateValueV2Spec")(test("UnformattedText TextValue fromJsonLd should contain the language") {
      check(Gen.fromIterable(transformations)) { f =>
        for {
          sf    <- ZIO.service[StringFormatter]
          value <- CreateValueV2.fromJsonLd(AssetIngested, f(unformattedTextValueWithLanguage), rootUser)
        } yield assertTrue(
          value == CreateValueV2(
            resourceIri = "http://rdfh.ch/0001/a-thing",
            resourceClassIri = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"),
            propertyIri = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText"),
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("This is English"),
              textValueType = UnformattedText,
              valueHasLanguage = Some("en"),
              standoff = Nil,
              mappingIri = None,
              mapping = None,
              xslt = None,
              comment = None,
            ),
            valueIri = None,
            valueUUID = None,
            valueCreationDate = None,
            permissions = None,
            ingestState = AssetIngested,
          ),
        )
      }
    }).provide(StringFormatter.test, MessageRelayLive.layer, IriConverter.layer, SipiServiceMock.layer)

}
