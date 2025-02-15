package org.eclipse.epsilon.rdf.emf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Maps RDF nodes to EClasses and EObjects.
 */
public class RDFDeserializer {

	private final Supplier<Registry> packageRegistry;

	public RDFDeserializer(Supplier<EPackage.Registry> packageRegistry) {
		this.packageRegistry = packageRegistry;
	}

	@SuppressWarnings("unchecked")
	public EObject deserializeObject(Resource node, EClass eClass) {
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

		return eob;
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
				throw new UnsupportedOperationException("References not supported yet - consider proxies?");
			}

			@Override
			public Object visitLiteral(Literal l) {
				// TODO add resource option for language preference
				return l.getValue();
			}
			
		});
	}

	public Set<EClass> findMostSpecificEClasses(Resource node) {
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

}
