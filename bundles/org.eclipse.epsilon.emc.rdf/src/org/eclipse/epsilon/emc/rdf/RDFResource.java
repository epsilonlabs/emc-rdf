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

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.epsilon.eol.execute.context.IEolContext;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class RDFResource extends RDFModelElement {
	protected static final String LITERAL_SUFFIX = "_literal";
	
	enum LiteralMode {
		RAW, VALUES_ONLY
	}

	private Resource resource;

	public RDFResource(Resource resource, RDFModel rdfModel) {
		super(rdfModel);
		this.resource = resource;
	}

	public Resource getResource() {
		return resource;
	}

	public Collection<Object> getProperty(String property, IEolContext context) {
		final RDFQualifiedName pName = RDFQualifiedName.from(property, this.owningModel::getNamespaceURI);

		Collection<Object> value = getProperty(pName, context, LiteralMode.RAW);
		if (!value.isEmpty() && !value.stream().anyMatch(p -> p instanceof RDFResource)) {
			value = filterByPreferredLanguage(value, LiteralMode.VALUES_ONLY);
			if (!value.isEmpty()) {
				return value;
			}
		}

		if (value.isEmpty() && pName.localName.endsWith(LITERAL_SUFFIX)) {
			final String localNameWithoutSuffix = pName.localName.substring(0,
					pName.localName.length() - LITERAL_SUFFIX.length());
			RDFQualifiedName withoutLiteral = pName.withLocalName(localNameWithoutSuffix);

			value = getProperty(withoutLiteral, context, LiteralMode.RAW);
			if (!value.isEmpty() && !value.stream().anyMatch(p -> p instanceof RDFResource)) {
				value = filterByPreferredLanguage(value, LiteralMode.RAW);
			}
		}

		return value;
	}

	private Collection<Object> filterByPreferredLanguage(Collection<Object> value, LiteralMode literalMode) {
		// If no preferred languages are specified, don't do any filtering
		if (super.getModel().getLanguagePreference().isEmpty()) {
			switch (literalMode) {
			case RAW:
				return value;
			case VALUES_ONLY:
				return value.stream().map(e -> e instanceof RDFLiteral
					? ((RDFLiteral) e).getValue() : e).collect(Collectors.toList());
			default:
				throw new IllegalArgumentException("Unknown literal mode " + literalMode);
			}
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
				switch (literalMode) {
				case RAW:
					return new ArrayList<>(literalsByTag.get(tag));
				case VALUES_ONLY:
					return literalsByTag.get(tag).stream().map(l -> 
					l.getValue()).collect(Collectors.toList());
				}
			}
		}

		// If we don't find any matches in the preferred languages,
		// fall back to the untagged literals (if any).
		Collection<RDFLiteral> rawFromUntagged = literalsByTag.get("");
		switch (literalMode) {
		case RAW:
			return new ArrayList<>(rawFromUntagged);
		case VALUES_ONLY:
			return rawFromUntagged.stream().map(l -> l.getValue())
				.collect(Collectors.toList());
		default:
			throw new IllegalArgumentException("Unknown literal mode " + literalMode);
		}
	}
	
	public Collection<Object> getProperty(RDFQualifiedName pName, IEolContext context, LiteralMode literalMode) {
		// Filter statements by prefix and local name
		ExtendedIterator<Statement> itStatements = null;
		if (pName.prefix == null) {
			itStatements = resource.listProperties()
				.filterKeep(stmt -> pName.localName.equals(stmt.getPredicate().getLocalName()));
		} else {
			String prefixIri = resource.getModel().getNsPrefixMap().get(pName.prefix);
			Property prop = new PropertyImpl(prefixIri, pName.localName);
			itStatements = resource.listProperties(prop);
		}

		// If a language tag is used, only keep literals with that tag
		if (pName.languageTag != null) {
			itStatements = itStatements.filterKeep(stmt -> {
				if (stmt.getObject() instanceof Literal) {
					Literal l = (Literal) stmt.getObject();
					return pName.languageTag.equals(l.getLanguage());
				}
				return false;
			});
		}

		if (pName.prefix == null) {
			// If no prefix was specified, watch out for ambiguity and issue warning in that case
			ListMultimap<String, Object> values = MultimapBuilder.hashKeys().arrayListValues().build();
			while (itStatements.hasNext()) {
				Statement stmt = itStatements.next();
				values.put(stmt.getPredicate().getURI(),
						convertToModelObject(stmt.getObject(), literalMode));
			}

			final Set<String> distinctKeys = values.keySet();
			if (distinctKeys.size() > 1) {
				context.getWarningStream().println(String.format(
					"Ambiguous access to property '%s': multiple prefixes found (%s)",
					pName,
					String.join(", ", distinctKeys)
				));
			}

			return values.values();
		} else {
			// Prefix was specified: we don't have to worry about ambiguity
			final List<Object> values = new ArrayList<>();
			while (itStatements.hasNext()) {
				Statement stmt = itStatements.next();
				values.add(convertToModelObject(stmt.getObject(), literalMode));
			}
			return values;
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

	protected Object convertToModelObject(RDFNode node, LiteralMode lMode) {
		if (node instanceof Literal) {
			switch (lMode) {
			case RAW:
				return new RDFLiteral((Literal) node, this.owningModel);
			case VALUES_ONLY:
				return ((Literal) node).getValue();
			}
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
