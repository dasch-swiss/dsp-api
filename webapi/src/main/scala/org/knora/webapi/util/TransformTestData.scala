package org.knora.webapi.util

import java.io._

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{Resource, Statement}
import org.eclipse.rdf4j.rio.turtle._
import org.eclipse.rdf4j.rio.{RDFHandler, RDFWriter}
import org.knora.webapi.OntologyConstants
import org.knora.webapi.responders.v1.PermissionUtilV1

/**
  * Transforms test data.
  */
object TransformTestData extends App {
    private val PermissionsTransformation = "permissions"

    private val HasRestrictedViewPermission = "http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission"
    private val HasViewPermission = "http://www.knora.org/ontology/knora-base#hasViewPermission"
    private val HasModifyPermission = "http://www.knora.org/ontology/knora-base#hasModifyPermission"
    private val HasDeletePermission = "http://www.knora.org/ontology/knora-base#hasDeletePermission"
    private val HasChangeRightsPermission = "http://www.knora.org/ontology/knora-base#hasChangeRightsPermission"

    private val allOldPermissions = Set(
        HasRestrictedViewPermission,
        HasViewPermission,
        HasModifyPermission,
        HasDeletePermission,
        HasChangeRightsPermission
    )

    private val oldPermissionIri2Abbreviation = Map(
        HasRestrictedViewPermission -> OntologyConstants.KnoraBase.RestrictedViewPermission,
        HasViewPermission -> OntologyConstants.KnoraBase.ViewPermission,
        HasModifyPermission -> OntologyConstants.KnoraBase.ModifyPermission,
        HasDeletePermission -> OntologyConstants.KnoraBase.DeletePermission,
        HasChangeRightsPermission -> OntologyConstants.KnoraBase.ChangeRightsPermission
    )

    if (args.length != 3) {
        printUsage()
    } else {
        val transformation = args(0)

        if (transformation != PermissionsTransformation) {
            printUsage()
        } else {
            val inputFile = args(1)
            val outputFile = args(2)
            transform(transformation, inputFile, outputFile)
            println("Done.")
        }
    }

    private def printUsage(): Unit = {
        println("Usage: TransformTestData transformation input-file output-file")
        println("    transformation must be 'permissions'")
    }

    private def transform(transformation: String, inputFile: String, outputFile: String): Unit = {
        val fileInputStream = new FileInputStream(new File(inputFile))
        val fileOutputStream = new FileOutputStream(new File(outputFile))
        val turtleParser = new TurtleParser()
        val turtleWriter = new TurtleWriter(fileOutputStream)

        val handler = transformation match {
            case PermissionsTransformation => new PermissionsHandler(turtleWriter)
            case _ => throw new Exception(s"Unsupported transformation $transformation")
        }

        turtleParser.setRDFHandler(handler)
        turtleParser.parse(fileInputStream, inputFile)
        fileOutputStream.close()
        fileInputStream.close()
    }

    /**
      * Transforms old-style permission assertions into knora-base:hasPermissions assertions.
      */
    private class PermissionsHandler(turtleWriter: RDFWriter) extends RDFHandler {
        private val valueFactory = SimpleValueFactory.getInstance()

        private var currentSubject: Option[Resource] = None
        private val currentPermissions = new collection.mutable.HashMap[String, Set[String]]

        private def maybeWritePermissions(): Unit = {
            if (currentPermissions.nonEmpty) {
                val permissionsLiteral = new StringBuilder

                val currentPermissionsSorted = currentPermissions.toVector.sortBy {
                    case (abbreviation, groups) => PermissionUtilV1.permissionsToV1PermissionCodes(abbreviation)
                }

                for ((abbreviation, groups) <- currentPermissionsSorted) {
                    if (permissionsLiteral.nonEmpty) {
                        permissionsLiteral.append(OntologyConstants.KnoraBase.PermissionListDelimiter)
                    }

                    permissionsLiteral.append(abbreviation).append(" ")

                    for ((group, index) <- groups.zipWithIndex) {
                        if (index > 0) {
                            permissionsLiteral.append(OntologyConstants.KnoraBase.GroupListDelimiter)
                        }

                        val shortGroup = group.replace(OntologyConstants.KnoraBase.KnoraBasePrefixExpansion, OntologyConstants.KnoraBase.KnoraBasePrefix)
                        permissionsLiteral.append(shortGroup)
                    }

                }

                val permissionStatement = valueFactory.createStatement(currentSubject.get, valueFactory.createIRI(OntologyConstants.KnoraBase.HasPermissions), valueFactory.createLiteral(permissionsLiteral.toString()))
                turtleWriter.handleStatement(permissionStatement)
                currentPermissions.clear()
            }
        }

        override def handleComment(comment: String): Unit = {
            maybeWritePermissions()
            turtleWriter.handleComment(comment)
        }

        override def handleStatement(st: Statement): Unit = {
            val predicateStr = st.getPredicate.toString

            // Did we get a permission predicate?
            if (allOldPermissions.contains(predicateStr)) {
                // Yes. Has the subject changed?
                val subjectHasChanged = currentSubject match {
                    case Some(subject) => subject.stringValue() != st.getSubject.stringValue()
                    case None => true
                }

                // If so, write the permissions for any previous subject.
                if (subjectHasChanged) {
                    maybeWritePermissions()
                }

                // Save the permission predicate and object.
                currentSubject = Some(st.getSubject)
                val group = st.getObject.stringValue()
                val abbreviation = oldPermissionIri2Abbreviation(predicateStr)
                val currentGroupsForPermission = currentPermissions.getOrElse(abbreviation, Set.empty[String])
                currentPermissions += (abbreviation -> (currentGroupsForPermission + group))
            } else {
                // We got some other kind of predicate. Write any permissions for the previous subject.
                maybeWritePermissions()
                turtleWriter.handleStatement(st)
            }
        }

        override def endRDF(): Unit = {
            maybeWritePermissions()
            turtleWriter.endRDF()
        }

        override def handleNamespace(prefix: String, uri: String): Unit = {
            maybeWritePermissions()
            turtleWriter.handleNamespace(prefix, uri)
        }

        override def startRDF(): Unit = {
            turtleWriter.startRDF()
        }
    }

}
