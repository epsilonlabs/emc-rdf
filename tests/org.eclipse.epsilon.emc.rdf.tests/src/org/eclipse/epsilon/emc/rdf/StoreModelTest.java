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
import org.junit.Test;

public class StoreModelTest {

	private static final String LANGUAGE_PREFERENCE_EN_STRING = "en";
	
	private static final String OWL_DEMO_FOLDER = "resources/OWL/";
	private static final String OWL_DEMO_DATAMODEL_INVALID = OWL_DEMO_FOLDER + "owlDemoData.ttl";
	private static final String OWL_DEMO_DATAMODEL_VALID = OWL_DEMO_FOLDER + "owlDemoData_valid.ttl";
	private static final String OWL_DEMO_SCHEMAMODEL = OWL_DEMO_FOLDER + "owlDemoSchema.ttl";

	private static final String SCRATCH_FOLDER_SHORT = "resources/storeTest";
	private static final String SCRATCH_FOLDER = "resources/storeTest/";
	private static final String SCRATCH_FOLDER_DATAMODEL_INVALID = SCRATCH_FOLDER + "owlDemoData.ttl";
	private static final String SCRATCH_FOLDER_DATAMODEL_VALID = SCRATCH_FOLDER + "owlDemoData_valid.ttl";

	private static final Path SCRATCH_PATH = Paths.get(SCRATCH_FOLDER);

	private static final String TEST_STRING = "#THIS_TEXT_SHOULD_VANISH";

	private RDFModel originalModel;

	private RDFModel copyModel;

	private RDFModel reloadCopyModel;
	
	@Before
	public void setup() throws IOException {
		originalModel = new RDFModel();
		copyModel = new RDFModel();
		reloadCopyModel = new RDFModel();
		checkScratchFolder();
	}

	@After
	public void postTest() throws IOException {
		originalModel.dispose();
		copyModel.dispose();
		reloadCopyModel.dispose();
		if (Files.exists(SCRATCH_PATH)) {
			clearScratchFolder();
		}
	}

	@Test
	public void testEnvFileSystemTest() throws IOException {
		Path source = Paths.get(OWL_DEMO_DATAMODEL_INVALID);
		Path destination = copyFileToScratchFolder(source);
		assertTrue("File copy failed", Files.exists(destination));
		clearScratchFolder();
		assertFalse("Clear Scratch Folder failed", Files.exists(destination));
	}

	@Test
	public void loadSaveNewLocation() throws EolModelLoadingException, IOException {

		loadOriginalModel();

		originalModel.store(SCRATCH_FOLDER);


		
		assertTrue("Missing file "+SCRATCH_FOLDER_DATAMODEL_VALID, 
				Files.exists(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID)));
		assertTrue("Missing file "+SCRATCH_FOLDER_DATAMODEL_INVALID, 
				Files.exists(Paths.get(SCRATCH_FOLDER_DATAMODEL_INVALID)));

		loadCopyModel();
		assertTrue("Size of the Original and Copy are not the same ",
				originalModel.allContents().size() == copyModel.allContents().size());
		
	}
	
	@Test
	public void loadSaveNewLocationBaduri() throws EolModelLoadingException, IOException {

		loadOriginalModel();

		originalModel.store(SCRATCH_FOLDER_SHORT);  // missing the last / which should get fixed
		
		assertTrue("Missing file "+SCRATCH_FOLDER_DATAMODEL_VALID, 
				Files.exists(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID)));
		assertTrue("Missing file "+SCRATCH_FOLDER_DATAMODEL_INVALID, 
				Files.exists(Paths.get(SCRATCH_FOLDER_DATAMODEL_INVALID)));
		
		loadCopyModel();
		assertTrue("Size of the Original and Copy are not the same ",
				originalModel.allContents().size() == copyModel.allContents().size());
		
	}
	
	@Test
	public void loadSaveOverwrite() throws IOException, EolModelLoadingException {		
		loadOriginalModel();				

		// Copy the model files to the Scratch Folder for test
		folderWalkCopy(OWL_DEMO_FOLDER, SCRATCH_FOLDER);

		loadCopyModel();
		assertTrue("Size of the Original and Copy are not the same ",originalModel.allContents().size() == copyModel.allContents().size());
		
		// Insert a comment at the end of both copied model files
		Files.writeString(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID),TEST_STRING,StandardOpenOption.APPEND);
		Files.writeString(Paths.get(SCRATCH_FOLDER_DATAMODEL_INVALID),TEST_STRING,StandardOpenOption.APPEND);
		String dmvFileContentBefore = Files.readString(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID));
		String dmiFileContentBefore = Files.readString(Paths.get(SCRATCH_FOLDER_DATAMODEL_INVALID));
		assertTrue("The test String comment was not added.",dmvFileContentBefore.contains(TEST_STRING));
		assertTrue("The test String comment was not added.",dmiFileContentBefore.contains(TEST_STRING));
		
		// Save the dataset and overwrite the copied model files.
		copyModel.store();

		// Check the comment on the end of the both files has been removed by the store()
		String dmvFileContentAfter = Files.readString(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID));
		String dmiFileContentAfter = Files.readString(Paths.get(SCRATCH_FOLDER_DATAMODEL_INVALID));
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
			fail("store() with no URIs should do nothing" + e);
		}
		
		try {
			originalModel.store(SCRATCH_FOLDER);
		} catch (Exception e) {
			fail("store(String location) with no URIs should do nothing" + e);
		}
		
		try (Stream<Path> walk = Files.walk(SCRATCH_PATH)) {
			assertEquals("Walk counted unexpected files/folders", 1, walk.count());
		}
	}


	@Test
	public void storeModelBeforeLoadingOrCreating () throws IOException {
		@SuppressWarnings("resource")
		RDFModel newModel = new RDFModel();	
		newModel.setDataUri(SCRATCH_FOLDER_DATAMODEL_VALID);
		newModel.store();
		Stream<Path> walk = Files.walk(SCRATCH_PATH);
		assertTrue("A data URI property is set, but the dataset should not contain a Named Model for it", 
				Files.notExists(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID)));
		assertEquals("Walk counted unexpected files/folders", 1, walk.count());
	}
	
	@Test
	public void storeLocationModelBeforeLoadingOrCreating () throws IOException {
		@SuppressWarnings("resource")
		RDFModel newModel = new RDFModel();	
		newModel.setDataUri(SCRATCH_FOLDER_DATAMODEL_VALID);
		newModel.store(SCRATCH_FOLDER);
		assertTrue("A data URI property is set, but the dataset should not contain a Named Model for it", 
				Files.notExists(Paths.get(SCRATCH_FOLDER_DATAMODEL_VALID)));

		try (Stream<Path> walk = Files.walk(SCRATCH_PATH)) {	
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
	
	private void reloadCopyModel() throws EolModelLoadingException {	
		StringProperties propsReloadCopyModel = new StringProperties();
		propsReloadCopyModel.put(RDFModel.PROPERTY_DATA_URIS, SCRATCH_FOLDER_DATAMODEL_VALID 
				+ "," + SCRATCH_FOLDER_DATAMODEL_INVALID);			
		propsReloadCopyModel.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		propsReloadCopyModel.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		propsReloadCopyModel.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_NONE);
		reloadCopyModel.load(propsReloadCopyModel);
	}

	private void loadCopyModel() throws EolModelLoadingException {
		StringProperties propsCopyModel = new StringProperties();
		propsCopyModel.put(RDFModel.PROPERTY_DATA_URIS, SCRATCH_FOLDER_DATAMODEL_VALID 
				+ "," + SCRATCH_FOLDER_DATAMODEL_INVALID);			
		propsCopyModel.put(RDFModel.PROPERTY_SCHEMA_URIS, OWL_DEMO_SCHEMAMODEL);
		propsCopyModel.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE, LANGUAGE_PREFERENCE_EN_STRING);
		propsCopyModel.put(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_NONE);
		copyModel.load(propsCopyModel);
	}

	private void checkScratchFolder() throws IOException {
		if (Files.notExists(SCRATCH_PATH)) {
			Files.createDirectory(SCRATCH_PATH);
		}
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
	
	private Path copyFileToScratchFolder(Path source) throws IOException {
		checkScratchFolder();
		Path destination = Paths.get(SCRATCH_FOLDER + source.getFileName());
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}

	private void deleteDirectoryAndFiles(Path deletePath) throws IOException {
		try (Stream<Path> walk = Files.walk(deletePath)) {
			walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}

	private void clearScratchFolder() throws IOException {
		if (Files.exists(SCRATCH_PATH)) {
			deleteDirectoryAndFiles(SCRATCH_PATH);
		}
	}
	
	private void listFilesToConsole(Path path) throws IOException {		
		try (Stream<Path> walk = Files.walk(path)) {
			System.out.println("--File list--");
			walk.forEach(p -> System.out.println(p));
			System.out.println("-------------");
		}
	}
}
