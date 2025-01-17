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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.ontology.MaxCardinalityRestriction;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.epsilon.eol.execute.context.IEolContext;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class RDFResource extends RDFModelElement {
	protected static final String LITERAL_SUFFIX = "_literal";

	public enum LiteralMode {
		RAW, VALUES_ONLY
	}

	private Resource resource;
	
	public RDFResource(Resource aResource, RDFModel rdfModel) {
		super(rdfModel);
		this.resource = aResource;
	}
	
	public Resource getResource() {
		return resource;
	}

	public Object getProperty(String property, IEolContext context) {
		final RDFQualifiedName pName = RDFQualifiedName.from(property, this.owningModel::getNamespaceURI);
		Collection<Object> value = listPropertyValues(pName, context, LiteralMode.VALUES_ONLY);

		if (value.isEmpty() && pName.localName.endsWith(LITERAL_SUFFIX)) {
			final String localNameWithoutSuffix = pName.localName.substring(0,
					pName.localName.length() - LITERAL_SUFFIX.length());
			RDFQualifiedName withoutLiteral = pName.withLocalName(localNameWithoutSuffix);
			value = listPropertyValues(withoutLiteral, context, LiteralMode.RAW);
		}

		// Disable this check to remove the maxCardinality limit on returned properties i.e. maxCardinality == null.
		MaxCardinalityRestriction maxCardinality = RDFPropertyProcesses.getPropertyStatementMaxCardinalityRestriction(pName, resource);

		// Check collection of rawValues is less than the MaxCardinality and prune as needed...
		if (null != maxCardinality) {
			if (value.size() > maxCardinality.getMaxCardinality()) {
				System.err.println("Property [" + pName + "] has a max cardinality " + maxCardinality.getMaxCardinality()
									+ ", raw property values list contained " + value.size()
									+ ".\n The list of raw property values has been pruned, it contained: " + value);

				value = value.stream().limit(maxCardinality.getMaxCardinality())
						.collect(Collectors.toList());
			}
			if (maxCardinality.getMaxCardinality() == 1) {
				// If the maximum cardinality is 1, return the single value (do not return a collection)
				return value.isEmpty() ? null : value.iterator().next();
			}
		}

		//return value.isEmpty() ? null : value; // Null returns cause issues with PropertyGetter which expects empty collections for no property matches
		return value;
	}

	protected Collection<Object> convertLiteralsToValues(Collection<Object> value) {
		return value.stream()
			.map(e -> e instanceof RDFLiteral ? ((RDFLiteral)e).getValue() : e)
			.collect(Collectors.toList());
	}

	protected Collection<Object> filterByPreferredLanguage(Collection<Object> value) {
		// If no preferred languages are specified, don't do any filtering
		if (super.getModel().getLanguagePreference().isEmpty()) {
			return value;
		}

		// Otherwise, group literals by language tag
		Multimap<String, RDFLiteral> literalsByTag = HashMultimap.create();
		for (Object element : value) {
			if (element instanceof RDFLiteral) {
				RDFLiteral literal = (RDFLiteral) element;
				literalsByTag.put(literal.getLanguage() == null ? "" : literal.getLanguage(), literal);
			} else {
				// TODO #19 see if we run into this scenario (perhaps with integers instead of strings?), print some warning, return value as is as fallback
				throw new IllegalArgumentException("Expected RDFLiteral while filtering based on preferred languages, but got " + element);
			}
		}

		for (String tag : super.getModel().getLanguagePreference()) {
			if (literalsByTag.containsKey(tag)) {
				return new ArrayList<>(literalsByTag.get(tag));
			}
		}

		// If we don't find any matches in the preferred languages,
		// fall back to the untagged literals (if any).
		Collection<RDFLiteral> rawFromUntagged = literalsByTag.get("");
		return new ArrayList<>(rawFromUntagged);
	}

	public Collection<Object> listPropertyValues(RDFQualifiedName propertyName, IEolContext context, LiteralMode literalMode) {
		ExtendedIterator<Statement> itStatements; 
		itStatements = RDFPropertyProcesses.getPropertyStatementIterator(propertyName, resource);	
		itStatements = RDFPropertyProcesses.filterPropertyStatementsIteratorWithLanguageTag(propertyName, itStatements);

		// Build a collection Objects for the rawValues of the Objects for the Properties remaining 
		Collection<Object> rawPropertyValues;
		if (propertyName.prefix == null) {
			// If no prefix was specified, watch out for ambiguity and issue warning in that case
			ListMultimap<String, Object> values = MultimapBuilder.hashKeys().arrayListValues().build();
			while (itStatements.hasNext()) {
				Statement stmt = itStatements.next();
				values.put(stmt.getPredicate().getURI(),
						convertToModelObject(stmt.getObject()));
			}

			final Set<String> distinctKeys = values.keySet();
			if (distinctKeys.size() > 1) {
				context.getWarningStream().println(String.format(
					"Ambiguous access to property '%s': multiple prefixes found (%s)",
					propertyName,
					String.join(", ", distinctKeys)
				));
			}

			rawPropertyValues = values.values();
		} else {
			// Prefix was specified: we don't have to worry about ambiguity
			final List<Object> values = new ArrayList<>();
			while (itStatements.hasNext()) {
				Statement stmt = itStatements.next();
				values.add(convertToModelObject(stmt.getObject()));
			}
			rawPropertyValues = values;
		}

		// Filter by preferred languages if any are set
		if (propertyName.languageTag == null && !rawPropertyValues.stream().anyMatch(p -> p instanceof RDFResource)) {
			rawPropertyValues = filterByPreferredLanguage(rawPropertyValues);
		}

		// Convert literals to values depending on mode
		switch (literalMode) {
		case VALUES_ONLY:
			return convertLiteralsToValues(rawPropertyValues);
		case RAW:
			return rawPropertyValues;
		default:
			throw new IllegalArgumentException("Unknown literal mode " + literalMode);
		}
	}

	public List<RDFResource> getTypes() {
		List<RDFResource> types = new ArrayList<>();
		for (StmtIterator itStmt = resource.listProperties(RDF.type); itStmt.hasNext(); ) {
			RDFNode node = itStmt.next().getObject();
			types.add(new RDFResource((Resource) node, this.owningModel));
		}
		return types;
	}

	public String getUri() {
		return resource.getURI();
	}

	protected Object convertToModelObject(RDFNode node) {
		if (node instanceof Literal) {
			return new RDFLiteral((Literal) node, this.owningModel);
		} else if (node instanceof Resource) {
			return new RDFResource((Resource) node, this.owningModel);
		}
		throw new IllegalArgumentException("Cannot convert " + node + " to a model object");
	}

	@Override
	public String toString() {
		return "RDFResource [resource=" + resource + "]";
	}

}
