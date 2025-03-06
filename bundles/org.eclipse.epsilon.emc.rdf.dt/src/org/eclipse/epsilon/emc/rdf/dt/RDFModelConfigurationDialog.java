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
package org.eclipse.epsilon.emc.rdf.dt;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.epsilon.common.dt.launching.dialogs.AbstractModelConfigurationDialog;
import org.eclipse.epsilon.common.dt.launching.dialogs.BrowseWorkspaceUtil;
import org.eclipse.epsilon.common.dt.util.DialogUtil;
import org.eclipse.epsilon.emc.rdf.RDFModel;
import org.eclipse.epsilon.emc.rdf.RDFModel.ValidationMode;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class RDFModelConfigurationDialog extends AbstractModelConfigurationDialog {
	private static final String[] RDFFILE_EXTENSIONS = new String[] { "*.rdf", "*.ttl", "*.nt", "*.nq", "*.trig", "*.owl", "*.jsonld", "*.trdf", "*.rt", "*.rpb", "*.pbrdf", "*.rj", "*.trix", "*.*"};

	private static final String SAMPLE_URL = "http://changeme";

	protected class PrefixEditingSupport extends EditingSupport {
		private final TableViewer viewer;
		private final TextCellEditor editor;

		public PrefixEditingSupport(TableViewer viewer) {
			super(viewer);
			this.viewer = viewer;
			this.editor = new TextCellEditor(viewer.getTable());
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return editor;
		}

		@Override
		protected boolean canEdit(Object element) {
			return element instanceof NamespaceMappingTableEntry;
		}

		@Override
		protected Object getValue(Object element) {
			return ((NamespaceMappingTableEntry) element).prefix;
		}

		@Override
		protected void setValue(Object element, Object value) {
			((NamespaceMappingTableEntry)element).prefix = String.valueOf(value);
			viewer.update(element, null);
			validateForm();
		}
	}

	protected class NamespaceURLEditingSupport extends EditingSupport {
		private final TableViewer viewer;
		private final TextCellEditor editor;

		public NamespaceURLEditingSupport(TableViewer viewer) {
			super(viewer);
			this.viewer = viewer;
			this.editor = new TextCellEditor(viewer.getTable());
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return editor;
		}

		@Override
		protected boolean canEdit(Object element) {
			return element instanceof NamespaceMappingTableEntry;
		}

		@Override
		protected Object getValue(Object element) {
			return ((NamespaceMappingTableEntry) element).url;
		}

		@Override
		protected void setValue(Object element, Object value) {
			((NamespaceMappingTableEntry)element).url = String.valueOf(value);
			viewer.update(element, null);
			validateForm();
		}
	}

	protected class URLEntryEditingSupport extends EditingSupport {
		private final TableViewer viewer;
		private final TextCellEditor editor;

		public URLEntryEditingSupport(TableViewer viewer) {
			super(viewer);
			this.viewer = viewer;
			this.editor = new TextCellEditor(viewer.getTable());
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return editor;
		}

		@Override
		protected boolean canEdit(Object element) {
			return element instanceof URLTableEntry;
		}

		@Override
		protected Object getValue(Object element) {
			return ((URLTableEntry) element).url;
		}

		@Override
		protected void setValue(Object element, Object value) {
			((URLTableEntry)element).url = String.valueOf(value);
			viewer.update(element, null);
			validateForm();
		}
	}

	protected class NamespaceMappingTableEntry {
		public String prefix, url;
	}

	private String getIFilePlatformAsUrlString(IFile file) {
		return "platform:/resource" + file.getFullPath().toPortableString();
	}
	
	protected class URLTableEntry {
		public URLTableEntry(String url) {
			this.url = url;
		}
		public String url;
	}

	private TableViewer dataModelUrlListViewer;
	private List<URLTableEntry> dataModelUrls = new ArrayList<>();

	private TableViewer schemaModelUrlListViewer;
	private List<URLTableEntry> schemaModelUrls = new ArrayList<>();
	
	private TableViewer nsMappingTable;
	private List<NamespaceMappingTableEntry> nsMappingEntries = new ArrayList<>();

	@Override
	protected String getModelName() {
		return "RDF Model";
	}

	@Override
	protected String getModelType() {
		return "RDF";
	}

	//
	// Ordering of Groups in Dialogue window
	
	@Override
	protected void createGroups(Composite control) {
		createNameAliasGroup(control);
		createDataModelRDFUrlsGroup(control);
		createSchemaModelRDFUrlsGroup(control);
		createNamespaceMappingGroup(control);
		createLanguagePreferenceGroup(control);
		createValidateModelGroup(control);
	}

	private Composite createNamespaceMappingGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Custom prefixes", 2);

		nsMappingTable = new TableViewer(groupContent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);

		TableViewerColumn prefixColumn = new TableViewerColumn(nsMappingTable, SWT.NONE);
		prefixColumn.getColumn().setText("Prefix");
		prefixColumn.getColumn().setWidth(150);
		prefixColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((NamespaceMappingTableEntry) element).prefix;
			}
		});
		prefixColumn.setEditingSupport(new PrefixEditingSupport(nsMappingTable));

		TableViewerColumn urlColumn = new TableViewerColumn(nsMappingTable, SWT.NONE);
		urlColumn.getColumn().setText("URL");
		urlColumn.getColumn().setWidth(400);
		urlColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((NamespaceMappingTableEntry) element).url;
			}
		});
		urlColumn.setEditingSupport(new NamespaceURLEditingSupport(nsMappingTable));

		nsMappingTable.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
		nsMappingTable.getTable().setHeaderVisible(true);
		nsMappingTable.getTable().setLinesVisible(true);

		nsMappingTable.setContentProvider(ArrayContentProvider.getInstance());
		nsMappingTable.setInput(nsMappingEntries);
		
		final Composite prefixesButtons = new Composite(groupContent, SWT.NONE);
		final GridData prefixesButtonsLayout = new GridData();
		prefixesButtonsLayout.horizontalAlignment = SWT.FILL;
		prefixesButtons.setLayoutData(prefixesButtonsLayout);
		prefixesButtons.setLayout(new FillLayout(SWT.VERTICAL));

		final Button addPrefixButton = new Button(prefixesButtons , SWT.NONE);
		addPrefixButton.setText("Add");
		addPrefixButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				NamespaceMappingTableEntry entry = new NamespaceMappingTableEntry();
				entry.prefix = "prefix";
				entry.url = SAMPLE_URL;
				nsMappingEntries.add(entry);
				nsMappingTable.refresh();
			}
		});

		final Button removePrefixButton = new Button(prefixesButtons, SWT.NONE);
		removePrefixButton.setText("Remove");
		removePrefixButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (nsMappingTable.getSelection() instanceof IStructuredSelection) {
					final IStructuredSelection sel = (IStructuredSelection)nsMappingTable.getSelection();
					for (Iterator<?> it = sel.iterator(); it.hasNext(); ) {
						nsMappingEntries.remove(it.next());
					}
					nsMappingTable.refresh();
				}
			}
		});

		final Button clearPrefixesButton = new Button(prefixesButtons, SWT.NONE);
		clearPrefixesButton.setText("Clear");
		clearPrefixesButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				nsMappingEntries.clear();
				nsMappingTable.refresh();
			}
		});

		groupContent.layout();
		groupContent.pack();
		return groupContent;
	}

	private String dataModelLastPath = null;
	private Composite createDataModelRDFUrlsGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Data Model URLs to load", 2);

		dataModelUrlListViewer = new TableViewer(groupContent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);

		TableViewerColumn urlColumn = new TableViewerColumn(dataModelUrlListViewer, SWT.NONE);
		urlColumn.getColumn().setWidth(800);
		urlColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((URLTableEntry) element).url;
			}
		});
		urlColumn.setEditingSupport(new URLEntryEditingSupport(dataModelUrlListViewer));
		
		dataModelUrlListViewer.setContentProvider(ArrayContentProvider.getInstance());
		dataModelUrlListViewer.setInput(dataModelUrls);

		GridData urlListLayout = new GridData(SWT.FILL, SWT.FILL, true, true);
		dataModelUrlListViewer.getControl().setLayoutData(urlListLayout);

		final Composite urlButtons = new Composite(groupContent, SWT.NONE);
		final GridData urlButtonsLayout = new GridData();
		urlButtonsLayout.horizontalAlignment = SWT.FILL;
		urlButtons.setLayoutData(urlButtonsLayout);
		urlButtons.setLayout(new FillLayout(SWT.VERTICAL));

		final Button addUrlButton = new Button(urlButtons, SWT.NONE);
		addUrlButton.setText("Add");
		addUrlButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dataModelUrls.add(new URLTableEntry(SAMPLE_URL));
				dataModelUrlListViewer.refresh();
			}
		});

		final Button addFromWorkspaceButton = new Button(urlButtons, SWT.NONE);
		addFromWorkspaceButton.setText("Browse Workspace...");
		addFromWorkspaceButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				IFile file = BrowseWorkspaceUtil.browseFile(getShell(),
						"Browse workspace", "Select file with RDF content", "*.rdf", null);				
				if (file != null) {
					dataModelUrls.add(new URLTableEntry(getIFilePlatformAsUrlString(file)));
					dataModelUrlListViewer.refresh();
				}
			}
		});
				
		final Button addFromFileSystemButton = new Button(urlButtons, SWT.NONE);
		addFromFileSystemButton.setText("Browse Filesystem...");
		addFromFileSystemButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
				fileDialog.setText("Select an RDF file to add");
				fileDialog.setFilterExtensions(RDFFILE_EXTENSIONS);
				if (dataModelLastPath != null)
					fileDialog.setFilterPath(dataModelLastPath);

				String selectedFile = fileDialog.open();
				if (selectedFile != null) {
					dataModelUrls.add(new URLTableEntry("file:" + selectedFile));
					dataModelUrlListViewer.refresh();
				}
				dataModelLastPath = fileDialog.getFilterPath();
			}
		});
		
		final Button removeUrlButton = new Button(urlButtons, SWT.NONE);
		removeUrlButton.setText("Remove");
		removeUrlButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (dataModelUrlListViewer.getSelection() instanceof IStructuredSelection) {
					final IStructuredSelection sel = (IStructuredSelection)dataModelUrlListViewer.getSelection();
					for (Iterator<?> it = sel.iterator(); it.hasNext(); ) {
						dataModelUrls.remove(it.next());
					}
					dataModelUrlListViewer.refresh();
					validateForm();
				}
			}
		});

		final Button clearUrlButton = new Button(urlButtons, SWT.NONE);
		clearUrlButton.setText("Clear");
		clearUrlButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dataModelUrls.clear();
				dataModelUrlListViewer.refresh();
				validateForm();
			}
		});

		groupContent.layout();
		groupContent.pack();
		return groupContent;
	}

	private String schemaModelLastPath = null;
	private Composite createSchemaModelRDFUrlsGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Schema Model URLs to load", 2);

		schemaModelUrlListViewer = new TableViewer(groupContent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);

		TableViewerColumn urlColumn = new TableViewerColumn(schemaModelUrlListViewer, SWT.NONE);
		urlColumn.getColumn().setWidth(800);
		urlColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((URLTableEntry) element).url;
			}
		});
		urlColumn.setEditingSupport(new URLEntryEditingSupport(schemaModelUrlListViewer));
		
		schemaModelUrlListViewer.setContentProvider(ArrayContentProvider.getInstance());
		schemaModelUrlListViewer.setInput(schemaModelUrls);

		GridData urlListLayout = new GridData(SWT.FILL, SWT.FILL, true, true);
		schemaModelUrlListViewer.getControl().setLayoutData(urlListLayout);

		final Composite urlButtons = new Composite(groupContent, SWT.NONE);
		final GridData urlButtonsLayout = new GridData();
		urlButtonsLayout.horizontalAlignment = SWT.FILL;
		urlButtons.setLayoutData(urlButtonsLayout);
		urlButtons.setLayout(new FillLayout(SWT.VERTICAL));

		final Button addUrlButton = new Button(urlButtons, SWT.NONE);
		addUrlButton.setText("Add");
		addUrlButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				schemaModelUrls.add(new URLTableEntry(SAMPLE_URL));
				schemaModelUrlListViewer.refresh();
			}
		});

		final Button addFromWorkspaceButton = new Button(urlButtons, SWT.NONE);
		addFromWorkspaceButton.setText("Browse Workspace...");
		addFromWorkspaceButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IFile file = BrowseWorkspaceUtil.browseFile(getShell(),
						"Browse workspace", "Select file with RDF content", "*.rdf", null);

				if (file != null) {
					schemaModelUrls.add(new URLTableEntry(getIFilePlatformAsUrlString(file)));
					schemaModelUrlListViewer.refresh();
				}
			}
		});
		
		final Button addFromFileSystemButton = new Button(urlButtons, SWT.NONE);
		addFromFileSystemButton.setText("Browse Filesystem...");
		addFromFileSystemButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
				fileDialog.setText("Select an RDF file to add");
				fileDialog.setFilterExtensions(RDFFILE_EXTENSIONS);
				if (schemaModelLastPath != null)
					fileDialog.setFilterPath(schemaModelLastPath);

				String selectedFile = fileDialog.open();
				if (selectedFile != null) {
					schemaModelUrls.add(new URLTableEntry("file:" + selectedFile));
					schemaModelUrlListViewer.refresh();
				}
				schemaModelLastPath = fileDialog.getFilterPath();
			}
		});
		
		final Button removeUrlButton = new Button(urlButtons, SWT.NONE);
		removeUrlButton.setText("Remove");
		removeUrlButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (schemaModelUrlListViewer.getSelection() instanceof IStructuredSelection) {
					final IStructuredSelection sel = (IStructuredSelection)schemaModelUrlListViewer.getSelection();
					for (Iterator<?> it = sel.iterator(); it.hasNext(); ) {
						schemaModelUrls.remove(it.next());
					}
					schemaModelUrlListViewer.refresh();
					validateForm();
				}
			}
		});

		final Button clearUrlButton = new Button(urlButtons, SWT.NONE);
		clearUrlButton.setText("Clear");
		clearUrlButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				schemaModelUrls.clear();
				schemaModelUrlListViewer.refresh();
				validateForm();
			}
		});

		groupContent.layout();
		groupContent.pack();
		return groupContent;
	}

	
	protected Label languagePreferenceLabel;
	protected Text languagePreferenceText;
	private Composite createLanguagePreferenceGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Language tag preference", 1);
		
		languagePreferenceLabel = new Label(groupContent, SWT.NONE);
		languagePreferenceLabel.setText("Comma-separated preferred language tags, in descending priority:");
		
		languagePreferenceText = new Text(groupContent, SWT.BORDER);
		languagePreferenceText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));	
		languagePreferenceText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent event) {
				validateForm();
			}
		});
		
		groupContent.layout();
		groupContent.pack();
		return groupContent;
	}
	
	
	protected Combo validateModelCombo;
	private Composite createValidateModelGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Model validation", 1);

		validateModelCombo = new Combo(groupContent,SWT.READ_ONLY);
		
		ArrayList<String> modes = new ArrayList<String>();
		for (ValidationMode mode : RDFModel.ValidationMode.values()) {
			validateModelCombo.add(mode.getId());
		}		
		
		if(validateModelCombo.getText().isBlank()) {
			validateModelCombo.setText(RDFModel.VALIDATION_SELECTION_DEFAULT.getId());
		}

		groupContent.layout();
		groupContent.pack();
		return groupContent;
	}
	
	@Override	
	protected void loadProperties(){
		super.loadProperties();
		if (properties == null) return;

		dataModelUrls.clear();
		for (String url : properties.getProperty(RDFModel.PROPERTY_DATA_URIS).split("\\s*,\\s*")) {
			if (url.length() > 0) {
				dataModelUrls.add(new URLTableEntry(url));
			}
		}
		
		schemaModelUrls.clear();
		for (String url : properties.getProperty(RDFModel.PROPERTY_SCHEMA_URIS).split("\\s*,\\s*")) {
			if (url.length() > 0) {
				schemaModelUrls.add(new URLTableEntry(url));
			}
		}

		nsMappingEntries.clear();
		for (String entry : properties.getProperty(RDFModel.PROPERTY_PREFIXES).split("\\s*,\\s*")) {
			if (entry.length() > 0) {
				final int idxEquals = entry.indexOf('=');
				if (idxEquals != -1) {
					NamespaceMappingTableEntry nsEntry = new NamespaceMappingTableEntry();
					nsEntry.prefix = entry.substring(0, idxEquals);
					nsEntry.url = entry.substring(idxEquals + 1);
					nsMappingEntries.add(nsEntry);
				}
			}
		}
		
		languagePreferenceText.setText(properties.getProperty(RDFModel.PROPERTY_LANGUAGE_PREFERENCE));
		
		// Load any saved property and default to Jena if none
		validateModelCombo.setText(
				properties.getProperty(RDFModel.PROPERTY_VALIDATE_MODEL, RDFModel.VALIDATION_SELECTION_DEFAULT.getId()));
		
		this.dataModelUrlListViewer.refresh();
		this.schemaModelUrlListViewer.refresh();
		this.nsMappingTable.refresh();
		validateForm();
	}
	 
	@Override
	protected void storeProperties(){
		super.storeProperties();

		properties.put(RDFModel.PROPERTY_DATA_URIS,
			String.join(",", dataModelUrls.stream()
				.map(e -> e.url)
				.collect(Collectors.toList())));
		
		properties.put(RDFModel.PROPERTY_SCHEMA_URIS,
				String.join(",", schemaModelUrls.stream()
					.map(e -> e.url)
					.collect(Collectors.toList())));

		properties.put(RDFModel.PROPERTY_PREFIXES,
			String.join(",", nsMappingEntries.stream()
				.map(e -> e.prefix + "=" + e.url)
				.collect(Collectors.toList())));
		
		properties.put(RDFModel.PROPERTY_LANGUAGE_PREFERENCE,
				languagePreferenceText.getText().replaceAll("\\s", ""));
		
		properties.put(RDFModel.PROPERTY_VALIDATE_MODEL, validateModelCombo.getText());
		
	}

	protected void validateForm() {
		String text = languagePreferenceText.getText().strip();
		if (text.length() > 0) {
			Set<String> invalidTags = new HashSet<>();
			for (String tag : text.split(",")) {
				if (!RDFModel.isValidLanguageTag(tag)) {
					invalidTags.add(tag);
				}
			}
			if (!invalidTags.isEmpty()) {
				setErrorMessage(String.format(
					"Invalid tags: %s", String.join(" ", invalidTags)));
				return;
			}
		}

		for (URLTableEntry entry : this.dataModelUrls) {
			String errorMessage = validateURL(entry.url);
			if (errorMessage != null) {
				setErrorMessage(errorMessage);
				return;
			}
		}

		for (URLTableEntry entry : this.schemaModelUrls) {
			String errorMessage = validateURL(entry.url);
			if (errorMessage != null) {
				setErrorMessage(errorMessage);
				return;
			}
		}
		
		for (NamespaceMappingTableEntry entry : this.nsMappingEntries) {
			String errorMessage = validateURL(entry.url);
			if (errorMessage != null) {
				setErrorMessage(errorMessage);
				return;
			}
		}
		
		setErrorMessage(null);
	}

	private static String validateURL(String text) {
		if (text.length() == 0) {
			return "Empty strings are not valid URLs";
		}
		try {
			new URL(text);
			return null;
		} catch (MalformedURLException ex) {
			return "Not a valid URL: " + text;
		}
	}
}
