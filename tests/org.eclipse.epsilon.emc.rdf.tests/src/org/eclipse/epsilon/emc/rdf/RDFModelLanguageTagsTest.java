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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.execute.context.EolContext;
import org.eclipse.epsilon.eol.execute.introspection.IPropertyGetter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RDFModelLanguageTagsTest {
	
	private RDFModel model;
	private IPropertyGetter pGetter;
	private EolContext context;
	
	private static final String SPIDERMAN_MULTILANG_TTL = "resources/spiderman-multiLang.ttl";
	private static final String LANGUAGEPREFERENCE_STRING = "e n,en-us,123,ja,ru";
	
	private static final String SPIDERMAN_URI = "http://example.org/#spiderman";
	private static final String SPIDERMAN_NAME = "Spiderman";
	private static final String SPIDERMAN_NAME_RU = "Человек-паук";
	private static final String SPIDERMAN_NAME_JA = "スパイダーマン";
	
	private static final Set<String> SPIDERMAN_NAMES = new HashSet<>(Arrays.asList(SPIDERMAN_NAME, SPIDERMAN_NAME_RU, SPIDERMAN_NAME_JA));

	private static final String GREEN_GOBLIN_NAME = "Green Goblin";

	private static final Set<String> ALL_NAMES = new HashSet<>();
	private static final Set<String> ALL_NAMES_UNTAGGED = new HashSet<>(Arrays.asList(GREEN_GOBLIN_NAME, SPIDERMAN_NAME));
	static {
		ALL_NAMES.add(GREEN_GOBLIN_NAME);
		ALL_NAMES.addAll(SPIDERMAN_NAMES);
	}

	@Before
	public void setup () throws EolModelLoadingException {
		this.model = new RDFModel();
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_URIS, SPIDERMAN_MULTILANG_TTL);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGEPREFERENCE_STRING);
		model.load(props);
		
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
	public void modelDefaultLanguageTagProperty() throws Exception {
		String answer = "[" + LANGUAGEPREFERENCE_STRING.replaceAll("\\s", "") + "]";
		List<String> langList = model.getLanguagePreference();
		String langListString = model.getLanguagePreference().toString().replaceAll("\\s", "");		
		assertEquals(answer, langListString);
	}
	
	
	@Test
	public void modelLanguageTagValidator() throws Exception {
		// Test for different tag patterns
		assertTrue(RDFModel.isValidLanguageTag("en"));
		assertFalse(RDFModel.isValidLanguageTag("e n"));
		
		assertTrue(RDFModel.isValidLanguageTag("en-us"));
		assertFalse(RDFModel.isValidLanguageTag("e n-u s"));
		
		assertFalse(RDFModel.isValidLanguageTag("123"));
	}
	
	@Test
	public void getNamesWithoutPrefixWithPreferredLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "name", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}
	
	@Test
	public void getNamesWithPrefixAndPreferredLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}
	
	@Test
	public void getNameLiteralWithPrefixAndPreferredLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}

	@Test
	public void getNameLiteralWithoutPrefixAndPreferredLanguageTag() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}

	@Test
	public void getNamesWithoutPrefix() throws Exception {
		Set<String> names = new HashSet<>();
		for (RDFModelElement o : model.allContents()) {
			names.addAll((Collection<String>) pGetter.invoke(o, "name@", context));
		}
		assertEquals(ALL_NAMES_UNTAGGED, names);
	}
	
	@Test
	public void getNamesWithPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name@", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME), names);
	}

	@Test
	public void getNamesWithDoubleColonPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf::name@", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME), names);
	}

	@Test
	public void getNameLiteralsWithPrefix() throws Exception {
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name_literal@", context)) {
			names.add((String) l.getValue());
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME), names);
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
	
}