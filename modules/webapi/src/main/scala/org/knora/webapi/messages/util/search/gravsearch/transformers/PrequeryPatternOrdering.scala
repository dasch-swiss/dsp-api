/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import scala.annotation.tailrec

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.search.*

/**
 * Orders the patterns of one Gravsearch prequery WHERE block so that Fuseki (TDB2, no `stats.opt`)
 * evaluates them in a sane order. This is a pure function, not wired into the prequery pipeline yet.
 *
 * Relies on the engine facts documented in `docs/development/dsp-api-fuseki-query-execution.md`: Fact 1
 * (Fuseki reorders heuristically but only within one BGP, essentially preserving document order across
 * barriers), Fact 3 (`*`/`+` property paths are anchored by whichever end is bound), Fact 4 (`MINUS`
 * evaluates its right side without the outer bindings; `OPTIONAL`/`UNION`/`FILTER NOT EXISTS` see it), and
 * Fact 7 (a large `VALUES` table poisons join order unless it drives the scan).
 *
 * Tier table (lower is better), data so the spike's result plugs in without touching the procedure:
 *   - T1 Lucene: a `text:query` statement, or a [[GroupPattern]] containing one at any depth.
 *   - T2 Bound IRI: a non-type statement (property paths included) with an `IriRef` subject or object,
 *     whose predicate is a bound IRI other than `knora-base:attachedToProject`; also an `rdf:type`
 *     statement with an `IriRef` subject.
 *   - T3 Bound literal: a non-type statement with an `XsdLiteral` object.
 *   - T4 Project-class type unit.
 *   - T5 Enumerating technical type unit: `rdf:type` on a variable bound by a `VALUES` that still
 *     contains a `knora-base` class.
 *   - T6 `?x knora-base:attachedToProject <iri>`.
 *   - T7 Plain: everything else; within T7, non-path statements before property-path statements.
 * The measured basis and the provenance of each row (which are measured, which carried forward as
 * hypothesis) are in `docs/specs/2026-09-17-01-gravsearch-prequery-ordering-design.md` § "Tier table
 * (measured)". A non-type statement with a variable predicate, or with predicate `rdfs:subClassOf` /
 * `rdfs:subPropertyOf`, is excluded from T2 and ranks T7 regardless of a bound object. A type unit whose
 * object is a single `IriRef` naming `knora-base:LinkValue` or `knora-base:Resource` is
 * unselective-technical: it ranks T7 and may never lead a component, listed by name rather than by
 * namespace (see the design doc's "Change the spike forces" section for why).
 *
 * The recursion seeds in step 4 below (`MINUS` recursed with an empty bound set; `OPTIONAL`/`UNION`
 * branches/`FILTER NOT EXISTS` recursed with the outer bound set) are an execution-plan heuristic
 * mirroring Fuseki's evaluation (Fact 4), not a SPARQL-semantics claim. Likewise, placing every statement
 * before every block is parity with today's `ReorderPatternsByDependency`, not a SPARQL identity: it can
 * change results when a block binds a variable a later statement also uses. Both are deliberate, scoped
 * choices; see the design doc for the argument.
 */
object PrequeryPatternOrdering {

  /**
   * Reorders `patterns` for connectivity-aware evaluation. `outerBound` is the set of variables already
   * bound by an enclosing scope (used when recursing into `OPTIONAL`/`UNION`/`FILTER NOT EXISTS`/`GROUP`
   * blocks). Preserves the size of the input.
   */
  def order(patterns: Seq[QueryPattern], outerBound: Set[QueryVariable] = Set.empty): Seq[QueryPattern] =
    orderGroup(patterns, outerBound)

  private val unselectiveTechnicalClasses: Set[String] =
    Set(OntologyConstants.KnoraBase.LinkValue, OntologyConstants.KnoraBase.Resource)

  private case class Buckets(
    binds: Vector[BindPattern],
    values: Vector[ValuesPattern],
    units: Vector[QueryPattern],
    blocks: Vector[QueryPattern],
    filters: Vector[FilterPattern],
    notExists: Vector[FilterNotExistsPattern],
  )

  private def partition(patterns: Seq[QueryPattern]): Buckets =
    patterns.foldLeft(Buckets(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)) {
      (acc, p) =>
        p match {
          case b: BindPattern              => acc.copy(binds = acc.binds :+ b)
          case v: ValuesPattern            => acc.copy(values = acc.values :+ v)
          case s: StatementPattern         => acc.copy(units = acc.units :+ s)
          case g: GroupPattern             => acc.copy(units = acc.units :+ g)
          case blk: OptionalPattern        => acc.copy(blocks = acc.blocks :+ blk)
          case blk: UnionPattern           => acc.copy(blocks = acc.blocks :+ blk)
          case blk: MinusPattern           => acc.copy(blocks = acc.blocks :+ blk)
          case f: FilterPattern            => acc.copy(filters = acc.filters :+ f)
          case fne: FilterNotExistsPattern => acc.copy(notExists = acc.notExists :+ fne)
        }
    }

  /** The variables a pattern binds or references; block patterns recurse into their contents. */
  private def vars(p: QueryPattern): Set[QueryVariable] = p match {
    case StatementPattern(subj, pred, obj) => Seq(subj, pred, obj).collect { case v: QueryVariable => v }.toSet
    case v: ValuesPattern                  => Set(v.variable)
    case b: BindPattern                    => Set(b.variable)
    case f: FilterPattern                  => f.expression.getVariables
    case o: OptionalPattern                => o.patterns.flatMap(vars).toSet
    case u: UnionPattern                   => u.blocks.flatMap(_.flatMap(vars)).toSet
    case m: MinusPattern                   => m.patterns.flatMap(vars).toSet
    case fne: FilterNotExistsPattern       => fne.patterns.flatMap(vars).toSet
    case g: GroupPattern                   => g.patterns.flatMap(vars).toSet
  }

  private def isTypeUnit(u: QueryPattern): Boolean = u match {
    case StatementPattern(_: QueryVariable, IriRef(pred, _), _) => pred.toIri == OntologyConstants.Rdf.Type
    case _                                                      => false
  }

  private def isUnselectiveTechnicalType(u: QueryPattern): Boolean = u match {
    case StatementPattern(_: QueryVariable, IriRef(pred, _), IriRef(obj, _)) =>
      pred.toIri == OntologyConstants.Rdf.Type && unselectiveTechnicalClasses.contains(obj.toIri)
    case _ => false
  }

  private def isPropertyPath(u: QueryPattern): Boolean = u match {
    case StatementPattern(_, IriRef(_, Some(_)), _) => true
    case _                                          => false
  }

  private def containsLucene(g: GroupPattern): Boolean = g.patterns.exists {
    case StatementPattern(_, IriRef(pred, _), _) => pred.toIri == OntologyConstants.Fuseki.luceneQueryPredicate
    case inner: GroupPattern                     => containsLucene(inner)
    case _                                       => false
  }

  /** Tier of an `rdf:type` unit whose subject is a variable; see the object's Scaladoc for the rules. */
  private def typeTier(s: StatementPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Int =
    if (isUnselectiveTechnicalType(s)) 7
    else
      s.obj match {
        case IriRef(iri, _) =>
          if (!iri.toIri.startsWith(OntologyConstants.KnoraBase.KnoraBasePrefixExpansion)) 4 else 7
        case v: QueryVariable =>
          val iris = valuesByVar.getOrElse(v, Seq.empty).flatMap(_.values).map(_.iri.toIri)
          if (iris.nonEmpty && iris.forall(!_.startsWith(OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))) 4
          else if (iris.nonEmpty) 5
          else 7
        case _ => 7
      }

  private def statementTier(s: StatementPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Int =
    s.pred match {
      case IriRef(pred, _) if pred.toIri == OntologyConstants.Fuseki.luceneQueryPredicate => 1
      case IriRef(pred, _) if pred.toIri == OntologyConstants.Rdf.Type                    =>
        s.subj match {
          case _: IriRef        => 2
          case _: QueryVariable => typeTier(s, valuesByVar)
          case _                => 7
        }
      case IriRef(pred, _)
          if pred.toIri == OntologyConstants.Rdfs.SubClassOf || pred.toIri == OntologyConstants.Rdfs.SubPropertyOf =>
        7
      case IriRef(pred, _) if pred.toIri == OntologyConstants.KnoraBase.AttachedToProject => 6
      case IriRef(_, _)                                                                   =>
        if (s.subj.isInstanceOf[IriRef] || s.obj.isInstanceOf[IriRef]) 2
        else if (s.obj.isInstanceOf[XsdLiteral]) 3
        else 7
      case _ => 7 // a variable predicate is never promoted to T2 (Fact 1's bound-object corollary)
    }

  private def tierNum(u: QueryPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Int = u match {
    case g: GroupPattern     => if (containsLucene(g)) 1 else 7
    case s: StatementPattern => statementTier(s, valuesByVar)
    case _                   => 7
  }

  private def boundTermsCount(unit: QueryPattern, bound: Set[QueryVariable]): Int = unit match {
    case StatementPattern(subj, pred, obj) =>
      Seq(subj, pred, obj).count {
        case _: IriRef        => true
        case _: XsdLiteral    => true
        case v: QueryVariable => bound.contains(v)
        case _                => false
      }
    case other => vars(other).count(bound.contains)
  }

  private def rank(
    u: QueryPattern,
    bound: Set[QueryVariable],
    valuesByVar: Map[QueryVariable, Seq[ValuesPattern]],
  ): (Int, Int, Int, String) = {
    val t        = tierNum(u, valuesByVar)
    val pathRank = if (t == 7 && isPropertyPath(u)) 1 else 0
    (t, pathRank, -boundTermsCount(u, bound), u.toSparql)
  }

  private def bestOf(
    candidates: Seq[QueryPattern],
    bound: Set[QueryVariable],
    valuesByVar: Map[QueryVariable, Seq[ValuesPattern]],
  ): QueryPattern = candidates.minBy(rank(_, bound, valuesByVar))

  /** Rules 3a-3d: picks the next unit to emit from the units still remaining. */
  private def pickNext(
    remaining: Seq[QueryPattern],
    bound: Set[QueryVariable],
    valuesByVar: Map[QueryVariable, Seq[ValuesPattern]],
  ): QueryPattern = {
    def connected(u: QueryPattern) = vars(u).intersect(bound).nonEmpty
    val (typeUnits, nonType)       = remaining.partition(isTypeUnit)
    val ruleA                      = nonType.filter(connected)
    val ruleB                      = typeUnits.filter(connected)
    val ruleC                      = remaining.filterNot(isUnselectiveTechnicalType)

    if (ruleA.nonEmpty) bestOf(ruleA, bound, valuesByVar)
    else if (ruleB.nonEmpty) bestOf(ruleB, bound, valuesByVar)
    else if (ruleC.nonEmpty) bestOf(ruleC, bound, valuesByVar)
    else bestOf(remaining, bound, valuesByVar)
  }

  /** Emits `units` (step 3), attaching each unit-referenced `VALUES` immediately before its unit. */
  private def emitUnits(
    units: Seq[QueryPattern],
    unitAttachedValues: Seq[ValuesPattern],
    valuesByVar: Map[QueryVariable, Seq[ValuesPattern]],
    bound0: Set[QueryVariable],
  ): (Seq[QueryPattern], Set[QueryVariable]) = {
    val byVar = unitAttachedValues.groupBy(_.variable)

    @tailrec
    def loop(
      remaining: Vector[QueryPattern],
      bound: Set[QueryVariable],
      attachedVars: Set[QueryVariable],
      acc: Vector[QueryPattern],
    ): (Vector[QueryPattern], Set[QueryVariable]) =
      if (remaining.isEmpty) (acc, bound)
      else {
        val chosen   = pickNext(remaining, bound, valuesByVar)
        val idx      = remaining.indexWhere(_ eq chosen)
        val toAttach = byVar.keySet.diff(attachedVars).filter(vars(chosen).contains).toSeq.sortBy(_.variableName)
        val emitted  = toAttach.flatMap(byVar)
        loop(remaining.patch(idx, Nil, 1), bound ++ vars(chosen), attachedVars ++ toAttach, acc ++ emitted :+ chosen)
      }

    loop(units.toVector, bound0, Set.empty, Vector.empty)
  }

  /** Inserts each block-attached `VALUES` immediately before the first element that references it. */
  private def attachValues(elems: Seq[QueryPattern], blockAttached: Seq[ValuesPattern]): Seq[QueryPattern] = {
    val byVar    = blockAttached.groupBy(_.variable)
    val (_, out) = elems.foldLeft((Set.empty[QueryVariable], Vector.empty[QueryPattern])) {
      case ((attached, acc), elem) =>
        val toAttach = byVar.keySet.diff(attached).filter(vars(elem).contains).toSeq.sortBy(_.variableName)
        (attached ++ toAttach, (acc ++ toAttach.flatMap(byVar)) :+ elem)
    }
    out
  }

  private def recurseBlock(block: QueryPattern, bound: Set[QueryVariable]): QueryPattern = block match {
    case OptionalPattern(ps) => OptionalPattern(orderGroup(ps, bound))
    case UnionPattern(bs)    => UnionPattern(bs.map(orderGroup(_, bound)))
    case MinusPattern(ps)    => MinusPattern(orderGroup(ps, Set.empty))
    case other               => other
  }

  private def orderGroup(patterns: Seq[QueryPattern], outerBound: Set[QueryVariable]): Seq[QueryPattern] = {
    val b           = partition(patterns)
    val valuesByVar = b.values.groupBy(_.variable)

    val unitVars    = b.units.flatMap(vars).toSet
    val nonUnitVars = (b.blocks ++ b.filters ++ b.notExists).flatMap(vars).toSet

    val (unitAttached, rest)     = b.values.partition(v => unitVars.contains(v.variable))
    val (blockAttached, orphans) = rest.partition(v => nonUnitVars.contains(v.variable))

    val bound0                          = outerBound ++ b.binds.flatMap(vars).toSet
    val (orderedUnits, boundAfterUnits) = emitUnits(b.units, unitAttached, valuesByVar, bound0)

    val recursedBlocks    = b.blocks.map(recurseBlock(_, boundAfterUnits))
    val recursedNotExists = b.notExists.map(fne => FilterNotExistsPattern(orderGroup(fne.patterns, boundAfterUnits)))
    val tail              = attachValues(recursedBlocks ++ b.filters ++ recursedNotExists, blockAttached)

    b.binds ++ orderedUnits ++ orphans ++ tail
  }
}
