/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import zio.test.*

/**
 * Doobie-style Fragment + `sparql"..."` interpolator.
 *
 * Demonstrates the library API against the benchmark queries.
 */
object SparqlInterpolatorSpec extends ZIOSpecDefault {

  // -- Common vocabulary (would live in the adapter layer in production) --
  val knoraBase      = "http://www.knora.org/ontology/knora-base#"
  val rdf            = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val rdfs           = "http://www.w3.org/2000/01/rdf-schema#"
  val xsd            = "http://www.w3.org/2001/XMLSchema#"
  val owl            = "http://www.w3.org/2002/07/owl#"
  val kbIsDeleted    = Iri.trusted(knoraBase + "isDeleted")
  val kbResource     = Iri.trusted(knoraBase + "Resource")
  val kbLastMod      = Iri.trusted(knoraBase + "lastModificationDate")
  val rdfType        = Iri.trusted(rdf + "type")
  val rdfsSubClassOf = Iri.trusted(rdfs + "subClassOf")

  override def spec = suite("Fragment + sparql interpolator")(
    simpleSelectSuite,
    isNodeUsedBenchmark,
    deletePropertyBenchmark,
    insertValueBenchmarkSketch,
    searchQueriesBenchmark,
    conditionalFragmentsSuite,
    iterationSuite,
  )

  // -------------------------------------------------------------------------
  // Simple query: SELECT with OPTIONAL
  // -------------------------------------------------------------------------
  val simpleSelectSuite = suite("Simple SELECT with OPTIONAL")(
    test("renders a basic SELECT with OPTIONAL") {
      val s             = Variable("s")
      val p             = Variable("p")
      val o             = Variable("o")
      val lmd           = Variable("lastModDate")
      val resourceClass = Iri.trusted("http://example.org/MyClass")

      val query = SparqlQuery
        .select(s, p, o)
        .where(
          sparql"$s a $resourceClass .",
          sparql"$s $kbIsDeleted false .",
          Fragments.optional(sparql"$s $kbLastMod $lmd ."),
          sparql"$s $p $o .",
        )
        .orderBy(lmd.desc)
        .limit(25)
        .render

      assertTrue(
        query.contains("SELECT ?s ?p ?o"),
        query.contains("?s a <http://example.org/MyClass>"),
        query.contains("?s <http://www.knora.org/ontology/knora-base#isDeleted> false"),
        query.contains("OPTIONAL"),
        query.contains("DESC(?lastModDate)"),
        query.contains("LIMIT 25"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: IsNodeUsedQuery (Hybrid → ASK with UNION)
  // -------------------------------------------------------------------------
  val isNodeUsedBenchmark = suite("Benchmark: IsNodeUsedQuery")(
    test("renders ASK with UNION") {
      val s              = Variable("s")
      val nodeIri        = Iri.trusted("http://rdfh.ch/lists/0001/treeList01")
      val guiAttr        = Iri.trusted("http://www.knora.org/ontology/salsah-gui#guiAttribute")
      val valHasListNode = Iri.trusted(knoraBase + "valueHasListNode")

      // IsNodeUsedQuery
      val query = SparqlQuery.ask
        .where(
          Fragments.union(
            sparql"$s $guiAttr ${Literal.string(s"hlist=<$nodeIri>")} .",
            sparql"$s $valHasListNode $nodeIri .",
          ),
        )
        .render

      assertTrue(
        query.contains("ASK"),
        query.contains("UNION"),
        query.contains("?s <http://www.knora.org/ontology/salsah-gui#guiAttribute>"),
        query.contains("?s <http://www.knora.org/ontology/knora-base#valueHasListNode>"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: DeletePropertyQuery (Pure RDF4J → conditional + filterNotExists)
  // -------------------------------------------------------------------------
  val deletePropertyBenchmark = suite("Benchmark: DeletePropertyQuery")(
    test("renders UPDATE with conditional link value patterns") {
      val ontologyIri = Iri.trusted("http://www.knora.org/ontology/0001/anything")
      val propertyIri = Iri.trusted("http://www.knora.org/ontology/0001/anything#hasOtherThing")
      val lmdValue    = Literal.typed("2024-01-01T00:00:00Z", Iri.trusted(xsd + "dateTime"))
      val newLmd      = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))

      val propertyPred = Variable("propertyPred")
      val propertyObj  = Variable("propertyObj")
      val s            = Variable("s")
      val p            = Variable("p")

      // Optional link value property (mirrors the Option[PropertyIri] in the real code)
      val linkValuePropertyIri: Option[Iri] =
        Some(Iri.trusted("http://www.knora.org/ontology/0001/anything#hasOtherThingValue"))
      val linkValuePropertyPred = Variable("linkValuePropertyPred")
      val linkValuePropertyObj  = Variable("linkValuePropertyObj")

      val linkValueDeletePattern: Option[Fragment] = linkValuePropertyIri.map { lvpIri =>
        sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
      }

      val linkValueWherePattern: Option[Fragment] = linkValuePropertyIri.map { lvpIri =>
        sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
      }

      val owlOntology   = Iri.trusted(owl + "Ontology")
      val owlObjectProp = Iri.trusted(owl + "ObjectProperty")

      val query = SparqlQuery.update
        .prefix("knora-base", knoraBase)
        .prefix("xsd", xsd)
        .prefix("owl", owl)
        .delete(
          sparql"$ontologyIri $kbLastMod $lmdValue .",
          sparql"$propertyIri $propertyPred $propertyObj .",
          Fragment.combine(linkValueDeletePattern),
        )
        .from(ontologyIri)
        .insert(sparql"$ontologyIri $kbLastMod $newLmd .")
        .into(ontologyIri)
        .where(
          sparql"$ontologyIri a $owlOntology .",
          sparql"$ontologyIri $kbLastMod $lmdValue .",
          sparql"$propertyIri a $owlObjectProp .",
          sparql"$propertyIri $propertyPred $propertyObj .",
          Fragments.filterNotExists(sparql"$s $p $propertyIri ."),
          Fragment.combine(linkValueWherePattern),
        )
        .render

      assertTrue(
        query.contains("PREFIX knora-base:"),
        query.contains("DELETE"),
        query.contains("INSERT"),
        query.contains("WHERE"),
        query.contains("FILTER NOT EXISTS"),
        query.contains("hasOtherThingValue"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: InsertValueQueryBuilder sketch (complex conditional + iteration)
  // -------------------------------------------------------------------------

  /** Simplified link update data for the benchmark. */
  case class LinkUpdate(
    linkPropertyIri: String,
    linkTargetIri: String,
    deleteDirectLink: Boolean,
    insertDirectLink: Boolean,
    linkValueExists: Boolean,
    newLinkValueIri: String,
    newReferenceCount: Int,
    currentReferenceCount: Int,
    newLinkValueCreator: String,
    newLinkValuePermissions: String,
  )

  val insertValueBenchmarkSketch = suite("Benchmark: InsertValueQueryBuilder sketch")(
    test("handles conditional link patterns and iteration with indexed variables") {
      val resource         = Variable("resource")
      val resourceLastMod  = Variable("resourceLastModificationDate")
      val kbHasPermissions = Iri.trusted(knoraBase + "hasPermissions")
      val kbValueHasUUID   = Iri.trusted(knoraBase + "valueHasUUID")

      val linkUpdates = List(
        LinkUpdate(
          "http://example.org/hasLink",
          "http://example.org/target1",
          deleteDirectLink = true,
          insertDirectLink = true,
          linkValueExists = true,
          "http://example.org/newLinkValue1",
          newReferenceCount = 1,
          currentReferenceCount = 1,
          "http://example.org/user1",
          "CR knora-admin:ProjectAdmin",
        ),
      )

      // Build delete patterns with conditional logic (mirrors InsertValueQueryBuilder.buildDeletePatterns)
      val linkValueDeletePatterns: Fragment = linkUpdates.zipWithIndex.map { case (linkUpdate, index) =>
        val deleteDirectLink = Option.when(linkUpdate.deleteDirectLink) {
          val linkProp = Iri.trusted(linkUpdate.linkPropertyIri)
          val target   = Iri.trusted(linkUpdate.linkTargetIri)
          sparql"$resource $linkProp $target ."
        }

        val linkValueExistsPatterns = Option.when(linkUpdate.linkValueExists) {
          val linkValue      = Variable(s"linkValue$index")
          val linkValueUUID  = Variable(s"linkValueUUID$index")
          val linkValuePerms = Variable(s"linkValuePermissions$index")
          val linkPropValue  = Iri.trusted(linkUpdate.linkPropertyIri + "Value")
          Fragment.join(
            List(
              sparql"$resource $linkPropValue $linkValue .",
              sparql"$linkValue $kbValueHasUUID $linkValueUUID .",
              sparql"$linkValue $kbHasPermissions $linkValuePerms .",
            ),
            Fragment.raw("\n"),
          )
        }

        Fragment.combine(deleteDirectLink, linkValueExistsPatterns)
      }.combineAll

      val deleteClause = Fragment.join(
        List(
          sparql"$resource $kbLastMod $resourceLastMod .",
          linkValueDeletePatterns,
        ),
        Fragment.raw("\n"),
      )

      val query = SparqlQuery.update
        .delete(deleteClause)
        .where(sparql"$resource a $kbResource .")
        .render

      assertTrue(
        query.contains(
          "?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModificationDate",
        ),
        query.contains("?resource <http://example.org/hasLink> <http://example.org/target1>"),
        query.contains("?linkValue0"),
        query.contains("?linkValueUUID0"),
        query.contains("?linkValuePermissions0"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: SearchQueries.selectCountByLabel (Hybrid → Lucene + filters)
  // -------------------------------------------------------------------------
  val searchQueriesBenchmark = suite("Benchmark: SearchQueries.selectCountByLabel")(
    test("renders SELECT count with Lucene and conditional filters") {
      val resource      = Variable("resource")
      val resourceClass = Variable("resourceClass")
      val count         = Variable("count")

      val luceneQuery   = "test search" // Would come from FusekiLuceneQuery
      val textQueryPred = Iri.trusted("http://jena.apache.org/text#query")
      val rdfsLabel     = Iri.trusted(rdfs + "label")

      val limitToProject: Option[Iri]       = Some(Iri.trusted("http://rdfh.ch/projects/0001"))
      val limitToResourceClass: Option[Iri] = None

      // Build conditional filter fragments
      val projectFilter = limitToProject.map { prj =>
        val attachedToProject = Iri.trusted(knoraBase + "attachedToProject")
        sparql"$resource $attachedToProject $prj ."
      }

      val classFilter = limitToResourceClass.map { cls =>
        sparql"$resourceClass ${Iri.trusted(rdfs + "subClassOf*")} $cls ."
      }

      val filters = Fragment.combine(projectFilter, classFilter)

      val query = SparqlQuery
        .select()
        .prefix("rdfs", rdfs)
        .prefix("knora-base", knoraBase)
        .withExpr(sparql"(count(distinct $resource) as $count)")
        .where(
          sparql"""$resource $textQueryPred ($rdfsLabel ${Literal.string(luceneQuery)}) ;
    a $resourceClass .""",
          sparql"$resourceClass ${Iri.trusted(rdfs + "subClassOf*")} ${Iri.trusted(knoraBase + "Resource")} .",
          filters,
          Fragments.filterNotExists(sparql"$resource $kbIsDeleted true ."),
        )
        .render

      assertTrue(
        query.contains("count(distinct ?resource) as ?count"),
        query.contains("text#query"),
        query.contains("\"test search\""),
        query.contains("attachedToProject"),
        query.contains("FILTER NOT EXISTS"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Conditional fragments (Twirl @if/@match equivalent)
  // -------------------------------------------------------------------------
  val conditionalFragmentsSuite = suite("Conditional fragments")(
    test("Option[Fragment] with combine") {
      val maybeComment: Option[String] = Some("A comment")
      val commentIri                   = Iri.trusted(knoraBase + "valueHasComment")
      val newValue                     = Variable("newValue")

      val commentPattern: Option[Fragment] = maybeComment.map { c =>
        sparql"$newValue $commentIri ${Literal.string(c)} ."
      }

      val result = Fragment.combine(
        Some(sparql"$newValue a ${Iri.trusted(knoraBase + "TextValue")} ."),
        commentPattern,
      )

      assertTrue(
        result.render.contains("TextValue"),
        result.render.contains("valueHasComment"),
        result.render.contains("A comment"),
      )
    },
    test("None fragments are skipped") {
      val noComment: Option[Fragment] = None
      val result                      = Fragment.combine(
        Some(sparql"?s a ?type ."),
        noComment,
      )
      assertTrue(
        result.render.contains("?s a ?type"),
        !result.render.contains("comment"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Dynamic iteration (Twirl @for equivalent)
  // -------------------------------------------------------------------------
  val iterationSuite = suite("Dynamic iteration")(
    test("collection to indexed variable patterns via map + combineAll") {
      case class ValueUpdate(predicateIri: String, value: String)

      val updates = List(
        ValueUpdate("http://example.org/hasName", "Alice"),
        ValueUpdate("http://example.org/hasAge", "30"),
      )

      val newValue = Variable("newValue")

      val patterns: Fragment = updates.map { update =>
        val pred = Iri.trusted(update.predicateIri)
        val lit  = Literal.string(update.value)
        sparql"$newValue $pred $lit ."
      }.combineAll

      val rendered = patterns.render
      assertTrue(
        rendered.contains("hasName"),
        rendered.contains("\"Alice\""),
        rendered.contains("hasAge"),
        rendered.contains("\"30\""),
      )
    },
    test("indexed variables for link updates (Twirl @for equivalent)") {
      val resource    = Variable("resource")
      val linkUpdates = List("target1", "target2", "target3")

      val patterns: Fragment = linkUpdates.zipWithIndex.map { case (target, idx) =>
        val linkValue = Variable(s"linkValue$idx")
        val targetIri = Iri.trusted(s"http://example.org/$target")
        sparql"$resource ${Iri.trusted(knoraBase + "hasLink")} $targetIri .\n$linkValue ${Iri.trusted(knoraBase + "valueHasRefCount")} ${Literal.int(1)} ."
      }.combineAll

      val rendered = patterns.render
      assertTrue(
        rendered.contains("?linkValue0"),
        rendered.contains("?linkValue1"),
        rendered.contains("?linkValue2"),
        rendered.contains("target1"),
        rendered.contains("target2"),
        rendered.contains("target3"),
      )
    },
  )
}
