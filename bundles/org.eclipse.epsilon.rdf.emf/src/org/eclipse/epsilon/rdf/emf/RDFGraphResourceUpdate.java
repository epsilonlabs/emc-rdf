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
package org.eclipse.epsilon.rdf.emf;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.rdf.emf.RDFGraphResourceImpl.MultiValueAttributeMode;

public class RDFGraphResourceUpdate {
	
	static final boolean CONSOLE_OUTPUT_ACTIVE = false;
	static private boolean SINGLE_MULTIVALUES_AS_STATEMENT = true;
	
	private boolean preferListsForMultiValues = false;
	private RDFDeserializer deserializer;
	private RDFGraphResourceImpl rdfGraphResource;
	
	public RDFGraphResourceUpdate(RDFDeserializer deserializer, RDFGraphResourceImpl rdfGraphResource, MultiValueAttributeMode multiValueMode) {
		this.deserializer = deserializer;
		this.rdfGraphResource = rdfGraphResource;
		this.preferListsForMultiValues = multiValueMode.equals(MultiValueAttributeMode.LIST);
	}

	//
	// Model Elements
	
	public void addModelElement() {
		
	}
	
	public void removeModelElement() {
		
	}	
	
	//
	// Generic Statement methods for EStructural Features
	
	private Literal createLiteral(Object value) {
		Literal object = null;
		if (value.getClass().equals(Date.class)) {
			Calendar c = Calendar.getInstance();
			c.setTime((Date) value);
			String date = DateTimeUtils.calendarToXSDDateTimeString(c);
			return ResourceFactory.createTypedLiteral(date, XSDDatatype.XSDdateTime);
		} else {
			return ResourceFactory.createTypedLiteral(value);
		}		
	}
	
	private RDFNode createValueRDFNode(Object value, Model model) {

		if(value instanceof Resource) {
			return (Resource) value;
		}
		
		if (value instanceof Literal) {
			return (Literal) value;
		}
		
		if(value instanceof EObject) {
			// get Resource for EObject from the model or make one
			Resource valueResource = rdfGraphResource.getRDFResource((EObject)value);
			if(null == valueResource) {			
				Resource newResource = addNewEObject(model, (EObject) value);
				if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Created a new Resource node: " + newResource);}
				return newResource;
			} else {
				return valueResource;
			}						
		} else {
			// Literal values
			return createLiteral(value);
		}
	}
	
	private Property createProperty(EStructuralFeature eStructuralFeature) {
		// PREDICATE
		String nameSpace = eStructuralFeature.getEContainingClass().getEPackage().getNsURI();
		String propertyURI = nameSpace + "#" + eStructuralFeature.getName();
		return ResourceFactory.createProperty(propertyURI);
	}
	
	private RDFNode getObjectRDFNode(EObject eObject, EStructuralFeature eStructuralFeature, Model model) {
		// SUBJECT
		Resource rdfNode = rdfGraphResource.getRDFResource(eObject);
		// PREDICATE
		Property property = createProperty(eStructuralFeature);
		// OBJECT
		if(model.contains(rdfNode, property)) {
			// TODO what happens when there are multiple statements? Are there any?
			if (model.listObjectsOfProperty(rdfNode, property).toList().size()>1) {System.err.println("getObjectRDFNode() there is more than one object");}
			
			return model.getProperty(rdfNode, property).getObject();
		} else {
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println(String.format(" %s RDF Node missing property %s : ", rdfNode, property));}
			return null;
		}
	}
	
	private Statement findEquivalentStatement(Model model, EObject eob, EStructuralFeature eStructuralFeature, Object value) { 
		return findEquivalentStatement(model, rdfGraphResource.getRDFResource(eob), eStructuralFeature, value);
	}
	
	
	private Statement findEquivalentStatement(Model model, Resource eobRes, EStructuralFeature eStructuralFeature, Object value) {		
		// Returns a statement for a value from similar model statements (Subject-Property) and the deserialized value matches
		List<Statement> matchedStatementList = new ArrayList<Statement>();
		
		Resource subject = eobRes;
		Property property = createProperty(eStructuralFeature);
		RDFNode object = (RDFNode) null;
		
		List<Statement> modelStatementList = model.listStatements(subject, property, object).toList();
		for (Statement modelStatement : modelStatementList) {
			Object deserialisedValue = deserializer.deserializeProperty(modelStatement.getSubject(), eStructuralFeature);
			if (Objects.equals(value, deserialisedValue)) {
				matchedStatementList.add(modelStatement);
			}			
		}
		
		if(matchedStatementList.isEmpty()) {
			return null;
		}
		
		// Warn if there is more than one statement matching
		if(matchedStatementList.size()>1) {
			StringBuilder statementList = new StringBuilder();
			for (Statement statement : modelStatementList) {
				statementList.append(String.format("\n - %s", statement));
			}
			System.err.println(
					String.format("Find equivalent statements method returned more than 1 statement. %s\n"
							,statementList));
		}
		return matchedStatementList.get(0); // Only return the first statement found
	}

	private Statement createStatement(EObject eObject, EStructuralFeature eStructuralFeature, Object value, Model model) {		
		Resource subject = rdfGraphResource.getRDFResource(eObject);		
		return createStatement(subject, eStructuralFeature, value, model);
	}
	
	
	private Statement createStatement(Resource eObjectResource, EStructuralFeature eStructuralFeature, Object value, Model model) {
		// A statement is formed as "subject–predicate–object"
		// SUBJECT
		Resource subject = eObjectResource;
		// PREDICATE
		Property property = createProperty(eStructuralFeature);
		// OBJECT
		RDFNode object = createValueRDFNode(value, model);
		return ResourceFactory.createStatement(subject, property, object);		
	}
	
	private String getNsPrefix (EObject eObject) {
		String namespacePrefix = null; 
		// TODO Add switch case here for different methods for creating NsPrefix.
		namespacePrefix = eObject.eClass().getEPackage().getNsURI(); // Name space based on EPackage prefix		
		System.out.println("nameSpace URI prefix: " + namespacePrefix );
		return namespacePrefix;
	}

	private String createEObjectURI(EObject eObject) {
		String namespacePrefix = getNsPrefix(eObject);
		// TODO Add switch case here for different URI generating methods.
		String eObjectName = EcoreUtil.generateUUID();  // This UUID is generated using Date and Time (now).
		
		String rdfURI = String.format("%s#%s", namespacePrefix,eObjectName);
		System.out.println("new eObject rdfURI: " + rdfURI);
		return rdfURI;
	}
	
	private Resource createEObjectRDFtypeResource(EObject eObject) {
		String namespacePrefix = getNsPrefix(eObject);
		
		String eObjectType = eObject.eClass().getName();
		
		String uri = String.format("%s#%s", namespacePrefix,eObjectType);
		System.out.println("new eObject Type rdfURI: " + uri);
		return ResourceFactory.createResource(uri);
	}
	
	
	
	
	private Resource createNewEObjectRootRDFstatement(EObject eObject, Model model) {
		
		// Make a URI		
		String uri = createEObjectURI(eObject);
		
		// Create the root Resource for the eObject		
		//Resource eObjectResource = ResourceFactory.createResource(uri);
		Resource eObjectResource = model.createResource(uri);
		
		// Make an RDF type statement for the eObject
		//Statement typeStmt = ResourceFactory.createStatement(eObjectResource, RDF.type, createEObjectRDFtypeResource(eObject));
		//model.add(typeStmt);
		model.createStatement(eObjectResource, RDF.type, eObjectResource);
		
		
		
		return eObjectResource;
	}

	private void createEStructuralFeatureStatements(EObject eObject, Model model, Resource eObjectResource) {
		// Make statements for EStructuralFeatures (Single-value/Multi-values)			
		EList<EStructuralFeature> eStructuralFeatureList = eObject.eClass().getEAllStructuralFeatures();
		for (EStructuralFeature eStructuralFeature : eStructuralFeatureList) {
			Object value = eObject.eGet(eStructuralFeature, true);
			System.out.println(String.format("%s %s %s %s", 
					eStructuralFeature.eClass().getName(),
					eStructuralFeature.getEType().getName(),
					eStructuralFeature.getName(),
					value));		
			if (null != value) {
				if (eStructuralFeature.isMany()) {
					addMultiValueEStructuralFeature(model, eObjectResource, eStructuralFeature, value, Notification.NO_INDEX);
				} else {
					newSingleValueEStructuralFeatureStatements(model, eObjectResource, eStructuralFeature, value);
				}
			}
		}
	}
	
	
	//
	// Containers statement operations
	
	private void checkAndRemoveEmptyContainers(Container container, EObject onEObject, EStructuralFeature eStructuralFeature) {
		Model model = container.asResource().getModel();

		if (1 == container.size() && SINGLE_MULTIVALUES_AS_STATEMENT) {
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("\n Removing container with 1 item replacing with statement: " + container);}
			RDFNode firstValue = container.iterator().next();
			container.removeProperties();
			model.remove(createStatement(onEObject, eStructuralFeature, container, model));
			model.add(createStatement(onEObject, eStructuralFeature, firstValue, model));
			return;
		}
		
		if (0 == container.size()) {
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("\n Removing empty container: " + container);}
			container.removeProperties();
			model.remove(createStatement(onEObject, eStructuralFeature, container, model));
			return;
		}
	}
	
	private void searchContainerAndRemoveValue(Object value, Container container, EStructuralFeature sf) {
		if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Remove from container: " + value);}

		List<Statement> containerPropertyStatements = container.listProperties().toList();
		Iterator<Statement> cpsItr = containerPropertyStatements.iterator();
		boolean done = false;
		while (!done) {
			if (cpsItr.hasNext()) {
				Statement statement = cpsItr.next();
				if (CONSOLE_OUTPUT_ACTIVE) {
					System.out.println(" statement check: " + statement);
				}
				Object deserializedValue = deserializer.deserializeValue(statement.getObject(), sf);
				if (value.equals(deserializedValue)) {
					if (CONSOLE_OUTPUT_ACTIVE) {
						System.out.println(" removing statement " + statement);
					}
					container.remove(statement);
					if (!sf.isUnique()) {
						done = true;
					}
				}
			} else {
				done = true;
			}
		}
	}

	private void removeFromContainer(Object values, Container container, EObject onEObject, EStructuralFeature eStructuralFeature) {
		if (CONSOLE_OUTPUT_ACTIVE) {reportContainer("Before remove", container);}
		
		EStructuralFeature sf = eStructuralFeature.eContainingFeature();
		if(values instanceof EList<?>) { 
			EList<?> valuesList = (EList<?>) values;
			valuesList.iterator().forEachRemaining(value -> 
				searchContainerAndRemoveValue(value, container, sf));
		} else {
			searchContainerAndRemoveValue(values, container, sf);
		}
		
		if (CONSOLE_OUTPUT_ACTIVE) {reportContainer("After remove", container);}
		
		// Check if container is empty (size 0), remove the blank node if true
		checkAndRemoveEmptyContainers(container, onEObject, eStructuralFeature);
	}
	
	private void removeFromSeq(Object value, Container container, EObject onEObject, EStructuralFeature eStructuralFeature) {
		removeFromContainer(value, container, onEObject, eStructuralFeature);
	}
	
	private void removeFromBag(Object value, Container container, EObject onEObject, EStructuralFeature eStructuralFeature) {
		removeFromContainer(value, container, onEObject, eStructuralFeature);
	}
	
	private void addToSequence(Object values, Seq container, int position) {
		Model model = container.getModel();
		if (CONSOLE_OUTPUT_ACTIVE) {reportContainer("Before add to container ", container);}

		if(values instanceof List) {			
			List<?> list = (List<?>) values;
			for (Object v : list) {
				++position;
				if (CONSOLE_OUTPUT_ACTIVE) {
					System.out.println(String.format("inserting: %s %s", position, v));
				}
				container.add(position, createValueRDFNode(v, model));
			}
		} else {
			// Assume values is a single value
			container.add(++position, createValueRDFNode(values, model));
		}

		if (CONSOLE_OUTPUT_ACTIVE) {reportContainer("After add to container ", container);}
	}

	private void addToBag(Object values, Bag container) {
		if (CONSOLE_OUTPUT_ACTIVE) {reportContainer("Before add to container ", container);}

		if(values instanceof Collection) {
			Collection<?> list = (Collection<?>) values;
			list.forEach(v -> container.add(v));
		} else {
			// Assume values is a single value
			container.add(values);
		}

		if (CONSOLE_OUTPUT_ACTIVE) {reportContainer("After add to container ", container);}
	}
	
	private Seq newSequence(Model model, EObject onEObject, EStructuralFeature eStructuralFeature) {
		Seq objectNode = model.createSeq();
		model.add(createStatement(onEObject, eStructuralFeature, objectNode, model));
		return objectNode;
	}
	
	private Bag newBag(Model model, EObject onEObject, EStructuralFeature eStructuralFeature) {
		Bag objectNode = model.createBag();
		model.add(createStatement(onEObject, eStructuralFeature, objectNode, model));
		return objectNode;
	}
	
	//
	// List statement operations	
	
	private void checkAndRemoveEmptyList(RDFList container, EObject onEObject, EStructuralFeature eStructuralFeature) {
		Model model = container.getModel();

		if(!container.isValid()) {
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Removing invalid (empty) container:" + container.asResource());}
			Statement stmtToRemove = createStatement(onEObject, eStructuralFeature, container.asResource(), model);
			model.remove(stmtToRemove);
			return;
		}
		
		if(1 == container.size() && SINGLE_MULTIVALUES_AS_STATEMENT) {
			// TODO convert list with 1 item to single statement
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Removing container with 1 item and making a statement:" + container.asResource());}
			RDFNode firstValue = container.iterator().next();
			container.removeList();
			model.remove(createStatement(onEObject, eStructuralFeature, container.asResource(), model));
			model.add(createStatement(onEObject, eStructuralFeature, firstValue, model));
			return;
		}
		
		
		if(container.isEmpty()) {
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Removing empty container:" + container.asResource());}
			container.removeList();
			model.remove (createStatement(onEObject, eStructuralFeature, container.asResource(), model));
			return;
		}
	}
	
	private void headSafeRemoveFromList(RDFList container, RDFNode rdfNode) {
		// Fixes the blank node for the list when value is removed from the head
		RDFList newContainer;
		newContainer = container.remove(rdfNode);
		if (!newContainer.equals(container)) {
			container.addProperty(RDF.first, RDF.nil);
			container.addProperty(RDF.rest, RDF.nil);
			if (!newContainer.isEmpty()) {
				container.setHead(newContainer.getHead());
				newContainer.removeList();
			} else {
				container.removeList();
			}
		}	
	}
	
	private void removeOneValueFromListHandleUnique(RDFList container, EStructuralFeature eStructuralFeature,
			RDFNode valueRDFNode) {
		if (eStructuralFeature.isUnique()) {
			while (container.isValid() && container.contains(valueRDFNode)) {
				headSafeRemoveFromList(container, valueRDFNode);
			}
		} else {
			headSafeRemoveFromList(container, valueRDFNode);
		}
	}
	
	private void removeOneValueFromList(Object value, RDFList container, EStructuralFeature eStructuralFeature) {
		if(value instanceof EObject && eStructuralFeature instanceof EReference) {		
			// References
			RDFNode valueRDFNode = rdfGraphResource.getRDFResource((EObject)value);
			removeOneValueFromListHandleUnique(container, eStructuralFeature, valueRDFNode);
			return;
		} else {	
			// Attributes (Literal values)
			ExtendedIterator<RDFNode> containerItr = container.iterator();
			while (containerItr.hasNext()) {
				RDFNode rdfNode = containerItr.next();
				Object deserializedValue = deserializer.deserializeValue(rdfNode, eStructuralFeature);
				if(value.equals(deserializedValue)) {
					if (CONSOLE_OUTPUT_ACTIVE) {
						System.out.println(String.format("removing: %s == %s", value , deserializedValue));
					}
					removeOneValueFromListHandleUnique(container, eStructuralFeature, rdfNode);
					return;
				}
			}
			return;
		}
	}
	
	private void removeFromList(Object values, RDFList container, EObject onEObject, EStructuralFeature eStructuralFeature, Model model) {
		RDFList originalContainer = container;
		if(values instanceof List<?> valueList) {
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println(String.format("list of values to remove: %s", valueList));}
			for (Object value : valueList) {
				removeOneValueFromList(value, container, eStructuralFeature);
			}
		} else {
			removeOneValueFromList(values, container, eStructuralFeature);
		}

		// Removing the head of a list will return a new list, statements need correcting
		if(!container.equals(originalContainer)) {
			System.err.println("Got a different container back: " + container.asNode() + " != " + originalContainer.asNode() );			
		}

		checkAndRemoveEmptyList(container, onEObject, eStructuralFeature);
	}
	
	private void addToList(Object values, RDFList container, int position, EStructuralFeature eStructuralFeature, EObject onEObject) {	
		if (CONSOLE_OUTPUT_ACTIVE) {
			System.out.println(String.format(
				"\n RDFList  ordered: %s position: %s size: %s",
				eStructuralFeature.isOrdered(), position, container.size()));
			reportRDFList("Before add to container ", container);
		}
		Model model = container.getModel();

		RDFList newList = createRDFListOnModel(values, model);

		// Un-ordered lists should be handed with these two methods
		if (container.isEmpty()) {
			// This should never happen, empty is handled elsewhere
			container.concatenate(newList);
			return;
		} 

		if (!eStructuralFeature.isOrdered()) {
			container.concatenate(newList);
			return;
		} 
		
		// Ordered lists that are not empty will be handled with the methods below.
		if(eStructuralFeature.isOrdered()) {
			if (container.size() == position) {
				// Append new list to existing list on model
				container.concatenate(newList);
				return;
			}

			if (0 == position) {
				model.remove(createStatement(onEObject, eStructuralFeature, container, model));
				model.add(createStatement(onEObject, eStructuralFeature, newList, model));
				newList.concatenate(container);				
				return;
			}
			
			// Split the existing list and insert the new list
			
			// EMF/Epsilon will complain if you try to add at a position beyond the size of the list
			int listIndex = 0;
			if (position > 0) {
				listIndex = position - 1;
			}

			if (CONSOLE_OUTPUT_ACTIVE) {
				System.out.println(String.format("\n [ORDERED insert] headNode: %s -- listIndex: %s -- posNode %s \n",
					container.getHead(), listIndex, container.get(listIndex)));
			}
			
			if (container.size() != listIndex) {
				System.err.println(
						String.format("Something is wrong with the list size. RDF list size: %s != EMF Index: %s",
								container.size(), listIndex) );
			}
			
			// Run down the list via RDF.rest to the node at the index position
			int i = 0;
			Resource insertAtNode = container;
			while (i < listIndex) {			
				Statement rest = insertAtNode.getProperty(RDF.rest);

				if(null != rest && rest.getObject().isResource()) {
					Resource point = rest.getObject().asResource();
					if (!RDF.nil.equals(point)) {
						insertAtNode = point;
						++i;
					} else {
						// The list is broken or shorter then expected if we have a null/RDF.nil rest
						break;
					}
				} else {
				//	System.err.println("This is not a resource: " + rest.getObject());
					break;
				}
			}
			
			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Insert at node: " + insertAtNode);}
			
			// Get the tail end of the current container list after the node we insert at.
			RDFList oldTail = insertAtNode.getProperty(RDF.rest).getList();
			
			// Cut off the tail end of the current container list
			insertAtNode.getProperty(RDF.rest).changeObject(RDF.nil);
			
			// Append the new values to the current container values
			container.concatenate(newList);

			if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("Old Tail: " + oldTail);}

			// Append the tail end of values we saved above from the original container lists
			container.concatenate(oldTail);
			
			return;
		}

		if (CONSOLE_OUTPUT_ACTIVE) {reportRDFList("After add to container ", container);}
	}
		
	private RDFList createRDFListOnModel (Object values, Model model) {
		List<RDFNode> rdfNodes = new ArrayList<RDFNode>();
		if(values instanceof List<?> valuesList) {
			valuesList.forEach(v -> rdfNodes.add(createValueRDFNode(v, model)));
		} else {
			rdfNodes.add(createValueRDFNode(values, model));
		}
		
		return model.createList(rdfNodes.iterator());
	}
	
	private RDFList newList(Model model, EObject onEObject, EStructuralFeature eStructuralFeature, Object values) {		
		RDFList objectNode = createRDFListOnModel(values, model);
		model.add(createStatement(onEObject, eStructuralFeature, objectNode, model));
		return objectNode;
	}
	
	//
	// EObject instance handling
	
	private void checkForOrphanedEObject(Model model, Object oldValue) {
		// oldValues that are EObject instances, can become orphans when the last EReference is removed.
		// However, these orphans could be added back on to the model if a new EReference is added. (if not cleared from memory)
		// Best method so far to check for Orphan EObjects is to check eContainer is not null.
		// If logging removed items, there needs to be an inverse of this method which removes orphans from the log on when references are added
		
		if (oldValue instanceof EObject) {
			EObject oldValueEObject = (EObject) oldValue;					
			Resource oldValueResource = rdfGraphResource.getRDFResource(oldValueEObject);
			
			if(EcoreUtil.getURI(oldValueEObject).toString().equals("#//")) {
				System.out.println(" [EObject to delete] - " + oldValueEObject);
			}
			
			if(null == oldValueEObject.eContainer() ) {			
				removeEObjectStatements(model, oldValueEObject, oldValueResource);			
			}
			
			// Now we need to update the Deserializer maps
			// Reload the RDF models or remove the entries
		}
	}

	private void removeEObjectStatements(Model model, EObject oldValueEObject, Resource oldValueResource) {
		System.out.println(" [EObject to delete] - " + oldValueEObject.eContainer());
		
		// RDF Type
		EClass oldValueEClass = oldValueEObject.eClass();
		String oldValueNameType = oldValueEClass.getName();
		
		// RDF name space
		EPackage oldValueEPackage = oldValueEClass.getEPackage();
		String oldValueNameSpace = oldValueEPackage.getNsURI();
		
		System.out.println(String.format("Remove RDF type: [ %s # %s ]", oldValueNameSpace, oldValueNameType));
		
		// Check how many RDF types this node has which may create multiple EObjects.
		List typeStmts = model.listObjectsOfProperty(oldValueResource, RDF.type).toList();
		
		if (1 == typeStmts.size()) {
			// If there is only one then simply clear all statements.
			
			// Delete all the statements from RDF where the old Value is Subject if it is not the Object of any other statements
			// We have to go to each data model and remove statements
			// This process should be run only when the Orphaned EObjects have been removed from memory and can't come back!
			
			List stmts = model.listStatements(null, null, oldValueResource).toList();
			if(stmts.isEmpty()) {
				//printModelToConsole(model, "BEFORE removing orphan EObject");
				System.out.println("orphan EObject:\n - " + oldValueEObject.hashCode() 
						+ "\n  - " + EcoreUtil.getIdentification(oldValueEObject)
						+ "\n  - " + oldValueResource.getURI());
				model.getResource(oldValueResource.getURI()).listProperties().forEach(s-> System.out.println(" -- " + s));
				model.removeAll(oldValueResource, null, null);
				//printModelToConsole(model, "AFTER removing orphan EObject");
			
			}
		} else {
			// If there is more than one type, then we need to prune only statements for the EObject.
			// Look at the EClass of the EObject and remove statements for each EStructuralFeature (recursively)
			// TODO We also need to not remove statements that overlap with EStructuralFeatures of other types when we have a dual-type RDF-EObject
			
			EList<EStructuralFeature> esfs = oldValueEObject.eClass().getEAllStructuralFeatures();
			for (EStructuralFeature esf : esfs) {
				if(esf.isMany()) {
					removeMultiEStructuralFeature(model, oldValueEObject, esf, null, oldValueEObject.eGet(esf));
				} else {
					removeSingleValueEStructuralFeatureStatements(model, oldValueEObject, esf, oldValueEObject.eGet(esf));
				}
			}
			
			// Then remove the type statement
			// Need to make a statement oldValueResource-RDF.type-eClass.getTypeName() (need to extend value to handle eClass to rdf encoding
			// See deserializer -> protected Set<EClass> findMostSpecificEClasses(Resource node)
			
			model.remove(oldValueResource, RDF.type, model.createLiteral(String.format("%s#%s", oldValueNameSpace, oldValueNameType )));
		}
	}
	
	
	private Resource addNewEObject(Model model, EObject eObject) {
		
		Resource eObjectRes = createNewEObjectRootRDFstatement(eObject, model);		
		
		// Update the deserializer maps
		deserializer.registerNewEObject(eObject, eObjectRes);
		
		createEStructuralFeatureStatements(eObject, model, eObjectRes);
		
		// Apply the notification Adapters
		eObject.eAdapters().add(new RDFGraphResourceNotificationAdapterTrace());
		eObject.eAdapters().add(new RDFGraphResourceNotificationAdapterChangeRDF());
		
		// TODO check the RDF statements and Model are in sync; did something fail when making the RDF?
		// The EObject has been made, and is has been added to a multi-value array in EMF; this Array might now have more values then an RDF container
		
		
		return eObjectRes;
	}
	

	//
	// Single-value Features operations
	
	public void newSingleValueEStructuralFeatureStatements(List<Resource> namedModelURIs, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue) {
		assert newValue != null : "new value must exist";

		// We default always to the first named model for a new statement.
		// In the future, we may use a rule-based system to decide which named model to use.
		List<Model> namedModelsToUpdate = rdfGraphResource.getNamedModels(namedModelURIs);
		for (Model model : namedModelsToUpdate) {
			newSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, newValue);
		}
	}
	
	public void newSingleValueEStructuralFeatureStatements(Model model, EObject onEObject,
			EStructuralFeature eStructuralFeature, Object newValue) {
		assert newValue != null : "new value must exist";
		Statement newStatement = createStatement(onEObject, eStructuralFeature, newValue, model);
		Statement existingStatements = findEquivalentStatement(model, onEObject, eStructuralFeature, newValue);

		if (!model.contains(newStatement) && null == existingStatements) {
			model.add(newStatement);
		} else {
			System.err.println(String.format("New statement already exists? : %s", newStatement));
		}

	}
	
	public void newSingleValueEStructuralFeatureStatements(Model model, Resource eobRes,
			EStructuralFeature eStructuralFeature, Object newValue) {
		assert newValue != null : "new value must exist";
		Statement newStatement = createStatement(eobRes, eStructuralFeature, newValue, model);
		Statement existingStatements = findEquivalentStatement(model, eobRes, eStructuralFeature, newValue);

		if (!model.contains(newStatement) && null == existingStatements) {
			model.add(newStatement);
		} else {
			System.err.println(String.format("New statement already exists? : %s", newStatement));
		}
	}
	
	public void removeSingleValueEStructuralFeatureStatements(List<Resource> namedModelURIs, EObject onEObject, EStructuralFeature eStructuralFeature, Object oldValue) {
		// Object type values set a new value "null", remove the statement the deserializer uses the meta-model so we won't have missing attributes
		assert oldValue != null : "old value must exist";		
		List<Model> namedModelsToUpdate = rdfGraphResource.getNamedModels(namedModelURIs);
		for (Model model : namedModelsToUpdate) {
			removeSingleValueEStructuralFeatureStatements(model,onEObject,eStructuralFeature,oldValue);
		}
	}
	
	public void removeSingleValueEStructuralFeatureStatements(Model model, EObject onEObject, EStructuralFeature eStructuralFeature, Object oldValue) {
		// Object type values set a new value "null", remove the statement the
		// deserializer uses the meta-model so we won't have missing attributes
		assert oldValue != null : "old value must exist";

		// try object-to-literal
		Statement oldStatement = createStatement(onEObject, eStructuralFeature, oldValue, model);
		if (model.contains(oldStatement)) {
			model.remove(oldStatement);
			// TODO handle removing the last reference to an EObject : remove orphan EObject
			checkForOrphanedEObject(model, oldValue);
			return;
		}
		
		// try literal-to-object
		Statement stmtToRemove = findEquivalentStatement(model, onEObject, eStructuralFeature, oldValue);
		if (stmtToRemove != null) {
			model.remove(stmtToRemove);
			// TODO handle removing the last reference to an EObject : remove orphan EObject
			checkForOrphanedEObject(model, oldValue);
			return;
		}
		
		// EAttribute has a default value no statements in the RDF to match
		if (oldValue.equals(eStructuralFeature.getDefaultValue())) {
			if (CONSOLE_OUTPUT_ACTIVE) {
				System.out.println(String.format(
						"Old statement not found, but the oldvalue matches the models default value, so there might not be a statement."
						+ "\n Remove - default value %s - old value %s ",
						eStructuralFeature.getDefaultValue(), oldValue));					
			}
			// TODO handle removing the last reference to an EObject : remove orphan EObject
			checkForOrphanedEObject(model, oldValue);
			return;
		}

		// Couldn't find old statement through either object-to-literal or
		// literal-to-object conversion, and there is no default value
		 
		System.err.println(String.format("Old statement not found during single removal: %s", oldStatement));
		return;
	}
	
	
	public void updateSingleValueEStructuralFeatureStatements(List<Resource> namedModelURIs, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue, Object oldValue) {
		assert oldValue != null : "old value must exist";
		assert newValue != null : "new value must exist";
		List<Model> namedModelsToUpdate = rdfGraphResource.getNamedModels(namedModelURIs);
		for (Model model : namedModelsToUpdate) { 
			updateSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, newValue, oldValue);			
		};
	}
	
	public void updateSingleValueEStructuralFeatureStatements(Model model, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue, Object oldValue) {
		// Remove any existing statements and add a new one
		removeSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, oldValue);
		newSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, newValue);
	}
	
	//
	// Multi-value Feature operations
	
	public void removeMultiEStructuralFeature (List<Resource> namedModelURIs, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue, Object oldValue) { 
		List<Model> namedModelsToUpdate = rdfGraphResource.getNamedModels(namedModelURIs);
		for (Model model : namedModelsToUpdate) {
			removeMultiEStructuralFeature(model, onEObject, eStructuralFeature, newValue, oldValue);
		}
	}
	
	public void removeMultiEStructuralFeature (Model model, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue, Object oldValue) {
		Resource onEObjectNode = rdfGraphResource.getRDFResource(onEObject);
		if (!onEObjectNode.hasProperty(createProperty(eStructuralFeature))) {
			System.err.println("Trying to remove a none existing RDFnode for a multivalue attribute");
		} else {
			// Exists on a model some where...
			RDFNode objectRDFNode = getObjectRDFNode(onEObject, eStructuralFeature, model);
			
			if(objectRDFNode.isLiteral()) {
				// A 1 multi-value exists as a statement with no container
				removeSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, oldValue);
				return;
			}
			
			if(objectRDFNode.isResource()) {
				Resource objectResource = objectRDFNode.asResource();
				// Try and the container from each model to be updated
				if ( (objectResource.hasProperty(RDF.rest) && objectResource.hasProperty(RDF.first)) 
						|| objectResource.hasProperty(RDF.type, RDF.List) ) {				
					RDFList list = model.getList(objectResource);
					list.setStrict(true);
					removeFromList(oldValue, list, onEObject, eStructuralFeature, model);
				} else if (objectResource.equals(RDF.nil)) {
					// Empty list
					System.err.println("Removing from Empty list");				
				} else if (objectResource.hasProperty(RDF.type, RDF.Bag)) {
					Bag bag = model.getBag(objectResource);
					removeFromBag(oldValue, bag, onEObject, eStructuralFeature);
				} else if (objectResource.hasProperty(RDF.type, RDF.Seq)) {
					Seq seq = model.getSeq(objectResource);
					removeFromSeq(oldValue, seq, onEObject, eStructuralFeature);
				} else {
					// The first item is might look like a single value EAttribute
					removeSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, oldValue);
				}
				
				// TODO handle removing the last reference to an EObject : remove orphan EObject
				checkForOrphanedEObject(model, oldValue);
				return;
			}
		}
	}
	
	
	public void addMultiValueEStructuralFeature (List<Resource> namedModelURIs, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue, Object oldValue, int position) { 
		List<Model> namedModelsToUpdate = rdfGraphResource.getNamedModels(namedModelURIs);
		for (Model model : namedModelsToUpdate) {
			addMultiValueEStructuralFeature(model, onEObject, eStructuralFeature, newValue, position);
		}
	}
	
	public void addMultiValueEStructuralFeature (Model model, Resource eobRes, EStructuralFeature eStructuralFeature, Object newValue, int position) {
		// TODO move multi-value processing to Resource
		Object object =  deserializer.getEObjects(eobRes);
		if(object instanceof EObject) {
			addMultiValueEStructuralFeature(model, eStructuralFeature, eStructuralFeature, newValue, position);
		}
	}
	
	public void addMultiValueEStructuralFeature (Model model, EObject onEObject, EStructuralFeature eStructuralFeature, Object newValue, int position) {
		// sequence (ordered), bag (unordered), list (ordered/unordered)
		
		Resource onEObjectNode = rdfGraphResource.getRDFResource(onEObject);
				
		// Work out if we are adding a NEW multi-value attribute with no existing RDF node.
		if(!onEObjectNode.hasProperty(createProperty(eStructuralFeature))) {
			// Does not exist anywhere so we need a NEW RDF representation			
			if(newValue instanceof List<?>) {
				if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("\n No existing container, multiple values added making container");}
				createContainerAndAdd(model, onEObject, eStructuralFeature, newValue, position, null);
			}else {
				if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("\n No existing container, first single value is a statement");}
				newSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, newValue);	
			}
		} else {
			// Exists on a model some where...
			RDFNode objectRDFNode = getObjectRDFNode(onEObject, eStructuralFeature, model);
			
			if(objectRDFNode.isLiteral()) {
				// There is a single value statement for the 1 multi-value
				if (CONSOLE_OUTPUT_ACTIVE) {System.out.println("\n No existing container, multiple values added making container");}
				createContainerAndAdd(model, onEObject, eStructuralFeature, newValue, position, objectRDFNode.asLiteral().getValue());		
			}
				
			if(objectRDFNode.isResource()) {
				Resource objectResource = objectRDFNode.asResource();
				// If we have one of these types, then we are updating an existing statement on a model
				if ( objectResource.hasProperty(RDF.type, RDF.List)
						|| (objectResource.hasProperty(RDF.rest) && objectResource.hasProperty(RDF.first))) {
					// Lists can be ordered or unique, both or none.
					RDFList list = model.getList(objectResource);
					addToList(newValue, list, position, eStructuralFeature, onEObject);
				} else if (objectResource.equals(RDF.nil)) {
					// List - Empty lists may be represented with an RDF.nil value
					Statement stmt = createStatement(onEObject, eStructuralFeature, objectResource, model);
					model.remove(stmt);
					newList(model, onEObject, eStructuralFeature, newValue);
				} else if (objectResource.hasProperty(RDF.type, RDF.Bag)) {
					Bag bag = model.getBag(objectResource);
					addToBag(newValue, bag);
				} else if (objectResource.hasProperty(RDF.type, RDF.Seq)) {
					Seq seq = model.getSeq(objectResource);
					addToSequence(newValue, seq, position);
				} else {
					createContainerAndAdd(model, onEObject, eStructuralFeature, newValue, position, objectResource);
				}
				return;
			}
			return;
		}
	}

	private void createContainerAndAdd(Model model, EObject onEObject, EStructuralFeature eStructuralFeature,
			Object newValue, int position, Object firstValue) {

		if(null != firstValue) {
			// There is a statement for the first value, with no container structure
			removeSingleValueEStructuralFeatureStatements(model, onEObject, eStructuralFeature, firstValue);
		}
		
		if (preferListsForMultiValues) {
			// List
			RDFList list = null;
			if(null != firstValue) {
				list = newList(model, onEObject, eStructuralFeature, firstValue);
				addToList(newValue, list, position, eStructuralFeature, onEObject);	
			} else {
				list = newList(model, onEObject, eStructuralFeature, newValue);	
			}
		} else {
			if (eStructuralFeature.isOrdered()) {
				// Sequence
				Seq sequence = newSequence(model, onEObject, eStructuralFeature);
				if(null != firstValue) {				
					addToSequence(firstValue, sequence , 0);
				}
				addToSequence(newValue, sequence, position);
			} else {
				// Bag
				Bag bag = newBag(model, onEObject, eStructuralFeature);
				if(null != firstValue) {
					addToBag(firstValue, bag);
				}
				addToBag(newValue, bag);				
			}				
		}		
	}
	
	
	//
	// Reporting (these could return formatted strings for logging or console use)

	private static void reportContainer(String label, Container container) {
		boolean hasType = container.hasProperty(RDF.type);
		if (hasType) {
			System.out.println(String.format("\n%s Containter: Type %s, Size %s", 
					label, container.getProperty(RDF.type), container.size()));
			container.iterator().forEach(i -> System.out.println("  * " + i));
		}
	}
	
	private static void reportRDFList (String label, RDFList container) {
		System.out.println(String.format("\n%s List: Strict %s, Size: %s", 
				label, container.getStrict(), container.size() ));
		
		Resource item = container;
		while (null != item) {
			Statement restStatement = item.getProperty(RDF.rest);
			Statement firstStatement = item.getProperty(RDF.first);
			
			if(null != restStatement) {
				System.out.println(String.format(" * RDFnode %s \n\t--> rest: %s \n\t--> first: %s ",
					item, restStatement.getObject(), firstStatement.getObject()));
				item = item.getProperty(RDF.rest).getResource();
			} else {
				item = null;
			}
		}
	}
	
	private static void printModelToConsole(Model model, String label) {
		System.out.println(String.format("\n %s \n", label));
		OutputStream console = System.out;
		model.write(console, "ttl");
		System.out.println("");
	}

}
