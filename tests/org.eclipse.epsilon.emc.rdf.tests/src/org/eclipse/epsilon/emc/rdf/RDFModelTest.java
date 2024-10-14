package org.eclipse.epsilon.emc.rdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

@SuppressWarnings("unchecked")
public class RDFModelTest {
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

	private RDFModel model;
	private IPropertyGetter pGetter;
	private EolContext context;

	@Before
	public void setup() throws EolModelLoadingException {
		this.model = new RDFModel();
		model.setUri("resources/spiderman.ttl");
		model.load();

		this.pGetter = model.getPropertyGetter();
		this.context = new EolContext();
	}

	@After
	public void teardown() {
		if (model != null) {
			model.dispose();
		}
	}

	@Test
	public void listAll() throws EolModelLoadingException {
		assertEquals("allContents should produce one element per resource", 2, model.allContents().size());
	}

	@Test
	public void getNamesWithoutPrefix() throws Exception {
		Set<String> names = new HashSet<>();
		for (RDFModelElement o : model.allContents()) {
			for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(o, "name", context)) {
				names.add((String) pGetter.invoke(l, "value", context));
			}
		}
		assertEquals(ALL_NAMES, names);
	}

	@Test
	public void getNamesWithPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name", context)) {
			names.add((String) pGetter.invoke(l, "value", context));
		}
		assertEquals(SPIDERMAN_NAMES, names);
	}

	@Test
	public void getNamesWithoutPrefixWithLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "name@ru", context)) {
			names.add((String) pGetter.invoke(l, "value", context));
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME_RU), names);
	}

	@Test
	public void getNamesWithPrefixAndLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name@ru", context)) {
			names.add((String) pGetter.invoke(l, "value", context));
		}
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

		// This may be revised later, if generic types are introduced
		assertEquals("The model should only report the Person type for that person",
			Collections.singletonList("foaf:Person"),
			model.getAllTypeNamesOf(firstPerson));
	}

	@Test(expected=EolModelElementTypeNotFoundException.class)
	public void jenaDoesNotFetchRelatedVocabulary() throws Exception {
		// By itself, Jena will not fetch the related FOAF vocabulary referenced in the Turtles example
		model.getAllOfType("Class");
	}

}
