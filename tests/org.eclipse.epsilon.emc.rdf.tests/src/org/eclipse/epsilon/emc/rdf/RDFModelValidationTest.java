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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.junit.Test;

public class RDFModelValidationTest {
	
	private static final RDFModel.ValidationMode NONE = RDFModel.ValidationMode.NONE;
	private static final RDFModel.ValidationMode JENA_VALID = RDFModel.ValidationMode.JENA_VALID;
	private static final RDFModel.ValidationMode JENA_CLEAN = RDFModel.ValidationMode.JENA_CLEAN;
	
	private static final String OWL_DEMO_DATAMODEL_VALID = "resources/OWL/owlDemoData_valid.ttl";
	private static final String OWL_DEMO_DATAMODEL_INVALID = "resources/OWL/owlDemoData.ttl";	
	private static final String SPIDERMAN_UNCLEAN = "resources/spiderman_unclean.ttl" + "," + "resources/foaf.rdf";
	
	private static final String OWL_DEMO_SCHEMAMODEL = "resources/OWL/owlDemoSchema.ttl";
	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";

	private RDFModel model;
	
	// VALID MODELS should always load

	@Test
	public void loadValidModelNone() throws EolModelLoadingException {
		loadModelDataAndSchema(OWL_DEMO_DATAMODEL_VALID, NONE);
	}
	
	@Test
	public void loadValidModelJenaValid() throws EolModelLoadingException {
		loadModelDataAndSchema(OWL_DEMO_DATAMODEL_VALID, JENA_VALID);
	}

	@Test
	public void loadValidModelJenaClean() throws EolModelLoadingException {
		loadModelDataAndSchema(OWL_DEMO_DATAMODEL_VALID, JENA_CLEAN);
	}
	
	// INVALID MODELS will create warnings or exception errors depending on the validation mode
	
	@Test
	public void loadInValidModelNone() throws EolModelLoadingException {
		loadModelDataAndSchema(OWL_DEMO_DATAMODEL_INVALID, NONE);
	}
	
	@Test
	public void loadInvalidModelJenaValid() {
		try {
			loadModelDataAndSchema(OWL_DEMO_DATAMODEL_INVALID, JENA_VALID);
			fail("An exception was expected");
		} catch (EolModelLoadingException e) {
			String sErrors = e.getMessage();
			//System.out.println(sErrors);
			assertTrue("The model loaded should FAIL validation (6 errors relating to BigName42 having 2 motherboards)",
					sErrors.contains("not valid"));
		}
	}
	
	@Test
	public void loadInvalidModelJenaClean() {
		try {
			loadModelDataAndSchema(OWL_DEMO_DATAMODEL_INVALID, JENA_CLEAN);
			fail("An exception was expected");
		} catch (EolModelLoadingException e) {
			String sErrors = e.getMessage();
			//System.out.println(sErrors);
			assertTrue("The model loaded should FAIL validation (6 errors relating to BigName42 having 2 motherboards)",
					sErrors.contains("not valid and not clean"));
		}
	}
	
	// UNCLEAN clean models will pass jena-valid with warnings, but fail jena-clean
	
	@Test
	public void loadUncleanModelNone() throws EolModelLoadingException {
		loadModelDataOnly(SPIDERMAN_UNCLEAN, NONE);
	}
	
	@Test
	public void loadUncleanModelJenaValid() throws EolModelLoadingException {
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		PrintStream oldErr = System.err;
		try {
			System.setErr(new PrintStream(errors));

			loadModelDataOnly(SPIDERMAN_UNCLEAN, JENA_VALID);

			String sErrors = errors.toString();
			assertTrue("The model is valid, but should report warnings", sErrors.contains("The loaded model is valid"));
		} finally {
			System.setErr(oldErr);
		}
	}
	
	@Test
	public void loadUncleanModelJenaClean() {
		try { // The loaded model is not clean, valid with warnings
			loadModelDataOnly(SPIDERMAN_UNCLEAN, JENA_CLEAN);
			fail("An exception was expected");
		} catch (EolModelLoadingException e) {
			String sErrors = e.getMessage();
			assertTrue("The loaded model is not clean, but should be valid with some warnings",
					sErrors.contains("valid and not clean"));
		}
	}
	
	// Functions not tests

	protected void loadModelDataAndSchema(String dataModelUri, RDFModel.ValidationMode mode) throws EolModelLoadingException {
		this.model = new RDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_DATA_URIS, dataModelUri);
		props.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		props.put(RDFModel.PROPERTY_VALIDATE_MODEL, mode.getId());
		model.load(props);
	}
	
	protected void loadModelDataOnly(String dataModelUri, RDFModel.ValidationMode mode) throws EolModelLoadingException {
		this.model = new RDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_DATA_URIS, dataModelUri);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		props.put(RDFModel.PROPERTY_VALIDATE_MODEL, mode.getId());
		model.load(props);
	}

}
