package edu.yale.plugins.tasks;

import edu.yale.plugins.tasks.model.ATContainer;
import edu.yale.plugins.tasks.model.ATContainerCollection;
import edu.yale.plugins.tasks.model.BoxLookupReturnRecords;
import edu.yale.plugins.tasks.model.BoxLookupReturnRecordsCollection;
import edu.yale.plugins.tasks.search.BoxLookupReturnScreen;
import edu.yale.plugins.tasks.utils.BoxLookupAndUpdate;
import edu.yale.plugins.tasks.utils.ContainerGatherer;
import edu.yale.plugins.tasks.utils.YalePluginTasksConfigDialog;
import org.archiviststoolkit.ApplicationFrame;
import org.archiviststoolkit.dialog.ATFileChooser;
import org.archiviststoolkit.dialog.ErrorDialog;
import org.archiviststoolkit.editor.ArchDescriptionFields;
import org.archiviststoolkit.exceptions.UnsupportedDatabaseType;
import org.archiviststoolkit.hibernate.SessionFactory;
import org.archiviststoolkit.importer.ImportExportLogDialog;
import org.archiviststoolkit.model.ArchDescriptionAnalogInstances;
import org.archiviststoolkit.model.Resources;
import org.archiviststoolkit.model.ResourcesCommon;
import org.archiviststoolkit.model.Users;
import org.archiviststoolkit.mydomain.*;
import org.archiviststoolkit.plugin.ATPlugin;
import org.archiviststoolkit.structure.ATFieldInfo;
import org.archiviststoolkit.structure.DefaultValues;
import org.archiviststoolkit.swing.ATProgressUtil;
import org.archiviststoolkit.swing.InfiniteProgressPanel;
import org.archiviststoolkit.util.*;
import org.java.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/**
 * Archivists' Toolkit(TM) Copyright � 2005-2007 Regents of the University of California, New York University, & Five Colleges, Inc.
 * All rights reserved.
 * <p/>
 * This software is free. You can redistribute it and / or modify it under the terms of the Educational Community License (ECL)
 * version 1.0 (http://www.opensource.org/licenses/ecl1.php)
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the ECL license for more details about permissions and limitations.
 * <p/>
 * <p/>
 * Archivists' Toolkit(TM)
 * http://www.archiviststoolkit.org
 * info@archiviststoolkit.org
 * <p/>
 * A simple plugin to allow more efficient inputting of yale data
 * <p/>
 * Created by IntelliJ IDEA.
 *
 * @author: Lee Mandell and Nathan Stevens
 * Date: Feb 10, 2009
 * Time: 1:07:45 PM
 */

public class YalePluginTasks extends Plugin implements ATPlugin {
    public static final String APPLY_CONTAINER_INFORMATION_TASK = "Assign Container Information";
    public static final String EXPORT_VOYAGER_INFORMATION = "Export Voyager Information";
    public static final String PARTIAL_EAD_IMPORT = "Partial EAD Import";
    public static final String BOX_LOOKUP = "Box Lookup";
    public static final String SHOW_CONFIG = "Show Config Dialog";

    public static final String PLUGIN_NAME = "Yale Tasks";

    protected ApplicationFrame mainFrame;
    protected ArchDescriptionFields parentEditorFields;
    private ResourcesCommon resourceOrComponent;

    protected YaleAnalogInstancesFields editorFields;
    private DomainEditor analogInstanceEditor;
    private JTable callingTable;
    private int selectedRow;
    protected ArchDescriptionAnalogInstances analogInstance;

    // class finding and storing container information
    BoxLookupAndUpdate boxLookupAndUpdate = null;

    public static final String BOX_RECORD_DATA_NAME = "box_record";
    public static final String AT_CONTAINER_DATA_NAME = "container_record";
    public static final String CONFIG_DATA_NAME = "config_record";

    // the config dialog use for configuring the application
    private YalePluginTasksConfigDialog configDialog;

    // the default constructor
    public YalePluginTasks() {
    }

    // get the category this plugin belongs to
    public String getCategory() {
        return ATPlugin.DEFAULT_CATEGORY + " " + ATPlugin.EMBEDDED_EDITOR_CATEGORY;
    }

    // get the name of this plugin
    public String getName() {
        return PLUGIN_NAME;
    }

    // Method to set the main frame
    public void setApplicationFrame(ApplicationFrame mainFrame) {
        this.mainFrame = mainFrame;
        initConfigDialog(this.mainFrame);
    }

    // Method that display the window
    public void showPlugin() {
    }

    // method to display a plugin that needs a parent frame
    public void showPlugin(Frame owner) {
    }

    // method to display a plugin that needs a parent dialog
    public void showPlugin(Dialog owner) {
    }

    // Method to return the jpanels for plugins that are in an AT editor
    public HashMap getEmbeddedPanels() {
        return new HashMap();
    }

    public HashMap getRapidDataEntryPlugins() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setEditorField(DomainEditorFields domainEditorFields) {
    }

    // Method to set the editor field component
    public void setEditorField(ArchDescriptionFields editorField) {
    }

    /**
     * Method to set the domain object for this plugin
     */
    public void setModel(DomainObject domainObject, InfiniteProgressPanel monitor) {
        detectChangesInChildRecords((Resources)domainObject);
    }

    /**
     * A method to detect changes, including add and removing instances
     * This is really a hack because the AT does properly detect changes in the child
     * records
     *
     * @param resource
     */
    private void detectChangesInChildRecords(final Resources resource) {
        Thread performer = new Thread(new Runnable() {
            public void run() {
                System.out.println("Waiting for changes on " + resource.toString());

                while(true) {
                    if(ApplicationFrame.getInstance().getRecordDirty()) {
                        System.out.println("Resource record change detected ...");

                        int currentLength = resource.getOtherLevel().length();

                        // put a random number of blank characters in the other level to force hibernate to
                        // update record number
                        Random randomGenerator = new Random();
                        int length = randomGenerator.nextInt(10);

                        // make sure current length and new length are different
                        while(currentLength == length) {
                            length = randomGenerator.nextInt(10);
                        }

                        // generate random length blank string
                        char[] array = new char[length];
                        Arrays.fill(array, ' ');
                        String dummyText = new String(array);

                        resource.setOtherLevel(dummyText);

                        System.out.println("old length/new length: " + currentLength + " / " + length);
                        break;
                    } else {
                        try {
                            //System.out.println("Waiting for record to be changed ...");
                            Thread.sleep(5000);
                        } catch (InterruptedException e) { }
                    }
                }
            }
        });

        // start thread now
        performer.start();
    }

    /**
     * Method to get the table from which the record was selected
     *
     * @param callingTable The table containing the record
     */
    public void setCallingTable(JTable callingTable) {
    }

    /**
     * Method to set the selected row of the calling table
     *
     * @param selectedRow
     */
    public void setSelectedRow(int selectedRow) {
    }

    /**
     * Method to set the current record number along with the total number of records
     *
     * @param recordNumber The current record number
     * @param totalRecords The total number of records
     */
    public void setRecordPositionText(int recordNumber, int totalRecords) {
    }

    // Method to do a specific task in the plugin
    public void doTask(String task) {
        DomainTableWorkSurface workSurface = mainFrame.getWorkSurfaceContainer().getCurrentWorkSurface();

        final DomainSortableTable worksurfaceTable = (DomainSortableTable) workSurface.getTable();

        final ResourcesDAO access = new ResourcesDAO();

        if (task.equals(APPLY_CONTAINER_INFORMATION_TASK)) {
            if (workSurface.getClazz() != Resources.class) {
                JOptionPane.showMessageDialog(mainFrame, "This function only works for the resources module");
            } else if (worksurfaceTable.getSelectedRowCount() != 1) {
                JOptionPane.showMessageDialog(mainFrame, "You must select one resource record");
            } else {
                Thread performer = new Thread(new Runnable() {
                    public void run() {
                        // make sure we have the class that looks up records
                        if (boxLookupAndUpdate == null) {
                            try {
                                boxLookupAndUpdate = new BoxLookupAndUpdate();
                                boxLookupAndUpdate.alwaysSaveCache = configDialog.getAlwaysSaveCache();
                            } catch (Exception e) {
                                JOptionPane.showMessageDialog(mainFrame, "Unable to connect to database");
                                e.printStackTrace();
                                return;
                            }
                        } else {
                            System.out.println("BoxLookupAndUpdate Already Created ...");
                        }

                        InfiniteProgressPanel monitor = ATProgressUtil.createModalProgressMonitor(mainFrame, 1000, true);
                        monitor.start("Gathering Containers...");

                        Resources resource = (Resources) worksurfaceTable.getFilteredList().get(worksurfaceTable.getSelectedRow());

                        try {
                            resource = (Resources) access.findByPrimaryKeyLongSession(resource.getIdentifier());
                        } catch (LookupException e) {
                            JOptionPane.showMessageDialog(mainFrame, "Unable to load resource record");
                            e.printStackTrace();
                            return;
                        }

                        final Color highlightColor = configDialog.getHighlightColor();

                        final BoxLookupReturnRecordsCollection boxes = boxLookupAndUpdate.gatherContainersBySeries(resource, monitor, configDialog.getUseCacheRecords());

                        // close the monitor
                        monitor.close();

                        // display the dialog in the EDT thread
                        Runnable doWorkRunnable = new Runnable() {
                            public void run() {
                                YaleLocationAssignmentResources locationAssignmentDialog = new YaleLocationAssignmentResources(mainFrame);
                                locationAssignmentDialog.setSize(900, 800);
                                locationAssignmentDialog.setHighlightColor(highlightColor);
                                locationAssignmentDialog.assignContainerListValues(boxes);
                                locationAssignmentDialog.setBoxLookupAndUpdate(boxLookupAndUpdate);
                                locationAssignmentDialog.setVisible(true);
                            }
                        };
                        SwingUtilities.invokeLater(doWorkRunnable);
                    }
                }, "Gather containers");
                performer.start();
            }

        } else if (task.equals(EXPORT_VOYAGER_INFORMATION)) {
            if (workSurface.getClazz() != Resources.class) {
                JOptionPane.showMessageDialog(mainFrame, "This function only works for the resources module");
            } else if (worksurfaceTable.getSelectedRowCount() == 0) {
                JOptionPane.showMessageDialog(mainFrame, "You must select at least one resource record");
            } else {
                ATFileChooser filechooser = new ATFileChooser();

                if (filechooser.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
                    final File outputFile = filechooser.getSelectedFile();

                    Thread performer = new Thread(new Runnable() {
                        public void run() {
                            InfiniteProgressPanel monitor = ATProgressUtil.createModalProgressMonitor(mainFrame, 1000);
                            monitor.start("Exporting Voyager Information...");

                            long resourceId;
                            Resources selectedResource, resource;

                            PrintWriter writer = null;

                            ContainerGatherer gatherer;

                            String accessionNumber;

                            // start the timer object
                            MyTimer timer = new MyTimer();
                            timer.reset();

                            try {
                                writer = new PrintWriter(outputFile);
                                int totalRecords = worksurfaceTable.getSelectedObjects().size();
                                int count = 0;
                                int containerCount = 0;
                                int totalContainers = 0;

                                for (int i : worksurfaceTable.getSelectedRows()) {
                                    selectedResource = (Resources) worksurfaceTable.getFilteredList().get(i);
                                    resourceId = selectedResource.getResourceId();
                                    resource = (Resources) access.findByPrimaryKeyLongSession(resourceId);

                                    count++;
                                    monitor.setTextLine("Exporting resource " + count + " of " + totalRecords + " - " + resource.getTitle(), 1);

                                    gatherer = new ContainerGatherer(resource, configDialog.getUseCacheRecords(), configDialog.getAlwaysSaveCache());
                                    ATContainerCollection containerCollection = gatherer.gatherContainers(monitor);

                                    for (ATContainer container : containerCollection.getContainers()) {
                                        totalContainers++;

                                        // checks to see if the voyager information for this container has already
                                        // been exported
                                        if(gatherer.isExportedToVoyager(container) && !configDialog.getAlwaysExportVoyagerInformation()) {
                                            String message = "Container: " + container.getContainerLabel() + " already exported ...";
                                            monitor.setTextLine(message, 3);
                                            continue;
                                        }

                                        accessionNumber = container.getAccessionNumber();
                                        writer.println(resource.getResourceIdentifier2() + "," +
                                                containerCollection.getVoyagerHoldingsKey() + "," +
                                                accessionNumber + "," +
                                                containerCollection.lookupAccessionDate(accessionNumber) + "," +
                                                container.getContainerLabel().replaceAll(",","") + "," +
                                                "," + //just a dummy for box number extension
                                                container.getBarcode() + ",");

                                        // save the fact that this record was already exported
                                        gatherer.updateExportedToVoyager(container, true);
                                        containerCount++;
                                    }

                                    // close the long session, otherwise memory would quickly run out
                                    access.closeLongSession();
                                    access.getLongSession();
                                }

                                writer.flush();

                                // display a message dialog
                                monitor.close();
                                String message = "Total Time to export: " + MyTimer.toString(timer.elapsedTimeMillis()) +
                                        "\nResource(s) processed: " + count +
                                        "\nContainers exported: " + containerCount + " out of "  + totalContainers;

                                JOptionPane.showMessageDialog(mainFrame,
                                        message,
                                        "Export of Voyager Information Completed",
                                        JOptionPane.PLAIN_MESSAGE);

                                System.out.println(message);
                            } catch (LookupException e) {
                                monitor.close();
                                new ErrorDialog("Error loading resource", e).showDialog();
                            } catch (FileNotFoundException e) {
                                monitor.close();
                                new ErrorDialog("Error creating file writer", e).showDialog();
                            } catch (PersistenceException e) {
                                new ErrorDialog("Error looking up accession date", e).showDialog();
                            } catch (SQLException e) {
                                new ErrorDialog("Error resetting the long session", e).showDialog();
                            } catch (Exception e) {
                                new ErrorDialog("Error updating export to voyager boolean", e).showDialog();
                            } finally {
                                writer.close();
                                monitor.close();
                            }
                        }
                    }, "Exporting Voyager Information");
                    performer.start();
                }
            }
        } else if (task.equals(BOX_LOOKUP)) {
            try {
                Color highlightColor = configDialog.getHighlightColor();
                BoxLookupReturnScreen returnScreen = new BoxLookupReturnScreen(mainFrame);
                returnScreen.setHighlightColor(highlightColor);
                returnScreen.showDialog();
            } catch (ClassNotFoundException e) {
                new ErrorDialog("", e).showDialog();
            } catch (SQLException e) {
                new ErrorDialog("", e).showDialog();
            }
        } else if (task.equals(SHOW_CONFIG)) {
            if(mainFrame.getCurrentUserAccessClass() == Users.ACCESS_CLASS_SUPERUSER) {
                showConfigDialog(false);
            } else {
                showConfigDialog(true);
            }
        }
    }

    public boolean doTask(String s, String[] strings) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }


    // Method to get the list of specific task the plugin can perform
    public String[] getTaskList() {
        String[] tasks = new String[]{APPLY_CONTAINER_INFORMATION_TASK,
                EXPORT_VOYAGER_INFORMATION,
                BOX_LOOKUP, SHOW_CONFIG};

        return tasks;
    }

    // Method to return the editor type for this plugin
    public String getEditorType() {
        return RESOURCE_EDITOR;
    }

    /**
     * Method to index records i.e cache box and container information in the database
     */
    public void indexRecords(final Window parent, final boolean updateAllRecords, final boolean gui) {
        final ResourcesDAO access = new ResourcesDAO();

        Thread performer = new Thread(new Runnable() {
            public void run() {
                InfiniteProgressPanel monitor = null;

                if (gui) {
                    monitor = ATProgressUtil.createModalProgressMonitor(parent, 1000, true);
                    monitor.start("Generating index...");
                }

                long resourceId;
                Resources selectedResource, resource;

                BoxLookupAndUpdate boxLookupAndUpdate;
                ContainerGatherer gatherer;

                // start the timer object
                MyTimer timer = new MyTimer();
                timer.reset();

                try {
                    ArrayList records = (ArrayList) access.findAll();

                    int totalRecords = records.size();
                    int i = 1;
                    for (Object object : records) {
                        if (monitor != null && monitor.isProcessCancelled()) {
                            System.out.println("Indexing cancelled ...");
                            break;
                        }

                        selectedResource = (Resources) object;
                        resourceId = selectedResource.getResourceId();
                        resource = (Resources) access.findByPrimaryKeyLongSession(resourceId);

                        monitor.setTextLine("Indexing resource " + i + " of " + totalRecords + " - " + resource.getTitle(), 1);

                        // index the containers
                        gatherer = new ContainerGatherer(resource, true, false);
                        gatherer.alwaysSaveCache = updateAllRecords;
                        ATContainerCollection containerCollection = gatherer.gatherContainers(monitor);

                        // index the boxes
                        boxLookupAndUpdate = new BoxLookupAndUpdate();
                        boxLookupAndUpdate.updateAllRecords = updateAllRecords;
                        boxLookupAndUpdate.setVoyagerInfo = false;
                        BoxLookupReturnRecordsCollection boxCollection = boxLookupAndUpdate.gatherContainersBySeries(resource, monitor, true);

                        // close the long session, otherwise memory would quickly run out
                        access.closeLongSession();
                        access.getLongSession();

                        i++;
                    }

                    String message = "Total time to export " + i + " records: " + MyTimer.toString(timer.elapsedTimeMillis());

                    if (gui) {
                        monitor.close();

                        JOptionPane.showMessageDialog(parent,
                                message,
                                "Record Indexing Completed",
                                JOptionPane.PLAIN_MESSAGE);
                    }

                    System.out.println(message);
                } catch (LookupException e) {
                    if (gui) {
                        monitor.close();
                        new ErrorDialog("Error loading resource", e).showDialog();
                    }
                    e.printStackTrace();
                } catch (PersistenceException e) {
                    if (gui) {
                        new ErrorDialog("Error looking up accession date", e).showDialog();
                    }
                    e.printStackTrace();
                } catch (SQLException e) {
                    if (gui) {
                        new ErrorDialog("Error resetting the long session", e).showDialog();
                    }
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    if (gui) {
                        monitor.close();
                        new ErrorDialog("Exception", e).showDialog();
                    }
                    e.printStackTrace();
                } finally {
                    if (gui) {
                        monitor.close();
                    }
                }
            }
        }, "Indexing Records ...");
        performer.start();
    }

    /**
     * Method to verify that all barcodes in a container are the same
     */
    public void verifyContainerBarcodes(final Window parent, final boolean useCache, final boolean gui) {
        final ResourcesDAO access = new ResourcesDAO();

        Thread performer = new Thread(new Runnable() {
            public void run() {
                InfiniteProgressPanel monitor = null;

                if (gui) {
                    monitor = ATProgressUtil.createModalProgressMonitor(parent, 1000, true);
                    monitor.start("Generating index...");
                }

                long resourceId;
                Resources selectedResource, resource;

                BoxLookupAndUpdate boxLookupAndUpdate;
                ContainerGatherer gatherer;

                // start the timer object
                MyTimer timer = new MyTimer();
                timer.reset();

                try {
                    StringBuilder sb = new StringBuilder();

                    ArrayList records = (ArrayList) access.findAll();

                    int totalRecords = records.size();
                    int barcodeMismatches = 0;
                    int totalInstances = 0;

                    int i = 1;

                    for (Object object : records) {
                        if (monitor != null && monitor.isProcessCancelled()) {
                            System.out.println("Barcode verification cancelled ...");
                            break;
                        }

                        selectedResource = (Resources) object;
                        resourceId = selectedResource.getResourceId();
                        resource = (Resources) access.findByPrimaryKeyLongSession(resourceId);

                        // get the resource identifier
                        String resourceIdentifier = resource.getResourceIdentifier();

                        monitor.setTextLine("Verifying barcodes for resource " + i + " of " + totalRecords + " - " + resource.getTitle(), 1);

                        // index the containers
                        gatherer = new ContainerGatherer(resource, true, false);
                        ATContainerCollection containerCollection = gatherer.gatherContainers(monitor);

                        // index the boxes
                        boxLookupAndUpdate = new BoxLookupAndUpdate();
                        boxLookupAndUpdate.setVoyagerInfo = false;

                        // get the containers
                        BoxLookupReturnRecordsCollection boxCollection = boxLookupAndUpdate.gatherContainersBySeries(resource, monitor, useCache);

                        // now check the barcode in all instances tp see which are different
                        for(BoxLookupReturnRecords boxRecord: boxCollection.getContainers()) {
                            if (monitor != null && monitor.isProcessCancelled()) {
                                System.out.println("Barcode verification cancelled ...");
                                break;
                            }

                            try {
                                if (gui) {
                                    monitor.setTextLine("", 2);
                                    monitor.setTextLine("Processing Container: " + boxRecord.getUniqueId(), 4);
                                }

                                String message = boxLookupAndUpdate.verifyBarcodes(boxRecord.getInstanceIds(), monitor);

                                if(!message.equals("OK")) {
                                    barcodeMismatches++;

                                    message = "\nMismatch # " + barcodeMismatches + "\nResource Id: " + resource.getResourceIdentifier() + "\n" + message + "\n";
                                    sb.append(message);

                                    System.out.println(message);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        // set the total instances
                        totalInstances += boxLookupAndUpdate.getNumberOfInstanceChecked();

                        // close the database connecion and long sessions, otherwise memory would quickly run out
                        boxLookupAndUpdate.closeConnection();

                        access.closeLongSession();
                        access.getLongSession();

                        i++;
                    }

                    String message = sb.toString() + "\nTotal time for verification of " + i + " records / " +
                            totalInstances + " instances : " +
                            MyTimer.toString(timer.elapsedTimeMillis());

                    if (gui) {
                        monitor.close();

                        ImportExportLogDialog logDialog = new ImportExportLogDialog(null, ImportExportLogDialog.DIALOG_TYPE_EXPORT, message);
                        logDialog.setTitle("Container Barcode Verification");
                        logDialog.showDialog();
                    }

                    System.out.println(message);
                } catch (LookupException e) {
                    if (gui) {
                        monitor.close();
                        new ErrorDialog("Error loading resource", e).showDialog();
                    }
                    e.printStackTrace();
                } catch (PersistenceException e) {
                    if (gui) {
                        new ErrorDialog("Error looking up accession date", e).showDialog();
                    }
                    e.printStackTrace();
                } catch (SQLException e) {
                    if (gui) {
                        new ErrorDialog("Error resetting the long session", e).showDialog();
                    }
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    if (gui) {
                        monitor.close();
                        new ErrorDialog("Exception", e).showDialog();
                    }
                    e.printStackTrace();
                } finally {
                    if (gui) {
                        monitor.close();
                    }
                }
            }
        }, "Verifying Container Barcodes");
        performer.start();
    }

    // code that is executed when plugin starts. not used here
    protected void doStart() {
    }

    // code that is executed after plugin stops. not used here
    protected void doStop() {
    }

    /**
     * Method to init the config dialog
     *
     * @param frame
     */
    public void initConfigDialog(Frame frame) {
        if (configDialog == null) {
            configDialog = new YalePluginTasksConfigDialog(frame);
            configDialog.pack();
            configDialog.setYalePluginTasks(this);
        }

        // TODO load the config record from the database
    }

    /**
     * Method to display tje config dialog
     * @param limitAccess
     */
    public void showConfigDialog(boolean limitAccess) {
        if(configDialog != null) {
            // used to limit the access of certain buttons only to level five
            if(limitAccess) {
                configDialog.limitAccess();
            }

            configDialog.setVisible(true);
        }
    }

    /**
     * Method to return the configuration dialog
     *
     * @return
     */
    public YalePluginTasksConfigDialog getConfigDialog() {
        return configDialog;
    }

    /**
     * Method to display the frame when running in stand alone mode
     */
    public void showApplicationFrame() {
        // display the dialog that allow running the commands
        YalePluginTasksFrame yalePluginTasksFrame = new YalePluginTasksFrame(this);
        yalePluginTasksFrame.pack();
        yalePluginTasksFrame.setVisible(true);
    }

    // main method for testing only
    public static void main(String[] args) {
        // load all the hibernate stuff that the AT application typically does
        startHibernate();

        // display the application frame
        YalePluginTasks yalePlugin = new YalePluginTasks();
        yalePlugin.showApplicationFrame();
    }

    /**
     * Method to load the hibernate engine and initial needed when running
     * as a standalone application
     */
    private static void startHibernate() {
        //get user preferences
        UserPreferences userPrefs = UserPreferences.getInstance();
        userPrefs.populateFromPreferences();

        // now bybass AT login since it assume that CLI will be used for command line.
        // Will have to change that in the future to a sure it's not abused
        if (!userPrefs.checkForDatabaseUrl()) {
            System.out.println("This appears to be the first time the AT was launched. \n" +
                    "Please fill out the database connection information");
            System.exit(1);
        }

        // start up hibernate
        try {
            userPrefs.updateSessionFactoryInfo();
        } catch (UnsupportedDatabaseType unsupportedDatabaseType) {
            System.out.println("Error connecting to database ...");
            System.exit(1);
        }

        // try connecting to the database
        try {
            connectAndTest();
        } catch (UnsupportedDatabaseType unsupportedDatabaseType) {
            System.out.println("Error connecting to database " + unsupportedDatabaseType);
            System.exit(1);
        }

        // Load the Lookup List
        if (!LookupListUtils.loadLookupLists()) {
            System.out.println("Failed to Load the Lookup List");
            System.exit(1);
        }

        // Loading Notes Etc. Types
        if (!NoteEtcTypesUtils.loadNotesEtcTypes()) {
            System.out.println("Failed to Load Notes Etc. Types");
            System.exit(1);
        }

        System.out.println("Loading Field Information");
        ATFieldInfo.loadFieldList();

        System.out.println("Loading Location Information");
        LocationsUtils.initLocationLookupList();

        System.out.println("Loading Default Value Information");
        DefaultValues.initDefaultValueLookup();

        System.out.println("Loading In-line tags");
        InLineTagsUtils.loadInLineTags();
    }

    /**
     * Connect and the database engine
     *
     * @throws UnsupportedDatabaseType
     */
    private static void connectAndTest() throws UnsupportedDatabaseType {
        while (!DatabaseConnectionUtils.testDbConnection()) {
            System.out.println("");
            System.exit(1);
        }

        try {
            while (!DatabaseConnectionUtils.checkVersion(DatabaseConnectionUtils.CHECK_VERSION_FROM_MAIN)) {
                System.out.println("Wrong database version connection");
                System.exit(1);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("The jdbc driver is missing");
            e.printStackTrace();
        }

        try {
            SessionFactory.testHibernate();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
