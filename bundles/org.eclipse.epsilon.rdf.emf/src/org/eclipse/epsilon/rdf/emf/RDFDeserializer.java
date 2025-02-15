package org.eclipse.epsilon.rdf.emf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * Maps RDF nodes to EClasses and EObjects.
 */
public class RDFDeserializer {

	private final Supplier<Registry> packageRegistry;

	private final Map<EObject, Resource> eobToResource = new IdentityHashMap<>();
	private final Multimap<Resource, EObject> resourceToEob = HashMultimap.create();

	public RDFDeserializer(Supplier<EPackage.Registry> packageRegistry) {
		this.packageRegistry = packageRegistry;
	}

	/**
	 * Populates the {@link #getEObjectToResourceMap()} from the contents of
	 * the {@code ontModel}.
	 */
	public void deserialize(OntModel ontologyModel) {
		// Phase 1: find all sources of an rdf:type edge
		for (ResIterator it = ontologyModel.listResourcesWithProperty(RDF.type); it.hasNext(); ) {
			Resource res = it.next();
			if (!res.isAnon()) {
				deserializeObjectAttributes(res);
			}
		}

		// Phase 2: set up cross-references
		for (Entry<Resource, EObject> entry : resourceToEob.entries()) {
			deserializeObjectReferences(entry.getKey(), entry.getValue());
		}
	}

	public Resource getRDFResource(EObject eob) {
		return eobToResource.get(eob);
	}

	public Collection<EObject> getEObjects(Resource resource) {
		return resourceToEob.get(resource);
	}

	public Map<EObject, Resource> getEObjectToResourceMap() {
		return Collections.unmodifiableMap(eobToResource);
	}

	protected EObject deserializeObjectAttributes(Resource node, EClass eClass) {
		EObject eob = eClass.getEPackage().getEFactoryInstance().create(eClass);
		eobToResource.put(eob, node);
		resourceToEob.put(node, eob);

		for (EAttribute sf : eClass.getEAllAttributes()) {
			if (sf.isDerived() || sf.isTransient()) {
				continue;
			}

			deserializeProperty(node, eob, sf);
		}

		return eob;
	}

	protected void deserializeObjectReferences(Resource resource, EObject eob) {
		for (EReference eRef : eob.eClass().getEAllReferences()) {
			if (eRef.isDerived() || eRef.isTransient()) {
				continue;
			}
			deserializeProperty(resource, eob, eRef);
		}
	}

	@SuppressWarnings("unchecked")
	protected void deserializeProperty(Resource node, EObject eob, EStructuralFeature sf) {
		Object value = deserializeProperty(node, sf);
		if (value instanceof Collection c) {
			((EList<Object>) eob.eGet(sf)).addAll(c);
		} else {
			eob.eSet(sf, value);
		}
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

	protected Object deserializeValue(RDFNode node, EStructuralFeature sf) {
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
				Collection<EObject> potentialTargets = resourceToEob.get(r);
				for (EObject target : potentialTargets) {
					if (sf.getEType().isInstance(target)) {
						return target;
					}
				}

				return null;
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

				EPackage ePackage = this.packageRegistry.get().getEPackage(nsURI);

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

	protected void deserializeObjectAttributes(Resource node) {
		Set<EClass> eClasses = findMostSpecificEClasses(node);
		for (EClass eClass: eClasses) {
			EObject eob = deserializeObjectAttributes(node, eClass);
			eobToResource.put(eob, node);
			resourceToEob.put(node, eob);
		}
	}

}
