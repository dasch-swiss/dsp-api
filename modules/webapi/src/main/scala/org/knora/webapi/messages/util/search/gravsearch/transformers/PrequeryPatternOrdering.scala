/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import scala.annotation.tailrec

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

/**
 * Orders the patterns of one Gravsearch prequery WHERE block so that Fuseki (TDB2, no `stats.opt`)
 * evaluates them in a sane order.
 *
 * Relies on the engine facts documented in `docs/development/dsp-api-fuseki-query-execution.md`: Fact 1
 * (Fuseki reorders heuristically but only within one BGP, essentially preserving document order across
 * barriers), Fact 3 (`*`/`+` property paths are anchored by whichever end is bound), Fact 4 (`MINUS`
 * evaluates its right side without the outer bindings; `OPTIONAL`/`UNION`/`FILTER NOT EXISTS` see it), and
 * Fact 7 (a large `VALUES` table poisons join order unless it drives the scan).
 *
 * Tier table (lower is better):
 *   - T1 Lucene: a `text:query` statement, or a [[GroupPattern]] containing one directly or in a nested
 *     [[GroupPattern]]; a `text:query` inside an `OPTIONAL`/`UNION`/`MINUS` within the group is not found.
 *     T1 pre-empts the connectivity rule: a T1 unit leads its block whether or not it is connected to the
 *     already-bound variables.
 *   - T2 Bound IRI: a non-type statement (property paths included) with an `IriRef` subject or object,
 *     whose predicate is a bound IRI other than `knora-base:attachedToProject`; also an `rdf:type`
 *     statement with an `IriRef` subject.
 *   - T3 Bound literal: a non-type statement with an `XsdLiteral` object.
 *   - T4 Project-class type unit: `rdf:type` naming a class in a project data ontology (an internal
 *     ontology IRI carrying a project shortcode), either directly or as the sole content of an
 *     enumerating `VALUES`.
 *   - T5 Enumerating technical type unit: `rdf:type` on a variable bound by a non-empty `VALUES`
 *     enumeration where not every entry is a class in a project data ontology - for example an
 *     enumeration mixing project and built-in classes, or naming an external IRI, or naming only classes
 *     from a built-in ontology (`knora-base`, `standoff`, `salsah-gui`, `knora-admin`, or `shared`).
 *   - T6 `?x knora-base:attachedToProject <iri>`.
 *   - T7 Plain: everything else; within T7, non-path statements before property-path statements.
 * A non-type statement with a variable predicate, or with predicate `rdfs:subClassOf` /
 * `rdfs:subPropertyOf`, is excluded from T2 and ranks T7 regardless of a bound object. A type unit whose
 * object is a single `IriRef` naming `knora-base:LinkValue` or `knora-base:Resource` is
 * unselective-technical: it ranks T7 and may never lead a component. These two classes are listed by name
 * rather than by namespace, deliberately - do not widen this to a namespace test: any other `rdf:type` unit
 * whose object is a bare `IriRef` in a built-in ontology (for example `?v a knora-base:TextValue` or
 * `?n a knora-base:ListNode`) also ranks T7, but is not unselective-technical and may still lead a
 * component. A term is treated as bound for the bound-terms-count tie-break not only when it is an
 * `IriRef`/`XsdLiteral` or a variable already in the greedy loop's growing `bound` set, but also when it
 * is a variable with a non-empty attached `VALUES`: `attachValues` emits that `VALUES` immediately before
 * the first pattern referencing the variable, so at evaluation time the term is restricted exactly like a
 * bound IRI, and a statement such as `?a ?p ?b` with a `VALUES` on `?p` must not be out-ranked by a
 * store-wide statement such as `?lv <rdf:object> ?o` on term count alone. When two candidates otherwise
 * tie, a statement whose predicate is a bound IRI in a project data ontology - or a variable predicate
 * whose attached `VALUES` enumerates only project data ontology IRIs - ranks ahead of one that is not,
 * before falling back to the rendered-text key; this keeps a project-scoped predicate (typically far more
 * selective on stage) as the join driver ahead of a store-wide built-in predicate such as
 * `knora-base:valueHasStartJDN` that would otherwise win on lexical order alone.
 *
 * The recursion seeds used when descending into a block (`MINUS` recursed with an empty bound set; `OPTIONAL`/`UNION`
 * branches/`FILTER NOT EXISTS` recursed with the outer bound set) are an execution-plan heuristic mirroring Fuseki's
 * evaluation (Fact 4), not a SPARQL-semantics claim. Likewise, placing every statement before every block stays
 * consistent with the `StatementsFirst` partition earlier in the pipeline, not a SPARQL identity: it can change results
 * when a block binds a variable a later statement also uses. A T1 Lucene unit leads its block regardless of
 * connectivity, matching the legacy hoisting pass this replaces, which recorded the class `VALUES` enumeration as ~300x
 * slower than the index-anchored Lucene lookup in the DEV-6715 performance spike.
 *
 * This pass's output is pinned by the golden files driven by `GravsearchToPrequeryTransformerE2ESpec` and
 * `GravsearchToCountPrequeryTransformerE2ESpec`, which live in `modules/test-it`, not in `modules/webapi`
 * alongside this file: a tier change verified only with `bazel test //modules/webapi:test` looks green while
 * silently changing emitted query order. Regenerate them through the `GOLDEN_REWRITE` switch documented in
 * `docs/development/dsp-api-conventions.md` and review the diff. `just test-gravsearch-prequery` runs both
 * halves together.
 */
object PrequeryPatternOrdering {

  /**
   * Reorders `patterns` for connectivity-aware evaluation. `outerBound` is the set of variables already
   * bound by an enclosing scope; it exists for the internal per-block recursion
   * (`OPTIONAL`/`UNION`/`MINUS`/`FILTER NOT EXISTS`) and for tests - production has a single call site, which
   * passes one argument. A [[GroupPattern]] is an opaque leaf and is never recursed into. Preserves the size
   * of the input.
   */
  def order(patterns: Seq[QueryPattern], outerBound: Set[QueryVariable] = Set.empty): Seq[QueryPattern] =
    orderGroup(patterns, outerBound)

  private val unselectiveTechnicalClasses: Set[String] =
    Set(OntologyConstants.KnoraBase.LinkValue, OntologyConstants.KnoraBase.Resource)

  /**
   * True iff `iri` is an internal ontology IRI carrying a project shortcode, i.e. it is shaped
   * `http://www.knora.org/ontology/<shortcode>/<name>#...`. The built-in technical ontologies
   * (`knora-base`, `standoff`, `salsah-gui`, `knora-admin`) have a single path segment after the
   * internal-ontology prefix and fail this test; so does `shared`, whose first segment is not a
   * valid shortcode.
   */
  private def isProjectDataOntologyIri(iri: String): Boolean = {
    val prefix = OntologyConstants.KnoraInternal.InternalOntologyStart + "/"
    if (!iri.startsWith(prefix)) false
    else {
      val afterPrefix = iri.substring(prefix.length)
      val path        = afterPrefix.takeWhile(_ != '#')
      path.split('/').toList match {
        case shortcode :: _ :: Nil => Shortcode.from(shortcode).isRight
        case _                     => false
      }
    }
  }

  private case class Buckets(
    binds: Vector[BindPattern],
    values: Vector[ValuesPattern],
    units: Vector[QueryPattern],
    blocks: Vector[OptionalPattern | UnionPattern | MinusPattern],
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

  /**
   * Deliberately asymmetric: only a bare `IriRef` object naming `LinkValue`/`Resource` is unselective. A
   * type statement whose object variable is bound by a `VALUES` enumeration is exempt (T5) even if that
   * enumeration happens to contain only these two classes, because the enumeration can drive the scan.
   */
  private def isUnselectiveTechnicalType(u: QueryPattern): Boolean = u match {
    case StatementPattern(_: QueryVariable, IriRef(pred, _), IriRef(obj, _)) =>
      pred.toIri == OntologyConstants.Rdf.Type && unselectiveTechnicalClasses.contains(obj.toIri)
    case _ => false
  }

  private def isPropertyPath(u: QueryPattern): Boolean = u match {
    case StatementPattern(_, IriRef(_, Some(_)), _) => true
    case _                                          => false
  }

  /** True iff `pred` is the Fuseki full-text-search predicate (`text:query`). */
  private def isLuceneQueryPredicate(pred: Entity): Boolean = pred match {
    case IriRef(iri, _) => iri.toIri == OntologyConstants.Fuseki.luceneQueryPredicate
    case _              => false
  }

  private def containsLucene(g: GroupPattern): Boolean = g.patterns.exists {
    case StatementPattern(_, pred, _) => isLuceneQueryPredicate(pred)
    case inner: GroupPattern          => containsLucene(inner)
    case _                            => false
  }

  /** Tier of an `rdf:type` unit whose subject is a variable; see the object's Scaladoc for the rules. */
  private def typeTier(s: StatementPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Int =
    if (isUnselectiveTechnicalType(s)) 7
    else
      s.obj match {
        case IriRef(iri, _) =>
          if (isProjectDataOntologyIri(iri.toIri)) 4 else 7
        case v: QueryVariable =>
          val iris = valuesByVar.getOrElse(v, Seq.empty).flatMap(_.values).map(_.iri.toIri)
          if (iris.nonEmpty && iris.forall(isProjectDataOntologyIri)) 4
          else if (iris.nonEmpty) 5
          else 7
        case _ => 7
      }

  private def statementTier(s: StatementPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Int =
    s.pred match {
      case pred if isLuceneQueryPredicate(pred)                        => 1
      case IriRef(pred, _) if pred.toIri == OntologyConstants.Rdf.Type =>
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

  /** True iff `v` has a non-empty attached `VALUES`, restricting it independently of the greedy loop's `bound` set. */
  private def isValuesRestricted(v: QueryVariable, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Boolean =
    valuesByVar.get(v).exists(_.exists(_.values.nonEmpty))

  /**
   * Counts bound terms for `k` against the loop's growing `bound` set: `k.restrictedTermsCount` (an
   * `IriRef`/`XsdLiteral` term, or a variable term with a non-empty attached `VALUES` - see the object's
   * Scaladoc) plus however many of `k.loopVars` `bound` already contains. Both fields are precomputed once
   * per unit in `unitKey`, since they do not depend on `bound`; recomputing them from `k.unit` on every
   * greedy-loop step would redo bound-independent work the cache exists to avoid.
   */
  private def boundTermsCount(k: UnitKey, bound: Set[QueryVariable]): Int =
    k.restrictedTermsCount + k.loopVars.count(bound.contains)

  /**
   * True iff `u` is a statement whose predicate is a bound IRI in a project data ontology, or whose
   * predicate is a variable with a non-empty attached `VALUES` in which every entry is a project data
   * ontology IRI (mirroring `typeTier`'s all-entries-must-be-project rule). This matches against any
   * predicate, including `rdf:type`; a type statement fails it in practice because `rdf` is not a project
   * data ontology, not because type statements are special-cased out. A mixed `VALUES` (any non-project
   * entry, e.g. a `knora-base` IRI) or an empty one stays false.
   */
  private def hasProjectDataPredicate(u: QueryPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): Boolean =
    u match {
      case StatementPattern(_, IriRef(pred, _), _)  => isProjectDataOntologyIri(pred.toIri)
      case StatementPattern(_, v: QueryVariable, _) =>
        val iris = valuesByVar.getOrElse(v, Seq.empty).flatMap(_.values).map(_.iri.toIri)
        iris.nonEmpty && iris.forall(isProjectDataOntologyIri)
      case _ => false
    }

  /**
   * A unit's rank fields that do not depend on the greedy loop's growing `bound` set: the tier, the
   * property-path tie-break, the project-data-predicate tie-break, the rendered SPARQL used as the final
   * tie-break, the unit's own variables, the two membership flags `pickNext` branches on, and the
   * bound-terms-count split (see `boundTermsCount`). Computed once per unit before the loop so the
   * per-step work is only checking `loopVars` against `bound`.
   *
   * `restrictedTermsCount` counts the terms already restricted independently of the loop: an
   * `IriRef`/`XsdLiteral` term, or a `QueryVariable` term with a non-empty attached `VALUES`.
   * `loopVars` lists the remaining variable terms the loop must check against `bound` - for a
   * `StatementPattern` with multiplicity (one entry per occurrence among subject/predicate/object, as
   * `boundTermsCount` did before this split), for any other unit kind as `varsOf` minus the
   * `VALUES`-restricted variables. A variable that is both `VALUES`-restricted and later added to `bound`
   * counts once, via `restrictedTermsCount`, never twice.
   */
  private case class UnitKey(
    unit: QueryPattern,
    tier: Int,
    pathRank: Int,
    predicateRank: Int,
    sparql: String,
    varsOf: Set[QueryVariable],
    isType: Boolean,
    isUnselectiveTechnical: Boolean,
    restrictedTermsCount: Int,
    loopVars: Seq[QueryVariable],
  )

  private def unitKey(u: QueryPattern, valuesByVar: Map[QueryVariable, Seq[ValuesPattern]]): UnitKey = {
    val t                                                      = tierNum(u, valuesByVar)
    val (restrictedCount, loopVars): (Int, Seq[QueryVariable]) = u match {
      case StatementPattern(subj, pred, obj) =>
        val terms      = Seq(subj, pred, obj)
        val restricted = terms.count {
          case _: IriRef                                              => true
          case _: XsdLiteral                                          => true
          case v: QueryVariable if isValuesRestricted(v, valuesByVar) => true
          case _                                                      => false
        }
        val remaining = terms.collect {
          case v: QueryVariable if !isValuesRestricted(v, valuesByVar) => v
        }
        (restricted, remaining)
      case _ =>
        val allVars    = vars(u)
        val restricted = allVars.count(v => isValuesRestricted(v, valuesByVar))
        val remaining  = allVars.filterNot(v => isValuesRestricted(v, valuesByVar)).toSeq
        (restricted, remaining)
    }
    UnitKey(
      unit = u,
      tier = t,
      pathRank = if (t == 7 && isPropertyPath(u)) 1 else 0,
      predicateRank = if (hasProjectDataPredicate(u, valuesByVar)) 0 else 1,
      sparql = u.toSparql,
      varsOf = vars(u),
      isType = isTypeUnit(u),
      isUnselectiveTechnical = isUnselectiveTechnicalType(u),
      restrictedTermsCount = restrictedCount,
      loopVars = loopVars,
    )
  }

  /**
   * Tie-break precedence, in order: tier, then T7 non-path-before-path, then bound-terms count, then
   * project-data predicate before any other predicate, then text. The final key must stay the rendered
   * SPARQL: it makes the result independent of input order and of `Set`/`Map` iteration order, which is
   * what the permutation-invariance spec case pins. Do not replace it with a positional index.
   *
   * For a `VALUES`-bearing unit, the rendered text includes the inference variable name that
   * `SparqlTransformer.createInferenceVariable` derives from a truncated hash of the statement; this final
   * key therefore inherits whatever injectivity that naming has, and two statements colliding on that hash
   * would tie here too (see that method's Scaladoc for the collision caveat).
   */
  private def rank(k: UnitKey, bound: Set[QueryVariable]): (Int, Int, Int, Int, String) =
    (k.tier, k.pathRank, -boundTermsCount(k, bound), k.predicateRank, k.sparql)

  private def bestOf(indices: Seq[Int], remaining: Vector[UnitKey], bound: Set[QueryVariable]): Int =
    indices.minBy(i => rank(remaining(i), bound))

  /**
   * Rule T1, then connectivity and unselective-technical filters, in order: picks the index (into
   * `remaining`) of the next unit to emit.
   */
  private def pickNext(remaining: Vector[UnitKey], bound: Set[QueryVariable]): Int = {
    def connected(k: UnitKey) = k.varsOf.intersect(bound).nonEmpty
    val indices               = remaining.indices
    val ruleT1                = indices.filter(i => remaining(i).tier == 1)
    val (typeIdx, nonTypeIdx) = indices.partition(i => remaining(i).isType)
    val ruleA                 = nonTypeIdx.filter(i => connected(remaining(i)))
    val ruleB                 = typeIdx.filter(i => connected(remaining(i)))
    val ruleC                 = indices.filterNot(i => remaining(i).isUnselectiveTechnical)

    if (ruleT1.nonEmpty) bestOf(ruleT1, remaining, bound)
    else if (ruleA.nonEmpty) bestOf(ruleA, remaining, bound)
    else if (ruleB.nonEmpty) bestOf(ruleB, remaining, bound)
    else if (ruleC.nonEmpty) bestOf(ruleC, remaining, bound)
    else bestOf(indices, remaining, bound)
  }

  /** Emits `units` (step 3) in greedy tier/connectivity order, without attaching any `VALUES`. */
  private def emitUnits(
    units: Seq[QueryPattern],
    valuesByVar: Map[QueryVariable, Seq[ValuesPattern]],
    bound0: Set[QueryVariable],
  ): (Seq[QueryPattern], Set[QueryVariable]) = {

    @tailrec
    def loop(
      remaining: Vector[UnitKey],
      bound: Set[QueryVariable],
      acc: Vector[QueryPattern],
    ): (Vector[QueryPattern], Set[QueryVariable]) =
      if (remaining.isEmpty) (acc, bound)
      else {
        val idx    = pickNext(remaining, bound)
        val chosen = remaining(idx)
        loop(remaining.patch(idx, Nil, 1), bound ++ chosen.varsOf, acc :+ chosen.unit)
      }

    loop(units.map(unitKey(_, valuesByVar)).toVector, bound0, Vector.empty)
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

  private def recurseBlock(
    block: OptionalPattern | UnionPattern | MinusPattern,
    bound: Set[QueryVariable],
  ): QueryPattern = block match {
    case OptionalPattern(ps) => OptionalPattern(orderGroup(ps, bound))
    case UnionPattern(bs)    => UnionPattern(bs.map(orderGroup(_, bound)))
    case MinusPattern(ps)    => MinusPattern(orderGroup(ps, Set.empty))
  }

  private def orderGroup(patterns: Seq[QueryPattern], outerBound: Set[QueryVariable]): Seq[QueryPattern] = {
    val b           = partition(patterns)
    val valuesByVar = b.values.groupBy(_.variable)

    val unitVars    = b.units.flatMap(vars).toSet
    val nonUnitVars = (b.blocks ++ b.filters ++ b.notExists).flatMap(vars).toSet

    val (unitAttached, rest)     = b.values.partition(v => unitVars.contains(v.variable))
    val (blockAttached, orphans) = rest.partition(v => nonUnitVars.contains(v.variable))

    /**
     * `b.binds` is hoisted unconditionally to the front (see the final line of this method) and seeds
     * `bound0` with only each bind's target variable (`vars` on a `BindPattern` never looks at the bind
     * expression). That is safe only because the Gravsearch parser never accepts a bind expression that
     * reads a variable -- today a bind's expression is always a constant, so nothing it reads needs to be
     * bound first. If that ever changed, both the seed and the hoist would need to account for the
     * variables the expression reads, not just the target.
     */
    val bound0                          = outerBound ++ b.binds.flatMap(vars).toSet
    val (orderedUnits, boundAfterUnits) = emitUnits(b.units, valuesByVar, bound0)
    val unitsWithValues                 = attachValues(orderedUnits, unitAttached)

    val recursedBlocks    = b.blocks.map(recurseBlock(_, boundAfterUnits))
    val recursedNotExists = b.notExists.map(fne => FilterNotExistsPattern(orderGroup(fne.patterns, boundAfterUnits)))
    val tail              = attachValues(recursedBlocks ++ b.filters ++ recursedNotExists, blockAttached)

    b.binds ++ unitsWithValues ++ orphans ++ tail
  }
}
