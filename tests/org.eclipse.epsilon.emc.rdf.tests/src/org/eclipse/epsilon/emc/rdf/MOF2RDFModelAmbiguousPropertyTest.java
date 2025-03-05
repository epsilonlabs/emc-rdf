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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.eclipse.epsilon.eol.execute.context.EolContext;
import org.eclipse.epsilon.eol.execute.introspection.IPropertyGetter;
import org.junit.Test;

public class MOF2RDFModelAmbiguousPropertyTest {

	private static final String TTL = "resources/ambiguous-name.ttl";

	@Test
	public void nameIsAmbiguous() throws Exception {
		try (MOF2RDFModel model = new MOF2RDFModel()) {
			model.setDataUri(TTL);
			model.load();

			RDFResource elem = model.getElementById("http://example.org/#green-goblin");
			IPropertyGetter pGetter = model.getPropertyGetter();

			EolContext context = new EolContext();
			ByteArrayOutputStream bOS = new ByteArrayOutputStream();
			context.setWarningStream(new PrintStream(bOS));
			Collection<?> names = (Collection<?>) pGetter.invoke(elem, "name", context);

			// We get both names, and a warning around ambiguity
			assertEquals("Both names should be reported", 2, names.size());

			String warningText = bOS.toString(StandardCharsets.UTF_8);
			assertTrue("A warning around an ambiguous property access should have been reported",
				warningText.toLowerCase().contains("ambiguous"));
			assertTrue("The FOAF prefix should be mentioned",
				warningText.contains("http://xmlns.com/foaf/0.1/"));
			assertTrue("The example prefix should be mentioned",
					warningText.contains("http://example.org/somethingElse/"));
		}
	}
	
}
