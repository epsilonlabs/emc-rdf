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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolEnumerationValueNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.exceptions.models.EolNotInstantiableModelElementTypeException;
import org.eclipse.epsilon.eol.models.CachedModel;
import org.eclipse.epsilon.eol.models.IRelativePathResolver;

public class RDFModel extends CachedModel<RDFModelElement> {

	public static final String PROPERTY_LANGUAGE_PREFERENCE = "languagePreference";

	public static final String PROPERTY_SCHEMA_URIS = "schemaUris";
	public static final String PROPERTY_DATA_URIS = "uris";  // TODO Update the uris to dataUris, breaks existing saved .launch files

	/**
	 * One of the keys used to construct the first argument to
	 * {@link #load(StringProperties, String)}.
	 *
	 * This key should be set to a comma-separated list of prefix=uri pairs that
	 * should be added to the prefix->URI map of the loaded RDF resource. These
	 * pairs will take precedence over existing pairs in the resource.
	 */
	public static final String PROPERTY_PREFIXES = "prefixes";

	protected final List<String> languagePreference = new ArrayList<>();
	protected final Map<String, String> customPrefixesMap = new HashMap<>();

	public enum RDFReasonerType {
		NONE,
		OWL_FULL;
	}  // TODO add to this list to cover reasoner types in the ReasonerRegistry Class
	protected RDFReasonerType rdfsReasonerType = RDFReasonerType.NONE;
	
	public RDFReasonerType getRdfsReasonerType() {
		return rdfsReasonerType;
	}

	public void setRdfsReasonerType(RDFReasonerType rdfsReasonerType) {
		this.rdfsReasonerType = rdfsReasonerType;
	}


	
	protected final List<String> schemaURIs = new ArrayList<>();
	protected final List<String> dataURIs = new ArrayList<>();
	protected OntModel model;
	
	// Write the OntModel the driver is using a file, this includes data model (inferred schemas) and additional Ont information
	// DO NOT USE THESE TO PERSIST A USER'S DATA MODEL!
	public void writeOntModel(OutputStream outputStream, String language) {
		model.write(outputStream, language);
	}
	
	// As writeOntModel() but with All option, which adds yet more model information into the file
	public void writeAllOntModel(OutputStream outputStream, String language) {
		model.writeAll(outputStream, language);
	}
	

	public RDFModel() {
		this.propertyGetter = new RDFPropertyGetter(this);
	}

	@Override
	public Object getEnumerationValue(String enumeration, String label) throws EolEnumerationValueNotFoundException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getTypeNameOf(Object instance) {
		if (instance instanceof RDFResource) {
			RDFResource res = (RDFResource) instance;
			List<RDFResource> types = res.getTypes();
			if (!types.isEmpty()) {
				RDFResource firstType = types.get(0);
				return getQualifiedTypeName(firstType);
			}
		}

		// TODO Generic names in case there isn't an RDF.type relationship?
		return null;
	}

	private String getQualifiedTypeName(RDFResource typeResource) {
		return String.format("%s:%s",
			getPrefix(typeResource.getResource().getNameSpace()),
			typeResource.getResource().getLocalName());
	}

	@Override
	public RDFResource getElementById(String iri) {
		Resource res = model.getResource(iri);
		if (res != null) {
			return new RDFResource(res, this);
		}
		return null;
	}

	@Override
	public String getElementId(Object instance) {
		if (instance instanceof RDFResource) {
			return ((RDFResource) instance).getUri();
		}
		return null;
	}

	@Override
	public void setElementId(Object instance, String newId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean owns(Object instance) {
		return instance instanceof RDFModelElement && ((RDFModelElement) instance).getModel() == this;
	}

	@Override
	public boolean isInstantiable(String type) {
		return false;
	}

	@Override
	public boolean hasType(String type) {
		try {
			getTypeResourceByName(type);
			return true;
		} catch (EolModelElementTypeNotFoundException e) {
			return false;
		}
	}

	@Override
	public void load(StringProperties properties, IRelativePathResolver resolver) throws EolModelLoadingException {
		super.load(properties, resolver);

		/*
		 * This method assumes that the StringProperties replaces all previous
		 * configuration of this RDFModel. This is the same as in other popular
		 * EMC drivers (e.g. the EmfModel class).
		 */
		this.dataURIs.clear();
		{
			String sUris = properties.getProperty(PROPERTY_DATA_URIS, "").strip();
			if (!sUris.isEmpty()) {
				for (String uri : sUris.split(",")) {
					this.dataURIs.add(uri.strip());
				}
			}
		}

		this.schemaURIs.clear();
		{
			String sUris = properties.getProperty(PROPERTY_SCHEMA_URIS, "").strip();
			if (!sUris.isEmpty()) {
				for (String uri : sUris.split(",")) {
					this.schemaURIs.add(uri.strip());
				}
			}
		}

		this.customPrefixesMap.clear();
		String sPrefixes = properties.getProperty(PROPERTY_PREFIXES, "").strip();
		if (!sPrefixes.isEmpty()) {
			for (String sItem : sPrefixes.split(",")) {
				int idxEquals = sItem.indexOf('=');
				if (idxEquals <= 0 || idxEquals == sItem.length() - 1) {
					throw new IllegalArgumentException(String.format("Entry '%s' does not follow the prefix=uri format", sItem));
				}

				String sPrefix = sItem.substring(0, idxEquals);
				String sURI = sItem.substring(idxEquals + 1);
				customPrefixesMap.put(sPrefix, sURI);
			}
		}

		this.languagePreference.clear();
		String sLanguagePreference = properties.getProperty(PROPERTY_LANGUAGE_PREFERENCE, "").strip();
		if (!sLanguagePreference.isEmpty()) {
			for (String tag : sLanguagePreference.split(",")) {
				tag = tag.strip();
				if (isValidLanguageTag(tag)) {
					this.languagePreference.add(tag);
				} else {
					throw new EolModelLoadingException(
						new IllegalArgumentException(
							String.format("'%s' is not a valid BCP 47 tag", tag)
						), this);
				}
			}
		}

		load();
	}

	@Override
	public boolean store(String location) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean store() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Collection<RDFModelElement> allContentsFromModel() {
		final List<RDFModelElement> elems = new ArrayList<>();

		for (ResIterator it = model.listSubjects(); it.hasNext(); ) {
			Resource stmt = it.next();
			elems.add(new RDFResource(stmt, this));	
		}
		
		return elems;
	}

	@Override
	protected Collection<RDFModelElement> getAllOfTypeFromModel(String type)
			throws EolModelElementTypeNotFoundException {
		Resource typeR = getTypeResourceByName(type);

		ResIterator itInstances = model.listResourcesWithProperty(RDF.type, typeR);
		List<RDFModelElement> instances = new ArrayList<>();
		while (itInstances.hasNext()) {
			instances.add(new RDFResource(itInstances.next(), this));
		}

		return instances;
	}

	protected Resource getTypeResourceByName(String type) throws EolModelElementTypeNotFoundException {
		RDFQualifiedName qName = RDFQualifiedName.from(type, this::getNamespaceURI);

		if (qName.namespaceURI == null) {
			// A namespace URI hasn't been found: we need to iterate through all known types
			NodeIterator itAvailableTypes = model.listObjectsOfProperty(RDF.type);
			while (itAvailableTypes.hasNext()) {
				RDFNode typeNode = itAvailableTypes.next();
				if (typeNode instanceof Resource) {
					Resource typeResource = (Resource) typeNode;
					if ((qName.namespaceURI == null || qName.namespaceURI.equals(typeResource.getNameSpace())) && qName.localName.equals(typeResource.getLocalName())) {
						return typeResource;
					}
				}
			}
		} else {
			// We known both namespace URI and local name: ask directly for it
			Resource typeResource = model.getResource(qName.namespaceURI + qName.localName);

			// Note: Jena will happily create the resource if it doesn't exist already, so
			// we at least check that there is at least one instance of it.
			ResIterator itInstances = model.listSubjectsWithProperty(RDF.type, typeResource);
			if (itInstances.hasNext()) {
				return typeResource;
			}
		}

		throw new EolModelElementTypeNotFoundException(this.getName(), type);
	}

	@Override
	protected Collection<RDFModelElement> getAllOfKindFromModel(String kind)
			throws EolModelElementTypeNotFoundException {
		// TODO investigate type hierarchies in RDF-related technologies
		// TODO investigate generic RDF-based types (Resource.all? Statement.all? Property.all?)
		return getAllOfTypeFromModel(kind);
	}

	@Override
	protected RDFResource createInstanceInModel(String type) throws EolModelElementTypeNotFoundException, EolNotInstantiableModelElementTypeException {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void loadModel() throws EolModelLoadingException {
		if (!this.readOnLoad) {
			return;
		}

		try {
			if (dataURIs.isEmpty()) {
				throw new IllegalStateException("No file path has been set");
			}

			// Read all the URIs into an integrated model
			Model schemaModel = ModelFactory.createDefaultModel();
			for (Iterator<String> itUri = schemaURIs.iterator(); itUri.hasNext(); ) {
				schemaModel.read(itUri.next());
			}
			
			// If a schema model has been loaded assume need for a reasoner using Jena's default OWL
			if( (schemaModel.size() >= 0) && (rdfsReasonerType == RDFReasonerType.NONE) )
			{
				this.setRdfsReasonerType(RDFReasonerType.OWL_FULL);
			}

			Model dataModel = ModelFactory.createDefaultModel();
			for (Iterator<String> itUri = dataURIs.iterator(); itUri.hasNext(); ) {
				dataModel.read(itUri.next());
			}
			
			//Create an OntModel to handle the data model being loaded or inferred from data and schema
			this.model = ModelFactory.createOntologyModel();
			
			if (rdfsReasonerType == RDFReasonerType.NONE) { // Just OntModel bits are added to the dataModel being loaded.
				this.model.add(dataModel);
			} else { // OntModel bits are added and the reasoner will add schema bits to the dataModel being loaded.
				Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
				InfModel infmodel = ModelFactory.createInfModel(reasoner, dataModel, schemaModel);
				this.model.add(infmodel);
			}
			
			// Copy the Name prefixmaps from the loaded Model dataModel to the new OntModel dataModel representation
			for (Entry<String, String> e : dataModel.getNsPrefixMap().entrySet()) {
				this.model.setNsPrefix(e.getKey(), e.getValue());
			}

		} catch (Exception ex) {
			throw new EolModelLoadingException(ex, this);
		}
	}

	@Override
	protected void disposeModel() {
		model = null;
	}

	@Override
	protected boolean deleteElementInModel(Object instance) throws EolRuntimeException {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Object getCacheKeyForType(String type) throws EolModelElementTypeNotFoundException {
		return getTypeResourceByName(type);
	}

	@Override
	public Collection<String> getAllTypeNamesOf(Object instance) {
		List<String> types = new ArrayList<>();

		if (instance instanceof RDFResource) {
			RDFResource res = (RDFResource) instance;
			for (RDFResource t : res.getTypes()) {
				types.add(getQualifiedTypeName(t));
			}
		}

		return types;
	}

	public List<String> getDataUris() {
		return dataURIs;
	}

	public void setDataUri(String uri) {
		this.dataURIs.clear();
		this.dataURIs.add(uri);
	}

	public List<String> getSchemaUris() {
		return schemaURIs;
	}

	public void setSchemaUri(String uri) {
		this.schemaURIs.clear();
		this.schemaURIs.add(uri);
	}

	public Map<String, String> getCustomPrefixesMap() {
		return this.customPrefixesMap;
	}
	
	public List<String> getLanguagePreference() {
		return languagePreference;
	}

	/**
	 * <p>
	 * Returns the URI associated to a prefix.
	 * </p>
	 *
	 * <p>
	 * The prefix may be defined in the loaded RDF resource, or it may have been
	 * specified by modifying {@load #getCustomPrefixesMap()} or calling
	 * {@link #load(StringProperties)} with {@link #PROPERTY_PREFIXES} being set.
	 * </p>
	 */
	public String getNamespaceURI(String prefix) {
		String uri = customPrefixesMap.get(prefix);
		if (uri == null) {
			return model.getNsPrefixURI(prefix);
		}
		return uri;
	}

	/**
	 * <p>
	 * Returns a prefix associated with a namespace URI, if it exists.
	 * </p>
	 *
	 * <p>
	 * The custom prefixes will take priority over the ones in the loaded RDF
	 * resource.
	 * </p>
	 */
	public String getPrefix(String namespaceURI) {
		for (Entry<String, String> entry : customPrefixesMap.entrySet()) {
			if (entry.getValue().equals(namespaceURI)) {
				return entry.getKey();
			}
		}
		return model.getNsURIPrefix(namespaceURI);
	}

	// Using Java's Locale class to check that tags conform to bcp47 structure
	public static boolean isValidLanguageTag (String bcp47tag) {
		boolean isValidBCP47 = !("und".equals(Locale.forLanguageTag(bcp47tag).toLanguageTag()));
		return isValidBCP47;
	}
}
