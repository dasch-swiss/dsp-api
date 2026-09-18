/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*

/**
 * Pins the DEV-7287 `PrequeryPatternOrdering` behaviour by hand-built AST.
 *
 * Covers: real-world query shapes (list-node anchor, link-target anchor, the "tanner" shape with and
 * without a project limit) with a bound-IRI or project-class type unit leading; the restated rule that a
 * `VALUES`-bound classless type unit may lead even when it enumerates `knora-base:Resource`; the ban on
 * `knora-base:LinkValue` and `knora-base:Resource` type units ever leading a component, while
 * `knora-base:ListNode` remains eligible; a size-preservation check applied to every input in `allInputs`,
 * which must list every fixture the suite defines; bind-first emission; Lucene-group opacity and its
 * ability to lead even when unconnected; `VALUES` attachment (unit-attached, block-attached, orphan);
 * filter/FNE placement; `MINUS`/`OPTIONAL` recursion seeds; disconnected components staying contiguous;
 * cycle termination; permutation invariance; the T2/T3/T7 tier exclusions and tie-breaks; and the two
 * measured DEV-7287 stage regressions -- a standoff `rdf:type` unit must not lead ahead of the `VALUES`
 * anchoring a `*` property path, and a project-scoped predicate must win the rendered-text tie-break over
 * a store-wide built-in one.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class PrequeryPatternOrderingSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val beol     = "http://www.knora.org/ontology/0801/beol#"
  private val anything = "http://www.knora.org/ontology/0001/anything#"

  private val letterIri         = IriRef((beol + "letter").toSmartIri)
  private val hasSubjectIri     = IriRef((beol + "hasSubject").toSmartIri)
  private val hasAuthorIri      = IriRef((beol + "hasAuthor").toSmartIri)
  private val hasAuthorValueIri = IriRef((beol + "hasAuthorValue").toSmartIri)
  private val titleIri          = IriRef((beol + "title").toSmartIri)
  private val hasFamilyNameIri  = IriRef((beol + "hasFamilyName").toSmartIri)
  private val writtenSourceIri  = IriRef((beol + "writtenSource").toSmartIri)
  private val manuscriptIri     = IriRef((beol + "manuscript").toSmartIri)
  private val basicLetterIri    = IriRef((beol + "basicLetter").toSmartIri)

  private val rdfsLabelIri        = IriRef(OntologyConstants.Rdfs.Label.toSmartIri)
  private val rdfTypeIri          = IriRef(OntologyConstants.Rdf.Type.toSmartIri)
  private val rdfObjectIri        = IriRef(OntologyConstants.Rdf.Object.toSmartIri)
  private val hasSubListNodeIri   = IriRef(OntologyConstants.KnoraBase.HasSubListNode.toSmartIri, Some('*'))
  private val valueHasListNodeIri = IriRef(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri)
  private val valueHasStringIri   = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri)
  private val attachedToProjIri   = IriRef(OntologyConstants.KnoraBase.AttachedToProject.toSmartIri)
  private val linkValueTypeIri    = IriRef(OntologyConstants.KnoraBase.LinkValue.toSmartIri)
  private val resourceTypeIri     = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)
  private val deletedResourceIri  = IriRef(OntologyConstants.KnoraBase.DeletedResource.toSmartIri)
  private val listNodeClassIri    = IriRef(OntologyConstants.KnoraBase.ListNode.toSmartIri)

  private val standoffParagraphTagIri   = IriRef(OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri)
  private val standoffTagHasStartParent =
    IriRef(OntologyConstants.KnoraBase.StandoffTagHasStartParent.toSmartIri, Some('*'))
  private val valueHasStandoffIri  = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri)
  private val hasTextIri           = IriRef((anything + "hasText").toSmartIri)
  private val standoffEventTagIri  = IriRef((anything + "StandoffEventTag").toSmartIri)
  private val standoffDateTagKbIri = IriRef(OntologyConstants.KnoraBase.StandoffDateTag.toSmartIri)
  private val hasDateIri           = IriRef((anything + "hasDate").toSmartIri)
  private val valueHasStartJdnIri  = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri)

  private val listNodeIri = IriRef("http://rdfh.ch/lists/0801/logarithmic_curves".toSmartIri)
  private val anchorIri   = IriRef("http://rdfh.ch/0801/anchor-person".toSmartIri)
  private val projectIri  = IriRef("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF".toSmartIri)

  private val luceneQueryIri      = IriRef(OntologyConstants.Fuseki.luceneQueryPredicate.toSmartIri)
  private val hasOtherThingIri    = IriRef((beol + "hasOtherThing").toSmartIri)
  private val hasOtherThingValIri = IriRef((beol + "hasOtherThingValue").toSmartIri)
  private val genericPropIri      = IriRef((beol + "genericProp").toSmartIri)
  private val subClassOfStarIri   = IriRef(OntologyConstants.Rdfs.SubClassOf.toSmartIri, Some('*'))
  private val hasXIri             = IriRef((anything + "hasX").toSmartIri)
  private val hasYIri             = IriRef((anything + "hasY").toSmartIri)
  private val hasXValueIri        = IriRef((anything + "hasXValue").toSmartIri)
  private val hasYValueIri        = IriRef((anything + "hasYValue").toSmartIri)

  private val letter      = QueryVariable("letter")
  private val subj        = QueryVariable("subj")
  private val lnv         = QueryVariable("lnv")
  private val letterLinkV = QueryVariable("letter__linkValue")
  private val src         = QueryVariable("src")
  private val resTypes    = QueryVariable("resTypes")
  private val title       = QueryVariable("title")
  private val titleStr    = QueryVariable("titleStr")
  private val label       = QueryVariable("label")
  private val res         = QueryVariable("res")
  private val a           = QueryVariable("a")
  private val aLinkV      = QueryVariable("a__linkValue")
  private val p           = QueryVariable("p")
  private val n           = QueryVariable("n")
  private val date        = QueryVariable("date")
  private val jdn         = QueryVariable("jdn")

  private def basel: FilterPattern = FilterPattern(
    CompareExpression(
      titleStr,
      CompareExpressionOperator.EQUALS,
      XsdLiteral("Basel", OntologyConstants.Xsd.String.toSmartIri),
    ),
  )

  // List node anchor. `?letter a beol:letter ; beol:hasSubject ?subj . ?subj knora-api:listValueAsListNode <node>`, expanded.
  private val listNodeExpected: Seq[QueryPattern] = Seq(
    StatementPattern(listNodeIri, hasSubListNodeIri, lnv),
    StatementPattern(subj, valueHasListNodeIri, lnv),
    StatementPattern(letter, hasSubjectIri, subj),
    StatementPattern(letter, rdfTypeIri, letterIri),
  )
  private val listNodeInput: Seq[QueryPattern] = Seq(
    listNodeExpected(3),
    listNodeExpected(2),
    listNodeExpected(1),
    listNodeExpected(0),
  )

  // Link target anchor. `?letter a beol:letter ; beol:hasAuthor <anchor>`, expanded.
  private val linkTargetExpected: Seq[QueryPattern] = Seq(
    StatementPattern(letter, hasAuthorIri, anchorIri),
    StatementPattern(letter, hasAuthorValueIri, letterLinkV),
    StatementPattern(letterLinkV, rdfObjectIri, anchorIri),
    StatementPattern(letter, rdfTypeIri, letterIri),
    StatementPattern(letterLinkV, rdfTypeIri, linkValueTypeIri),
  )
  private val linkTargetInput: Seq[QueryPattern] = Seq(
    linkTargetExpected(3),
    linkTargetExpected(4),
    linkTargetExpected(1),
    linkTargetExpected(2),
    linkTargetExpected(0),
  )

  // Tanner shape. `?src a beol:writtenSource ; rdfs:label ?label ; beol:title ?title . ?title valueHasString ?titleStr`.
  private val tannerValues = ValuesPattern(
    resTypes,
    Set(writtenSourceIri, manuscriptIri, basicLetterIri, letterIri),
  )
  private val tannerTypeStmt   = StatementPattern(src, rdfTypeIri, resTypes)
  private val tannerTitleStmt  = StatementPattern(src, titleIri, title)
  private val tannerLabelStmt  = StatementPattern(src, rdfsLabelIri, label)
  private val tannerStringStmt = StatementPattern(title, valueHasStringIri, titleStr)
  private val tannerFilter     = basel

  private val tannerWithoutProjectInput: Seq[QueryPattern] = Seq(
    tannerValues,
    tannerLabelStmt,
    tannerTitleStmt,
    tannerTypeStmt,
    tannerStringStmt,
    tannerFilter,
  )
  private val tannerWithoutProjectExpected: Seq[QueryPattern] = Seq(
    tannerValues,
    tannerTypeStmt,
    tannerTitleStmt,
    tannerLabelStmt,
    tannerStringStmt,
    tannerFilter,
  )

  private val tannerAttachedToProject                   = StatementPattern(src, attachedToProjIri, projectIri)
  private val tannerWithProjectInput: Seq[QueryPattern] = Seq(
    tannerValues,
    tannerLabelStmt,
    tannerAttachedToProject,
    tannerTitleStmt,
    tannerTypeStmt,
    tannerStringStmt,
    tannerFilter,
  )
  private val tannerWithProjectExpected: Seq[QueryPattern] = Seq(
    tannerValues,
    tannerTypeStmt,
    tannerAttachedToProject,
    tannerTitleStmt,
    tannerLabelStmt,
    tannerStringStmt,
    tannerFilter,
  )

  // A classless `VALUES` still containing `knora-base:Resource` must lead over `attachedToProject`.
  private val classlessValues                   = ValuesPattern(resTypes, Set(resourceTypeIri, deletedResourceIri))
  private val classlessTypeStmt                 = StatementPattern(res, rdfTypeIri, resTypes)
  private val classlessProjStmt                 = StatementPattern(res, attachedToProjIri, projectIri)
  private val classlessLabelStmt                = StatementPattern(res, rdfsLabelIri, label)
  private val classlessInput: Seq[QueryPattern] = Seq(
    classlessValues,
    classlessLabelStmt,
    classlessProjStmt,
    classlessTypeStmt,
  )
  private val classlessExpected: Seq[QueryPattern] = Seq(
    classlessValues,
    classlessTypeStmt,
    classlessProjStmt,
    classlessLabelStmt,
  )

  // Linked chain: `knora-base:LinkValue` may never lead a component, even in a fully anchorless chain.
  private val linkValueBannedInput: Seq[QueryPattern] = Seq(
    StatementPattern(aLinkV, rdfTypeIri, linkValueTypeIri),
    StatementPattern(p, hasFamilyNameIri, n),
    StatementPattern(a, hasAuthorValueIri, aLinkV),
    StatementPattern(aLinkV, rdfObjectIri, p),
  )

  // Bind first: a BindPattern is emitted before every unit, whatever its input position.
  private val bindVar                           = QueryVariable("bindVar")
  private val bindPattern                       = BindPattern(bindVar, IntegerLiteral(1))
  private val bindFirstInput: Seq[QueryPattern] = Seq(
    StatementPattern(letter, hasAuthorIri, anchorIri),
    bindPattern,
    StatementPattern(letter, rdfTypeIri, letterIri),
  )

  // Lucene group opaque: a GroupPattern containing text:query leads a T2 statement, and its own
  // `patterns` sequence is never reordered inside.
  private val luceneGroupInner: Seq[QueryPattern] = Seq(
    StatementPattern(letter, luceneQueryIri, XsdLiteral("test", OntologyConstants.Xsd.String.toSmartIri)),
    StatementPattern(letter, rdfsLabelIri, label),
  )
  private val luceneGroup                         = GroupPattern(luceneGroupInner)
  private val luceneLeadT2Stmt                    = StatementPattern(letter, hasAuthorIri, anchorIri)
  private val luceneGroupInput: Seq[QueryPattern] = Seq(luceneLeadT2Stmt, luceneGroup)

  // T1 leads regardless of connectivity: a text:query statement, bare or wrapped in a
  // GroupPattern, leads a block even when nothing has bound its variable yet, while a plain statement
  // whose variable the outer scope already bound is connected. Parity with `moveLuceneToBeginning`.
  private val luceneOuterBoundVar                  = QueryVariable("obVar")
  private val luceneUnboundVar                     = QueryVariable("luceneVar")
  private val luceneOuterBound: Set[QueryVariable] = Set(luceneOuterBoundVar)
  private val connectedPlainStmt                   = StatementPattern(luceneOuterBoundVar, genericPropIri, n)
  private val unconnectedLuceneStmt                =
    StatementPattern(luceneUnboundVar, luceneQueryIri, XsdLiteral("test", OntologyConstants.Xsd.String.toSmartIri))
  private val luceneLeadsUnconnectedInput: Seq[QueryPattern]      = Seq(connectedPlainStmt, unconnectedLuceneStmt)
  private val unconnectedLuceneGroup                              = GroupPattern(Seq(unconnectedLuceneStmt))
  private val luceneGroupLeadsUnconnectedInput: Seq[QueryPattern] = Seq(connectedPlainStmt, unconnectedLuceneGroup)

  // Attached VALUES adjacency: the VALUES on ?subj is emitted immediately before the statement
  // using ?subj, wherever that statement lands.
  private val attachAnchorStmt                       = StatementPattern(letter, hasAuthorIri, anchorIri)
  private val attachSubjStmt                         = StatementPattern(letter, hasSubjectIri, subj)
  private val attachValuesPattern                    = ValuesPattern(subj, Set(anchorIri))
  private val attachedValuesInput: Seq[QueryPattern] = Seq(attachValuesPattern, attachSubjStmt, attachAnchorStmt)

  // Orphan VALUES last among units, before blocks/filters.
  private val orphanValues                             = ValuesPattern(QueryVariable("orphanVar"), Set(anchorIri))
  private val orphanWithFilterInput: Seq[QueryPattern] =
    Seq(orphanValues, attachAnchorStmt, attachSubjStmt, basel)

  // Block-attached VALUES: a VALUES referenced only inside an OPTIONAL is emitted immediately
  // before that OPTIONAL, not hoisted to the front.
  private val optVar                                = QueryVariable("optVar")
  private val blockValues                           = ValuesPattern(optVar, Set(anchorIri))
  private val optionalBlock                         = OptionalPattern(Seq(StatementPattern(letter, hasSubjectIri, optVar)))
  private val blockAttachedInput: Seq[QueryPattern] = Seq(blockValues, attachAnchorStmt, optionalBlock)

  // Filters/FNE ordering: statements, then blocks, then filters, then FNE last; each keeps its
  // own relative input order.
  private val filter1 = FilterPattern(
    CompareExpression(
      titleStr,
      CompareExpressionOperator.EQUALS,
      XsdLiteral("A", OntologyConstants.Xsd.String.toSmartIri),
    ),
  )
  private val filter2 = FilterPattern(
    CompareExpression(
      titleStr,
      CompareExpressionOperator.EQUALS,
      XsdLiteral("B", OntologyConstants.Xsd.String.toSmartIri),
    ),
  )
  private val fne1                              = FilterNotExistsPattern(Seq(StatementPattern(letter, hasSubjectIri, subj)))
  private val fne2                              = FilterNotExistsPattern(Seq(StatementPattern(letter, titleIri, title)))
  private val orderingOptBlk                    = OptionalPattern(Seq(StatementPattern(letter, rdfsLabelIri, label)))
  private val filterFneInput: Seq[QueryPattern] =
    Seq(fne2, filter2, orderingOptBlk, fne1, filter1, attachAnchorStmt)
  private val filterFneExpected: Seq[QueryPattern] =
    Seq(attachAnchorStmt, orderingOptBlk, filter2, filter1, fne2, fne1)

  // MINUS parity: a statement binding a variable also used in a MINUS body is still hoisted
  // before the MINUS -- deliberate consistency with the StatementsFirst partition, not a SPARQL identity
  // (MINUS does not actually export bindings outward).
  private val minusBody                     = MinusPattern(Seq(StatementPattern(letter, hasSubjectIri, subj)))
  private val minusInput: Seq[QueryPattern] = Seq(minusBody, attachAnchorStmt)

  // Recursion seeds: OPTIONAL/UNION/FNE recurse with the outer bound set as seed, MINUS recurses
  // with an empty seed. Same block content, different seed, different inner order.
  private val outerVar                          = QueryVariable("outerV")
  private val seedBx                            = QueryVariable("bx")
  private val seedAy                            = QueryVariable("ay")
  private val seedAz                            = QueryVariable("az")
  private val outerAnchor                       = StatementPattern(outerVar, hasAuthorIri, anchorIri)
  private val seedStmtA                         = StatementPattern(outerVar, genericPropIri, seedBx) // connected to outerVar
  private val seedStmtB                         = StatementPattern(seedAy, genericPropIri, seedAz)   // unconnected
  private val optSeedInput: Seq[QueryPattern]   = Seq(outerAnchor, OptionalPattern(Seq(seedStmtA, seedStmtB)))
  private val minusSeedInput: Seq[QueryPattern] = Seq(outerAnchor, MinusPattern(Seq(seedStmtA, seedStmtB)))

  // Two disconnected components stay contiguous, T2-anchored leading.
  private val c1                                      = QueryVariable("c1")
  private val c1b                                     = QueryVariable("c1b")
  private val c2                                      = QueryVariable("c2")
  private val c2b                                     = QueryVariable("c2b")
  private val s1a                                     = StatementPattern(c1, hasAuthorIri, anchorIri)   // T2 anchor of component 1
  private val s1b                                     = StatementPattern(c1, hasFamilyNameIri, c1b)     // T7, connected to c1
  private val typeS2                                  = StatementPattern(c2, rdfTypeIri, manuscriptIri) // T4 project-class anchor of component 2
  private val s2b                                     = StatementPattern(c2, titleIri, c2b)             // T7, connected to c2
  private val disconnectedInput: Seq[QueryPattern]    = Seq(s2b, s1b, typeS2, s1a)
  private val disconnectedExpected: Seq[QueryPattern] = Seq(s1a, s1b, typeS2, s2b)

  // Cycle terminates: ?thing hasOtherThing ?thing1 . ?thing1 hasOtherThing ?thing2 .
  // ?thing2 hasOtherThing ?thing, each link property expanded.
  private val thing                                                                                               = QueryVariable("thing")
  private val thing1                                                                                              = QueryVariable("thing1")
  private val thing2                                                                                              = QueryVariable("thing2")
  private val lvA                                                                                                 = QueryVariable("lvA")
  private val lvB                                                                                                 = QueryVariable("lvB")
  private val lvC                                                                                                 = QueryVariable("lvC")
  private def expandLink(subject: QueryVariable, lv: QueryVariable, target: QueryVariable): Seq[StatementPattern] =
    Seq(
      StatementPattern(subject, hasOtherThingIri, target),
      StatementPattern(subject, hasOtherThingValIri, lv),
      StatementPattern(lv, rdfObjectIri, target),
      StatementPattern(lv, rdfTypeIri, linkValueTypeIri),
    )
  private val cycleInput: Seq[QueryPattern] =
    expandLink(thing, lvA, thing1) ++ expandLink(thing1, lvB, thing2) ++ expandLink(thing2, lvC, thing)

  // Permutation invariance: the order of statements/GroupPatterns/VALUES is a pure function of
  // the input set. Binds, blocks and filters are excluded from the permuted set by design.
  private val permutationBase: Seq[QueryPattern] =
    linkTargetExpected :+ ValuesPattern(letterLinkV, Set(linkValueTypeIri))

  // An rdf:type statement with an IriRef subject ranks T2, leading over a plain T7 statement.
  private val typeIriSubjStmt                          = StatementPattern(anchorIri, rdfTypeIri, letterIri)
  private val plainT7Stmt                              = StatementPattern(subj, hasFamilyNameIri, n)
  private val typeIriSubjLeadsInput: Seq[QueryPattern] = Seq(plainT7Stmt, typeIriSubjStmt)

  // `rdfs:subClassOf*` ranks T7 (path) despite the bound object, and follows the statement that
  // binds its subject; within T7 it sorts after a non-path T7 statement.
  private val xVar                                = QueryVariable("x")
  private val subClassPathStmt                    = StatementPattern(xVar, subClassOfStarIri, anchorIri)
  private val bindXStmt                           = StatementPattern(xVar, hasFamilyNameIri, n)
  private val subClassInput: Seq[QueryPattern]    = Seq(subClassPathStmt, bindXStmt)
  private val subClassExpected: Seq[QueryPattern] = Seq(bindXStmt, subClassPathStmt)

  // A variable predicate is not promoted to T2 despite a bound object (Fact 1's bound-object
  // corollary); a T2 statement leads over it.
  private val varPredVar                      = QueryVariable("vp")
  private val varPredStmt                     = StatementPattern(subj, varPredVar, anchorIri)
  private val t2LeadsOverVarPredStmt          = StatementPattern(letter, hasAuthorIri, anchorIri)
  private val varPredInput: Seq[QueryPattern] = Seq(varPredStmt, t2LeadsOverVarPredStmt)

  // A non-type statement with a bound `XsdLiteral` object ranks T3: it leads a plain statement with no
  // bound literal, but still ranks behind a bound-IRI (T2) statement.
  private val boundLiteralStmt =
    StatementPattern(subj, hasFamilyNameIri, XsdLiteral("Muster", OntologyConstants.Xsd.String.toSmartIri))
  private val boundLiteralPlainCompanionStmt       = StatementPattern(p, hasFamilyNameIri, n)
  private val boundLiteralInput: Seq[QueryPattern] =
    Seq(boundLiteralPlainCompanionStmt, boundLiteralStmt, t2LeadsOverVarPredStmt)
  private val boundLiteralExpected: Seq[QueryPattern] =
    Seq(t2LeadsOverVarPredStmt, boundLiteralStmt, boundLiteralPlainCompanionStmt)

  // `knora-base:Resource`, the second unselective-technical class, may never lead a component
  // either.
  private val resourceTypeStmt                       = StatementPattern(res, rdfTypeIri, resourceTypeIri)
  private val resourceCompanionStmt                  = StatementPattern(res, hasFamilyNameIri, n)
  private val resourceBannedInput: Seq[QueryPattern] = Seq(resourceTypeStmt, resourceCompanionStmt)

  // `knora-base:ListNode` is a knora-base class deliberately absent from the unselective-technical
  // list, so its type unit stays eligible to lead. Widening the rule to a knora-base namespace test would
  // demote this unit and swap the emitted order.
  private val listNodeTypeStmt                             = StatementPattern(n, rdfTypeIri, listNodeClassIri)
  private val listNodeLabelStmt                            = StatementPattern(n, rdfsLabelIri, label)
  private val listNodeTypeLeadsInput: Seq[QueryPattern]    = Seq(listNodeLabelStmt, listNodeTypeStmt)
  private val listNodeTypeLeadsExpected: Seq[QueryPattern] = Seq(listNodeTypeStmt, listNodeLabelStmt)

  // DEV-7287 standoff regression, measured 166x: the standoff paragraph-tag type unit must not
  // lead ahead of the VALUES + type unit that anchors the `standoffTagHasStartParent*` property path.
  // `?standoffParagraphTag` names a built-in standoff class (T7, plain), while `?standoffDateTag`'s type is
  // bound by a VALUES enumerating one project class and one knora-base class (T5), so the VALUES and its
  // type statement now lead, and the property path is evaluated once its `standoffDateTag` end is bound.
  private val standoffTypeParagraphStmt        = StatementPattern(subj, rdfTypeIri, standoffParagraphTagIri)
  private val standoffPathStmt                 = StatementPattern(lnv, standoffTagHasStartParent, subj)
  private val standoffValueHasStmt             = StatementPattern(title, valueHasStandoffIri, lnv)
  private val standoffHasTextStmt              = StatementPattern(thing, hasTextIri, title)
  private val standoffTypesValues              = ValuesPattern(resTypes, Set(standoffEventTagIri, standoffDateTagKbIri))
  private val standoffTypesStmt                = StatementPattern(lnv, rdfTypeIri, resTypes)
  private val standoffInput: Seq[QueryPattern] = Seq(
    standoffTypeParagraphStmt,
    standoffPathStmt,
    standoffValueHasStmt,
    standoffHasTextStmt,
    standoffTypesValues,
    standoffTypesStmt,
  )
  private val standoffExpected: Seq[QueryPattern] = Seq(
    standoffTypesValues,
    standoffTypesStmt,
    standoffValueHasStmt,
    standoffHasTextStmt,
    standoffPathStmt,
    standoffTypeParagraphStmt,
  )

  // DEV-7287 sort-by-date regression, measured 6.3x: without the predicate tie-break, the lexical
  // key would put `?date knora-base:valueHasStartJDN ?jdn` first because "date" < "thing"; the project
  // predicate `anything:hasDate` must instead win the tie and lead.
  private val dateProjectStmt                 = StatementPattern(thing, hasDateIri, date)
  private val dateJdnStmt                     = StatementPattern(date, valueHasStartJdnIri, jdn)
  private val dateInput: Seq[QueryPattern]    = Seq(dateJdnStmt, dateProjectStmt)
  private val dateExpected: Seq[QueryPattern] = Seq(dateProjectStmt, dateJdnStmt)

  // DEV-7287 stage regression, essence of the `reorderWithCycle` shape: `?a ?p ?b` and `?a ?pv ?lv` each
  // have a variable predicate restricted by an attached VALUES enumerating only project-data-ontology
  // predicate IRIs, so each must count as fully bound on the bound-terms tie-break, exactly like the plain
  // `?lv rdf:object ?b` statement; only the project-predicate tie-break then keeps `rdf:object` from
  // leading. `?lv rdf:type knora-base:LinkValue` stays unselective-technical and never leads. The ordering
  // is fully deterministic (the final tie-break is the rendered SPARQL text), so the expected order below
  // pins the exact winner rather than accepting either VALUES-restricted statement.
  private val essenceA                        = QueryVariable("essenceA")
  private val essenceB                        = QueryVariable("essenceB")
  private val essenceP                        = QueryVariable("essenceP")
  private val essencePv                       = QueryVariable("essencePv")
  private val essenceLv                       = QueryVariable("essenceLv")
  private val essencePValues                  = ValuesPattern(essenceP, Set(hasXIri, hasYIri))
  private val essencePvValues                 = ValuesPattern(essencePv, Set(hasXValueIri, hasYValueIri))
  private val essenceAPStmt                   = StatementPattern(essenceA, essenceP, essenceB)
  private val essenceAPvStmt                  = StatementPattern(essenceA, essencePv, essenceLv)
  private val essenceObjectStmt               = StatementPattern(essenceLv, rdfObjectIri, essenceB)
  private val essenceLvTypeStmt               = StatementPattern(essenceLv, rdfTypeIri, linkValueTypeIri)
  private val essenceInput: Seq[QueryPattern] = Seq(
    essenceObjectStmt,
    essenceLvTypeStmt,
    essenceAPvStmt,
    essencePvValues,
    essenceAPStmt,
    essencePValues,
  )
  private val essenceExpected: Seq[QueryPattern] = Seq(
    essencePValues,
    essenceAPStmt,
    essencePvValues,
    essenceAPvStmt,
    essenceObjectStmt,
    essenceLvTypeStmt,
  )

  // A mixed VALUES on a predicate variable (one project-data-ontology IRI, one knora-base IRI) must not
  // get the project-predicate tie-break; only a VALUES whose every entry is project-data does. Both
  // statements are otherwise identical in shape and tie on tier, path-rank and bound-terms count, so
  // predicateRank is the only differentiator.
  private val mixedPredVar                              = QueryVariable("mixedPredVar")
  private val purePredVar                               = QueryVariable("purePredVar")
  private val mixedPredSubj                             = QueryVariable("mixedPredSubj")
  private val mixedPredObj                              = QueryVariable("mixedPredObj")
  private val purePredSubj                              = QueryVariable("purePredSubj")
  private val purePredObj                               = QueryVariable("purePredObj")
  private val mixedPredValues                           = ValuesPattern(mixedPredVar, Set(hasXIri, valueHasStringIri))
  private val purePredValues                            = ValuesPattern(purePredVar, Set(hasXIri, hasYIri))
  private val mixedPredicateStmt                        = StatementPattern(mixedPredSubj, mixedPredVar, mixedPredObj)
  private val purePredicateStmt                         = StatementPattern(purePredSubj, purePredVar, purePredObj)
  private val mixedPredicateTieInput: Seq[QueryPattern] = Seq(
    mixedPredicateStmt,
    mixedPredValues,
    purePredicateStmt,
    purePredValues,
  )

  private val allInputs: Seq[Seq[QueryPattern]] = Seq(
    listNodeInput,
    linkTargetInput,
    tannerWithoutProjectInput,
    tannerWithProjectInput,
    classlessInput,
    linkValueBannedInput,
    bindFirstInput,
    luceneGroupInput,
    attachedValuesInput,
    orphanWithFilterInput,
    blockAttachedInput,
    filterFneInput,
    minusInput,
    optSeedInput,
    minusSeedInput,
    disconnectedInput,
    cycleInput,
    typeIriSubjLeadsInput,
    subClassInput,
    varPredInput,
    luceneLeadsUnconnectedInput,
    luceneGroupLeadsUnconnectedInput,
    permutationBase,
    resourceBannedInput,
    listNodeTypeLeadsInput,
    standoffInput,
    dateInput,
    boundLiteralInput,
    essenceInput,
    mixedPredicateTieInput,
  )

  override val spec = suite("PrequeryPatternOrdering")(
    test("order the list-node anchor shape with the bound-IRI path statement leading") {
      assertTrue(PrequeryPatternOrdering.order(listNodeInput) == listNodeExpected)
    },
    test("order the link-target anchor shape with the direct link statement leading") {
      assertTrue(PrequeryPatternOrdering.order(linkTargetInput) == linkTargetExpected)
    },
    test("order the tanner shape without a project limit with the project-class type unit leading") {
      assertTrue(PrequeryPatternOrdering.order(tannerWithoutProjectInput) == tannerWithoutProjectExpected)
    },
    test("order the tanner shape with a project limit, attachedToProject right after the type unit") {
      assertTrue(PrequeryPatternOrdering.order(tannerWithProjectInput) == tannerWithProjectExpected)
    },
    test(
      "let a classless VALUES-bound type unit lead over attachedToProject " +
        "(when no unit is connected to anything already bound, the leader is chosen among all units that are not unselective-technical)",
    ) {
      assertTrue(PrequeryPatternOrdering.order(classlessInput) == classlessExpected)
    },
    test("never let an unselective-technical knora-base:LinkValue type unit lead a component") {
      val actual = PrequeryPatternOrdering.order(linkValueBannedInput)
      assertTrue(actual.head != StatementPattern(aLinkV, rdfTypeIri, linkValueTypeIri))
    },
    test("preserve the number of patterns for every case above") {
      assertTrue(allInputs.forall(input => PrequeryPatternOrdering.order(input).size == input.size))
    },
    test("emit a BindPattern before every unit, whatever its input position") {
      assertTrue(PrequeryPatternOrdering.order(bindFirstInput).head == bindPattern)
    },
    test("let a Lucene GroupPattern lead and never reorder its own patterns") {
      val actual = PrequeryPatternOrdering.order(luceneGroupInput)
      assertTrue(
        actual.head == luceneGroup,
        actual.head.asInstanceOf[GroupPattern].patterns == luceneGroupInner,
      )
    },
    test("let a bare text:query statement lead even when unconnected, over a connected plain statement") {
      val actual = PrequeryPatternOrdering.order(luceneLeadsUnconnectedInput, luceneOuterBound)
      assertTrue(actual.head == unconnectedLuceneStmt)
    },
    test("let a Lucene GroupPattern lead even when unconnected, without reordering its own patterns") {
      val actual = PrequeryPatternOrdering.order(luceneGroupLeadsUnconnectedInput, luceneOuterBound)
      assertTrue(
        actual.head == unconnectedLuceneGroup,
        actual.head.asInstanceOf[GroupPattern].patterns == Seq(unconnectedLuceneStmt),
      )
    },
    test("attach a unit-referenced VALUES immediately before the statement using it") {
      val actual = PrequeryPatternOrdering.order(attachedValuesInput)
      val idx    = actual.indexOf(attachSubjStmt)
      assertTrue(idx > 0, actual(idx - 1) == attachValuesPattern)
    },
    test("emit an orphan VALUES after every unit but before blocks and filters") {
      val actual    = PrequeryPatternOrdering.order(orphanWithFilterInput)
      val orphanIdx = actual.indexOf(orphanValues)
      assertTrue(
        orphanIdx > actual.indexOf(attachAnchorStmt),
        orphanIdx > actual.indexOf(attachSubjStmt),
        orphanIdx < actual.indexOf(basel),
      )
    },
    test("attach a block-referenced VALUES immediately before its OPTIONAL, not hoisted to the front") {
      val actual = PrequeryPatternOrdering.order(blockAttachedInput)
      val idx    = actual.indexOf(optionalBlock)
      assertTrue(idx > 0, actual(idx - 1) == blockValues)
    },
    test("order statements, then blocks, then filters, then FNE last, each keeping its own input order") {
      assertTrue(PrequeryPatternOrdering.order(filterFneInput) == filterFneExpected)
    },
    test("hoist a statement binding a MINUS variable before the MINUS (parity with today, not SPARQL identity)") {
      val actual = PrequeryPatternOrdering.order(minusInput)
      assertTrue(actual.indexOf(attachAnchorStmt) < actual.indexOf(minusBody))
    },
    test("recurse an OPTIONAL with the outer bound set as seed, favoring the statement connected to it") {
      val actual = PrequeryPatternOrdering.order(optSeedInput)
      val opt    = actual.collectFirst { case o: OptionalPattern => o }.get
      assertTrue(opt.patterns == Seq(seedStmtA, seedStmtB))
    },
    test("recurse a MINUS body with an empty seed, ignoring outer bound connectivity") {
      val actual = PrequeryPatternOrdering.order(minusSeedInput)
      val minus  = actual.collectFirst { case m: MinusPattern => m }.get
      assertTrue(minus.patterns == Seq(seedStmtB, seedStmtA))
    },
    test("keep two disconnected components contiguous, with the T2-anchored component leading") {
      assertTrue(PrequeryPatternOrdering.order(disconnectedInput) == disconnectedExpected)
    },
    test("terminate and preserve size on a cyclic chain, without a LinkValue type statement leading") {
      val actual = PrequeryPatternOrdering.order(cycleInput)
      assertTrue(
        actual.size == cycleInput.size,
        actual.head != StatementPattern(lvA, rdfTypeIri, linkValueTypeIri),
        actual.head != StatementPattern(lvB, rdfTypeIri, linkValueTypeIri),
        actual.head != StatementPattern(lvC, rdfTypeIri, linkValueTypeIri),
      )
    },
    test("order 5 statements plus 1 VALUES the same way for every permutation of the input") {
      val reference = PrequeryPatternOrdering.order(permutationBase)
      assertTrue(permutationBase.permutations.forall(p => PrequeryPatternOrdering.order(p) == reference))
    },
    test("rank an rdf:type statement with an IriRef subject as T2, leading over a plain T7 statement") {
      assertTrue(PrequeryPatternOrdering.order(typeIriSubjLeadsInput).head == typeIriSubjStmt)
    },
    test("rank rdfs:subClassOf* as T7 (path) despite a bound object, after the statement binding its subject") {
      assertTrue(PrequeryPatternOrdering.order(subClassInput) == subClassExpected)
    },
    test("never promote a variable predicate to T2 despite a bound object; a T2 statement leads over it") {
      assertTrue(PrequeryPatternOrdering.order(varPredInput).head == t2LeadsOverVarPredStmt)
    },
    test("never let an unselective-technical knora-base:Resource type unit lead a component") {
      assertTrue(PrequeryPatternOrdering.order(resourceBannedInput) == Seq(resourceCompanionStmt, resourceTypeStmt))
    },
    test("still let a knora-base:ListNode type unit lead, since it is not unselective-technical") {
      assertTrue(PrequeryPatternOrdering.order(listNodeTypeLeadsInput) == listNodeTypeLeadsExpected)
    },
    test(
      "anchor the standoff `standoffTagHasStartParent*` path with the VALUES + type unit, not the plain paragraph-tag type (DEV-7287, measured 166x)",
    ) {
      val actual = PrequeryPatternOrdering.order(standoffInput)
      assertTrue(
        actual == standoffExpected,
        actual.head == standoffTypesValues,
        actual(1) == standoffTypesStmt,
        actual.head != standoffTypeParagraphStmt,
      )
    },
    test(
      "let the project predicate win the tie-break over a store-wide knora-base predicate (DEV-7287, measured 6.3x)",
    ) {
      assertTrue(PrequeryPatternOrdering.order(dateInput) == dateExpected)
    },
    test("rank a non-type statement with a bound XsdLiteral object as T3, between T2 and a plain T7 statement") {
      assertTrue(PrequeryPatternOrdering.order(boundLiteralInput) == boundLiteralExpected)
    },
    test(
      "let a VALUES-restricted predicate variable statement lead over a store-wide `rdf:object` statement " +
        "on the essence of the reorderWithCycle shape (DEV-7287 stage regression)",
    ) {
      val actual = PrequeryPatternOrdering.order(essenceInput)
      assertTrue(
        actual == essenceExpected,
        actual.head != essenceObjectStmt,
        actual.indexOf(essenceObjectStmt) > 0,
      )
    },
    test("never grant the project-predicate tie-break to a VALUES mixing a knora-base IRI with a project IRI") {
      val statementOrder =
        PrequeryPatternOrdering.order(mixedPredicateTieInput).collect { case s: StatementPattern => s }
      assertTrue(statementOrder.head == purePredicateStmt, statementOrder.head != mixedPredicateStmt)
    },
    test("order the essence-of-the-cycle shape identically for every permutation of the input") {
      val reference = PrequeryPatternOrdering.order(essenceInput)
      assertTrue(essenceInput.permutations.forall(p => PrequeryPatternOrdering.order(p) == reference))
    },
  )
}
