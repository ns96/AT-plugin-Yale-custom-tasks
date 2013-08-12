/*
 * Created by JFormDesigner on Wed Sep 12 19:21:00 EDT 2012
 */

package edu.yale.plugins.tasks;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.*;
import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;
import edu.yale.plugins.tasks.dbdialog.RemoteDBConnectDialog;
import edu.yale.plugins.tasks.model.ATContainer;
import edu.yale.plugins.tasks.model.ATContainerCollection;
import edu.yale.plugins.tasks.model.BoxLookupReturnRecordsCollection;
import edu.yale.plugins.tasks.search.BoxLookupReturnScreen;
import edu.yale.plugins.tasks.utils.BoxLookupAndUpdate;
import edu.yale.plugins.tasks.utils.ContainerGatherer;
import org.archiviststoolkit.dialog.ErrorDialog;
import org.archiviststoolkit.model.Resources;
import org.archiviststoolkit.mydomain.LookupException;
import org.archiviststoolkit.mydomain.PersistenceException;
import org.archiviststoolkit.mydomain.ResourcesDAO;
import org.archiviststoolkit.swing.ATProgressUtil;
import org.archiviststoolkit.swing.InfiniteProgressPanel;
import org.archiviststoolkit.util.MyTimer;

/**
 * @author Nathan Stevens
 */
public class YalePluginTasksFrame extends JFrame {
    // Used to connect to the AT database
    private RemoteDBConnectDialog dbdialog = null;

    // method for find and storing the
    private BoxLookupAndUpdate boxLookupAndUpdate = null;

    // the yale plugin task object
    private YalePluginTasks yalePluginTasks;

    /**
     * Main constructor
     */
    public YalePluginTasksFrame(YalePluginTasks yalePluginTasks) {
        this.yalePluginTasks  = yalePluginTasks;
        this.yalePluginTasks.initConfigDialog(this);
        initComponents();
    }

    /**
     * Display the dialog that allows selection of a connection, and
     * ultimately a resource record
     */
    private void showDBDialogButtonActionPerformed() {
        if(dbdialog == null) {
            dbdialog = new RemoteDBConnectDialog(this, true);
        }

        dbdialog.pack();
        dbdialog.setVisible(true);

        // load the resource records now
        dbdialog.loadResourcesAndUsers();
    }

    /**
     * Exit the application when the OK button is pressed
     */
    private void okButtonActionPerformed() {
        setVisible(false);
        System.exit(0);
    }

    /**
     * This will display the location assignment dialog
     */
    private void assignContainerButtonActionPerformed() {
        if(dbdialog != null) {
            try {
                final Resources record = dbdialog.getResourceRecord();
                if(record == null) {
                    System.out.println("Select a Record ...");
                    return;
                }

                final boolean useCache = yalePluginTasks.getConfigDialog().getUseCacheRecords();
                final boolean alwaysSaveCache = yalePluginTasks.getConfigDialog().getAlwaysSaveCache();
                final Color highlightColor = yalePluginTasks.getConfigDialog().getHighlightColor();

                // disable the appropriate button
                assignContainerButton.setEnabled(false);

                Thread performer = new Thread(new Runnable() {
                    public void run() {

                        InfiniteProgressPanel monitor = ATProgressUtil.createModalProgressMonitor(YalePluginTasksFrame.this, 1000, true);
                        monitor.start("Gathering Containers...");

                        try {
                            if(boxLookupAndUpdate == null) {
                                boxLookupAndUpdate = new BoxLookupAndUpdate();
                                boxLookupAndUpdate.alwaysSaveCache = alwaysSaveCache;
                            }

                            //final Collection<BoxLookupReturnRecords> boxes = boxLookupAndUpdate.gatherContainersJDBC(record, monitor, true);
                            final BoxLookupReturnRecordsCollection boxes = boxLookupAndUpdate.gatherContainersBySeries(record, monitor, useCache);

                            monitor.close();

                            // display the dialog in the EDT thread
                            Runnable doWorkRunnable = new Runnable() {
                                public void run() {
                                    YaleLocationAssignmentResources locationAssignmentDialog = new YaleLocationAssignmentResources(YalePluginTasksFrame.this);
                                    locationAssignmentDialog.setSize(900, 800);
                                    locationAssignmentDialog.setHighlightColor(highlightColor);
                                    locationAssignmentDialog.assignContainerListValues(boxes);
                                    locationAssignmentDialog.setBoxLookupAndUpdate(boxLookupAndUpdate);
                                    locationAssignmentDialog.setVisible(true);

                                    // re-enable the assign container button
                                    assignContainerButton.setEnabled(true);
                                }
                            };
                            SwingUtilities.invokeLater(doWorkRunnable);
                        } catch (SQLException e) {
                            monitor.close();
                            assignContainerButton.setEnabled(true);
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        } catch (ClassNotFoundException e) {
                            monitor.close();
                            assignContainerButton.setEnabled(true);
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                });
                performer.start();

            } catch (Exception e) {
                assignContainerButton.setEnabled(true);
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    /**
     * Method to export voyager information
     */
    private void voyagerExportButtonActionPerformed() {
        if(dbdialog != null) {
            JFileChooser fileChooser = new JFileChooser();

            if(fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                final File outputFile = fileChooser.getSelectedFile();
                final ResourcesDAO access = new ResourcesDAO();

                final boolean useCache = yalePluginTasks.getConfigDialog().getUseCacheRecords();
                final boolean alwaysSaveCache = yalePluginTasks.getConfigDialog().getAlwaysSaveCache();
                final boolean alwaysExportVoyager = yalePluginTasks.getConfigDialog().getAlwaysExportVoyagerInformation();

                Thread performer = new Thread(new Runnable() {
                    public void run() {
                        InfiniteProgressPanel monitor = ATProgressUtil.createModalProgressMonitor(YalePluginTasksFrame.this, 1000);
                        monitor.start("Exporting Voyager Information...");

                        long resourceId;

                        Resources resource;

                        PrintWriter writer = null;

                        ContainerGatherer gatherer;

                        String accessionNumber;

                        // start the timer object
                        MyTimer timer = new MyTimer();
                        timer.reset();

                        try {
                            writer = new PrintWriter(outputFile);

                            ArrayList<Resources> selectedResources = dbdialog.getSelectedResourceRecords();

                            int totalRecords = selectedResources.size();
                            int count = 0;

                            for (Resources selectedResource: selectedResources)  {
                                count++;
                                resourceId = selectedResource.getResourceId();

                                resource = (Resources) access.findByPrimaryKeyLongSession(resourceId);

                                monitor.setTextLine("Exporting resource " + count + " of " + totalRecords + " - " + resource.getTitle(), 1);

                                gatherer = new ContainerGatherer(resource, useCache, alwaysSaveCache);

                                ATContainerCollection containerCollection = gatherer.gatherContainers(monitor);

                                for (ATContainer container: containerCollection.getContainers()) {
                                    // checks to see if the voyager information for this container has already
                                    // been exported
                                    if(gatherer.isExportedToVoyager(container) && !alwaysExportVoyager) {
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
                                }

                                // close the long session, otherwise memory would quickly run out
                                access.closeLongSession();
                                access.getLongSession();
                            }

                            writer.flush();

                            System.out.println("Total Time to export: " + MyTimer.toString(timer.elapsedTimeMillis()));
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
    }

    /**
     * Method to index the records
     */
    private void indexButtonActionPerformed() {
        yalePluginTasks.indexRecords(this, true, true);
    }

    private void showConfigDialogButtonActionPerformed() {
        yalePluginTasks.showConfigDialog(false);
    }

    private void boxSearchButtonActionPerformed() {
        try {
            BoxLookupReturnScreen returnScreen = new BoxLookupReturnScreen(this);
            returnScreen.showDialog();
        } catch (ClassNotFoundException e) {
            new ErrorDialog("", e).showDialog();
        } catch (SQLException e) {
            new ErrorDialog("", e).showDialog();
        }
    }

    /**
     * Method to display the frame that allows linking of location barcodes
     * to container barcodes
     */
    private void barcodeLinkerButtonActionPerformed() {
        yalePluginTasks.showBarcodeLinkerFrame();
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        assignContainerButton = new JButton();
        voyagerExportButton = new JButton();
        boxSearchButton = new JButton();
        indexButton = new JButton();
        barcodeLinkerButton = new JButton();
        buttonBar = new JPanel();
        showConfigDialogButton = new JButton();
        showDBDialogButton = new JButton();
        okButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setTitle("Yale Tasks Application v 2.4");
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(Borders.DIALOG_BORDER);
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC
                    },
                    new RowSpec[] {
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC
                    }));

                //---- assignContainerButton ----
                assignContainerButton.setText("Assign Container Information");
                assignContainerButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        assignContainerButtonActionPerformed();
                    }
                });
                contentPanel.add(assignContainerButton, cc.xy(1, 1));

                //---- voyagerExportButton ----
                voyagerExportButton.setText("Export Voyager Info");
                voyagerExportButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        voyagerExportButtonActionPerformed();
                    }
                });
                contentPanel.add(voyagerExportButton, cc.xy(5, 1));

                //---- boxSearchButton ----
                boxSearchButton.setText("Box Search");
                boxSearchButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        boxSearchButtonActionPerformed();
                    }
                });
                contentPanel.add(boxSearchButton, cc.xy(1, 3));

                //---- indexButton ----
                indexButton.setText("Index Records");
                indexButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        indexButtonActionPerformed();
                    }
                });
                contentPanel.add(indexButton, cc.xy(5, 3));

                //---- barcodeLinkerButton ----
                barcodeLinkerButton.setText("Barcode Linker");
                barcodeLinkerButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        barcodeLinkerButtonActionPerformed();
                    }
                });
                contentPanel.add(barcodeLinkerButton, cc.xy(1, 5));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(Borders.BUTTON_BAR_GAP_BORDER);
                buttonBar.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.GLUE_COLSPEC,
                        FormFactory.BUTTON_COLSPEC
                    },
                    RowSpec.decodeSpecs("pref")));

                //---- showConfigDialogButton ----
                showConfigDialogButton.setText("Show Config Dialog");
                showConfigDialogButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showConfigDialogButtonActionPerformed();
                    }
                });
                buttonBar.add(showConfigDialogButton, cc.xy(2, 1));

                //---- showDBDialogButton ----
                showDBDialogButton.setText("Show Resource Records");
                showDBDialogButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showDBDialogButtonActionPerformed();
                    }
                });
                buttonBar.add(showDBDialogButton, cc.xy(4, 1));

                //---- okButton ----
                okButton.setText("Exit");
                okButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        okButtonActionPerformed();
                    }
                });
                buttonBar.add(okButton, cc.xy(6, 1));
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel contentPanel;
    private JButton assignContainerButton;
    private JButton voyagerExportButton;
    private JButton boxSearchButton;
    private JButton indexButton;
    private JButton barcodeLinkerButton;
    private JPanel buttonBar;
    private JButton showConfigDialogButton;
    private JButton showDBDialogButton;
    private JButton okButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
