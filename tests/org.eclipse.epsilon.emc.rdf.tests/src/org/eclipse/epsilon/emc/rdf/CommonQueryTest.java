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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.execute.context.EolContext;
import org.eclipse.epsilon.eol.execute.introspection.IPropertyGetter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for the common querying behaviour across all RDF model types.
 */
@RunWith(Parameterized.class)
@SuppressWarnings("unchecked")
public class CommonQueryTest<T extends RDFModel> {
	private static final String SPIDERMAN_URI = "http://example.org/#spiderman";
	private static final String SPIDERMAN_NAME = "Spiderman";
	private static final String SPIDERMAN_NAME_RU = "Человек-паук";
	private static final Set<String> SPIDERMAN_NAMES = new HashSet<>(Arrays.asList(SPIDERMAN_NAME, SPIDERMAN_NAME_RU));

	private static final String GREEN_GOBLIN_URI = "http://example.org/#green-goblin";

	private static final Set<String> ALL_NAMES = new HashSet<>();
	private static final Set<String> ALL_PERSON_URIS = new HashSet<>(Arrays.asList(SPIDERMAN_URI, GREEN_GOBLIN_URI));
	static {
		ALL_NAMES.add("Green Goblin");
		ALL_NAMES.addAll(SPIDERMAN_NAMES);
	}

	private Class<T> modelClass;
	private T model;
	private IPropertyGetter pGetter;
	private EolContext context;

	@Parameters
	public static Object[][] params() {
		return new Object[][] {
			{ RDFModel.class },
			{ MOF2RDFModel.class }
		};
	}

	public CommonQueryTest(Class<T> modelClass) {
		this.modelClass = modelClass;
	}

	@Before
	public void setup() throws Exception {
		this.model = modelClass.getConstructor().newInstance();
		model.setDataUri("resources/spiderman.ttl");
		model.load();

		this.pGetter = model.getPropertyGetter();
		this.context = new EolContext();
		ByteArrayOutputStream errorBOS = new ByteArrayOutputStream();
		context.setWarningStream(new PrintStream(errorBOS));
	}

	@After
	public void teardown() {
		if (model != null) {
			model.dispose();
		}
	}

	@Test
	public void listAll() throws EolModelLoadingException {
		assertTrue("allContents should produce at least one element for the Green Goblin and Spiderman ", model.allContents().size() >= 2);
	}

	@Test
	public void getNamesWithoutPrefix() throws Exception {
		Set<String> names = new HashSet<>();
		for (RDFModelElement o : model.allContents()) {
			names.addAll((Collection<String>) pGetter.invoke(o, "name", context));
		}
		assertEquals("With no language preference and no tag, all values are returned", ALL_NAMES, names);
	}

	@Test
	public void getNamesWithPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name", context));
		assertEquals("With no language preference and no tag, all values are returned", SPIDERMAN_NAMES, names);
	}

	@Test
	public void getNamesWithDoubleColonPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf::name", context));
		assertEquals("With no language preference and no tag, all values are returned", SPIDERMAN_NAMES, names);
	}

	@Test
	public void getNameLiteralsWithPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals("With no language preference and no tag, all values are returned", SPIDERMAN_NAMES, names);
	}

	@Test
	public void getNamesWithoutPrefixWithLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "name@ru", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_RU), names);
	}

	@Test
	public void getNamesWithPrefixAndLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name@ru", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_RU), names);
	}

	@Test
	public void getEnemiesOfSpiderman() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		assertEquals("The getElementById and getElementId methods should match each other",
			SPIDERMAN_URI, model.getElementId(res));

		Set<String> uris = new HashSet<>();
		for (RDFResource r : (Collection<RDFResource>) pGetter.invoke(res, "rel:enemyOf", context)) {
			Object uri = pGetter.invoke(r, "uri", context);
			uris.add((String) uri);
		}

		assertEquals(Collections.singleton(GREEN_GOBLIN_URI), uris);
	}

	@Test
	public void getAllPeopleWithoutPrefix() throws Exception {
		Set<String> uris = new HashSet<>();
		for (Object o : model.getAllOfType("Person")) {
			uris.add((String) pGetter.invoke(o, "uri", context));
		}
		assertEquals(ALL_PERSON_URIS, uris);
	}

	@Test(expected=EolModelElementTypeNotFoundException.class)
	public void getAllInstancesOfMissingType() throws Exception {
		model.getAllOfType("NoSuchTypeExists");
	}

	@Test
	public void getAllPeopleWithPrefix() throws Exception {
		Set<String> uris = new HashSet<>();
		for (Object o : model.getAllOfType("foaf:Person")) {
			uris.add((String) pGetter.invoke(o, "uri", context));
		}
		assertEquals(ALL_PERSON_URIS, uris);
	}

	@Test
	public void getAllPeopleWithPrefixDoubleColon() throws Exception {
		Set<String> uris = new HashSet<>();
		for (Object o : model.getAllOfType("foaf::Person")) {
			uris.add((String) pGetter.invoke(o, "uri", context));
		}
		assertEquals(ALL_PERSON_URIS, uris);
	}

	@Test
	public void knownTypes() {
		assertTrue("The model should confirm that it knows the foaf:Person type", model.hasType("foaf:Person"));
		assertFalse("The model should deny that it knows the foaf:SomethingElse type", model.hasType("foaf:SomethingElse"));
	}

	@Test
	public void getPersonInformation() throws Exception {
		RDFModelElement firstPerson = model.getAllOfType("foaf:Person").iterator().next();
		assertTrue("The model should own the person", model.owns(firstPerson));
		assertFalse("The model should not own an unrelated object", model.owns(1234));
		assertEquals("The model should report the first type of the resource",
			"foaf:Person",
			model.getTypeNameOf(firstPerson));
		
		assertEquals("The model should only report the foaf Person, rdfs Resource and owl Thing",
				new HashSet<>(Arrays.asList("foaf:Person", "rdfs:Resource", "owl:Thing")),
				new HashSet<>(model.getAllTypeNamesOf(firstPerson)));		
	}

}
