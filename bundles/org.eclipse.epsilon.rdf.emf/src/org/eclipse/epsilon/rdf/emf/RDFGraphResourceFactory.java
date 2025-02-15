package org.eclipse.epsilon.rdf.emf;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Factory;

public class RDFGraphResourceFactory implements Factory {

	@Override
	public Resource createResource(URI uri) {
		RDFGraphResourceImpl impl = new RDFGraphResourceImpl();
		impl.setURI(uri);

		// define default option values here

		return impl;
	}

}
