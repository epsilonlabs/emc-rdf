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
package org.eclipse.epsilon.emc.rdf.dt.tests;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.junit.After;
import org.junit.Before;
public class EclipseProjectEnvTest {

/**
 * <p>
 * Base class for tests requiring an Eclipse IDE project and workbench environment.
 * </p>
 * 
 * <p>
 * Note: all tests based on this class must run as JUnit Plug-In tests, not as
 * regular tests, and the ui.workbench product (or Headless) needs to be run. We need a
 * working, open workbench for these tests.
 * </p>
 */
	
	private final int FILESYSTEM_SYNC_TIMEOUT_SECONDS = 10;
	
	private final String projectUrl;
	
	private IProject testProject;

	/**
	 * Creates a new project with this URL
	 * 
	 * @param projectUrl
	 *            Project URL to use for a project resource in Eclipse IDE Workbench that the test will use
	 */
	public EclipseProjectEnvTest(String projectUrl) {
		this.projectUrl = projectUrl;
	}

	@Before
	public void createTestProject() throws Exception {
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspaceRoot root = workspace.getRoot();
		
		testProject = root.getProject(projectUrl);
		if (testProject.exists()) {
			deleteTestProject();
		}
		testProject.create(null);
		testProject.open(null);		
	}

	@After
	public void deleteTestProject() throws Exception {
		testProject.delete(true, true, null);
		
		int count = 0;
		while (!testProject.isSynchronized(1)) {
			count = checkTimeOut(count, FILESYSTEM_SYNC_TIMEOUT_SECONDS,"Waiting for delete sync... ");
		}
	}
	
	public void copyIntoProject(String from, String to) throws Exception {
		IFile destFile=null; 
		try (InputStream source = new FileInputStream(from)) {
			destFile = testProject.getFile(new Path(to));
			createParentFolders(destFile);
			destFile.create(source, false, null);
		}
		if (null != destFile) {
			int count = 0;
			while (!destFile.isSynchronized(1)) {
				count = checkTimeOut(count, FILESYSTEM_SYNC_TIMEOUT_SECONDS,"Waiting for file sync");
			}
		}
	}
	
	private static void createParentFolders(IResource res) throws Exception {
		final IContainer parent = res.getParent();
		if (parent instanceof IFolder) {
			createParentFolders(parent);
		}
		if (res instanceof IFolder && !res.exists()) {
			((IFolder) res).create(false, true, null);
		}
	}
		
	// Delays 1 second, set limit to X seconds you want to wait
	private int checkTimeOut(int current, int limit, String errorLabel) throws Exception {
		System.out.println(" - " + errorLabel + " Time out: " + current + "/" + limit );
		if (current >= limit)
		{
			//System.err.println("Check time out error: " + errorLabel);
			throw new Exception("Check time out error: " + errorLabel);
		}
		delaySeconds(1);
		return ++current;
	}
	
	private void delaySeconds(int seconds) {
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
	
	public String getProjectUrl() {
		return projectUrl;
	}
	
	public String getTestProjectURIString() {
		return testProject.getLocationURI().toString();
	}

	public IProject getTestProject() {
		return testProject;
	}
}