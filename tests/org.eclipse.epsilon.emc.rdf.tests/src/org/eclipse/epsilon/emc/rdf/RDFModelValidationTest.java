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

import static org.junit.Assert.assertFalse;
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

public class RDFModelValidationTest {

	private static final String OWL_DEMO_DATAMODEL_INVALID = "resources/OWL/owlDemoData.ttl";
	private static final String OWL_DEMO_DATAMODEL_VALID = "resources/OWL/owlDemoData_valid.ttl";
	private static final String OWL_DEMO_SCHEMAMODEL = "resources/OWL/owlDemoSchema.ttl";

	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";
	private static final String URI_BIGNAME42 = "urn:x-hp:eg/bigName42";
	private static final String URI_ALIENBOX51 = "urn:x-hp:eg/alienBox51";
	private static final String URI_WHITEBOX = "urn:x-hp:eg/whiteBoxZX";

	private RDFModel model;
	private EolContext context;

	@Test
	public void loadInvalidModel() {

		try {
			loadModel(OWL_DEMO_DATAMODEL_INVALID);
		} catch (EolModelLoadingException e) {
			String sErrors = e.getMessage();
			assertTrue("The model loaded should FAIL validation (6 errors relating to BigName42 having 2 motherboards)",
					sErrors.contains("Error whilst loading model null: The loaded model is not valid or not clean"));
		}

	}

	@Test
	public void loadValidModel() throws EolModelLoadingException {
		try {
			loadModel(OWL_DEMO_DATAMODEL_VALID);
		} catch (EolModelLoadingException e) {
			String sErrors = e.getMessage();
			assertFalse("The model loaded should NOT FAIL validation",
					sErrors.contains("Error whilst loading model null: The loaded model is not valid or not clean"));
		}
	}

	// Functions not tests

	protected void loadModel(String dataModelUri) throws EolModelLoadingException {
		this.model = new RDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_DATA_URIS, dataModelUri);
		props.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		props.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_JENA); // There is a known issues in the model required for tests
		model.load(props);

		this.context = new EolContext();
	}

}
