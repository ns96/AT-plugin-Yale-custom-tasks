/**
 * Archivists' Toolkit(TM) Copyright � 2005-2007 Regents of the University of California, New York University, & Five Colleges, Inc.
 * All rights reserved.
 *
 * This software is free. You can redistribute it and / or modify it under the terms of the Educational Community License (ECL)
 * version 1.0 (http://www.opensource.org/licenses/ecl1.php)
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the ECL license for more details about permissions and limitations.
 *
 *
 * Archivists' Toolkit(TM)
 * http://www.archiviststoolkit.org
 * info@archiviststoolkit.org
 *
 * Created by JFormDesigner on Wed Apr 19 10:43:50 EDT 2006
 */

package edu.yale.plugins.tasks;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.swing.EventTableModel;
import ca.odell.glazedlists.swing.TableComparatorChooser;
import ca.odell.glazedlists.swing.TextComponentMatcherEditor;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.*;
import edu.yale.plugins.tasks.model.BoxLookupReturnRecords;
import edu.yale.plugins.tasks.model.BoxLookupReturnRecordsCollection;
import edu.yale.plugins.tasks.table.BoxLookupTableFormat;
import edu.yale.plugins.tasks.table.BoxReturnRecordsFilterator;
import edu.yale.plugins.tasks.table.YaleAlternatingRowColorTable;
import edu.yale.plugins.tasks.utils.BoxLookupAndUpdate;
import edu.yale.plugins.tasks.utils.PluginDataUtils;
import edu.yale.plugins.tasks.voyager.VoyagerInputValuesDialog;
import org.archiviststoolkit.dialog.ErrorDialog;
import org.archiviststoolkit.editor.LocationEditor;
import org.archiviststoolkit.model.Locations;
import org.archiviststoolkit.mydomain.*;
import org.archiviststoolkit.util.LocationsUtils;
import org.archiviststoolkit.util.StringHelper;
import org.hibernate.exception.ConstraintViolationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;

public class YaleLocationAssignmentResources extends JDialog {
    // used in the table
    private EventList<BoxLookupReturnRecords> resultsEventList = new BasicEventList<BoxLookupReturnRecords>();

    // The container table model
    EventTableModel<BoxLookupReturnRecords> containerTableModel = null;

    //private Collection<ContainerGroup> containerListValues;
    private Collection<BoxLookupReturnRecords> containerListValues;

    // object to perform jdbc lookup and updates
    private BoxLookupAndUpdate boxLookupAndUpdate = null;

    // The parent frame
    private Frame parentFrame = null;

    // keep track of the total instances
    private int totalInstances = 0;

    // box collection that saved to the database
    private BoxLookupReturnRecordsCollection boxCollection;

    // used to specify if record needs to be saved
    private boolean recordDirty = false;

    public YaleLocationAssignmentResources(Frame owner) {
        super(owner);
        initComponents();
        initLookup();
        this.parentFrame = owner;
    }

    /**
     * Set the box lookup update object
     *
     * @param boxLookupAndUpdate
     */
    public void setBoxLookupAndUpdate(BoxLookupAndUpdate boxLookupAndUpdate) {
        this.boxLookupAndUpdate = boxLookupAndUpdate;
    }

    public DomainSortableTable getLocationLookupTable() {
        return locationLookupTable;
    }

    public JTextField getFilterField() {
        return filterField;
    }

    /**
     * Method to return a customized JTable with alternate row colors
     *
     * @return The customized JTable
     */
    private YaleAlternatingRowColorTable createYaleTable() {
        SortedList<BoxLookupReturnRecords> sortedResults = new SortedList<BoxLookupReturnRecords>(resultsEventList);
        FilterList filterList = new FilterList(sortedResults, new TextComponentMatcherEditor(containerFilterField, new BoxReturnRecordsFilterator()));
        containerTableModel = new EventTableModel<BoxLookupReturnRecords>(filterList, new BoxLookupTableFormat(false));
        YaleAlternatingRowColorTable returnTable = new YaleAlternatingRowColorTable(containerTableModel, sortedResults);
        TableComparatorChooser.install(returnTable, sortedResults, TableComparatorChooser.MULTIPLE_COLUMN_MOUSE);

        return returnTable;
    }

    private void createLocationButtonActionPerformed(ActionEvent e) {
        DomainEditor dialog = new LocationEditor(this);
        dialog.setButtonListeners();
        Locations instance = new Locations();
        dialog.setModel(instance, null);
        dialog.disableNavigationButtons();
        if (dialog.showDialog() == JOptionPane.OK_OPTION) {
            try {
                DomainAccessObject access = DomainAccessObjectFactory.getInstance().getDomainAccessObject(Locations.class);
                access.add(instance);
                LocationsUtils.addLocationToLookupList(instance);
                initLookup();
                filterField.setText(instance.getBuilding() + " " + instance.getCoordinates());

            } catch (ConstraintViolationException persistenceException) {
                JOptionPane.showMessageDialog(this, "Can't save, Duplicate record:" + instance);
            } catch (PersistenceException persistenceException) {
                if (persistenceException.getCause() instanceof ConstraintViolationException) {
                    JOptionPane.showMessageDialog(this, "Can't save, Duplicate record:" + instance);
                    return;
                }
                new ErrorDialog(
                        "Error saving new record.",
                        StringHelper.getStackTrace(persistenceException)).showDialog();
            }
        }
    }

    private void removeAssignedLocationButtonActionPerformed(ActionEvent e) {
        if (containerLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select a container first.");
        } else {
            int[] selectedRows = containerLookupTable.getSelectedRows();

            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to remove " + selectedRows.length + " linked location(s)",
                    "Remove Linked Location", JOptionPane.YES_NO_OPTION);

            if (response == JOptionPane.OK_OPTION) {

                BoxLookupReturnRecords boxRecord = null;

                try {
                    // for each box record update all the instances associated with each
                    int instances = 0;

                    for (int i : selectedRows) {
                        boxRecord = containerTableModel.getElementAt(i);
                        String instanceIds = boxRecord.getInstanceIds();

                        instances += boxLookupAndUpdate.updateInstanceLocation(instanceIds, null);

                        // update the box location information now with blank string
                        boxRecord.setLocation("");
                    }

                    recordDirty = true;

                    System.out.println("Total # of Instances Updated: " + instances);
                } catch (Exception e1) {
                    showInstanceUpdateErrorDialog(boxRecord.toString());
                    e1.printStackTrace();
                }

                containerLookupTable.invalidate();
                containerLookupTable.repaint();
            }
        }
    }

    private void assignContainerInformationActionPerformed(ActionEvent e) {
        System.out.println("do assign container");

        if (containerLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select at least one container.");
        } else {
            YaleAssignContainerInformation dialog = new YaleAssignContainerInformation(parentFrame);

            int status = dialog.showDialog();

            if (status == JOptionPane.OK_OPTION) {
                BoxLookupReturnRecords boxRecord = null;

                try {
                    String barcode = dialog.getBarcode();
                    String container3Type = dialog.getContainer3Type();
                    String userDefinedString2 = dialog.getUserDefinedString2();
                    Boolean changeRestriction = dialog.restrictionChange();
                    Boolean restriction = dialog.newRestrictionValue();
                    Boolean changeExportedToVoyager = dialog.exportedToVoyagerChange();
                    Boolean exportedToVoyager = dialog.newExportedToVoyagerValue();

                    int[] selectedRows = containerLookupTable.getSelectedRows();

                    for (int i : selectedRows) {
                        boxRecord = containerTableModel.getElementAt(i);
                        String instanceIds = boxRecord.getInstanceIds();

                        // use the box updater class to make updates using sql calls
                        String topLevelContainerName = boxLookupAndUpdate.updateInstanceInformation(
                                instanceIds,
                                barcode,
                                container3Type,
                                userDefinedString2,
                                changeRestriction,
                                restriction,
                                changeExportedToVoyager,
                                exportedToVoyager
                        );

                        //update the box record values now
                        if (barcode.length() != 0) {
                            boxRecord.setBarcode(barcode);
                        }

                        if(userDefinedString2.length() != 0) {
                            boxRecord.setContainerType(userDefinedString2);
                        }

                        if(changeExportedToVoyager) {
                            boxRecord.setExportedToVoyager(exportedToVoyager);
                        }

                        if(changeRestriction) {
                            boxRecord.setRestriction(restriction);
                        }

                        boxRecord.setTopLevelContainerName(topLevelContainerName);
                    }

                    recordDirty = true;
                } catch (Exception e1) {
                    showInstanceUpdateErrorDialog(boxRecord.toString());

                    e1.printStackTrace();
                }

                containerLookupTable.invalidate();
                containerLookupTable.repaint();
            }
        }
    }

    /**
     * Update the barcode for instances
     *
     * @param e
     */
    private void rapidBarcodeEntryActionPerformed(ActionEvent e) {
        int[] selectedRows = containerLookupTable.getSelectedRows();

        if (selectedRows == null || selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "You must select a container to start first.");
        } else {
            int firstContainerIndex = selectedRows[0];
            int lastContainerIndex = -1;

            // stores the containers to process
            ArrayList<BoxLookupReturnRecords> selectedContainers = new ArrayList<BoxLookupReturnRecords>();

            if (selectedRows.length == 1) {
                //just one container was selected so build the array starting at that index
                lastContainerIndex = containerListValues.size();
            } else {
                lastContainerIndex = selectedRows[selectedRows.length - 1];
            }

            // the barcode string
            String barcode;

            for (int index = firstContainerIndex; index <= lastContainerIndex; index++) {
                BoxLookupReturnRecords boxRecord = containerTableModel.getElementAt(index);

                barcode = JOptionPane.showInputDialog(parentFrame, "Enter Barcode for " + boxRecord);

                if (barcode != null && barcode.length() != 0) {
                    boxRecord.setBarcode(barcode);
                    String instanceIds = boxRecord.getInstanceIds();

                    try {
                        boxLookupAndUpdate.updateBarcode(instanceIds, barcode);
                    } catch (Exception e1) {
                        showInstanceUpdateErrorDialog(boxRecord.toString());
                        e1.printStackTrace();
                    }

                } else {
                    //break out of the for loop since cancel was pressed
                    break;
                }
            }

            recordDirty = true;

            containerLookupTable.invalidate();
            containerLookupTable.repaint();
        }
    }

    /**
     * Method to assign voyager information to the selected containers
     * @param e
     */
    private void assignVoyagerInfoActionPerformed(ActionEvent e) {
        if (containerLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select at least one container.");
        } else {
            VoyagerInputValuesDialog dialog = new VoyagerInputValuesDialog(this);

            if (dialog.showDialog() == JOptionPane.OK_OPTION) {
                final int[] selectedRows = containerLookupTable.getSelectedRows();

                // disable the UI buttons and set the progress monitor moving
                setUIButtonsEnable(false);
                updateProgressBar.setStringPainted(true);
                updateProgressBar.setString("Updating Voyager Info ..."); // this doesn't show up!
                updateProgressBar.setMinimum(0);
                updateProgressBar.setMaximum(getInstanceCount(selectedRows));

                final String bibHolding = dialog.getKeyHolding();

                // run the update process in a separate thread
                Thread performer = new Thread(new Runnable() {
                    public void run() {
                        int instances = 0;

                        for (int i : selectedRows) {
                            BoxLookupReturnRecords boxRecord = containerTableModel.getElementAt(i);

                            try {
                                // need to show a progress dialog here and call update voyager info in thread
                                instances += boxLookupAndUpdate.updateVoyagerInformation(boxRecord.getInstanceIds(), bibHolding);

                                // update the box record voyager information
                                boxRecord.setVoyagerInfo(bibHolding);

                                // update the progress bar
                                updateProgressBar.setValue(instances);
                            } catch (Exception e1) {
                                showInstanceUpdateErrorDialog(boxRecord.toString());
                                e1.printStackTrace();
                            }
                        }

                        System.out.println("Total # of Instances Updated: " + instances);

                        // re-enable the ui buttons and update other UI stuff
                        setUIButtonsEnable(true);
                        updateProgressBar.setStringPainted(false);
                        updateProgressBar.setValue(0);

                        containerLookupTable.invalidate();
                        containerLookupTable.repaint();
                    }
                });

                // start the thread now
                performer.start();
            }
        }
    }

    /**
     * Method to get the instance count of the selected row
     *
     * @param selectedRows
     * @return
     */
    private int getInstanceCount(int[] selectedRows) {
        int total = 0;

        for (int i : selectedRows) {
            BoxLookupReturnRecords boxRecord = containerTableModel.getElementAt(i);
            String[] instanceIds = boxRecord.getInstanceIds().split(",\\s*");
            total += instanceIds.length;
        }

        return total;
    }

    /**
     * Method used to enable or disable the UI buttons when doing a long task in a thread
     *
     * @param enable
     */
    private void setUIButtonsEnable(boolean enable) {
        assignContainerInformation.setEnabled(enable);
        rapidBarcodeEntry.setEnabled(enable);
        assignVoyagerInfo.setEnabled(enable);
        assignLocation.setEnabled(enable);
        removeAssignedLocationButton.setEnabled(enable);
        createLocationButton.setEnabled(enable);
        doneButton.setEnabled(enable);
    }

    /**
     * Method to display an error dialog indicating an error occurred while updating
     * instance information
     *
     * @param containerName
     */
    private void showInstanceUpdateErrorDialog(String containerName) {
        JOptionPane.showMessageDialog(parentFrame,
                "Failed to update instances for container " + containerName,
                "Instance Update Fail",
                JOptionPane.ERROR_MESSAGE);
    }

    public JButton getCreateLocationButton() {
        return createLocationButton;
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        HeaderPanel = new JPanel();
        panel2 = new JPanel();
        mainHeaderLabel = new JLabel();
        panel3 = new JPanel();
        subHeaderLabel = new JLabel();
        contentPane = new JPanel();
        containerLabel = new JLabel();
        panel5 = new JPanel();
        label4 = new JLabel();
        containerFilterField = new JTextField();
        scrollPane1 = new JScrollPane();
        containerLookupTable = createYaleTable();
        panel4 = new JPanel();
        assignContainerInformation = new JButton();
        rapidBarcodeEntry = new JButton();
        assignVoyagerInfo = new JButton();
        updateProgressBar = new JProgressBar();
        separator5 = new JSeparator();
        label1 = new JLabel();
        panel1 = new JPanel();
        label2 = new JLabel();
        filterField = new JTextField();
        scrollPane2 = new JScrollPane();
        locationLookupTable = new DomainSortableTable(Locations.class, filterField);
        buttonBar = new JPanel();
        assignLocation = new JButton();
        removeAssignedLocationButton = new JButton();
        createLocationButton = new JButton();
        doneButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setModal(true);
        setTitle("Assign Container Location");
        Container contentPane2 = getContentPane();
        contentPane2.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(null);
            dialogPane.setBackground(new Color(200, 205, 232));
            dialogPane.setMinimumSize(new Dimension(640, 836));
            dialogPane.setLayout(new BorderLayout());

            //======== HeaderPanel ========
            {
                HeaderPanel.setBackground(new Color(80, 69, 57));
                HeaderPanel.setOpaque(false);
                HeaderPanel.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
                HeaderPanel.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        new ColumnSpec(Sizes.bounded(Sizes.MINIMUM, Sizes.dluX(100), Sizes.dluX(200))),
                        new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                    },
                    RowSpec.decodeSpecs("default")));

                //======== panel2 ========
                {
                    panel2.setBackground(new Color(73, 43, 104));
                    panel2.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
                    panel2.setLayout(new FormLayout(
                        new ColumnSpec[] {
                            FormFactory.RELATED_GAP_COLSPEC,
                            new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        },
                        new RowSpec[] {
                            FormFactory.RELATED_GAP_ROWSPEC,
                            FormFactory.DEFAULT_ROWSPEC,
                            FormFactory.RELATED_GAP_ROWSPEC
                        }));

                    //---- mainHeaderLabel ----
                    mainHeaderLabel.setText("Resources");
                    mainHeaderLabel.setFont(new Font("Trebuchet MS", Font.PLAIN, 18));
                    mainHeaderLabel.setForeground(Color.white);
                    panel2.add(mainHeaderLabel, cc.xy(2, 2));
                }
                HeaderPanel.add(panel2, cc.xy(1, 1));

                //======== panel3 ========
                {
                    panel3.setBackground(new Color(66, 60, 111));
                    panel3.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
                    panel3.setLayout(new FormLayout(
                        new ColumnSpec[] {
                            FormFactory.RELATED_GAP_COLSPEC,
                            new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        },
                        new RowSpec[] {
                            FormFactory.RELATED_GAP_ROWSPEC,
                            FormFactory.DEFAULT_ROWSPEC,
                            FormFactory.RELATED_GAP_ROWSPEC
                        }));

                    //---- subHeaderLabel ----
                    subHeaderLabel.setText("Assign Locations");
                    subHeaderLabel.setFont(new Font("Trebuchet MS", Font.PLAIN, 18));
                    subHeaderLabel.setForeground(Color.white);
                    panel3.add(subHeaderLabel, cc.xy(2, 2));
                }
                HeaderPanel.add(panel3, cc.xy(2, 1));
            }
            dialogPane.add(HeaderPanel, BorderLayout.NORTH);

            //======== contentPane ========
            {
                contentPane.setBackground(new Color(231, 188, 251));
                contentPane.setMinimumSize(new Dimension(600, 600));
                contentPane.setOpaque(false);
                contentPane.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        FormFactory.UNRELATED_GAP_COLSPEC,
                        new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                        FormFactory.UNRELATED_GAP_COLSPEC
                    },
                    new RowSpec[] {
                        FormFactory.UNRELATED_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        new RowSpec(RowSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        new RowSpec(RowSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.UNRELATED_GAP_ROWSPEC
                    }));

                //---- containerLabel ----
                containerLabel.setText("Containers");
                contentPane.add(containerLabel, cc.xy(2, 2));

                //======== panel5 ========
                {
                    panel5.setOpaque(false);
                    panel5.setLayout(new FormLayout(
                        new ColumnSpec[] {
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        },
                        RowSpec.decodeSpecs("default")));

                    //---- label4 ----
                    label4.setText("Filter: ");
                    panel5.add(label4, cc.xy(1, 1));
                    panel5.add(containerFilterField, cc.xy(3, 1));
                }
                contentPane.add(panel5, cc.xy(2, 4));

                //======== scrollPane1 ========
                {
                    scrollPane1.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                    scrollPane1.setPreferredSize(new Dimension(600, 300));
                    scrollPane1.setViewportView(containerLookupTable);
                }
                contentPane.add(scrollPane1, cc.xy(2, 6));

                //======== panel4 ========
                {
                    panel4.setOpaque(false);
                    panel4.setLayout(new FormLayout(
                        new ColumnSpec[] {
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        },
                        RowSpec.decodeSpecs("default")));

                    //---- assignContainerInformation ----
                    assignContainerInformation.setText("Assign Container Information");
                    assignContainerInformation.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            assignContainerInformationActionPerformed(e);
                        }
                    });
                    panel4.add(assignContainerInformation, cc.xy(1, 1));

                    //---- rapidBarcodeEntry ----
                    rapidBarcodeEntry.setText("Rapid Barcode Entry");
                    rapidBarcodeEntry.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            rapidBarcodeEntryActionPerformed(e);
                        }
                    });
                    panel4.add(rapidBarcodeEntry, cc.xy(3, 1));

                    //---- assignVoyagerInfo ----
                    assignVoyagerInfo.setText("Assign Voyager Info");
                    assignVoyagerInfo.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            assignVoyagerInfoActionPerformed(e);
                        }
                    });
                    panel4.add(assignVoyagerInfo, cc.xy(5, 1));
                    panel4.add(updateProgressBar, cc.xy(7, 1));
                }
                contentPane.add(panel4, cc.xy(2, 8));

                //---- separator5 ----
                separator5.setBackground(new Color(220, 220, 232));
                separator5.setForeground(new Color(147, 131, 86));
                separator5.setMinimumSize(new Dimension(1, 10));
                separator5.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
                contentPane.add(separator5, cc.xywh(1, 10, 3, 1));

                //---- label1 ----
                label1.setText("Locations");
                contentPane.add(label1, cc.xy(2, 11));

                //======== panel1 ========
                {
                    panel1.setOpaque(false);
                    panel1.setLayout(new FormLayout(
                        new ColumnSpec[] {
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        },
                        RowSpec.decodeSpecs("default")));

                    //---- label2 ----
                    label2.setText("Filter:");
                    panel1.add(label2, cc.xy(1, 1));
                    panel1.add(filterField, cc.xy(3, 1));
                }
                contentPane.add(panel1, cc.xy(2, 13));

                //======== scrollPane2 ========
                {
                    scrollPane2.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                    scrollPane2.setPreferredSize(new Dimension(600, 300));

                    //---- locationLookupTable ----
                    locationLookupTable.setPreferredScrollableViewportSize(new Dimension(450, 300));
                    scrollPane2.setViewportView(locationLookupTable);
                }
                contentPane.add(scrollPane2, cc.xy(2, 15));

                //======== buttonBar ========
                {
                    buttonBar.setBorder(Borders.BUTTON_BAR_GAP_BORDER);
                    buttonBar.setBackground(new Color(231, 188, 251));
                    buttonBar.setOpaque(false);
                    buttonBar.setLayout(new FormLayout(
                        new ColumnSpec[] {
                            FormFactory.GLUE_COLSPEC,
                            FormFactory.BUTTON_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                            FormFactory.DEFAULT_COLSPEC,
                            FormFactory.RELATED_GAP_COLSPEC,
                            FormFactory.BUTTON_COLSPEC
                        },
                        RowSpec.decodeSpecs("pref")));

                    //---- assignLocation ----
                    assignLocation.setText("Add Location Link");
                    assignLocation.setBackground(new Color(231, 188, 251));
                    assignLocation.setOpaque(false);
                    assignLocation.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            assignLocationButtonActionPerformed(e);
                        }
                    });
                    buttonBar.add(assignLocation, cc.xy(2, 1));

                    //---- removeAssignedLocationButton ----
                    removeAssignedLocationButton.setText("Remove Location Link");
                    removeAssignedLocationButton.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            removeAssignedLocationButtonActionPerformed(e);
                        }
                    });
                    buttonBar.add(removeAssignedLocationButton, cc.xy(4, 1));

                    //---- createLocationButton ----
                    createLocationButton.setText("Create Location");
                    createLocationButton.setBackground(new Color(231, 188, 251));
                    createLocationButton.setOpaque(false);
                    createLocationButton.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            createLocationButtonActionPerformed(e);
                        }
                    });
                    buttonBar.add(createLocationButton, cc.xy(6, 1));

                    //---- doneButton ----
                    doneButton.setText("Done");
                    doneButton.setBackground(new Color(231, 188, 251));
                    doneButton.setOpaque(false);
                    doneButton.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            doneButtonActionPerformed(e);
                        }
                    });
                    buttonBar.add(doneButton, cc.xy(8, 1));
                }
                contentPane.add(buttonBar, cc.xywh(1, 17, 3, 1, CellConstraints.CENTER, CellConstraints.DEFAULT));
            }
            dialogPane.add(contentPane, BorderLayout.CENTER);
        }
        contentPane2.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    private void assignLocationButtonActionPerformed(ActionEvent e) {
        if (locationLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select a location first.");
        } else if (containerLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select a container first.");
        } else {
            int selectedRow = locationLookupTable.getSelectedRow();
            Locations selectedLocation = (Locations) lookupTableModel.getElementAt(selectedRow);

            int[] selectedRows = containerLookupTable.getSelectedRows();
            BoxLookupReturnRecords boxRecord = null;

            try {
                // for each box record update all the instances associated with each
                int instances = 0;

                for (int i : selectedRows) {
                    boxRecord = containerTableModel.getElementAt(i);
                    String instanceIds = boxRecord.getInstanceIds();

                    instances += boxLookupAndUpdate.updateInstanceLocation(instanceIds, selectedLocation);

                    // update the box information now
                    boxRecord.setLocation(selectedLocation.toString());
                }

                recordDirty = true;

                System.out.println("Total # of Instances Updated: " + instances);
            } catch (Exception e1) {
                showInstanceUpdateErrorDialog(boxRecord.toString());
                e1.printStackTrace();
            }

            // update the table
            containerLookupTable.invalidate();
            containerLookupTable.repaint();
        }
    }

    private void doneButtonActionPerformed(ActionEvent e) {
        // if any changes were made save the updated box collection record
        if(recordDirty) {
            try {
                PluginDataUtils.saveBoxLookReturnRecord(boxCollection);
            } catch(Exception ex) {
                new ErrorDialog("", ex).showDialog();
            }
        }

        // hide this window
        this.setVisible(false);
    }

    public JButton getAssignLocation() {
        return assignLocation;
    }

    public JButton getDoneButton() {
        return doneButton;
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel HeaderPanel;
    private JPanel panel2;
    private JLabel mainHeaderLabel;
    private JPanel panel3;
    private JLabel subHeaderLabel;
    private JPanel contentPane;
    private JLabel containerLabel;
    private JPanel panel5;
    private JLabel label4;
    private JTextField containerFilterField;
    private JScrollPane scrollPane1;
    private JTable containerLookupTable;
    private JPanel panel4;
    private JButton assignContainerInformation;
    private JButton rapidBarcodeEntry;
    private JButton assignVoyagerInfo;
    private JProgressBar updateProgressBar;
    private JSeparator separator5;
    private JLabel label1;
    private JPanel panel1;
    private JLabel label2;
    private JTextField filterField;
    private JScrollPane scrollPane2;
    private DomainSortableTable locationLookupTable;
    private JPanel buttonBar;
    private JButton assignLocation;
    private JButton removeAssignedLocationButton;
    private JButton createLocationButton;
    private JButton doneButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

    /**
     * The status of the editor.
     */
    protected int status = 0;
    FilterList textFilteredIssues;
    EventTableModel lookupTableModel;

    /*public void assignContainerListValues(Collection<ContainerGroup> values) {
        containerListValues = values;
        //containerList.setListData(values.toArray());
    }*/

    /**
     * Method the list of boxes for this resource record
     *
     * @param boxCollection The collection of containers along with some other information
     */
    public void assignContainerListValues(BoxLookupReturnRecordsCollection boxCollection) {
        this.boxCollection = boxCollection;
        totalInstances = boxCollection.getTotalInstances();
        containerListValues = boxCollection.getContainers();
        resultsEventList.clear();
        resultsEventList.addAll(containerListValues);

        // update the display now
        containerLabel.setText("Containers: " + containerListValues.size() + " || Analog Instances: " + totalInstances);
    }

    private void initLookup() {
        SortedList sortedLocations = LocationsUtils.getLocationsGlazedList();
        textFilteredIssues = new FilterList(sortedLocations, new TextComponentMatcherEditor(filterField, new DomainFilterator()));
        lookupTableModel = new EventTableModel(textFilteredIssues, new DomainTableFormat(Locations.class));
        locationLookupTable.setModel(lookupTableModel);
        TableComparatorChooser tableSorter = new TableComparatorChooser(locationLookupTable, sortedLocations, true);
        filterField.requestFocusInWindow();
    }

    /**
     * Displays the dialog box representing the editor.
     *
     * @return true if it displayed okay
     */

    public final int showDialog() {
        this.pack();

        setLocationRelativeTo(getOwner());
        this.setVisible(true);

        return (status);
    }

    /**
     * Method to set the highlight color of the Jtable
     * @param highlightColor
     */
    public void setHighlightColor(Color highlightColor) {
        ((YaleAlternatingRowColorTable)containerLookupTable).setHighlightColor(highlightColor);
    }
}