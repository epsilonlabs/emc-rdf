package org.eclipse.epsilon.rdf.emf.config;

import java.util.HashSet;
import java.util.Set;

public class RDFResourceConfiguration {

	private Set<String> dataModels = new HashSet<>();
	private Set<String> schemaModels = new HashSet<>();

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

	@Override
	public String toString() {
		return "RDFResourceConfiguration [dataModels=" + dataModels + ", schemaModels=" + schemaModels + "]";
	}

}
