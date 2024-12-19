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
	
	private static final String LANGUAGE_PREFERENCE_INVALID_STRING = "e n,en-us,123,ja,ru";
	private static final String LANGUAGE_PREFERENCE_JA_STRING = "e n,en-us,123,ja,ru";
	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";
	
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

	public void setupModel (String languagePreference) throws EolModelLoadingException {
		this.model = new RDFModel();
		//model.setUri("resources/spiderman.ttl");
		StringProperties props = new StringProperties();
		props.put(RDFModel.PROPERTY_URIS, SPIDERMAN_MULTILANG_TTL);
		props.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, languagePreference);	
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
	public void modelLanguageTagPropertyLoad() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_INVALID_STRING);
		String answer = "[" + LANGUAGE_PREFERENCE_INVALID_STRING.replaceAll("\\s", "") + "]";
		List<String> langList = model.getLanguagePreference();
		String langListString = model.getLanguagePreference().toString().replaceAll("\\s", "");		
		assertEquals(answer, langListString);
	}
	
	@Test
	public void modelLanguageTagPropertyLoadEMPTY() throws Exception {
		setupModel(null);
		assertTrue(model.getLanguagePreference().isEmpty());
	}
	
	
	@Test
	public void modelLanguageTagValidator() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		// Test for different tag patterns
		assertTrue(RDFModel.isValidLanguageTag("en"));
		assertFalse(RDFModel.isValidLanguageTag("e n"));
		
		assertTrue(RDFModel.isValidLanguageTag("en-us"));
		assertFalse(RDFModel.isValidLanguageTag("e n-u s"));
		
		assertFalse(RDFModel.isValidLanguageTag("123"));
	}
	
	@Test
	public void getAllContentsNamesWithoutPrefixNoPreferredLanguageTags() throws Exception {
		setupModel(null);
		Set<String> names = new HashSet<>();
		for (RDFModelElement o : model.allContents()) {
			names.addAll((Collection<String>) pGetter.invoke(o, "name", context));
		}
		assertEquals("With no language preference and no tag, all values return", ALL_NAMES, names);;
	}
	
	@Test
	public void getNamesWithoutPrefixNoPreferredLanguageTag() throws Exception {
		setupModel(null);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "name", context));
		assertEquals("With no language preference and no tag, all values return", SPIDERMAN_NAMES, names);
	}
	
	// EN preferred but not available
	
	@Test
	public void getNamesWithoutPrefixUsingPreferredLanguageTagEN() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_EN_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "name", context));
		assertEquals("Should return untagged when language preference cant be matched",Collections.singleton(SPIDERMAN_NAME), names);
	}
	
	@Test
	public void getNamesWithPrefixUsingPreferredLanguageTagEN() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_EN_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name", context));
		assertEquals("Should return untagged when language preference cant be matched",Collections.singleton(SPIDERMAN_NAME), names);
	}
	
	@Test
	public void getNameLiteralWithPrefixUsingPreferredLanguageTagEN() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_EN_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals("Should return untagged when language preference cant be matched",Collections.singleton(SPIDERMAN_NAME), names);
	}

	@Test
	public void getNameLiteralWithoutPrefixUsingPreferredLanguageTagEN() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_EN_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals("Should return untagged when language preference cant be matched",Collections.singleton(SPIDERMAN_NAME), names);
	}
	
	// JA preferred and available
	
	@Test
	public void getNamesWithoutPrefixUsingPreferredLanguageTagJA() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "name", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}
	
	@Test
	public void getNamesWithPrefixUsingPreferredLanguageTagJA() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}
	
	@Test
	public void getNameLiteralWithPrefixUsingPreferredLanguageTagJA() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}

	@Test
	public void getNameLiteralWithoutPrefixUsingPreferredLanguageTagJA() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "name_literal", context)) {
			names.add((String) l.getValue());
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME_JA), names);
	}

	// Empty Tag -- untagged
	
	@Test
	public void getNamesWithoutPrefixAndNoTag() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		Set<String> names = new HashSet<>();
		for (RDFModelElement o : model.allContents()) {
			names.addAll((Collection<String>) pGetter.invoke(o, "name@", context));
		}
		assertEquals(ALL_NAMES_UNTAGGED, names);
	}
	
	@Test
	public void getNamesWithPrefixAndNoTag() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name@", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME), names);
	}

	@Test
	public void getNamesWithDoubleColonPrefixAndNoTag() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf::name@", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME), names);
	}

	@Test
	public void getNameLiteralsWithPrefixAndNoTag() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>();
		for (RDFLiteral l : (Collection<RDFLiteral>) pGetter.invoke(res, "foaf:name_literal@", context)) {
			names.add((String) l.getValue());
		}
		assertEquals(Collections.singleton(SPIDERMAN_NAME), names);
	}

	// RU tag requested and available
	
	@Test
	public void getNamesWithoutPrefixWithRULanguageTag() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "name@ru", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_RU), names);
	}

	@Test
	public void getNamesWithPrefixAndRULanguageTag() throws Exception {
		setupModel(LANGUAGE_PREFERENCE_JA_STRING);
		RDFResource res = (RDFResource) model.getElementById(SPIDERMAN_URI);
		Set<String> names = new HashSet<>((Collection<String>) pGetter.invoke(res, "foaf:name@ru", context));
		assertEquals(Collections.singleton(SPIDERMAN_NAME_RU), names);
	}
	
}