/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.OpVisitorBase
import org.apache.jena.sparql.algebra.OpWalker
import org.apache.jena.sparql.algebra.op.OpBGP
import org.apache.jena.sparql.algebra.op.OpFilter
import org.apache.jena.sparql.expr.E_NotExists
import org.apache.jena.sparql.expr.Expr
import org.junit.runner.RunWith
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.gravsearch.mainquery.GravsearchMainQueryGenerator
import org.knora.webapi.slice.resources.repo.GetResourcePropertiesAndValuesQuery

/**
 * Guards the placement rule of [[StandoffLink]] against the optimizer: the NOT EXISTS filter must be applied to
 * the link scan alone, not to the join with the standoff node properties.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkAlgebraSpec extends ZIOSpecDefault {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private val valueHasStandoff = OntologyConstants.KnoraBase.ValueHasStandoff

  private def isStoredXmlCheck(expr: Expr): Boolean =
    expr.isInstanceOf[E_NotExists] && expr.toString.contains("valueHasXml")

  /** The sub-operators of every `FILTER NOT EXISTS` on `valueHasXml` in the optimized algebra of the query. */
  private def notExistsFilterSubOps(sparql: String): List[Op] = {
    val optimized = Algebra.optimize(Algebra.compile(QueryFactory.create(sparql)))
    val found     = scala.collection.mutable.ListBuffer.empty[Op]
    OpWalker.walk(
      optimized,
      new OpVisitorBase {
        override def visit(opFilter: OpFilter): Unit =
          if (opFilter.getExprs.getList.asScala.exists(isStoredXmlCheck)) found += opFilter.getSubOp
      },
    )
    found.toList
  }

  private def isOnlyTheLinkTriple(op: Op): Boolean = op match {
    case bgp: OpBGP =>
      val triples = bgp.getPattern.getList.asScala.toList
      triples.size == 1 && triples.head.getPredicate.getURI == valueHasStandoff
    case _ => false
  }

  private val getResourcePropertiesAndValues: String =
    GetResourcePropertiesAndValuesQuery
      .build(
        resourceIris = Seq("http://rdfh.ch/0001/resource1"),
        preview = false,
        withDeleted = false,
        standoff = StandoffSelection.OnlyWithoutStoredXml,
      )
      .sparql

  private val gravsearchMainQuery: String =
    GravsearchMainQueryGenerator
      .createMainQuery(
        Set(IriRef("http://rdfh.ch/0001/resource1".toSmartIri)),
        Set.empty,
        Set("http://rdfh.ch/0001/resource1/values/value1"),
        StandoffSelection.OnlyWithoutStoredXml,
      )
      .toSparql

  val spec: Spec[Any, Nothing] = suite("StandoffLink optimized algebra")(
    test("GetResourcePropertiesAndValuesQuery filters the link scan alone") {
      val subOps = notExistsFilterSubOps(getResourcePropertiesAndValues)
      assertTrue(subOps.size == 1, subOps.forall(isOnlyTheLinkTriple))
    },
    test("GravsearchMainQueryGenerator filters the link scan alone") {
      val subOps = notExistsFilterSubOps(gravsearchMainQuery)
      assertTrue(subOps.size == 1, subOps.forall(isOnlyTheLinkTriple))
    },
  )
}
