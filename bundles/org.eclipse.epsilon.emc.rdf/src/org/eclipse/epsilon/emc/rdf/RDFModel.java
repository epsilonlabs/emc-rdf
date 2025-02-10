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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.ValidityReport;
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

	public static final String PROPERTY_DATA_URIS = "uris";
	public static final String PROPERTY_SCHEMA_URIS = "schemaUris";
	public static final String PROPERTY_LANGUAGE_PREFERENCE = "languagePreference";
	public static final String PROPERTY_PREFIXES = "prefixes";
	public static final String PROPERTY_VALIDATE_MODEL = "enableModelValidation";

	public static final String VALIDATION_SELECTION_JENA = "jena";
	public static final String VALIDATION_SELECTION_NONE = "none";
	public static final String VALIDATION_SELECTION_DEFAULT = VALIDATION_SELECTION_JENA;
	
	// Model loading and properties are located at the bottom of this Class
	
	protected OntModel model;

	public RDFModel() {
		this.propertyGetter = new RDFPropertyGetter(this);
	}
	
	protected RDFResource createResource(Resource aResource) {
		return new RDFResource(aResource, this);
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
	protected Object getCacheKeyForType(String type) throws EolModelElementTypeNotFoundException {
		return getTypeResourceByName(type);
	}

	private String getQualifiedTypeName(RDFResource typeResource) {
		return String.format("%s:%s",
			getPrefix(typeResource.getResource().getNameSpace()),
			typeResource.getResource().getLocalName());
	}

	@Override
	public RDFResource getElementById(String uri) {
		Resource res = model.getResource(uri);
		if (res != null) {
			return createResource(res);
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
	public boolean owns(Object instance) {
		return instance instanceof RDFModelElement && ((RDFModelElement) instance).getModel() == this;
	}

	@Override
	public boolean isInstantiable(String type) {
		return false;
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
	
	//
	// GET ALL ___ FROM MODEL
	
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
	
	@Override
	protected Collection<RDFModelElement> allContentsFromModel() {
		final List<RDFModelElement> elems = new ArrayList<>();

		for (ResIterator it = model.listSubjects(); it.hasNext(); ) {
			Resource stmt = it.next();
			elems.add(createResource(stmt));
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
			instances.add(createResource(itInstances.next()));
		}

		return instances;
	}

	@Override
	protected Collection<RDFModelElement> getAllOfKindFromModel(String kind)
			throws EolModelElementTypeNotFoundException {
		// TODO investigate type hierarchies in RDF-related technologies
		// TODO investigate generic RDF-based types (Resource.all? Statement.all? Property.all?)
		return getAllOfTypeFromModel(kind);
	}

	//
	// MODEL LIFE CYCLE (LOAD/DISPOSE/STORE)
	
	@Override
	public void load(StringProperties properties, IRelativePathResolver resolver) throws EolModelLoadingException {
		super.load(properties, resolver);

		/*
		 * This method assumes that the StringProperties replaces all previous
		 * configuration of this RDFModel. This is the same as in other popular
		 * EMC drivers (e.g. the EmfModel class).
		 */
		loadProperty(properties, PROPERTY_DATA_URIS, this.dataURIs);
		loadProperty(properties, PROPERTY_SCHEMA_URIS, this.schemaURIs);
		loadPropertyPrefixes(properties);
		loadPropertyLanguagePreference(properties);
		loadPropertyValidateModel(properties);

		load();
		
		// After Loading all scheme, data models and inferring the full model, validate
		if (validationProperty.contains(VALIDATION_SELECTION_JENA)) {
			try {
				validateModel();
			} catch (Exception e) {
				throw new EolModelLoadingException(e, this);
			}
		}
	}

	private void loadProperty(StringProperties properties, String propertyName, List<String> targetList) {
		targetList.clear();
		{
			String sUris = properties.getProperty(propertyName, "").strip();
			if (!sUris.isEmpty()) {
				for (String uri : sUris.split(",")) {
					targetList.add(uri.strip());
				}
			}
		}
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
			if (schemaModel.size() >= 0 && reasonerType == ReasonerType.NONE) {
				this.setReasonerType(ReasonerType.OWL_FULL);
			}

			Model dataModel = ModelFactory.createDefaultModel();
			for (Iterator<String> itUri = dataURIs.iterator(); itUri.hasNext(); ) {
				dataModel.read(itUri.next());
			}

			//Create an OntModel to handle the data model being loaded or inferred from data and schema
			this.model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF);
			
			if (reasonerType == ReasonerType.NONE) {
				// Only the OntModel bits are added to the dataModel being loaded.
				this.model.add(dataModel);
			} else {
				// OntModel bits are added and the reasoner will add schema bits to the dataModel being loaded.
				Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
				InfModel infmodel = ModelFactory.createInfModel(reasoner, dataModel, schemaModel);
				this.model.add(infmodel);
			}

			// Copy the Name prefix maps from the loaded Model dataModel to the new OntModel dataModel representation
			for (Entry<String, String> e : dataModel.getNsPrefixMap().entrySet()) {
				this.model.setNsPrefix(e.getKey(), e.getValue());
			}
		} catch (Exception ex) {
			throw new EolModelLoadingException(ex, this);
		}
	}
	
	//
	// VALIDATE MODEL

	private void validateModel() throws Exception {
		/*
		 * The way the model is validated by Jena depends on how the new OntModel was
		 * created by the ModelFactory.
		 */

		ValidityReport modelValidityReport = model.validate();
		
		if (!modelValidityReport.isValid() | (!modelValidityReport.isClean())) {
			// Throw error with a string containing the validity report 
			String reportString = "The loaded model is not valid or not clean\n";
			int i = 1;
			for (Iterator o = modelValidityReport.getReports(); o.hasNext();) {
				ValidityReport.Report report = (ValidityReport.Report) o.next();
				reportString = reportString.concat(" " + i + ", " + report.toString());
				i++;
			}
			throw new Exception(reportString.toString());
		}
	}
		
	@Override
	protected void disposeModel() {
		model = null;
	}
	
	// Store support coming soon...
	@Override
	public boolean store(String location) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean store() {
		throw new UnsupportedOperationException();
	}

	//
	// JENA REASONER

	public enum ReasonerType {
		// TODO add to this list to cover reasoner types in the ReasonerRegistry Class
		NONE, OWL_FULL;
	}

	protected ReasonerType reasonerType = ReasonerType.NONE;

	public ReasonerType getReasonerType() {
		return reasonerType;
	}

	public void setReasonerType(ReasonerType rdfsReasonerType) {
		this.reasonerType = rdfsReasonerType;
	}
	
	//
	// PROPERTY DATA URIS
	
	protected final List<String> dataURIs = new ArrayList<>();
	
	public List<String> getDataUris() {
		return dataURIs;
	}

	public void setDataUri(String uri) {
		this.dataURIs.clear();
		this.dataURIs.add(uri);
	}

	//
	// PROPERTY SCHEMA URIS
	
	protected final List<String> schemaURIs = new ArrayList<>();
	
	public List<String> getSchemaUris() {
		return schemaURIs;
	}
	

	public void setSchemaUri(String uri) {
		this.schemaURIs.clear();
		this.schemaURIs.add(uri);
	}


	//
	// PROPERTY PREFIXES
	
	/** 
	 * One of the keys used to construct the first argument to
	 * {@link #load(StringProperties, String)}.
	 *
	 * This key should be set to a comma-separated list of prefix=uri pairs that
	 * should be added to the prefix->URI map of the loaded RDF resource. These
	 * pairs will take precedence over existing pairs in the resource.
	 */
	protected final Map<String, String> customPrefixesMap = new HashMap<>();
	
	private void loadPropertyPrefixes (StringProperties properties) {
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
	}
	
	public Map<String, String> getCustomPrefixesMap() {
		return this.customPrefixesMap;
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


	//
	// PROPERTY LANGUAGE PREFERENCE
	

	protected final List<String> languagePreference = new ArrayList<>();

	private void loadPropertyLanguagePreference(StringProperties properties) throws EolModelLoadingException {
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
	}
	
	public List<String> getLanguagePreference() {
		return languagePreference;
	}
	

	// Using Java's Locale class to check that tags conform to bcp47 structure
	public static boolean isValidLanguageTag (String bcp47tag) {
		boolean isValidBCP47 = !("und".equals(Locale.forLanguageTag(bcp47tag).toLanguageTag()));
		return isValidBCP47;
	}
	
	//
	// PROPERTY JANA VALIDATE MODEL 
	
	protected String validationProperty = VALIDATION_SELECTION_DEFAULT;
	
	private void loadPropertyValidateModel(StringProperties properties) {
		validationProperty = properties.getProperty(RDFModel.PROPERTY_VALIDATE_MODEL, VALIDATION_SELECTION_JENA);
	}
	
	public String getValidationProperty () {
		return validationProperty;
	}
	
	//
	// UNSUPPORTED OPERATIONS
	
	@Override
	protected RDFResource createInstanceInModel(String type) throws EolModelElementTypeNotFoundException, EolNotInstantiableModelElementTypeException {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void setElementId(Object instance, String newId) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected boolean deleteElementInModel(Object instance) throws EolRuntimeException {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Object getEnumerationValue(String enumeration, String label) throws EolEnumerationValueNotFoundException {
		throw new UnsupportedOperationException();
	}	

}
