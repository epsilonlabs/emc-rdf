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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.execute.context.EolContext;
import org.junit.After;
import org.junit.Test;

public class MOF2RDFModelOWLReasonerTest {

	private static final String OWL_DEMO_DATAMODEL = "resources/OWL/owlDemoData.ttl";
	private static final String OWL_DEMO_SCHEMAMODEL = "resources/OWL/owlDemoSchema.ttl";

	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";
	private static final String URI_BIGNAME42 = "urn:x-hp:eg/bigName42";
	private static final String URI_ALIENBOX51 = "urn:x-hp:eg/alienBox51";
	private static final String URI_WHITEBOX = "urn:x-hp:eg/whiteBoxZX";

	private MOF2RDFModel model;
	private EolContext context;

	@After
	public void teardown() {
		if (model != null) {
			model.dispose();
		}
	}

	@Test
	public void loadModelDefaultDataSchemaLangPref() throws EolModelLoadingException {
		loadModelDefaults();
		assertTrue(model != null);
	}
	
	@Test
	public void getMotherBoardTest() {
		loadModelDefaults();
		RDFResource element = model.getElementById(URI_WHITEBOX);
		Object motherBoard = element.getProperty("eg:motherBoard", context);
		assertTrue("motherBoard has max cardinality of 1 should only have that value returned ",
			motherBoard instanceof MOF2RDFResource);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void getMotherBoardNoPrefixTest() {
		loadModelDefaults();
		RDFResource element = model.getElementById(URI_WHITEBOX);
		
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		context.setWarningStream(new PrintStream(errors));
		
		Collection<MOF2RDFResource> motherBoard = 
				(Collection<MOF2RDFResource>) element.getProperty("motherBoard", context);

		assertTrue("2 motherboards should have been reported, with no restrictions ", 2 == motherBoard.size());

		String sErrors = errors.toString();
		assertTrue("An error should be raised Ambiguous property access turning off restriction checks",
				sErrors.contains("No restriction checks applied"));
 		
	}

	@Test
	public void getPropertyThatDoesNotExistAsNullTest() {
		loadModelDefaults();
		RDFResource element = model.getElementById(URI_ALIENBOX51);
		Object motherBoard = element.getProperty("eg:motherBoard", context);
		assertNull("URI_ALIENBOX51 computer does not have motherBoard ", motherBoard);
	}

	@Test
	public void getMotherBoardTestIssuesWarning() throws IOException {
		ByteArrayOutputStream errors = new ByteArrayOutputStream();

		PrintStream oldErr = System.err;
		try {
			System.setErr(new PrintStream(errors));
			loadModelDefaults();

			// This will return only the first motherboard, but we actually have two
			model.getElementById(URI_BIGNAME42).getProperty("eg:motherBoard", context);

			String sErrors = errors.toString();
			assertTrue("An error should be raised for max cardinality being exceeded",
				sErrors.contains("has a max cardinality 1, raw property values list contained"));
		} finally {
			System.setErr(oldErr);
		}
	}

	// Functions not tests

	protected void loadModel(String dataModelUri, String schemaModelUri, String languagePreference) throws EolModelLoadingException {
		this.model = new MOF2RDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_DATA_URIS, dataModelUri);
		props.put(RDFModel.PROPERTY_SCHEMA_URIS, schemaModelUri);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, languagePreference);
		props.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_NONE);
		model.load(props);

		this.context = new EolContext();
	}

	protected void loadModelDefaults() {
		try {
			loadModel(OWL_DEMO_DATAMODEL, OWL_DEMO_SCHEMAMODEL, LANGUAGE_PREFERENCE_EN_STRING);
		} catch (EolModelLoadingException e) {
			System.out.println("default Load Model failed");
			e.printStackTrace();
		}
	}

}
