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
package org.eclipse.epsilon.rdf.emf.config;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.epsilon.rdf.validation.RDFValidation.ValidationMode;

public class RDFResourceConfiguration {

	private Set<String> dataModels = new HashSet<>();
	private Set<String> schemaModels = new HashSet<>();
	private String validationMode = ValidationMode.NONE.getId();
	
	public Set<String> getDataModels() {
		return dataModels;
	}

	public void setDataModels(Set<String> dataModels) {
		this.dataModels = dataModels;
	}

	public Set<String> getSchemaModels() {
		return schemaModels;
	}

	public void setSchemaModels(Set<String> schemaModels) {
		this.schemaModels = schemaModels;
	}

	public String getValidationMode() {
		return validationMode;
	}
	
	public ValidationMode getRawValidationMode() {
		return ValidationMode.fromString(validationMode);
	}

	public void setValidationMode(String validationMode) {
		this.validationMode = validationMode;
	}
	
	@Override
	public String toString() {
		return "RDFResourceConfiguration [dataModels=" + dataModels + ", schemaModels=" + schemaModels + ", validationMode=" + validationMode + "]";
	}



}
