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

	// TODO MOF2RDFResource.getProperty()
	public Object getProperty(String property, IEolContext context) {

		Collection<Object> value = super.getCollectionOfProperyValues(property, context);

		// Perform Cardinality checks
		final RDFQualifiedName pName = RDFQualifiedName.from(property, this.owningModel::getNamespaceURI);

		// Disable this check to remove the maxCardinality limit on returned properties
		// i.e. maxCardinality == null.
		MaxCardinalityRestriction maxCardinality = RDFPropertyProcesses
				.getPropertyStatementMaxCardinalityRestriction(pName, resource);

		// Check collection of rawValues is less than the MaxCardinality and prune
		if (null != maxCardinality) {
			if (value.size() > maxCardinality.getMaxCardinality()) {
				System.err.println("Property [" + pName + "] has a max cardinality "
						+ maxCardinality.getMaxCardinality() + ", raw property values list contained " + value.size()
						+ ".\n The list of raw property values has been pruned, it contained: " + value);

				value = value.stream().limit(maxCardinality.getMaxCardinality()).collect(Collectors.toList());
			}
			if (maxCardinality.getMaxCardinality() == 1) {
				// If the maximum cardinality is 1, return the single value (do not return a
				// collection)
				return value.isEmpty() ? null : value.iterator().next();
			}
		}
		return value;

	}

}
