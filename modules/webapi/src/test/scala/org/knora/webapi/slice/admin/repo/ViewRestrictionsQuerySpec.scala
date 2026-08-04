/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.GroupBy

/**
 * Query-generator tests for [[ViewRestrictionsRepo]]. They assert on the rendered SPARQL string rather
 * than on triplestore results, pinning the query shape that the service's correctness relies on: the
 * restriction pre-filter, the file-value markers, deleted exclusion, the optional group filter, and the
 * scan-cap LIMIT (the large-project guardrail).
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsQuerySpec extends ZIOSpecDefault {

  private val projectIri  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass  = "http://www.knora.org/ontology/0001/anything#Thing"
  private val hasPicture  = "http://www.knora.org/ontology/0001/anything#hasPicture"
  private val scanCapLine = s"LIMIT ${ViewRestrictionsRepo.ScanCap}"

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("resourceQuery")(
      test("selects project resources, excludes deleted, keeps only restricted rows, and caps the scan") {
        val q = ViewRestrictionsRepo.resourceQuery(projectIri, group = None).getQueryString
        assertTrue(
          q.contains(s"knora-base:attachedToProject <${projectIri.value}>"),
          q.contains("knora-base:isDeleted false"),
          // the restriction pre-filter (drops rows that grant view to anonymous)
          q.contains("FILTER") && q.contains("knora-admin:UnknownUser"),
          q.contains(scanCapLine),
        )
      },
      test("with a group binds a class-equality filter on that IRI") {
        val q = ViewRestrictionsRepo.resourceQuery(projectIri, group = Some(thingClass)).getQueryString
        assertTrue(q.contains(thingClass))
      },
    ),
    suite("valueQuery")(
      test("binds the file-value marker, excludes link values and caps the scan") {
        val q = ViewRestrictionsRepo.valueQuery(projectIri, group = None, GroupBy.ResourceClass).getQueryString
        assertTrue(
          // the generic file marker distinguishes File from Value items
          q.contains("knora-base:FileValue"),
          // …and no still-image marker: visibility no longer depends on whether a file is an image, so the
          // subclass-closure OPTIONAL that bound ?imageClass must not come back as dead query work.
          !q.contains("knora-base:StillImageFileValue"),
          !q.contains("imageClass"),
          // link values are excluded and the scan is capped
          q.contains("FILTER NOT EXISTS") && q.contains("knora-base:LinkValue"),
          q.contains("knora-base:valueHasComment"),
          q.contains(scanCapLine),
        )
      },
      test("in property mode a group filters on the carrying property, not the resource class") {
        val q = ViewRestrictionsRepo.valueQuery(projectIri, group = Some(hasPicture), GroupBy.Property).getQueryString
        assertTrue(q.contains(hasPicture))
      },
    ),
  )
}
