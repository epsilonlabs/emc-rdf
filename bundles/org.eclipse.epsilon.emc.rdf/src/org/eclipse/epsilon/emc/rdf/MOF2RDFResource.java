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

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.jena.ontology.MaxCardinalityRestriction;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.epsilon.eol.execute.context.IEolContext;

public class MOF2RDFResource extends RDFResource {

	public MOF2RDFResource(Resource aResource, RDFModel rdfModel) {
		super(aResource, rdfModel);
	}

	public Object getProperty(String property, IEolContext context) {

		Collection<Object> value = super.getCollectionOfProperyValues(property, context);

		// Restriction checking on property 
		final RDFQualifiedName pName = RDFQualifiedName.from(property, this.owningModel::getNamespaceURI);
		
		// Perform Cardinality checks
		MaxCardinalityRestriction maxCardinality = RDFPropertyProcesses
				.getPropertyStatementMaxCardinalityRestriction(pName, resource, context);

		// Check collection of rawValues is less than the MaxCardinality and prune
		if (null != maxCardinality) {
			if (value.size() > maxCardinality.getMaxCardinality()) {
				//TODO move this warning to context.getWarningStream()
				context.getWarningStream().println("Property [" + pName + "] has a max cardinality "
						+ maxCardinality.getMaxCardinality() + ", raw property values list contained " + value.size()
						+ ".\n The list of raw property values has been pruned, it contained: " + value);

				value = value.stream().limit(maxCardinality.getMaxCardinality()).collect(Collectors.toList());
			}
			if (maxCardinality.getMaxCardinality() == 1) {
				// If the maximum cardinality is 1, return the single value (not a collection)
				return value.isEmpty() ? null : value.iterator().next();
			}
		}
	
		return value;

	}

}
