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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
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
import org.apache.jena.reasoner.ValidityReport.Report;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
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
	public static final String PROPERTY_DATA_URIS = "uris";

	/**
	 * One of the keys used to construct the first argument to
	 * {@link #load(StringProperties, String)}.
	 *
	 * This key should be set to a comma-separated list of prefix=uri pairs that
	 * should be added to the prefix->URI map of the loaded RDF resource. These
	 * pairs will take precedence over existing pairs in the resource.
	 */
	public static final String PROPERTY_PREFIXES = "prefixes";
	public static final String PROPERTY_VALIDATE_MODEL = "enableModelValidation";

	public static final String VALIDATION_SELECTION_JENA = "jena";
	public static final String VALIDATION_SELECTION_NONE = "none";
	public static final String VALIDATION_SELECTION_DEFAULT = VALIDATION_SELECTION_JENA;

	protected final List<String> languagePreference = new ArrayList<>();
	protected final Map<String, String> customPrefixesMap = new HashMap<>();
	protected String validationMode = VALIDATION_SELECTION_DEFAULT;

	// TODO add to this list to cover reasoner types in the ReasonerRegistry Class
	public enum ReasonerType {
		NONE,
		OWL_FULL;
	}

	protected ReasonerType reasonerType = ReasonerType.NONE;

	public ReasonerType getReasonerType() {
		return reasonerType;
	}

	public void setReasonerType(ReasonerType rdfsReasonerType) {
		this.reasonerType = rdfsReasonerType;
	}

	
	protected final List<String> schemaURIs = new ArrayList<>();
	protected Dataset schemaModelSet;	// DefaultModel empty, using NamedModels
	
	protected final List<String> dataURIs = new ArrayList<>();
	protected Dataset dataModelSet;		// DefaultModel empty, using NamedModels
	
	protected OntModel model;

	public RDFModel() {
		this.propertyGetter = new RDFPropertyGetter(this);
	}
	
	protected RDFResource createResource(Resource aResource) {
		return new RDFResource(aResource, this);
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
		loadCommaSeparatedProperty(properties, PROPERTY_DATA_URIS, this.dataURIs);
		loadCommaSeparatedProperty(properties, PROPERTY_SCHEMA_URIS, this.schemaURIs);

		this.validationMode = properties.getProperty(RDFModel.PROPERTY_VALIDATE_MODEL, VALIDATION_SELECTION_JENA);

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
		
		// After Loading all scheme, data models and inferring the full model, validate
		if (VALIDATION_SELECTION_JENA.equals(validationMode)) {
			try {
				validateModel();
			} catch (Exception e) {
				throw new EolModelLoadingException(e, this);
			}
		}
	}

	protected void loadCommaSeparatedProperty(StringProperties properties, String propertyName, List<String> targetList) {
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
		
	private void storeDataNamedModel (String namedModelURI, String saveLocationURI) throws IOException {
		// Assumes the NamedModelURI is for a model in the dataset and locationURI be saved to
	
		Model modelToSave = dataModelSet.getNamedModel(namedModelURI);
		Lang lang = RDFDataMgr.determineLang(namedModelURI, namedModelURI, null);
		
	    try (OutputStream out = new FileOutputStream(saveLocationURI)) {
	    	RDFDataMgr.write(out, modelToSave, lang);
	    	out.close();	
	    } 
	}
	
	@Override
	public boolean store(String location) {
		// Save models to new URIs using the location given and namedModel filename
		// iterate over URIs, write the dataset named models back to new storage URI with format detected by Jena
		
		System.out.println("location: " + location);
		if (!location.endsWith("/"))
		{
			location = location + "/";
			System.out.println("fixed locations: " + location);
		}			
		
		for (String uri : dataURIs) {
			String fileName = uri.substring(uri.lastIndexOf('/') + 1);
			String newUri = location + fileName;
			try {
				storeDataNamedModel(uri, newUri);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}			
			//System.out.println("dataURI: " + uri + "dataURI file name: " + fileName	+ "new Location and fileName: " + newUri );			
		}
		return true;
	}

	@Override
	public boolean store() {
		// Save back to original URI
		// iterate over URIs, write the dataset named models back to storage with format detected by Jena
		for (String uri : dataURIs) {
			try {
				storeDataNamedModel(uri, uri);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
		}
		return true;
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
			
			schemaModelSet = DatasetFactory.createNamed(schemaURIs);
			Model schemaUnionModel = schemaModelSet.getUnionModel(); // READ-ONLY

			// If a schema model has been loaded assume need for a reasoner using Jena's default OWL
			if (schemaURIs.size() >= 0 && reasonerType == ReasonerType.NONE) {
				this.setReasonerType(ReasonerType.OWL_FULL);
			}

			dataModelSet = DatasetFactory.createNamed(dataURIs);
			Model dataUnionModel = dataModelSet.getUnionModel(); // READ-ONLY

			//Create an OntModel to handle the data model being loaded or inferred from data and schema
			if (reasonerType == ReasonerType.NONE) {
				// Only the OntModel bits are added to the dataModel being loaded.
				this.model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF, dataUnionModel);
			} else {
				// OntModel bits are added and the reasoner will add schema bits to the dataModel being loaded.
				Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
				InfModel infmodel = ModelFactory.createInfModel(reasoner, schemaUnionModel, dataUnionModel);
				this.model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF, infmodel);
			}

		// Copy the Name prefix maps from the loaded Model dataModel to the new OntModel dataModel representation
		//	May not need this now...
			/* 
			for (Entry<String, String> e : dataModel.getNsPrefixMap().entrySet()) {
				this.model.setNsPrefix(e.getKey(), e.getValue());
			}
			*/
		} catch (Exception ex) {
			throw new EolModelLoadingException(ex, this);
		}
	}

	protected void validateModel() throws Exception {
		/*
		 * The way the model is validated by Jena depends on how the new OntModel was
		 * created by the ModelFactory.
		 */

		ValidityReport modelValidityReport = model.validate();
		if (!modelValidityReport.isValid() || !modelValidityReport.isClean()) {
			StringBuilder sb = new StringBuilder("The loaded model is not valid or not clean\n");
			int i = 1;
			for (Iterator<Report> o = modelValidityReport.getReports(); o.hasNext();) {
				ValidityReport.Report report = (ValidityReport.Report) o.next();
				sb.append(String.format(" %d: %s", i, report.toString()));
				i++;
			}
			throw new Exception(sb.toString());
		}
	}

	public String getValidationMode() {
		return validationMode;
	}

	/**
	 * Changes the internal consistency validation mode used during loading.
	 *
	 * @param mode New mode. Must be one of {@code RDFModel#VALIDATION_SELECTION_NONE} or
	 *             {@code RDFModel#VALIDATION_SELECTION_JENA}.
	 */
	public void setValidationMode(String mode) {
		if (!VALIDATION_SELECTION_JENA.equals(mode) && !VALIDATION_SELECTION_NONE.equals(mode)) {
			throw new IllegalArgumentException("Unknown validation mode " + mode);
		}
		this.validationMode = mode;
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
