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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.epsilon.rdf.emf.config.RDFResourceConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

public class RDFGraphResourceImpl extends ResourceImpl {

	private RDFResourceConfiguration config;
	private RDFDeserializer deserializer;

	private Model rdfSchemaModel;
	private Model rdfDataModel;
	private OntModel rdfOntModel;

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		if (this.getURI().isRelative()) {
			throw new IllegalArgumentException("URI must be absolute");
		}

		// The custom classloader constructor is needed for OSGi compatibility
		CustomClassLoaderConstructor constructor = new CustomClassLoaderConstructor(this.getClass().getClassLoader(), new LoaderOptions());
		this.config = new Yaml(constructor).loadAs(inputStream, RDFResourceConfiguration.class);
		loadRDFModels();

		deserializer = new RDFDeserializer(() -> this.getResourceSet().getPackageRegistry());
		deserializer.deserialize(rdfOntModel);
		for (EObject eob : deserializer.getEObjectToResourceMap().keySet()) {
			if (eob.eContainer() == null) {
				getContents().add(eob);
			}
		}
	}

	public Resource getRDFResource(EObject eob) {
		return deserializer.getRDFResource(eob);
	}

	public Collection<EObject> getEObjects(Resource res) {
		return deserializer.getEObjects(res);
	}

	protected void loadRDFModels() throws IOException {
		this.rdfSchemaModel = ModelFactory.createDefaultModel();
		loadRDFModels(config.getSchemaModels(), rdfSchemaModel);

		this.rdfDataModel = ModelFactory.createDefaultModel();
		loadRDFModels(config.getDataModels(), rdfDataModel);

		InfModel infModel = ModelFactory.createRDFSModel(rdfSchemaModel, rdfDataModel);
		this.rdfOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF, infModel);

		// TODO reintroduce internal consistency validation
	}

	public void loadRDFModels(Set<String> uris, Model targetModel) throws IOException, MalformedURLException {
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
				targetModel.read(sFileURI);
			} else {
				targetModel.read(uri.toString());
			}
		}
	}

	public RDFResourceConfiguration getConfig() {
		return config;
	}

	public void setConfig(RDFResourceConfiguration config) {
		this.config = config;
	}

}
