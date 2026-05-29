package org.knora.webapi.slice.ontology

import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sparql.core.Quad
import zio.IO
import zio.UIO
import zio.ZIO
import zio.ZLayer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

import org.knora.webapi.config.AppConfig

final case class TransformerError(message: String)

final class OntologyTransformer { self =>

  private val extPrefix: UIO[String] = AppConfig.config(_.knoraApi.externalOntologyIriHostAndPort)
  private val intPrefix: String      = "http://www.knora.org"

  def toKnoraBase(rdf: Path): IO[TransformerError, Path] =
    for {
      prefix <- extPrefix
      out    <- ZIO.attemptBlocking {
               val nq = Files.createTempFile("onto-transformer-", ".nq")
               val in = new BufferedInputStream(new FileInputStream(rdf.toFile))
               try {
                 val os = new BufferedOutputStream(new FileOutputStream(nq.toFile))
                 try {
                   val writer = StreamRDFLib.writer(os)
                   val sink   = rewritingSink(writer, prefix, intPrefix)
                   sink.start()
                   try RDFParser.source(in).lang(Lang.JSONLD).parse(sink)
                   finally sink.finish()
                 } finally os.close()
               } finally in.close()
               nq
             }
               .mapError(e => TransformerError(s"Failed to transform RDF: ${e.getMessage}"))
    } yield out

  private def rewritingSink(downstream: StreamRDF, fromPrefix: String, toPrefix: String): StreamRDF = {
    def rewriteUri(uri: String): String =
      if (uri.startsWith(fromPrefix)) toPrefix + uri.substring(fromPrefix.length) else uri

    def rewriteNode(n: Node): Node =
      if (n != null && n.isURI) NodeFactory.createURI(rewriteUri(n.getURI)) else n

    new StreamRDFBase {
      override def start(): Unit                             = downstream.start()
      override def finish(): Unit                            = downstream.finish()
      override def base(base: String): Unit                  = downstream.base(base)
      override def prefix(prefix: String, iri: String): Unit = downstream.prefix(prefix, rewriteUri(iri))
      override def triple(t: Triple): Unit                   =
        downstream.triple(
          Triple.create(rewriteNode(t.getSubject), rewriteNode(t.getPredicate), rewriteNode(t.getObject)),
        )
      override def quad(q: Quad): Unit =
        downstream.quad(
          Quad.create(
            rewriteNode(q.getGraph),
            rewriteNode(q.getSubject),
            rewriteNode(q.getPredicate),
            rewriteNode(q.getObject),
          ),
        )
    }
  }
}

object OntologyTransformer {
  val layer: ZLayer[Any, Nothing, OntologyTransformer] = ZLayer.derive[OntologyTransformer]
}
