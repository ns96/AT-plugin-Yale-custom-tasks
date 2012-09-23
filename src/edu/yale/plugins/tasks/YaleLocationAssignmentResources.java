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
import edu.yale.plugins.tasks.table.BoxLookupTableFormat;
import edu.yale.plugins.tasks.table.YaleAlternatingRowColorTable;
import edu.yale.plugins.tasks.utils.BoxLookupAndUpdate;
import edu.yale.plugins.tasks.voyager.VoyagerInputValuesDialog;
import org.archiviststoolkit.ApplicationFrame;
import org.archiviststoolkit.dialog.ErrorDialog;
import org.archiviststoolkit.editor.LocationEditor;
import org.archiviststoolkit.model.ArchDescriptionAnalogInstances;
import org.archiviststoolkit.model.ContainerGroup;
import org.archiviststoolkit.model.Locations;
import org.archiviststoolkit.mydomain.*;
import org.archiviststoolkit.util.LocationsUtils;
import org.archiviststoolkit.util.StringHelper;
import org.hibernate.exception.ConstraintViolationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class YaleLocationAssignmentResources extends JDialog {
    // used in the table
    private EventList<BoxLookupReturnRecords> resultsEventList = new BasicEventList<BoxLookupReturnRecords>();

    // The container table model
    EventTableModel<BoxLookupReturnRecords> containerTableModel = null;

    //private Collection<ContainerGroup> containerListValues;
    private Collection<BoxLookupReturnRecords> containerListValues;

    // The parent frame
    private Frame parentFrame = null;

    public YaleLocationAssignmentResources(Frame owner) {
        super(owner);
        initComponents();
        initLookup();
        this.parentFrame = owner;
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
        FilterList filterList = new FilterList(sortedResults, new TextComponentMatcherEditor(containerFilterField, new DomainFilterator()));
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
//				DomainObject link =  accessionsModel.addLocation(instance);
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

    public JButton getCreateLocationButton() {
        return createLocationButton;
    }

    private void removeAssignedLocationButtonActionPerformed(ActionEvent e) {
        /*if (containerList.getSelectedIndex() == -1) {
            JOptionPane.showMessageDialog(this, "You must select a container first.");
        } else {
            Object[] selectedContainers = containerList.getSelectedValues();

            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to remove " + selectedContainers.length + " linked location(s)",
                    "Remove Linked Location", JOptionPane.YES_NO_OPTION);

            if (response == JOptionPane.OK_OPTION) {
                for (Object o : selectedContainers) {
                    ((ContainerGroup) o).setLocations(null);
                }
                //set the record to dirty
                ApplicationFrame.getInstance().setRecordDirty();
                containerList.invalidate();
                containerList.repaint();
            }
        }*/
    }

    private void assignContainerInformationActionPerformed(ActionEvent e) {
        System.out.println("do assign container");

        if (containerLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select at least one container.");
        } else {
            YaleAssignContainerInformation dialog = new YaleAssignContainerInformation(parentFrame);

            int status = dialog.showDialog();

            if (status == JOptionPane.OK_OPTION) {
                try {
                    BoxLookupAndUpdate boxUpdater = new BoxLookupAndUpdate();

                    String barcode = dialog.getBarcode();
                    String container3Type = dialog.getContainer3Type();
                    String userDefinedString2 = dialog.getUserDefinedString2();
                    Boolean changeRestriction = dialog.restrictionChange();
                    Boolean restriction = dialog.newRestrictionValue();
                    Boolean changeExportedToVoyager = dialog.exportedToVoyagerChange();
                    Boolean exportedToVoyager = dialog.newExportedToVoyagerValue();

                    int[] selectedRows = containerLookupTable.getSelectedRows();

                    for (int i : selectedRows) {
                        BoxLookupReturnRecords boxRecord = containerTableModel.getElementAt(i);
                        String instanceIds = boxRecord.getInstanceIds();

                        if (barcode.length() != 0) {
                            boxRecord.setBarcode(barcode);
                        }

                        // use the box updater class to make updates using sql calls
                        boxUpdater.updateInstanceInformation(
                                instanceIds,
                                barcode,
                                container3Type,
                                userDefinedString2,
                                changeRestriction,
                                restriction,
                                changeExportedToVoyager,
                                exportedToVoyager
                        );

                        /*for (ArchDescriptionAnalogInstances instance : group.getInstances()) {
                            if (barcode.length() != 0) {
                                instance.setBarcode(barcode);
                            }
                            if (container3Type.length() != 0) {
                                instance.setContainer3Type(container3Type);
                            }
                            if (userDefinedString2.length() != 0) {
                                instance.setUserDefinedString2(userDefinedString2);
                            }
                            if (dialog.restrictionChange()) {
                                instance.setUserDefinedBoolean1(dialog.newRestrictionValue());
                            }
                            if (dialog.exportedToVoyagerChange()) {
                                instance.setUserDefinedBoolean2(dialog.newExportedToVoyagerValue());
                            }

                            //boxRecord.setTopLevelContainerName(instance.getTopLevelLabel() + " (" + instance.getBarcode() + ")");
                        }*/
                    }
                } catch (Exception error) {
                    error.printStackTrace();
                }
                //set the record to dirty
                //ApplicationFrame.getInstance().setRecordDirty();

                containerLookupTable.invalidate();
                containerLookupTable.repaint();
            }
        }
    }

    private void rapidBarcodeEntryActionPerformed(ActionEvent e) {
        /*int firstContainerIndex = containerList.getSelectedIndex();
        if (firstContainerIndex == -1) {
            JOptionPane.showMessageDialog(this, "You must select a container to start first.");
        } else {
            Object[] selectedContainers = containerList.getSelectedValues();
            if (selectedContainers.length == 1) {
                //just one container was selected so build the array starting at that index
                ArrayList objectList = new ArrayList();
                int lastContainerIndex = containerList.getModel().getSize();
                for (int index = firstContainerIndex; index < lastContainerIndex; index++) {
                    objectList.add(containerList.getModel().getElementAt(index));
                }
                selectedContainers = objectList.toArray();
            }
            String barcode;
            ApplicationFrame applicationFrame = ApplicationFrame.getInstance();
            for (Object o : selectedContainers) {
                ContainerGroup containerGroup = (ContainerGroup) o;
                barcode = JOptionPane.showInputDialog(this, "Enter Barcode for " + containerGroup.getTopLevelContainerName());
                if (barcode != null && barcode.length() != 0) {
                    for (ArchDescriptionAnalogInstances instance : containerGroup.getInstances()) {
                        instance.setBarcode(barcode);
                        containerGroup.setTopLevelContainerName(instance.getTopLevelLabel() + " (" + instance.getBarcode() + ")");
                        //set the record to dirty
                        applicationFrame.setRecordDirty();
                    }
                } else {
                    //break out of the for loop since cancel was pressed
                    break;
                }
            }
            containerList.invalidate();
            containerList.repaint();
        }*/
    }

    private void assignVoyagerInfoActionPerformed(ActionEvent e) {

        VoyagerInputValuesDialog dialog = new VoyagerInputValuesDialog(this);

        if (dialog.showDialog() == JOptionPane.OK_OPTION) {
            String bibHolding = dialog.getKeyHolding();
//				System.out.println("Bibholding: " + bibHolding);
            ContainerGroup containerGroup;
            ApplicationFrame applicationFrame = ApplicationFrame.getInstance();
            for (Object o : containerListValues) {
                containerGroup = (ContainerGroup) o;
                for (ArchDescriptionAnalogInstances instance : containerGroup.getInstances()) {
                    instance.setUserDefinedString1(bibHolding);
                    //set the record to dirty
                    applicationFrame.setRecordDirty();
                }

            }
        }
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
                        new ColumnSpec[]{
                                new ColumnSpec(Sizes.bounded(Sizes.MINIMUM, Sizes.dluX(100), Sizes.dluX(200))),
                                new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                        },
                        RowSpec.decodeSpecs("default")));

                //======== panel2 ========
                {
                    panel2.setBackground(new Color(73, 43, 104));
                    panel2.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
                    panel2.setLayout(new FormLayout(
                            new ColumnSpec[]{
                                    FormFactory.RELATED_GAP_COLSPEC,
                                    new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                            },
                            new RowSpec[]{
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
                            new ColumnSpec[]{
                                    FormFactory.RELATED_GAP_COLSPEC,
                                    new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                            },
                            new RowSpec[]{
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
                        new ColumnSpec[]{
                                FormFactory.UNRELATED_GAP_COLSPEC,
                                new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                                FormFactory.UNRELATED_GAP_COLSPEC
                        },
                        new RowSpec[]{
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
                            new ColumnSpec[]{
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
                            new ColumnSpec[]{
                                    FormFactory.DEFAULT_COLSPEC,
                                    FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                                    FormFactory.DEFAULT_COLSPEC,
                                    FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                                    FormFactory.DEFAULT_COLSPEC
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
                            new ColumnSpec[]{
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
                            new ColumnSpec[]{
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

    public JList getContainerList() {
        //return containerList;
        return null;
    }

    private void assignLocationButtonActionPerformed(ActionEvent e) {
        /*if (locationLookupTable.getSelectedRow() == -1) {
            JOptionPane.showMessageDialog(this, "You must select a location first.");
        } else if (containerList.getSelectedIndex() == -1) {
            JOptionPane.showMessageDialog(this, "You must select a container first.");
        } else {
            int selectedRow = locationLookupTable.getSelectedRow();
            Locations selectedLocation = (Locations) lookupTableModel.getElementAt(selectedRow);
            Object[] selectedContainers = containerList.getSelectedValues();
            for (Object o : selectedContainers) {
                ((ContainerGroup) o).setLocations(selectedLocation);
            }
            //set the record to dirty
            ApplicationFrame.getInstance().setRecordDirty();
            containerList.invalidate();
            containerList.repaint();
        }*/
    }

    private void doneButtonActionPerformed(ActionEvent e) {
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
     * @param values
     */
    public void assignContainerListValues(Collection<BoxLookupReturnRecords> values) {
        containerListValues = values;
        resultsEventList.clear();
        resultsEventList.addAll(containerListValues);
        containerLabel.setText("Containers: " + containerListValues.size() + " found");
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

}