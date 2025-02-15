package org.eclipse.epsilon.rdf.emf;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.epsilon.rdf.emf.config.RDFResourceConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

public class RDFGraphResourceImpl extends ResourceImpl {

	private RDFResourceConfiguration config;

	private Model rdfSchemaModel;
	private Model rdfDataModel;
	private OntModel rdfOntModel;

	private Map<EObject, Resource> eobToResource;

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		// The custom classloader constructor is needed for OSGi compatibility
		CustomClassLoaderConstructor constructor = new CustomClassLoaderConstructor(this.getClass().getClassLoader(), new LoaderOptions());
		this.config = new Yaml(constructor).loadAs(inputStream, RDFResourceConfiguration.class);

		eobToResource = new IdentityHashMap<>();
		loadRDFModels();
		deserializeObjects();
	}

	public Resource getRDFResource(EObject eob) {
		return eobToResource.get(eob);
	}

	protected void deserializeObjects() {
		for (NodeIterator it = rdfOntModel.listObjects(); it.hasNext(); ) {
			RDFNode node = it.next();
			if (node.isResource() && !node.isAnon()) {
				deserializeObject(node.asResource());
			}
		}
	}

	protected void deserializeObject(Resource node) {
		Set<EClass> eClasses = findMostSpecificEClasses(node);
		for (EClass eClass: eClasses) {
			deserializeObject(node, eClass);
		}
	}

	@SuppressWarnings("unchecked")
	protected void deserializeObject(Resource node, EClass eClass) {
		EObject eob = eClass.getEPackage().getEFactoryInstance().create(eClass);

		for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
			if (sf.isDerived() || sf.isTransient()) {
				continue;
			}

			Object value = deserializeProperty(node, sf);
			if (value instanceof Collection c) {
				((EList<Object>) eob.eGet(sf)).addAll(c);
			} else {
				eob.eSet(sf, value);
			}
		}

		if (eob.eContainer() == null) {
			getContents().add(eob);
		}
		eobToResource.put(eob, node);
	}

	@SuppressWarnings("unchecked")
	protected Object deserializeProperty(Resource node, EStructuralFeature sf) {
		String sfPackageURI = sf.getEContainingClass().getEPackage().getNsURI();

		List<Object> values = new ArrayList<>();
		for (StmtIterator itValue = node.listProperties(new PropertyImpl(sfPackageURI + "#", sf.getName())); itValue.hasNext(); ) {
			Statement stmt = itValue.next();
			
			Object deserialized = deserializeValue(stmt.getObject(), sf);
			if (deserialized instanceof Collection c) {
				values.addAll(c);
			} else if (deserialized != null) {
				values.add(deserialized);
			}
		}

		if (sf.isMany()) {
			return values;
		} else if (values.isEmpty()) {
			return null;
		} else {
			return values.iterator().next();
		}
	}

	private Object deserializeValue(RDFNode node, EStructuralFeature sf) {
		return node.visitWith(new RDFVisitor() {
			@Override
			public Object visitBlank(Resource r, AnonId id) {
				if (r.hasProperty(RDF.type, RDF.List)) {
					List<Object> values = new ArrayList<>();
					values.add(deserializeValue(r.getProperty(RDF.first).getObject(), sf));

					// TODO: check if Jena has a better API for collections.
					//
					// This is inefficient at the moment, as it's O(n^2) instead
					// of O(n) as it should be.
					RDFNode restNode = r.getProperty(RDF.rest).getObject();
					if (!RDF.nil.equals(restNode)) {
						Object convertedRest = deserializeValue(restNode, sf);
						if (convertedRest instanceof Collection<?> c) {
							values.addAll(c);
						} else {
							values.add(convertedRest);
						}
					}
					return values;
				}

				// TODO add support for containers
				return null;
			}

			@Override
			public Object visitURI(Resource r, String uri) {
				throw new UnsupportedOperationException("References not supported yet - consider proxies?");
			}

			@Override
			public Object visitLiteral(Literal l) {
				// TODO add resource option for language preference
				return l.getValue();
			}
			
		});
	}

	protected Set<EClass> findMostSpecificEClasses(Resource node) {
		Set<EClass> eClasses = new HashSet<>();

		for (StmtIterator it = node.listProperties(RDF.type); it.hasNext(); ) {
			RDFNode typeObject = it.next().getObject();
			if (typeObject.isAnon()) {
				continue;
			}

			String typeURI = typeObject.asResource().getURI();
			String[] parts = typeURI.split("#");
			if (parts.length == 2) {
				String nsURI = parts[0];
				String typeName = parts[1];

				Registry packageRegistry = this.getResourceSet().getPackageRegistry();
				EPackage ePackage = packageRegistry.getEPackage(nsURI);

				/* 
				 * NOTE: there may be URIs that don't correspond to any namespaces,
				 * such as the OWL or XML Schema ones. We skip them without raising
				 * errors.
				 */
				if (ePackage != null) {
					EClassifier eClassifier = ePackage.getEClassifier(typeName);
					if (eClassifier == null) {
						throw new NoSuchElementException(
							String.format("Cannot find type '%s' in EPackage with nsURI '%s'", typeName, nsURI));
					}

					if (eClassifier instanceof EClass newEClass) {
						for (Iterator<EClass> itEClass = eClasses.iterator(); itEClass.hasNext();) {
							EClass existingEClass = itEClass.next();
							if (existingEClass.isSuperTypeOf(newEClass)) {
								/*
								 * The new EClass is more specific than an existing one: remove the existing
								 * one.
								 */
								itEClass.remove();
							} else if (newEClass.isSuperTypeOf(existingEClass)) {
								// The new EClass is a supertype of an existing one: skip
								continue;
							}
						}
						eClasses.add(newEClass);
					}
				}
			}
		}

		return eClasses;
	}

	protected void loadRDFModels() throws IOException {
		this.rdfSchemaModel = ModelFactory.createDefaultModel();
		loadRDFModels(config.getSchemaModels(), rdfSchemaModel);

		this.rdfDataModel = ModelFactory.createDefaultModel();
		loadRDFModels(config.getDataModels(), rdfDataModel);

		InfModel infModel = ModelFactory.createRDFSModel(rdfSchemaModel, rdfDataModel);
		this.rdfOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF, infModel);
	}

	public void loadRDFModels(Set<String> uris, Model targetModel) throws IOException, MalformedURLException {
		for (String sURI : uris) {
			URI uri = URI.createURI(sURI);
			if (uri.isRelative()) {
				uri = uri.resolve(this.getURI());
			}

			/*
			 * NOTE: ideally we'd use a Jena StreamManager to handle this, but we can't
			 * because we would run into a conflict between the SLF4J used in Eclipse and
			 * the SLF4J bundled within Jena. It's simpler to just preprocess the platform
			 * URI here.
			 */
			if ("platform".equals(uri.scheme())) {
				String sFileURI = FileLocator.toFileURL(new URL(uri.toString())).toString();
				targetModel.read(sFileURI);
			} else {
				targetModel.read(uri.toString());
			}
		}
	}

	public RDFResourceConfiguration getConfig() {
		return config;
	}

	public void setConfig(RDFResourceConfiguration config) {
		this.config = config;
	}

}
