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
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Stream;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StoreModelTest {

	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";
	
	private static final String INVALID_FILENAME = "owlDemoData.ttl";
	private static final String VALID_FILENAME = "owlDemoData_valid.ttl";
	private static final String SCHEMAMODEL_FILENAME = "owlDemoSchema.ttl";
	
	private static final String OWL_DEMO_FOLDER = "resources/OWL/";
	private static final String OWL_DEMO_DATAMODEL_INVALID = OWL_DEMO_FOLDER + INVALID_FILENAME;
	private static final String OWL_DEMO_DATAMODEL_VALID = OWL_DEMO_FOLDER + VALID_FILENAME;
	private static final String OWL_DEMO_SCHEMAMODEL = OWL_DEMO_FOLDER + SCHEMAMODEL_FILENAME;

	private static final String TEST_STRING = "#THIS_TEXT_SHOULD_VANISH";

	private RDFModel originalModel;  // In resources/OWL (do not overwrite)

	private RDFModel copyModel; // In resources/storeTest

	private RDFModel reloadCopyModel; // Used for reloading the copyModel after store()
	
	@Rule
	public TemporaryFolder scratch = new TemporaryFolder();
	
	@Before
	public void setup() throws IOException {
		originalModel = new RDFModel();
		copyModel = new RDFModel();
		reloadCopyModel = new RDFModel();
	}

	@After
	public void postTest() throws IOException {
		originalModel.dispose();
		copyModel.dispose();
		reloadCopyModel.dispose();
	}
		
	@Test
	public void loadSaveNewLocation() throws EolModelLoadingException, IOException {

		String scratchFolder = scratch.getRoot().toString();
		
		loadOriginalModel();

		originalModel.store(scratchFolder + "/");
		
		checkScratchFiles(scratchFolder);

		loadCopyModel();
		
		assertTrue("Size of the Original and Copy are not the same ",
				originalModel.allContents().size() == copyModel.allContents().size());
		
	}
	
	@Test
	public void loadSaveNewLocationBaduri() throws EolModelLoadingException, IOException {

		String scratchFolder = scratch.getRoot().toString();
		
		loadOriginalModel();

		originalModel.store(scratchFolder);  // missing the last / which should get fixed
		
		checkScratchFiles(scratchFolder);
		
		loadCopyModel();
		
		assertTrue("Size of the Original and Copy are not the same ",
				originalModel.allContents().size() == copyModel.allContents().size());
		
	}
	
	@Test
	public void loadSaveOverwrite() throws IOException, EolModelLoadingException {		
		
		String scratchFolder = scratch.getRoot().toString() + "/";
		
		loadOriginalModel();				

		// Copy the model files to the Scratch Folder for test
		folderWalkCopy(OWL_DEMO_FOLDER, scratchFolder);

		loadCopyModel();
		
		assertTrue("Size of the Original and Copy are not the same ",originalModel.allContents().size() == copyModel.allContents().size());
		
		// Insert a comment at the end of both copied model files
		Files.writeString(Paths.get(scratchFolder + VALID_FILENAME),TEST_STRING,StandardOpenOption.APPEND);
		Files.writeString(Paths.get(scratchFolder + INVALID_FILENAME),TEST_STRING,StandardOpenOption.APPEND);
		String dmvFileContentBefore = Files.readString(Paths.get(scratchFolder + VALID_FILENAME));
		String dmiFileContentBefore = Files.readString(Paths.get(scratchFolder + INVALID_FILENAME));
		
		assertTrue("The test String comment was not added.",dmvFileContentBefore.contains(TEST_STRING));
		assertTrue("The test String comment was not added.",dmiFileContentBefore.contains(TEST_STRING));
		
		// Save the dataset and overwrite the copied model files.
		copyModel.store();

		// Check the comment on the end of the both files has been removed by the store()
		String dmvFileContentAfter = Files.readString(Paths.get(scratchFolder + VALID_FILENAME));
		String dmiFileContentAfter = Files.readString(Paths.get(scratchFolder + INVALID_FILENAME));
		
		assertFalse("The test String comment was not added.",dmvFileContentAfter.contains(TEST_STRING));
		assertFalse("The test String comment was not added.",dmiFileContentAfter.contains(TEST_STRING));
		
		// Check the stored models are the same size as the Copy before being stored.
		reloadCopyModel();
		
		// The Copy and the Reloaded Copy after a save should be the same size
		assertTrue("Size of the Copy and ReloadCopy are not the same ",
				copyModel.allContents().size() == reloadCopyModel.allContents().size());

	}
	
	@Test
	public void callStoreOnModelWithNoURIs() throws IOException {

		try {
			originalModel.store();
		} catch (Exception e) {
			fail("store() with no URIs should not generate an Exception" + e);
		}
		
		try {
			originalModel.store(scratch.getRoot().toString());
		} catch (Exception e) {
			fail("store(String location) with no URIs should not generate and Exception" + e);
		}
		
		try (Stream<Path> walk = Files.walk(scratch.getRoot().toPath())) {
			assertEquals("Walk counted unexpected files/folders", 1, walk.count());
		}
	}


	@Test
	public void storeModelBeforeLoadingOrCreating () throws IOException {
		@SuppressWarnings("resource")
		RDFModel newModel = new RDFModel();	
		
		String scratchFile = scratch.getRoot().toString() + VALID_FILENAME;
		
		newModel.setDataUri(scratchFile);
		
		newModel.store();
		
		assertTrue("A data URI property is set, but the dataset should not contain a Named Model for it", 
				Files.notExists(Paths.get(scratchFile)));
		
		try (Stream<Path> walk = Files.walk(scratch.getRoot().toPath())) {	
			assertEquals("Walk counted unexpected files/folders", 1 , walk.count());
		}
	}
	
	@Test
	public void storeLocationModelBeforeLoadingOrCreating () throws IOException {
		@SuppressWarnings("resource")
		RDFModel newModel = new RDFModel();	
		
		String scratchFolder = scratch.getRoot().toString();
		String scratchFile = scratch.getRoot().toString() + VALID_FILENAME;
		
		newModel.setDataUri(scratchFile);
		
		newModel.store(scratchFolder);

		assertTrue("A data URI property is set, but the dataset should not contain a Named Model for it", 
				Files.notExists(Paths.get(scratchFile)));

		try (Stream<Path> walk = Files.walk(scratch.getRoot().toPath())) {	
			assertEquals("Walk counted unexpected files/folders", 1 , walk.count());
		}
	}


//
// FUNCTIONS NOT TESTS
//

	private void loadOriginalModel() throws EolModelLoadingException {
		StringProperties propsOriginalModel = new StringProperties();
		propsOriginalModel.put(RDFModel.PROPERTY_DATA_URIS, OWL_DEMO_DATAMODEL_VALID 
				+ "," + OWL_DEMO_DATAMODEL_INVALID);			
		propsOriginalModel.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		propsOriginalModel.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		propsOriginalModel.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_NONE);
		originalModel.load(propsOriginalModel);
	}
	
	private StringProperties scratchCopyProperties() {
		String scratchDatamodelValidFile = scratch.getRoot().toString() + "/" + VALID_FILENAME;
		String scratchDatamodelInvalidFile = scratch.getRoot().toString() + "/" + INVALID_FILENAME;
		StringProperties scratchCopyProperties = new StringProperties();
		scratchCopyProperties.put(RDFModel.PROPERTY_DATA_URIS, scratchDatamodelValidFile 
				+ "," + scratchDatamodelInvalidFile);			
		scratchCopyProperties.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		scratchCopyProperties.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		scratchCopyProperties.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_NONE);
		
		return scratchCopyProperties;
	}
	
	private void loadCopyModel() throws EolModelLoadingException {
		copyModel.load(scratchCopyProperties());
	}
	
	private void reloadCopyModel() throws EolModelLoadingException {	
		reloadCopyModel.load(scratchCopyProperties());
	}

	public void checkScratchFiles (String scratchFolder) {	
		if(!scratchFolder.endsWith("/")) {
			scratchFolder = scratchFolder + "/";	
		}
		
		assertTrue("Missing file " + VALID_FILENAME, 
				Files.exists(Paths.get(scratchFolder + VALID_FILENAME)));
		assertTrue("Missing file " + INVALID_FILENAME, 
				Files.exists(Paths.get(scratchFolder + INVALID_FILENAME)));
	}
	
	private void folderWalkCopy (String sourceFolder, String destinationFolder) throws IOException {
		Path sourcePath = Paths.get(sourceFolder);
		Stream<Path> walk = Files.walk(sourcePath).sorted();
		Iterator<Path> itr = walk.iterator();
		
		while (itr.hasNext()) {
			Path path = itr.next();
			Path newPath = Paths.get(destinationFolder + path.getFileName());
			Files.copy(path,newPath, StandardCopyOption.REPLACE_EXISTING);
		}
		
	}
	
}
