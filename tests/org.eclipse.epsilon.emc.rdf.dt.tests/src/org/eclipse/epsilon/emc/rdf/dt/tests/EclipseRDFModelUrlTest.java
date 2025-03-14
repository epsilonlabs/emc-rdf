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

package org.eclipse.epsilon.emc.rdf.dt.tests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.rdf.RDFModel;
import org.eclipse.epsilon.emc.rdf.RDFResource;
import org.eclipse.epsilon.emc.rdf.dt.EclipseRDFModel;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.execute.context.EolContext;
import org.eclipse.epsilon.rdf.validation.RDFValidation.ValidationMode;
import org.junit.After;
import org.junit.Test;

public class EclipseRDFModelUrlTest extends EclipseProjectEnvTest {

	public EclipseRDFModelUrlTest() {
		super(PROJECT_URL);
	}

	private static final String PROJECT_URL = "myProject";

	private static final String RESOURCES_BASEDIR = "../org.eclipse.epsilon.emc.rdf.tests";
	private static final String OWL_DEMO_DATAMODEL_PROJECT_PATH = "/resources/OWL/owlDemoData_valid.ttl";
	private static final String OWL_DEMO_SCHEMAMODEL_PROJECT_PATH = "/resources/OWL/owlDemoSchema.ttl";

	private static final String OWL_DEMO_DATAMODEL = RESOURCES_BASEDIR + OWL_DEMO_DATAMODEL_PROJECT_PATH;
	private static final String OWL_DEMO_SCHEMAMODEL = RESOURCES_BASEDIR + OWL_DEMO_SCHEMAMODEL_PROJECT_PATH;
	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";
	
	private static final String URI_BIGNAME42 = "urn:x-hp:eg/bigName42";

	private EclipseRDFModel model;
	private EolContext context;

	//
	// NO ERRORS EXPECTED
	//
	
	@Test
	public void relativePathModelLoad() throws Exception {
		StringProperties props = createStringProperties(OWL_DEMO_DATAMODEL, OWL_DEMO_SCHEMAMODEL, LANGUAGE_PREFERENCE_EN_STRING);

		this.context = new EolContext();
		copyModelFiles();
		model.load(props);
	}

	@Test
	public void relativeFileUrlModelLoad() throws Exception {
		String dataUrl = "file:" + OWL_DEMO_DATAMODEL;
		String schemaUrl = "file:" + OWL_DEMO_SCHEMAMODEL;
		StringProperties props = createStringProperties(dataUrl, schemaUrl, LANGUAGE_PREFERENCE_EN_STRING);

		this.context = new EolContext();
		copyModelFiles();
		model.load(props);
		loadedModelTest();
	}

	@Test
	public void longFileUrlModelLoad() throws Exception {
		String dataUrl = getTestProjectURIString() + OWL_DEMO_DATAMODEL_PROJECT_PATH;
		String schemaUrl = getTestProjectURIString() + OWL_DEMO_SCHEMAMODEL_PROJECT_PATH;
		StringProperties props = createStringProperties(dataUrl, schemaUrl, LANGUAGE_PREFERENCE_EN_STRING);

		this.context = new EolContext();
		copyModelFiles();
		model.load(props);
		loadedModelTest();
	}

	@Test
	public void platformUrlModelLoad() throws Exception {
		String dataUrl = "platform:/resource/" + PROJECT_URL + OWL_DEMO_DATAMODEL_PROJECT_PATH;
		String schemaUrl = "platform:/resource/" + PROJECT_URL + OWL_DEMO_SCHEMAMODEL_PROJECT_PATH;
		StringProperties props = createStringProperties(dataUrl, schemaUrl, LANGUAGE_PREFERENCE_EN_STRING);

		this.context = new EolContext();
		copyModelFiles();

		model.load(props);
		loadedModelTest();
	}

	//
	//  EXPECTED ERRORS!
	//

	@Test
	public void missingResourceInPlatformUrlModelLoad() throws Exception  {
		String dataUrl = new String("platform:/-/" + PROJECT_URL + "/" + OWL_DEMO_DATAMODEL);
		String schemaUrl = new String("platform:/-/" + PROJECT_URL + "/" + OWL_DEMO_SCHEMAMODEL);
		StringProperties props = createStringProperties(dataUrl, schemaUrl, LANGUAGE_PREFERENCE_EN_STRING);

		this.context = new EolContext();
		copyModelFiles();

		try {
			model.load(props);
			fail("A model loading exception was expected");
		} catch (EolModelLoadingException e) {
			assertTrue(e.getMessage().contains("protocol variation"));
		}
	}

	@Test
	public void missingProjectUrlInPlatformUrlModelLoad() throws Exception  {
		String dataUrl = "platform:/resource" + OWL_DEMO_DATAMODEL;
		String schemaUrl = "platform:/resource" + OWL_DEMO_SCHEMAMODEL;
		StringProperties props = createStringProperties(dataUrl, schemaUrl, LANGUAGE_PREFERENCE_EN_STRING);

		this.context = new EolContext();
		copyModelFiles();

		try {
			model.load(props);
			fail("A model loading exception was expected");
		} catch (EolModelLoadingException e) {
			assertTrue(e.getMessage().contains("protocol variation"));
		}
	}

	@After
	public void teardown() {
		if (model != null) {
			model.dispose();
		}
	}

	private void loadedModelTest() {
		assertTrue(model != null);
		RDFResource element = model.getElementById(URI_BIGNAME42);
		@SuppressWarnings("unchecked")
		Collection<RDFResource> motherBoardList = (Collection<RDFResource>) element.getProperty("eg:motherBoard", context);		
		assertTrue("motherBoard has max cardinality of 1 should only have that value returned ",
			motherBoardList.size() == 1);
	}
	
	private void copyModelFiles() throws Exception {
		super.copyIntoProject(OWL_DEMO_DATAMODEL, OWL_DEMO_DATAMODEL_PROJECT_PATH);
		super.copyIntoProject(OWL_DEMO_SCHEMAMODEL, OWL_DEMO_SCHEMAMODEL_PROJECT_PATH);
	}

	protected StringProperties createStringProperties(String dataModelUri, String schemaModelUri,
			String languagePreference) {
		this.model = new EclipseRDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_DATA_URIS, dataModelUri);
		props.put(RDFModel.PROPERTY_SCHEMA_URIS, schemaModelUri);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, languagePreference);
		props.put(RDFModel.PROPERTY_VALIDATE_MODEL, ValidationMode.NONE.getId());
		return props;
	}
}
