/********************************************************************************
 * Copyright (c) 2024 University of York
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Antonio Garcia-Dominguez - initial API and implementation
 ********************************************************************************/
package org.eclipse.epsilon.emc.rdf;

import java.util.Objects;

import org.apache.jena.ontology.MaxCardinalityRestriction;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.eclipse.epsilon.eol.execute.context.IEolContext;
public class RDFPropertyProcesses {

	private RDFPropertyProcesses() {
		// This class is not meant to be instantiated
	}

	public static ExtendedIterator<Statement> getPropertyStatementIterator(RDFQualifiedName propertyName, Resource resource) {
		ExtendedIterator<Statement> propertyStatementIt = null;
		// Filter all Property (Predicate) statements by prefix and local name
		if (propertyName.prefix == null) {
			propertyStatementIt = resource.listProperties()
				.filterKeep(stmt -> propertyName.localName.equals(stmt.getPredicate().getLocalName()));
		} else {
			Property prop = new PropertyImpl(propertyName.namespaceURI, propertyName.localName);
			propertyStatementIt = resource.listProperties(prop);
		}
		return propertyStatementIt;
	}

	public static ExtendedIterator<Statement> filterPropertyStatementsIteratorWithLanguageTag(RDFQualifiedName propertyName, ExtendedIterator<Statement> propertyStatements) {
		// If a language tag is used, only keep literals with that tag
		if (propertyName.languageTag != null) {
			propertyStatements = propertyStatements.filterKeep(stmt -> {
				if (stmt.getObject() instanceof Literal) {
					Literal l = (Literal) stmt.getObject();
					return propertyName.languageTag.equals(l.getLanguage());
				}
				return false;
			});
		}
		return propertyStatements;
	}

	public static MaxCardinalityRestriction getPropertyStatementMaxCardinalityRestriction(RDFQualifiedName propertyName, Resource resource, IEolContext context) {
		// Gets all the propertyStatements and finds all the MaxCardinality restrictions, keeps the most restrictive (lowest maxCardinality)
		MaxCardinalityRestriction mostRestrictiveMaxCardinality = null;
		OntProperty mostRestrictiveMaxCardinalityProperty = null;
		
		OntResource ontResource = resource.as(OntResource.class);
		
		// TODO re-evaluate if it is OK to use listRDFTypes(true) if we've triggered reasoning
		for (ExtendedIterator<Resource> itRDFType = ontResource.listRDFTypes(false); itRDFType.hasNext();) {
			Resource rdfType = itRDFType.next();
			OntClass ontClass = rdfType.as(OntClass.class);

			for (ExtendedIterator<OntProperty> itProp = ontClass.listDeclaredProperties(); itProp.hasNext();) {

				OntProperty prop = itProp.next();
				if ((propertyName.localName.equals(prop.getLocalName()))
						&& ((Objects.equals(propertyName.namespaceURI, prop.getNameSpace()))
								|| (null == propertyName.prefix))) {

					if (null != mostRestrictiveMaxCardinalityProperty) {
						if (mostRestrictiveMaxCardinalityProperty.equals(prop)) {
							// same property, don't need to look at it again
						} else {
							// You've run into ambiguity around the name of the property - raise a warning
							context.getWarningStream().println(String.format(
									"Ambiguous access to property with no prefix '%s':"
									+"\n Most restrictive Max Cardinality found was %s %s,"
									+" but also found this a similar property %s",
									propertyName, mostRestrictiveMaxCardinalityProperty,
									mostRestrictiveMaxCardinality.getMaxCardinality(), prop));
						}
					} else {

						for (ExtendedIterator<Restriction> itRestriction = prop
								.listReferringRestrictions(); itRestriction.hasNext();) {
							
							Restriction restriction = itRestriction.next();
							if (restriction.isMaxCardinalityRestriction()) {
								
								MaxCardinalityRestriction maxCardinalityRestriction = restriction.asMaxCardinalityRestriction();
								if (mostRestrictiveMaxCardinality == null) {
									mostRestrictiveMaxCardinality = maxCardinalityRestriction;
									mostRestrictiveMaxCardinalityProperty = prop;
								} else {
									if (mostRestrictiveMaxCardinality.getMaxCardinality() > maxCardinalityRestriction.getMaxCardinality()) {
										mostRestrictiveMaxCardinality = maxCardinalityRestriction;
										mostRestrictiveMaxCardinalityProperty = prop;
									}
								}
							}
						}
						
					}
					
				}

			}
		}
		return mostRestrictiveMaxCardinality;
	}

}
