/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.junit.runner.RunWith
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class AssetAccessSpec extends ZIOSpecDefault {

  private val noPermission = None
  private val restricted   = Some(Permission.ObjectAccess.RestrictedView)
  private val view         = Some(Permission.ObjectAccess.View)

  private val storedSize      = Some(RestrictedView.Size.unsafeFrom("!512,512"))
  private val storedWatermark = Some(RestrictedView.Watermark.On)

  private def decide(
    perm: Option[Permission.ObjectAccess],
    media: MediaKind,
    stored: Option[RestrictedView] = None,
  ) = AssetAccess.from(perm, media, stored)

  /** Every kind but a raster still image, and but an archive. These are the ones that stream under RV. */
  private val streamable =
    List(MediaKind.Vector, MediaKind.MovingImage, MediaKind.Audio, MediaKind.Document, MediaKind.Text, MediaKind.ThreeD)

  override def spec: Spec[Any, Any] = suite("AssetAccess.from")(
    test("withhold everything without a view permission, for every media kind") {
      check(Gen.fromIterable(MediaKind.values.toList)) { media =>
        val access = decide(noPermission, media)
        assertTrue(
          access.original == OriginalAccess.Withhold,
          access.derivative == DerivativeAccess.Denied,
        )
      }
    },
    test("grant the original and the full derivative from View upwards, for every media kind") {
      check(
        Gen.fromIterable(MediaKind.values.toList),
        Gen.fromIterable(Permission.ObjectAccess.all - Permission.ObjectAccess.RestrictedView),
      ) { (media, perm) =>
        val access = decide(Some(perm), media)
        assertTrue(
          access.original == OriginalAccess.Grant,
          access.derivative == DerivativeAccess.Full,
        )
      }
    },
    test("withhold the original under RV, for every media kind") {
      check(Gen.fromIterable(MediaKind.values.toList)) { media =>
        assertTrue(decide(restricted, media).original == OriginalAccess.Withhold)
      }
    },
    test("clamp a raster still image under RV to the project's stored setting") {
      assertTrue(
        decide(restricted, MediaKind.RasterStillImage, storedSize).derivative ==
          DerivativeAccess.Clamped(RestrictedView.Size.unsafeFrom("!512,512")),
        decide(restricted, MediaKind.RasterStillImage, storedWatermark).derivative ==
          DerivativeAccess.Clamped(RestrictedView.Watermark.On),
      )
    },
    test("clamp a raster still image under RV to the platform default when the project stores nothing") {
      assertTrue(
        decide(restricted, MediaKind.RasterStillImage).derivative ==
          DerivativeAccess.Clamped(RestrictedView.default),
      )
    },
    test("deny an archive under RV, because it has no in-place consumption and is never transcoded") {
      assertTrue(decide(restricted, MediaKind.Archive).derivative == DerivativeAccess.Denied)
    },
    test("stream every other media kind under RV, whatever the project stores") {
      check(Gen.fromIterable(streamable), Gen.fromIterable(List(None, storedSize, storedWatermark))) { (media, stored) =>
        assertTrue(decide(restricted, media, stored).derivative == DerivativeAccess.Stream)
      }
    },
    test("ignore the project's stored setting for every decision but a clamp") {
      check(Gen.fromIterable(MediaKind.values.toList), Gen.fromIterable(List(noPermission, view))) { (media, perm) =>
        assertTrue(decide(perm, media, storedSize) == decide(perm, media, None))
      }
    },
    test("fail closed when the file value class has no media kind") {
      assertTrue(
        AssetAccess.failClosed.original == OriginalAccess.Withhold,
        AssetAccess.failClosed.derivative == DerivativeAccess.Denied,
      )
    },
  )
}

/**
 * The compiler makes `MediaKind => AssetAccess` total, but it cannot see the ontology. This spec reads
 * `knora-base.ttl` and fails when a file value class is added there without a [[MediaKind]].
 */
@RunWith(classOf[DspZTestJUnitRunner])
class MediaKindOntologyCoverageSpec extends ZIOSpecDefault {

  // Every class that specialises kb:FileValue and is not itself specialised further: the classes an
  // instance can actually be typed with. kb:FileValue and kb:StillImageAbstractFileValue are superclasses
  // and drop out.
  private val concreteFileValueClasses =
    """
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |
      |SELECT ?cls WHERE {
      |  ?cls rdfs:subClassOf+ knora-base:FileValue .
      |  FILTER NOT EXISTS { ?sub rdfs:subClassOf ?cls . }
      |}
      |""".stripMargin

  private def declaredFileValueClasses: Set[String] = {
    val model  = ModelFactory.createDefaultModel()
    val stream = getClass.getClassLoader.getResourceAsStream("knora-ontologies/knora-base.ttl")
    try RDFDataMgr.read(model, stream, Lang.TURTLE)
    finally stream.close()
    val exec = QueryExecutionFactory.create(QueryFactory.create(concreteFileValueClasses), model)
    try exec.execSelect().asScala.map(_.getResource("cls").getURI).toSet
    finally exec.close()
  }

  override def spec: Spec[Any, Any] = suite("MediaKind")(
    test("maps every concrete file value class declared in knora-base.ttl") {
      val declared = declaredFileValueClasses
      val unmapped = declared.filter(MediaKind.fromFileValueClass(_).isEmpty)
      assertTrue(declared.nonEmpty, unmapped == Set.empty[String])
    },
    test("maps no class that knora-base.ttl does not declare") {
      assertTrue(MediaKind.fileValueClasses -- declaredFileValueClasses == Set.empty[String])
    },
  )
}
