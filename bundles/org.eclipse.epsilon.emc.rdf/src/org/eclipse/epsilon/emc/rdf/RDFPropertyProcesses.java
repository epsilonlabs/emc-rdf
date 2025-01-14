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

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.util.iterator.ExtendedIterator;

public class RDFPropertyProcesses {

	public static ExtendedIterator<Statement> getPropertyStatementIterator (RDFQualifiedName propertyName, Resource resource) {
		ExtendedIterator<Statement> propertyStatementIt = null;
		// Filter all Property (Predicate) statements by prefix and local name
		if (propertyName.prefix == null) {
			propertyStatementIt = resource.listProperties()
				.filterKeep(stmt -> propertyName.localName.equals(stmt.getPredicate().getLocalName()));
		} else {
			String prefixIri = resource.getModel().getNsPrefixMap().get(propertyName.prefix);
			Property prop = new PropertyImpl(prefixIri, propertyName.localName);
			propertyStatementIt = resource.listProperties(prop);
		}
		return propertyStatementIt;
	}
	
	public static ExtendedIterator<Statement> filterPropertyStatementsIteratorWithLanguageTag (RDFQualifiedName propertyName, ExtendedIterator<Statement> propertyStatements) {
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
	
}
