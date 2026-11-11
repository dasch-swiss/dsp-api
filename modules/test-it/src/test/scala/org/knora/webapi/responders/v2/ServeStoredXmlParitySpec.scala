/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadLinkValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadTextValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingUser2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.resources.repo.service.ValueRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Reads of formatted text values with and without [[ServeStoredXml]] render the same JSON-LD on the same data. Both
 * reads run at service level, on the services the client REST services call. `textValueAsXml` is compared after
 * [[StandoffTagUtilV2.canonicalize]]; every other field is compared exactly.
 *
 * Every assertion that the served path was taken uses [[TextValueContentV2.isServedFromStore]], so a spec run
 * that ignores the option fails instead of passing vacuously.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ServeStoredXmlParitySpec extends E2EZSpec {
  import ServeStoredXmlParitySpec.*

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  private val fixtureRef: Ref[Option[Fixture]] = Unsafe.unsafe(implicit u => Ref.unsafe.make(Option.empty[Fixture]))
  private val fixture: UIO[Fixture]            = fixtureRef.get.someOrElseZIO(ZIO.die(IllegalStateException("no fixture")))

  private val paths: List[ReadPath] = List(
    ReadPath("resource read", servesStoredXml = true)(s => f => readResources(Seq(f.main), s)),
    ReadPath("batch resource read", servesStoredXml = true)(s => f => readResources(Seq(f.main, f.linked), s)),
    ReadPath("read of a resource version", servesStoredXml = true) { s => f =>
      readResources(Seq(f.main), s, versionDate = Some(VersionDate.fromInstant(f.versionDate)))
    },
    ReadPath("value read by UUID", servesStoredXml = true)(s =>
      f => readResources(Seq(f.main), s, valueUuid = Some(f.mutualUuid)),
    ),
    // StoredXmlServing never adds the option for the simple schema, and search by class would serve it internally.
    ReadPath("search by class", Set(ApiV2Complex), servesStoredXml = true)(s => _ => searchByClass(s)),
    ReadPath("full text search", servesStoredXml = true)(s => _ => fulltext(fullTextToken, s)),
    ReadPath("incoming links")(s => f => searchIncomingLinks(f.linked, s)),
    ReadPath("Gravsearch with a standoff link filter", Set(ApiV2Complex), servesStoredXml = true)(s =>
      f => gravsearch(standoffLinkQuery(f.main), s),
    ),
    ReadPath("Gravsearch returning a linked resource", Set(ApiV2Complex), servesStoredXml = true)(s =>
      f => gravsearch(linkedResourceQuery(f.main), s),
    ),
    ReadPath("Gravsearch in the simple schema", Set(ApiV2Simple))(s => f => gravsearch(simpleQuery(f.main), s)),
    ReadPath("incoming regions")(s => _ => searchIncomingRegions(s)),
    ReadPath("still image representations")(s => _ => searchRepresentations(s)),
  )

  private def parityTests(renderingName: String, schema: ApiV2Schema, base: Set[Rendering]) =
    paths
      .filter(_.schemas(schema))
      .map { p =>
        test(s"$renderingName: ${p.name}") {
          for {
            f      <- fixture
            parity <- compare(p.read(_)(f), schema, base)
          } yield {
            val expectServed = schema == ApiV2Complex && base == Set(MarkupRendering.Xml) && p.servesStoredXml
            assertTrue(
              parity.withOption == parity.withoutOption,
              parity.resourceCount > 0,
              parity.servedWithout == 0,
              expectServed == (parity.servedWith > 0),
            )
          }
        }
      }

  private def permissionTest(label: String, hidden: Fixture => String, read: Fixture => SchemaRendering => ReadIO) =
    test(label) {
      for {
        f      <- fixture
        parity <- compare(read(f)(_), ApiV2Complex, Set(MarkupRendering.Xml))
      } yield {
        val withOption    = parity.withOption.toJson
        val withoutOption = parity.withoutOption.toJson
        assertTrue(
          withOption == withoutOption,
          parity.servedWith > 0,
          withOption.contains(f.fallbackValueIri),
          !withOption.contains(hidden(f)),
          !withoutOption.contains(hidden(f)),
        )
      }
    }

  override val e2eSpec: Spec[env, Any] = (suite("ServeStoredXmlParitySpec")(
    suite("complex schema, XML markup")(parityTests("complex xml", ApiV2Complex, Set(MarkupRendering.Xml))*),
    suite("complex schema, markup=standoff")(
      parityTests("markup=standoff", ApiV2Complex, Set(MarkupRendering.Standoff))*,
    ),
    suite("simple schema")(parityTests("simple", ApiV2Simple, Set.empty)*),
    suite("permissions")(
      permissionTest(
        "a value the user may not see is absent with and without the option",
        _.hiddenValueIri,
        f => readResources(Seq(f.main), _, user = anythingUser2),
      ),
      permissionTest(
        "an older version the user may not see is absent with and without the option",
        _.hiddenVersionIri,
        f =>
          readResources(
            Seq(f.main),
            _,
            versionDate = Some(VersionDate.fromInstant(f.versionDate)),
            user = anythingUser2,
          ),
      ),
    ),
    suite("fallback")(
      test("a value whose literal is removed is rebuilt, and the output is unchanged") {
        for {
          f      <- fixture
          _      <- removeStoredXml(f.fallbackValueIri)
          parity <- compare(readResources(Seq(f.main), _), ApiV2Complex, Set(MarkupRendering.Xml))
        } yield
          // The other values of the resource are still served, the one without the literal is not.
          assertTrue(
            parity.withOption == parity.withoutOption,
            parity.servedWith > 0,
            servedFlags(parity.sequenceWithOption, f.fallbackValueIri).contains(false),
          )
      },
    ),
  ) @@ TestAspect.beforeAll(createFixture.flatMap(f => fixtureRef.set(Some(f)))))
    .provideSomeAuto(TestHelper.layer, ValueRepo.layer)
}

object ServeStoredXmlParitySpec {

  private given StringFormatter = StringFormatter.getGeneralInstance

  type ReadIO = ZIO[ReadResourcesService & SearchResponderV2, Throwable, ReadResourcesSequenceV2]

  private val anythingGraph = "http://www.knora.org/data/0001/anything"
  private val kb            = "http://www.knora.org/ontology/knora-base"
  private val standard      = StandoffMappingIri.unsafeFrom("http://rdfh.ch/standoff/mappings/StandardMapping")
  private val projectIri    = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val fullTextToken = "zzservedtoken"

  private def doc(body: String): String =
    s"""<?xml version="1.0" encoding="UTF-8"?><text documentType="html">$body</text>"""

  private val singleReferenceXml =
    doc(
      """<p>Single <strong id="link_id">strong value</strong> is linked by <a class="internal-link" href="#link_id">this</a></p>""",
    )
  private val mutualReferencesXml =
    doc(
      """<p>ref to note <a class="internal-link" href="#_note1" id="_ref-note1">[1]</a></p><p><a class="internal-link" href="#_ref-note1" id="_note1">[1]</a> note 1</p>""",
    )
  private def standoffLinkXml(target: ResourceIri) =
    doc(s"""<p>Link to <a class="salsah-link" href="${target.value}">another resource</a> $fullTextToken</p>""")
  private val fallbackXml = doc("""<p>Rebuilt <em>emphasis</em> and <strong>strong</strong></p>""")
  private val linkedXml   = doc(s"""<p>Text of the <strong>linked</strong> resource $fullTextToken</p>""")
  private val versionedV1 = doc("""<p>First <em>version</em></p>""")
  private val versionedV2 = doc("""<p>Second <em>version</em></p>""")
  private val hiddenXml   = doc("""<p>Visible to its creator <em>only</em></p>""")

  final case class Fixture(
    main: ResourceIri,
    linked: ResourceIri,
    mutualUuid: UUID,
    fallbackValueIri: String,
    versionDate: Instant,
    hiddenValueIri: String,
    hiddenVersionIri: String,
  )

  /**
   * @param schemas         the schemas in which a client can make the read
   * @param servesStoredXml whether the read serves a text value from the store under the complex XML rendering
   */
  final case class ReadPath(
    name: String,
    schemas: Set[ApiV2Schema] = Set(ApiV2Complex, ApiV2Simple),
    servesStoredXml: Boolean = false,
  )(val read: SchemaRendering => Fixture => ReadIO)

  /**
   * The outcome of one read with and without the option on the same data.
   * @param withOption    the normalised JSON-LD of the read that carries [[ServeStoredXml]]
   * @param withoutOption the normalised JSON-LD of the read without it
   */
  final case class Parity(
    sequenceWithOption: ReadResourcesSequenceV2,
    withOption: Json,
    withoutOption: Json,
    servedWith: Int,
    servedWithout: Int,
    resourceCount: Int,
  )

  def createFixture: ZIO[TestHelper & ValueRepo & TriplestoreService, Throwable, Fixture] =
    for {
      linked <- TestHelper.createResource
      main   <- TestHelper.createResource
      _      <- TestHelper.createTextValueWithStandoff(linked, linkedXml)
      _      <- TestHelper.createLinkValue(main, linked)
      _      <- TestHelper.createTextValueWithStandoff(main, singleReferenceXml)
      mutual <- TestHelper.createTextValueWithStandoff(main, mutualReferencesXml)
      _      <- TestHelper.createTextValueWithStandoff(main, standoffLinkXml(linked.iri))
      fall   <- TestHelper.createTextValueWithStandoff(main, fallbackXml)
      hidden <- TestHelper.createTextValueWithStandoff(main, hiddenXml)
      _      <- restrictToCreator(hidden.iri.value)
      v1     <- TestHelper.createTextValueWithStandoff(main, versionedV1)
      // A version read at the creation date of v1 shows v1, because v2 is created later.
      v1Date <- creationDate(v1.iri.value)
      v2     <- TestHelper.updateTextValueWithStandoff(v1, main, versionedV2)
      // A version read takes the permissions of the current version, so v1 is hidden together with v2.
      _ <- restrictToCreator(v2.iri.value)
    } yield Fixture(
      main.iri,
      linked.iri,
      UuidUtil.decode(mutual.iri.valueId.value),
      fall.iri.value,
      v1Date,
      hidden.iri.value,
      v1.iri.value,
    )

  /** Only the creator, the root user, may view the value. */
  private def restrictToCreator(valueIri: String): RIO[TriplestoreService, Unit] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(
        Update(
          s"""DELETE { GRAPH <$anythingGraph> { <$valueIri> <$kb#hasPermissions> ?p } }
             |INSERT { GRAPH <$anythingGraph> { <$valueIri> <$kb#hasPermissions> "CR knora-admin:Creator" } }
             |WHERE { GRAPH <$anythingGraph> { <$valueIri> <$kb#hasPermissions> ?p } }""".stripMargin,
        ),
      ),
    )

  private def creationDate(valueIri: String): RIO[TriplestoreService, Instant] =
    ZIO
      .serviceWithZIO[TriplestoreService](
        _.query(
          Select(s"SELECT ?d WHERE { GRAPH <$anythingGraph> { <$valueIri> <$kb#valueCreationDate> ?d } }"),
        ),
      )
      .map(rows => Instant.parse(rows.getFirstOrThrow("d")))

  private def removeStoredXml(valueIri: String): RIO[TriplestoreService, Unit] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(
        Update(
          s"""DELETE WHERE { GRAPH <$anythingGraph> { <$valueIri> <http://www.knora.org/ontology/knora-base#valueHasXml> ?xml } }""",
        ),
      ),
    )

  private def readResources(
    iris: Seq[ResourceIri],
    s: SchemaRendering,
    versionDate: Option[VersionDate] = None,
    valueUuid: Option[UUID] = None,
    user: User = rootUser,
  ): ZIO[ReadResourcesService, Throwable, ReadResourcesSequenceV2] =
    ZIO.serviceWithZIO[ReadResourcesService](
      _.getResourcesWithDeletedResource(
        iris,
        None,
        valueUuid,
        versionDate,
        withDeleted = true,
        showDeletedValues = false,
        s.schema,
        s.rendering,
        user,
      ),
    )

  private def search[A](f: SearchResponderV2 => Task[A]): ZIO[SearchResponderV2, Throwable, A] =
    ZIO.serviceWithZIO[SearchResponderV2](f)

  private def searchByClass(s: SchemaRendering) = {
    val thing = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri
    // The class has more instances than one page; walk the pages so that the created resources are included.
    ZIO
      .foreach(0 to 3)(page => search(_.searchResourcesByProjectAndClassV2(projectIri, thing, None, page, s, rootUser)))
      .map(_.reduce(_ ++ _))
  }

  private def fulltext(query: String, s: SchemaRendering) =
    search(_.fulltextSearchV2(query, 0, None, None, None, returnFiles = false, s, rootUser))

  private def searchIncomingLinks(target: ResourceIri, s: SchemaRendering) =
    search(_.searchIncomingLinksV2(target, 0, s, rootUser, None))

  private val picture = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-picture")
  private val pages   = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-pages")

  private def searchIncomingRegions(s: SchemaRendering) =
    search(_.searchIncomingRegionsV2(picture, 0, s, rootUser, None))

  private def searchRepresentations(s: SchemaRendering) =
    search(_.searchStillImageRepresentationsV2(pages, 0, s, rootUser, None))

  private def gravsearch(query: String, s: SchemaRendering) =
    search(_.gravsearchV2(query, s, rootUser, None))

  /** A Gravsearch query for `main` and its `hasText` values; `construct` and `where` add to the two clauses. */
  private def gravsearchQuery(main: ResourceIri, schema: ApiV2Schema = ApiV2Complex)(
    construct: String = "",
    where: String = "",
  ) = {
    val (knoraApi, anything) = schema match {
      case ApiV2Complex =>
        ("http://api.knora.org/ontology/knora-api/v2#", "http://0.0.0.0:3333/ontology/0001/anything/v2#")
      case ApiV2Simple =>
        ("http://api.knora.org/ontology/knora-api/simple/v2#", "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#")
    }
    s"""PREFIX knora-api: <$knoraApi>
       |PREFIX anything: <$anything>
       |CONSTRUCT {
       |  ?thing knora-api:isMainResource true .
       |  ?thing anything:hasText ?text .
       |  $construct
       |} WHERE {
       |  ?thing a anything:Thing .
       |  FILTER(?thing = <${main.value}>)
       |  ?thing anything:hasText ?text .
       |  $where
       |}""".stripMargin
  }

  private def standoffLinkQuery(main: ResourceIri) =
    gravsearchQuery(main)(
      where = """?text knora-api:textValueHasStandoff ?standoffTag .
                |  ?standoffTag a knora-api:StandoffLinkTag .
                |  FILTER knora-api:standoffLink(?thing, ?standoffTag, ?otherThing)
                |  ?otherThing a anything:Thing .""".stripMargin,
    )

  private def linkedResourceQuery(main: ResourceIri) =
    gravsearchQuery(main)(
      construct = """?thing anything:hasOtherThing ?other .
                    |  ?other anything:hasText ?otherText .""".stripMargin,
      where = """?thing anything:hasOtherThing ?other .
                |  ?other a anything:Thing .
                |  ?other anything:hasText ?otherText .""".stripMargin,
    )

  private def simpleQuery(main: ResourceIri) = gravsearchQuery(main, ApiV2Simple)()

  /** Runs the read with and without [[ServeStoredXml]], and renders both with the options the client sent. */
  def compare(
    read: SchemaRendering => ReadIO,
    schema: ApiV2Schema,
    base: Set[Rendering],
  ): ZIO[
    ReadResourcesService & SearchResponderV2 & KnoraResponseRenderer & StandoffMappingService,
    Throwable,
    Parity,
  ] =
    for {
      without  <- read(SchemaRendering(schema, base))
      withOpt  <- read(SchemaRendering(schema, base + ServeStoredXml))
      mapping  <- ZIO.serviceWithZIO[StandoffMappingService](_.getMappingV2(standard))
      jWithout <- render(without, schema, base, mapping)
      jWith    <- render(withOpt, schema, base, mapping)
    } yield Parity(
      withOpt,
      jWith,
      jWithout,
      servedCount(withOpt),
      servedCount(without),
      withOpt.resources.size,
    )

  private def render(
    response: ReadResourcesSequenceV2,
    schema: ApiV2Schema,
    base: Set[Rendering],
    mapping: GetMappingResponseV2,
  ): ZIO[KnoraResponseRenderer, Throwable, Json] =
    for {
      rendered <- ZIO.serviceWithZIO[KnoraResponseRenderer](_.render(response, FormatOptions(JsonLD, schema, base)))
      json     <- ZIO.fromEither(rendered._1.fromJson[Json]).mapError(e => new RuntimeException(e))
    } yield normalise(json, mapping)

  /** Sorts object keys and replaces every `textValueAsXml` by its canonical form. */
  private def normalise(json: Json, mapping: GetMappingResponseV2): Json = {
    val asXml     = "knora-api:textValueAsXml"
    val hasMapped = "knora-api:textValueHasMapping"
    json match {
      case Json.Obj(fields) =>
        val canonical = fields.map {
          case (`asXml`, Json.Str(xml)) =>
            val mappingIri = fields.collectFirst { case (`hasMapped`, Json.Obj(m)) => m.toMap.get("@id") }.flatten
            require(mappingIri.contains(Json.Str(standard.value)), s"unexpected mapping $mappingIri")
            asXml -> Json.Str(StandoffTagUtilV2.canonicalize(xml, mapping))
          case (key, value) => key -> normalise(value, mapping)
        }
        Json.Obj(canonical.sortBy(_._1))
      case Json.Arr(items) => Json.Arr(items.map(normalise(_, mapping)))
      case other           => other
    }
  }

  private def textContents(resource: ReadResourceV2): Seq[(String, TextValueContentV2)] =
    resource.values.values.flatten.toSeq.flatMap {
      case text: ReadTextValueV2 => Seq(text.valueIri.value -> text.valueContent)
      case link: ReadLinkValueV2 => link.valueContent.nestedResource.toSeq.flatMap(textContents)
      case _                     => Seq.empty
    }

  private def allTextContents(response: ReadResourcesSequenceV2) = response.resources.flatMap(textContents)

  private def servedCount(response: ReadResourcesSequenceV2): Int =
    allTextContents(response).count(_._2.isServedFromStore)

  /** Whether the text value is served from its stored XML, or `None` if the response does not contain it. */
  def servedFlags(response: ReadResourcesSequenceV2, valueIri: String): Option[Boolean] =
    allTextContents(response).collectFirst { case (`valueIri`, content) => content.isServedFromStore }
}
