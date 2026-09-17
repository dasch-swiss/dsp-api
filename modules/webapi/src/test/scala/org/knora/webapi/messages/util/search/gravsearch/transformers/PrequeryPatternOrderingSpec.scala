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
 * Cases 1-4 pin the DEV-7287 shapes from the ordering plan/design docs by hand-built AST; case 5 pins the
 * restated rule 3c (a `VALUES`-bound classless type unit may lead even when it enumerates
 * `knora-base:Resource`); case 6 pins that `knora-base:LinkValue` may never lead. Case 7 is the
 * size-preservation check applied to every input above.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class PrequeryPatternOrderingSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val beol = "http://www.knora.org/ontology/0801/beol#"

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

  private val listNodeIri = IriRef("http://rdfh.ch/lists/0801/logarithmic_curves".toSmartIri)
  private val anchorIri   = IriRef("http://rdfh.ch/0801/anchor-person".toSmartIri)
  private val projectIri  = IriRef("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF".toSmartIri)

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

  private def basel: FilterPattern = FilterPattern(
    CompareExpression(
      titleStr,
      CompareExpressionOperator.EQUALS,
      XsdLiteral("Basel", OntologyConstants.Xsd.String.toSmartIri),
    ),
  )

  // Case 1: list node anchor. `?letter a beol:letter ; beol:hasSubject ?subj . ?subj knora-api:listValueAsListNode <node>`, expanded.
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

  // Case 2: link target anchor. `?letter a beol:letter ; beol:hasAuthor <anchor>`, expanded.
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

  // Case 3/4: tanner. `?src a beol:writtenSource ; rdfs:label ?label ; beol:title ?title . ?title valueHasString ?titleStr`.
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

  // Case 5: restated 3c. A classless `VALUES` still containing `knora-base:Resource` must lead over `attachedToProject`.
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

  // Case 6: `knora-base:LinkValue` may never lead a component, even in a fully anchorless chain.
  private val linkValueBannedInput: Seq[QueryPattern] = Seq(
    StatementPattern(aLinkV, rdfTypeIri, linkValueTypeIri),
    StatementPattern(p, hasFamilyNameIri, n),
    StatementPattern(a, hasAuthorValueIri, aLinkV),
    StatementPattern(aLinkV, rdfObjectIri, p),
  )

  private val allInputs: Seq[Seq[QueryPattern]] = Seq(
    listNodeInput,
    linkTargetInput,
    tannerWithoutProjectInput,
    tannerWithProjectInput,
    classlessInput,
    linkValueBannedInput,
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
    test("let a classless VALUES-bound type unit lead over attachedToProject (restated rule 3c)") {
      assertTrue(PrequeryPatternOrdering.order(classlessInput) == classlessExpected)
    },
    test("never let an unselective-technical knora-base:LinkValue type unit lead a component") {
      val actual = PrequeryPatternOrdering.order(linkValueBannedInput)
      assertTrue(actual.head != StatementPattern(aLinkV, rdfTypeIri, linkValueTypeIri))
    },
    test("preserve the number of patterns for every case above") {
      assertTrue(allInputs.forall(input => PrequeryPatternOrdering.order(input).size == input.size))
    },
  )
}
