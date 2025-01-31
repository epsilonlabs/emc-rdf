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

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;

public class EclipseProtocolParser {
	
	private EclipseProtocolParser() {
		// This class is not meant to be instantiated
	}
	
	// Pushes a list of URLs through a process to turn any Platform:/ into File:/ 
	static public void processEclipsePlatformUrlsToFileUrls(List<String> urlList) throws IOException {
		for (int i = 0; i < urlList.size(); i++) {
			urlList.set(i, processPlatformURLtoFileUrl(urlList.get(i)));
		}
	}

	// A File:/ URL or relative path starting '/' or '.' is unchanged by this process, Platform:/ URLs become File:/ URLs
	// Attempts to resolve a String to a URI and then URL, then gets the File:/ URL and returns it
	static public String processPlatformURLtoFileUrl(String urlString) throws IOException {
		if (!urlString.startsWith("platform:")) {
			return urlString;
		}
		URL url = new URL(urlString);
		URL fileSystemPathUrl = FileLocator.toFileURL(url);
		return fileSystemPathUrl.toString();
	}
}
