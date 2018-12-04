package org.knora.webapi.responders.v2.search

import org.knora.webapi._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Methods and classes for Sparql transformation.
  */
object SparqlTransformer {

    /**
      * Transforms the the Knora explicit graph name to GraphDB explicit graph name.
      *
      * @param statement the given statement whose graph name has to be renamed.
      * @return the statement with the renamed graph, if given.
      */
    def transformKnoraExplicitToGraphDBExplicit(statement: StatementPattern): Seq[StatementPattern] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val transformedPattern = statement.copy(
            pred = statement.pred match {
                case iri: IriRef if iri.iri == OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri => IriRef(OntologyConstants.Ontotext.LuceneFulltext.toSmartIri) // convert to special Lucene property
                case other => other // no conversion needed
            },
            namedGraph = statement.namedGraph match {
                case Some(IriRef(SmartIri(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph), _)) => Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph.toSmartIri))
                case Some(IriRef(_, _)) => throw AssertionException(s"Named graphs other than ${OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph} cannot occur in non-triplestore-specific generated search query SPARQL")
                case None => None
            }
        )

        Seq(transformedPattern)
    }

    /**
      * Transforms a non triplestore specific SELECT query to a query for GraphDB.
      */
    class GraphDBSelectToSelectTransformer extends SelectToSelectTransformer {
        def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

    }

    /**
      * Transforms non triplestore specific SELECT query to a query for a triplestore other than GraphDB (e.g., Fuseki, TODO: to be implemented)
      */
    class NoInferenceSelectToSelectTransformer extends SelectToSelectTransformer {
        def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
            Seq(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

    }

    /**
      * Transforms non-triplestore-specific query patterns to GraphDB-specific ones.
      */
    class GraphDBConstructToConstructTransformer extends ConstructToConstructTransformer {
        def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Transforms non-triplestore-specific query patterns for a triplestore that does not have inference enabled.
      */
    class NoInferenceConstructToConstructTransformer extends ConstructToConstructTransformer {
        def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
            Seq(statementPattern)
        }

        def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Creates a syntactically valid variable base name, based on the given entity.
      *
      * @param entity the entity to be used to create a base name for a variable.
      * @return a base name for a variable.
      */
    def escapeEntityForVariable(entity: Entity): String = {
        val entityStr = entity match {
            case QueryVariable(varName) => varName
            case IriRef(iriLiteral, _) => iriLiteral.toString
            case XsdLiteral(stringLiteral, _) => stringLiteral
            case _ => throw GravsearchException(s"A unique variable name could not be made for ${entity.toSparql}")
        }

        entityStr.replaceAll("[:/.#-]", "").replaceAll("\\s", "") // TODO: check if this is complete and if it could lead to collision of variable names
    }

    /**
      * Creates a unique variable name from the given entity and the local part of a property IRI.
      *
      * @param base        the entity to use to create the variable base name.
      * @param propertyIri the IRI of the property whose local part will be used to form the unique name.
      * @return a unique variable.
      */
    def createUniqueVariableNameFromEntityAndProperty(base: Entity, propertyIri: IRI): QueryVariable = {
        val propertyHashIndex = propertyIri.lastIndexOf('#')

        if (propertyHashIndex > 0) {
            val propertyName = propertyIri.substring(propertyHashIndex + 1)
            QueryVariable(escapeEntityForVariable(base) + "__" + escapeEntityForVariable(QueryVariable(propertyName)))
        } else {
            throw AssertionException(s"Invalid property IRI: $propertyIri")
        }
    }

}
