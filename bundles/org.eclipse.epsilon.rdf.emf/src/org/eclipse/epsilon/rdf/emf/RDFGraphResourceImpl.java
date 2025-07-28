/********************************************************************************
 * Copyright (c) 2025 University of York
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
package org.eclipse.epsilon.rdf.emf;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.epsilon.rdf.emf.config.RDFResourceConfiguration;
import org.eclipse.epsilon.rdf.validation.RDFValidation.ValidationMode;
import org.eclipse.epsilon.rdf.validation.RDFValidation.ValidationMode.RDFModelValidationReport;
import org.eclipse.epsilon.rdf.validation.RDFValidationException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

public class RDFGraphResourceImpl extends ResourceImpl {
	
	private static final boolean NOTIFICATION_TRACE = true;
	
	private RDFResourceConfiguration config;
	private RDFDeserializer deserializer;
	private RDFGraphResourceUpdate rdfGraphUpdater;
	
	private Dataset dataModelSet;
	private Dataset schemaModelSet;

	private Model rdfSchemaModel;
	private Model rdfDataModel;
	private OntModel rdfOntModel;

	public static enum MultiValueAttributeMode {
		LIST("List"), CONTAINER("Container");

		private final String id;

		public String getId() {
			return id;
		}

		MultiValueAttributeMode(String value) {
			this.id = value;
		}

		public static MultiValueAttributeMode fromValue(String value) {
			for (MultiValueAttributeMode mode : values()) {
				if (mode.id.equalsIgnoreCase(value)) {
					return mode;
				}
			}
			return null; // or throw an exception if not found
		}
	};
	private MultiValueAttributeMode multiValueAttributeMode = MultiValueAttributeMode.LIST;
	
	public static final ValidationMode VALIDATION_SELECTION_DEFAULT = ValidationMode.NONE;
	protected ValidationMode validationMode = VALIDATION_SELECTION_DEFAULT;
	
	public ValidationMode getValidationMode() {
		return validationMode;
	}

	public void setValidationMode(ValidationMode validationMode) {
		this.validationMode = validationMode;
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		if (this.getURI().isRelative()) {
			throw new IllegalArgumentException("URI must be absolute");
		}

		// The custom classloader constructor is needed for OSGi compatibility
		CustomClassLoaderConstructor constructor = new CustomClassLoaderConstructor(this.getClass().getClassLoader(), new LoaderOptions());
		this.config = new Yaml(constructor).loadAs(inputStream, RDFResourceConfiguration.class);
		loadRDFModels();

		validationMode = config.getRawValidationMode();
		
		multiValueAttributeMode = MultiValueAttributeMode.fromValue(this.config.getMultiValueAttributeMode());

		deserializer = new RDFDeserializer(() -> this.getResourceSet().getPackageRegistry());
		deserializer.deserialize(rdfOntModel);
		for (EObject eob : deserializer.getEObjectToResourceMap().keySet()) {
			if (eob.eContainer() == null) {
				getContents().add(eob);
			}
		}
		
		// Apply eAdapters for notifications of changes, and setup the Graph Resource updater
		if (NOTIFICATION_TRACE) {
			// Produce a console trace for debugging and development
			this.eAdapters().add(new RDFGraphResourceNotificationAdapterTrace());
		}
		this.eAdapters().add(new RDFGraphResourceNotificationAdapterChangeRDF());
		rdfGraphUpdater = new RDFGraphResourceUpdate(deserializer, this, multiValueAttributeMode);
	}
	
	public RDFGraphResourceUpdate getRDFGraphUpdater() {
		return rdfGraphUpdater;
	}
	
	// Save the Graph resource
	private void storeDatasetNamedModels (Dataset dataset, String namedModelURI, String saveLocationURI) throws IOException {
		// NamedModelURI is a model in the provided dataset and saveLocationURI is the file system path to save it too.
		if (dataset.containsNamedModel(namedModelURI))
		{
			Model modelToSave = dataset.getNamedModel(namedModelURI);
			Lang lang = RDFDataMgr.determineLang(namedModelURI, namedModelURI, Lang.TTL);  // Hint becomes default
			
			try (OutputStream out = new BufferedOutputStream(new FileOutputStream(saveLocationURI))) {
				RDFDataMgr.write(out, modelToSave, lang);
				out.close();
			}
		}
		else {
			System.err.printf("Cannot find named model URI: %s\n", namedModelURI);
		}
	}
	
	@Override
	public void save(Map<?, ?> options) throws IOException {
		// TODO need some way to work out which of the Named models we want to write out, for now dump them all.
		for (Iterator<Resource> namedModels = dataModelSet.listModelNames(); namedModels.hasNext(); ) {
			Resource m = namedModels.next();
			URL url = new URL(m.getURI());
			URL fileSystemPathUrl = FileLocator.toFileURL(url);
			storeDatasetNamedModels(dataModelSet, m.getURI(), fileSystemPathUrl.getPath());
		}
	}

	public Resource getRDFResource(EObject eob) {
		return deserializer.getRDFResource(eob);
	}

	public Set<Resource> getRDFResources() {
		return deserializer.getRDFResources();
	}

	public Collection<EObject> getEObjects(Resource res) {
		return deserializer.getEObjects(res);
	}
	
	public Map<EObject, Resource> getEObjectToRDFResourceMap() {
		return deserializer.getEObjectToResourceMap();
	}

	protected void loadRDFModels() throws IOException {
		this.schemaModelSet = loadRDFModels(config.getSchemaModels());
		this.rdfSchemaModel = schemaModelSet.getUnionModel();

		this.dataModelSet = loadRDFModels(config.getDataModels());
		this.rdfDataModel = dataModelSet.getUnionModel();
				
		InfModel infModel = ModelFactory.createRDFSModel(rdfSchemaModel, rdfDataModel);
		this.rdfOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF, infModel);

		RDFModelValidationReport result = validationMode.validate(rdfOntModel);
		if (!result.isValid()) {
			throw new RDFValidationException(result.getText());
		}
	}

	public Dataset loadRDFModels(Set<String> uris) throws IOException, MalformedURLException {
		Dataset newDataset = null;
		List<String> namedModelSources = new ArrayList<String>();

		for (String sURI : uris) {
			URI uri = URI.createURI(sURI);
			if (uri.isRelative()) {
				uri = uri.resolve(this.getURI());
			}

			/*
			 * NOTE: ideally we'd use a Jena StreamManager to handle this, but we can't
			 * because we would run into a conflict between the SLF4J used in Eclipse and
			 * the SLF4J bundled within Jena. It's simpler to just preprocess the platform
			 * URI here.
			 */
			if ("platform".equals(uri.scheme())) {
				String sFileURI = FileLocator.toFileURL(new URL(uri.toString())).toString();
				namedModelSources.add(sFileURI);
			} else {
				namedModelSources.add(uri.toString());
			}
		}

		// create a dataset with all the named models
		if (!namedModelSources.isEmpty()) {
			newDataset = DatasetFactory.createNamed(namedModelSources);
		} else {
			newDataset = DatasetFactory.create(); // create an empty one
		}
		return newDataset;
	}

	public RDFResourceConfiguration getConfig() {
		return config;
	}

	public void setConfig(RDFResourceConfiguration config) {
		this.config = config;
	}

	public List<Resource> getResourcesForNamedModelsContaining(EObject eObject) {
		Resource res = this.getRDFResource(eObject);
		return getResourcesForNamedModelsContaining(res);
	}
	
	public List<Resource> getResourcesForNamedModelsContaining(Resource res) {
		List<Resource> resources = new ArrayList<Resource>();		
		if (null != dataModelSet) {
			Iterator<Resource> namedModels = dataModelSet.listModelNames();
			namedModels.forEachRemaining(m -> {
				Model model = dataModelSet.getNamedModel(m);
				if (model.containsResource(res)) {
					resources.add(m);
				}
			});
		}
		return resources;
	}

	public Model getNamedModel(Resource model) {
		return dataModelSet.getNamedModel(model);
	}
	
	public Model getFirstNamedModel() {
		return dataModelSet.getNamedModel(getResourcesForAllNamedModels().get(0));
	}

	public Resource getFirstNamedModelResource() {
		return getResourcesForAllNamedModels().get(0);
	}
	
	public List<Model> getNamedModels(List<Resource> namedModelURIs) {
		List<Model> namedModels = new ArrayList<Model>();
		for (Resource model : namedModelURIs) {
			namedModels.add(dataModelSet.getNamedModel(model));
		}
		return namedModels;
	}

	public List<Resource> getResourcesForAllNamedModels() {
		List<Resource> modelResourceList = new ArrayList<Resource>();
		dataModelSet.listModelNames().forEachRemaining(m->modelResourceList.add(m));
		return modelResourceList;
	}

}
