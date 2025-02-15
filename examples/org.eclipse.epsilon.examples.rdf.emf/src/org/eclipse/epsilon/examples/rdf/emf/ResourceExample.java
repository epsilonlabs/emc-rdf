package org.eclipse.epsilon.examples.rdf.emf;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Factory.Registry;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;
import org.eclipse.epsilon.rdf.emf.RDFGraphResourceImpl;

public class ResourceExample {

	public static void main(String[] args) throws Exception {
		final Registry factoryRegistry = Resource.Factory.Registry.INSTANCE;
		factoryRegistry.getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());

		// Load the Emfatic-based metamodels
		ResourceSet rsEmfatic = new ResourceSetImpl();
		Resource rBook = rsEmfatic.createResource(URI.createFileURI("models/book.emf"));
		rsEmfatic.getResources().add(rBook);
		rBook.load(null);
		EPackage pkgBook = (EPackage) rBook.getContents().get(0);

		Resource rFiction = rsEmfatic.createResource(URI.createFileURI("models/fiction.emf"));
		rsEmfatic.getResources().add(rFiction);
		rFiction.load(null);
		EPackage pkgFiction = (EPackage) rFiction.getContents().get(0);

		// Set up the resource set with the metamodels and load the RDF graph
		ResourceSet rs = new ResourceSetImpl();
		rs.getPackageRegistry().put(pkgFiction.getNsURI(), pkgFiction);
		rs.getPackageRegistry().put(pkgBook.getNsURI(), pkgBook);

		RDFGraphResourceImpl r = new RDFGraphResourceImpl();
		r.setURI(URI.createFileURI(args[0]));
		rs.getResources().add(r);
		r.load(null);

		for (TreeIterator<EObject> it = r.getAllContents(); it.hasNext(); ) {
			EObject eob = it.next();
			System.out.println(eob + " -> " + r.getRDFResource(eob));
		}
	}

}
