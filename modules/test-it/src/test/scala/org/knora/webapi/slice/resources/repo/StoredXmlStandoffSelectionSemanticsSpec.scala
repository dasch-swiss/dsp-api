/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.StandoffSelection
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.FullTextMainQueryGenerator
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.gravsearch.mainquery.GravsearchMainQueryGenerator
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Runs every read query builder that honours [[StandoffSelection.OnlyWithoutStoredXml]] against a live triplestore:
 * a text value with stored XML returns its XML literal and no standoff, a text value without stored XML returns the
 * same standoff as with [[StandoffSelection.AllStandoff]].
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StoredXmlStandoffSelectionSemanticsSpec extends E2EZSpec {

  private given StringFormatter = StringFormatter.getGeneralInstance

  private val graph = "http://www.knora.org/data/0001/stored-xml-selection"

  private val kb       = "http://www.knora.org/ontology/knora-base#"
  private val standoff = "http://www.knora.org/ontology/standoff#"
  private val rdfs     = "http://www.w3.org/2000/01/rdf-schema#"
  private val xsd      = "http://www.w3.org/2001/XMLSchema#"

  private val resource   = "http://rdfh.ch/0001/stored-xml-selection/resource"
  private val withXml    = s"$resource/values/with-xml"
  private val withoutXml = s"$resource/values/without-xml"
  private val nodesOf    = Map(withXml -> Seq("a", "b"), withoutXml -> Seq("c", "d")).map { case (v, ids) =>
    v -> ids.map(id => s"$v/standoff/$id")
  }
  private val storedXmlLit = "<text>stored</text>"

  private val created = "2020-01-01T00:00:00Z"
  private val asOf    = Instant.parse("2021-01-01T00:00:00Z")

  private def textValue(iri: String, uuid: String, storedXml: Boolean): String = {
    val tags = nodesOf(iri).zipWithIndex.map { case (node, i) =>
      s"""<$iri> <${kb}valueHasStandoff> <$node> .
         |<$node> a <${standoff}StandoffParagraphTag> ;
         |  <${kb}standoffTagHasStartIndex> $i ;
         |  <${kb}standoffTagHasStart> ${i * 10} ;
         |  <${kb}standoffTagHasEnd> ${i * 10 + 5} .""".stripMargin
    }
    s"""<$resource> <${kb}hasValue> <$iri> .
       |<$iri> a <${kb}TextValue> ;
       |  <${kb}isDeleted> false ;
       |  <${kb}valueHasUUID> "$uuid" ;
       |  <${kb}valueCreationDate> "$created"^^<${xsd}dateTime> ;
       |  <${kb}hasPermissions> "CR knora-admin:Creator" ;
       |  <${kb}valueHasString> "text" ;
       |  <${kb}valueHasMaxStandoffStartIndex> 1 .
       |${if (storedXml) s"""<$iri> <${kb}valueHasXml> "$storedXmlLit" .""" else ""}
       |${tags.mkString("\n")}""".stripMargin
  }

  private val fixture =
    s"""<$resource> a <${kb}Resource> ;
       |  <${kb}isDeleted> false ;
       |  <${kb}attachedToProject> <http://rdfh.ch/projects/0001> ;
       |  <${kb}attachedToUser> <http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q> ;
       |  <${kb}hasPermissions> "CR knora-admin:Creator" ;
       |  <${kb}creationDate> "$created"^^<${xsd}dateTime> ;
       |  <${rdfs}label> "resource" .
       |${textValue(withXml, "uuid-with-xml", storedXml = true)}
       |${textValue(withoutXml, "uuid-without-xml", storedXml = false)}""".stripMargin

  private val seed: RIO[TriplestoreService, Unit] =
    for {
      _ <- ZIO.serviceWithZIO[TriplestoreService](_.query(Update(s"DROP SILENT GRAPH <$graph>")))
      _ <- ZIO.serviceWithZIO[TriplestoreService](_.query(Update(s"INSERT DATA { GRAPH <$graph> { $fixture } }")))
    } yield ()

  private type Triple = (String, String, String)

  private def run(query: Construct): RIO[TriplestoreService, Set[Triple]] =
    ZIO
      .serviceWithZIO[TriplestoreService](_.query(query))
      .map(_.statements.toSet.flatMap { case (subject, pos) => pos.map { case (p, o) => (subject, p, o) } })

  /** `linksFiltered` is false where the generic value branch itself returns the `valueHasStandoff` links. */
  private case class Builder(name: String, build: StandoffSelection => Construct, linksFiltered: Boolean = true)

  private val builders: Seq[Builder] = Seq(
    Builder(
      "current values",
      selection =>
        GetResourcePropertiesAndValuesQuery.build(Seq(resource), preview = false, withDeleted = false, selection),
    ),
    Builder(
      "versioned values",
      selection =>
        GetResourcePropertiesAndValuesQuery.build(
          Seq(resource),
          preview = false,
          withDeleted = false,
          selection,
          maybeVersionDate = Some(asOf),
        ),
    ),
    Builder("search result resources", selection => SearchResultResourcesQuery.build(Seq(resource), selection)),
    Builder(
      "Gravsearch main query",
      selection =>
        Construct.gravsearch(
          GravsearchMainQueryGenerator
            .createMainQuery(
              Set(IriRef(resource.toSmartIri)),
              Set.empty,
              Set(withXml, withoutXml),
              selection,
            )
            .toSparql,
        ),
    ),
    Builder(
      "full text main query",
      selection =>
        Construct(
          FullTextMainQueryGenerator
            .createMainQuery(Set(resource), Set(withXml, withoutXml), selection)
            .toSparql,
        ),
      linksFiltered = false,
    ),
  )

  /** The triples of the standoff nodes of a value; the `valueHasStandoff` links are left out. */
  private def standoffNodeTriplesOf(triples: Set[Triple], value: String): Set[Triple] =
    triples.filter { case (s, _, _) => nodesOf(value).contains(s) }

  override val e2eSpec: Spec[env, Any] = suite("OnlyWithoutStoredXml against a live triplestore")(
    builders.map { case Builder(name, build, linksFiltered) =>
      test(name) {
        for {
          _        <- seed
          all      <- run(build(StandoffSelection.AllStandoff))
          filtered <- run(build(StandoffSelection.OnlyWithoutStoredXml))
        } yield assertTrue(
          standoffNodeTriplesOf(all, withXml).nonEmpty,
          standoffNodeTriplesOf(all, withoutXml).nonEmpty,
          standoffNodeTriplesOf(filtered, withXml).isEmpty,
          standoffNodeTriplesOf(filtered, withoutXml) == standoffNodeTriplesOf(all, withoutXml),
          filtered.contains((withXml, s"${kb}valueHasXml", storedXmlLit)),
          !filtered.exists { case (s, p, _) => s == withoutXml && p == s"${kb}valueHasXml" },
          !linksFiltered || !filtered.exists { case (s, p, _) => s == withXml && p == s"${kb}valueHasStandoff" },
        )
      }
    }*,
  )
}
