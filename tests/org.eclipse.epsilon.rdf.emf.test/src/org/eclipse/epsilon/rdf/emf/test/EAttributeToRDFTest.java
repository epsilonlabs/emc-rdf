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
 package org.eclipse.epsilon.rdf.emf.test;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.URIHandlerImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;
import org.eclipse.epsilon.rdf.emf.RDFGraphResourceFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EAttributeToRDFTest {
	
	// TODO Changes for each type of EAttribute
	// TODO Changes to the order of Multi-value EAttributes (Not in this pull request)
	// TODO Add an XMI file that should represent the EMF Resource after the RDF has been changed
	
	static final boolean CONSOLE_OUTPUT_ACTIVE = false;
	
	
	@BeforeClass
	public static void setupDrivers() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("rdfres", new RDFGraphResourceFactory());
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());
	}
	
	private final File OriginalTTL = new File("resources/EAttribute_test/model_data.ttl.org");
	private final File WorkingTTL = new File("resources/EAttribute_test/model_data.ttl");
	
	private final File RDFGraphModel = new File("resources/EAttribute_test/model.rdfres");
	private final File EMFNativeModelBefore = new File("resources/EAttribute_test/model_before.xmi");
	private final File EMFNativeModelAfter = new File("resources/EAttribute_test/model_after.xmi");  // This file is generated _AFTER_ changing the RDFGraphResource
	private final File MetaModel = new File("resources/EAttribute_test/metamodel.emf");
	
	ResourceSet metaModel = null;
	ResourceSet rdf = null; 
	ResourceSet xmiBefore = null;
	ResourceSet xmiAfter = null;

	// Entity from the Model(s) used to test Attributes
	EObject rdfEntity = null;
	EObject xmiEntity = null;
	
	private String testType = "";
	
	@Before
	public void claimResources() throws IOException {
		copyFile(OriginalTTL, WorkingTTL);

		metaModel = getMetaModelResourceSet(MetaModel);
		rdf = getGraphResourceSet(RDFGraphModel, metaModel);
		xmiBefore = getXMIResourceSet(EMFNativeModelBefore, metaModel);
		
		rdfEntity = getRdfEntityForAttributeTest(rdf);
		xmiEntity = getRdfEntityForAttributeTest(xmiBefore);
	}

	@After
	public void releaseResources() throws IOException {			
		if(WorkingTTL.exists()) {
			Files.delete(WorkingTTL.toPath());
		}
		if(EMFNativeModelAfter.exists()) {
			Files.delete(EMFNativeModelAfter.toPath());
		}
		
		metaModel = null;
		rdf = null; 
		xmiBefore = null;
	}
	
	@Test
	public void baselineBeforeTest () throws IOException {
		// Check we can load the beforeXMI and that it matched the RDF version
		testType = "baselineBeforeTest";	
		equivalentModels("baselineBeforeTest : ", rdf , xmiBefore);
	}

	@Test
	public void eByteTest () throws IOException {
		testType = "eByte";	
		Byte b = 126;
		changeAndTest(b);
	}
	
	@Test
	public void eBooleanTest () throws IOException {
		testType = "eBoolean";
		changeAndTest((Boolean) false);
	}
	
	// TODO Add in all the other types using the simple test pattern (as above)
	
	public void reportConsoleAttributeState(String reportLabel) {
		if (CONSOLE_OUTPUT_ACTIVE) { 
			System.out.println(reportLabel);
			System.out.println(testType + " Feature ID : " + getEAttribute(rdfEntity, testType).getFeatureID());
			System.out.println(testType + " Value : " + rdfEntity.eGet(getEAttribute(rdfEntity, testType)));
		}
	}
	
	
	public void changeAndTest (Object value) throws IOException {
		reportConsoleAttributeState("[TEST] Before " + testType);
		
		if (CONSOLE_OUTPUT_ACTIVE) {
			System.out.println("[TEST] Change " + value.getClass().getTypeName() + " " + value);
		}
		
		rdfEntity.eSet(getEAttribute(rdfEntity, testType), value);
		xmiEntity.eSet(getEAttribute(xmiEntity, testType), value);
		
		rdf.getResources().get(0).save(null);
		saveBeforeXmi(EMFNativeModelAfter);
		
		xmiAfter = getXMIResourceSet(EMFNativeModelAfter, metaModel);
		rdf = getGraphResourceSet(RDFGraphModel, metaModel);
		
		equivalentModels(testType + " After", rdf , xmiAfter);
		
		reportConsoleAttributeState("[TEST] After " + testType);
	}
	
	
	public void saveBeforeXmi (File destinationFile) throws FileNotFoundException, IOException {
		if (destinationFile.exists()) {
			destinationFile.delete();
		}	
		if (destinationFile.createNewFile()) {
			try (OutputStream destinationStream = new FileOutputStream(destinationFile)) {
				xmiBefore.getResources().get(0).save(destinationStream, null);
				delaySeconds(1);
			}
		} else {
			System.err.println("Failed to save XMI : " + destinationFile.toPath().toString());
		}		
		
	}
		
	
	protected void copyFile(File source, File destination) throws FileNotFoundException, IOException {
		Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
		delaySeconds(1);
	}
	
	private void delaySeconds(int seconds) {
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
	
	protected EObject getRdfEntityForAttributeTest (ResourceSet rdf) {
		rdf.getResources().forEach(r -> System.out.println("\n RDF: " + r.getURI()) );
		Resource rdfResource = rdf.getResources().get(0);
		EObject rdfModel = rdfResource.getContents().get(0);
		EObject rdfEntity = rdfModel.eContents().get(0);
		return rdfEntity;
	}
	
	public EAttribute getEAttribute(EObject eObject, String AttributeName ) {		
		EList<EAttribute> attributes = eObject.eClass().getEAttributes();
		for (EAttribute eAttribute : attributes) {
			if(eAttribute.getName().equals(AttributeName)) {
				return eAttribute;
			}
		}		 
		return null;
	}
	
	protected void registerEPackages(ResourceSet rsMetamodels, ResourceSet rsTarget) {
		for (Resource rMetamodel : rsMetamodels.getResources()) {
			for (EObject eob : rMetamodel.getContents()) {
				if (eob instanceof EPackage epkg) {
					rsTarget.getPackageRegistry().put(epkg.getNsURI(), epkg);
				}
			}
		}
	}

	protected void loadFileIntoResourceSet(File file, ResourceSet rs) throws IOException {
		Resource resource = rs.createResource(URI.createFileURI(file.getAbsolutePath()));		
		resource.load(null);
		rs.getResources().add(resource);
	}
	
	
	protected ResourceSet getMetaModelResourceSet (File file) throws IOException {
		ResourceSet resourceSet = new ResourceSetImpl();
		loadFileIntoResourceSet(file, resourceSet);
		return resourceSet;
	}
	
	
	protected ResourceSet getGraphResourceSet(File file, ResourceSet rsMetamodels) throws IOException {
		ResourceSet rsRDF = new ResourceSetImpl();
		registerEPackages(rsMetamodels, rsRDF);
		loadFileIntoResourceSet(file, rsRDF);
		return rsRDF;
	}
	
	protected ResourceSet getXMIResourceSet(File file, ResourceSet rsMetamodels) throws IOException {
		if (file.exists()) {
			ResourceSet rsXmi = new ResourceSetImpl();
			registerEPackages(rsMetamodels, rsXmi);
			loadFileIntoResourceSet(file, rsXmi);
			return rsXmi;
		} else {
			System.err.println("Missing file : " + file.toPath().toString());
			return null;
		}
			
		
	}
	
	protected void assertNoDifferences(String testLabel, Comparison cmp) {
		if (cmp.getDifferences().isEmpty()) {
			return;
		}

		System.err.println("Differences were found in " + testLabel + ": ");
		for (Diff diff : cmp.getDifferences()) {
			System.err.println("* " + diff);
		}

		fail("Differences were reported: see error messages");
	}
	
	protected void equivalentModels (String testLabel, ResourceSet rsXmi, ResourceSet rsRDF) {
		EMFCompare compareEngine = EMFCompare.builder().build();
		final IComparisonScope scope = new DefaultComparisonScope(rsXmi, rsRDF, null);
		final Comparison cmp = compareEngine.compare(scope);
		assertNoDifferences(testLabel, cmp);
	}
}
