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
package org.eclipse.epsilon.emc.rdf.dt;

import org.eclipse.epsilon.emc.rdf.MOF2RDFModel;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;

public class EclipseMOF2RDFModel extends MOF2RDFModel {
	
	@Override
	protected void loadModel() throws EolModelLoadingException { 
		// Change any platform:/ URLs to file:/ URLs in these lists...
		try {
			EclipseProtocolParser.processEclipsePlatformUrlsToFileUrls(schemaURIs);
			EclipseProtocolParser.processEclipsePlatformUrlsToFileUrls(dataURIs);	
		} catch (Exception ex) {
			throw new EolModelLoadingException(ex, this);
		}
		
		// Call the RDFModel load as normal, no platform URLs are passed to Jena
		super.loadModel();
	}
	


}
