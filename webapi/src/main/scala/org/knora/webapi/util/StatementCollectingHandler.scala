package org.knora.webapi.util

import org.eclipse.rdf4j.model.{Resource, Statement}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.{RDFHandler, RDFWriter}
import org.knora.webapi.{IRI, OntologyConstants}

import scala.collection.immutable.TreeMap

/**
  * An abstract [[RDFHandler]] that collects all statements so they can be processed when the end of the
  * input file is reached. Subclasses need to implement only `endRDF`, which must call `turtleWriter.endRDF()`
  * when finished.
  *
  * @param turtleWriter an [[RDFWriter]] that writes to the output file.
  */
abstract class StatementCollectingHandler(turtleWriter: RDFWriter) extends RDFHandler {
    /**
      * An instance of [[SimpleValueFactory]] for creating RDF statements.
      */
    protected val valueFactory: SimpleValueFactory = SimpleValueFactory.getInstance()

    implicit object ResourceOrdering extends Ordering[Resource] {
        def compare(o1: Resource, o2: Resource): Int = o1.stringValue.compare(o2.stringValue)
    }

    /**
      * A collection of all the statements in the input file, grouped and sorted by subject IRI.
      */
    protected var statements: TreeMap[Resource, Vector[Statement]] = TreeMap.empty[Resource, Vector[Statement]]

    /**
      * A convenience method that returns the first object of the specified predicate in a list of statements.
      *
      * @param subjectStatements the statements to search.
      * @param predicateIri      the predicate to search for.
      * @return the first object found for the specified predicate.
      */
    protected def getObject(subjectStatements: Vector[Statement], predicateIri: IRI): Option[String] = {
        subjectStatements.find(_.getPredicate.stringValue == predicateIri).map(_.getObject.stringValue)
    }

    /**
      * Adds a statement to the collection `statements`.
      *
      * @param st the statement to be added.
      */
    override def handleStatement(st: Statement): Unit = {
        val subject = st.getSubject
        val currentStatementsForSubject = statements.getOrElse(subject, Vector.empty[Statement])

        if (st.getPredicate.stringValue == OntologyConstants.Rdf.Type) {
            // Make rdf:type the first statement for the subject.
            statements += (subject -> (st +: currentStatementsForSubject))
        } else {
            statements += (subject -> (currentStatementsForSubject :+ st))
        }
    }

    /**
      * Does nothing (comments are ignored).
      *
      * @param comment a Turtle comment.
      */
    override def handleComment(comment: String): Unit = {}

    /**
      * Writes the specified namespace declaration to the output file.
      *
      * @param prefix the namespace prefix.
      * @param uri    the namespace URI.
      */
    override def handleNamespace(prefix: String, uri: String): Unit = {
        turtleWriter.handleNamespace(prefix, uri)
    }

    /**
      * Calls `turtleWriter.startRDF()`.
      */
    override def startRDF(): Unit = {
        turtleWriter.startRDF()
    }
}
