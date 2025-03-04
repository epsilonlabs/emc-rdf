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

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.junit.Test;

public class RDFModelValidationTest {

	private static final String OWL_DEMO_DATAMODEL_INVALID = "resources/OWL/owlDemoData.ttl";
	private static final String OWL_DEMO_DATAMODEL_VALID = "resources/OWL/owlDemoData_valid.ttl";
	private static final String OWL_DEMO_SCHEMAMODEL = "resources/OWL/owlDemoSchema.ttl";

	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";

	private RDFModel model;

	@Test
	public void loadInvalidModel() {

		try {
			loadModel(OWL_DEMO_DATAMODEL_INVALID);
			fail("An exception was expected");
		} catch (EolModelLoadingException e) {
			String sErrors = e.getMessage();
			assertTrue("The model loaded should FAIL validation (6 errors relating to BigName42 having 2 motherboards)",
					sErrors.contains("not valid"));
		}
	}

	@Test
	public void loadValidModel() throws EolModelLoadingException {
		loadModel(OWL_DEMO_DATAMODEL_VALID);
	}

	// Functions not tests

	protected void loadModel(String dataModelUri) throws EolModelLoadingException {
		this.model = new RDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_DATA_URIS, dataModelUri);
		props.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		props.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_JENA_CLEAN);
		model.load(props);
	}

}
