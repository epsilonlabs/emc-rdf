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

import org.apache.jena.rdf.model.Resource;

public class MOF2RDFModel extends RDFModel {

	@Override
	protected MOF2RDFResource createResource(Resource aResource, RDFModel aModel) {
		return new MOF2RDFResource(aResource, aModel);
	}
}
