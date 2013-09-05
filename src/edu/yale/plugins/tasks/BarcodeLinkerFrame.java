/*
 * Created by JFormDesigner on Fri Aug 09 14:19:06 EDT 2013
 */

package edu.yale.plugins.tasks;

import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.*;
import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;
import edu.yale.plugins.tasks.utils.BarcodeLinker;
import org.archiviststoolkit.importer.ImportExportLogDialog;
import org.archiviststoolkit.model.Locations;

/**
 * @author Nathan Stevens
 */
public class BarcodeLinkerFrame extends JFrame {
    private BarcodeLinker barcodeLinker;

    public BarcodeLinkerFrame() {
        initComponents();
        initBarcodeLinker();
    }

    /**
     * init the barcode linker object
     */
    private void initBarcodeLinker() {
        try {
            barcodeLinker = new BarcodeLinker(this);
        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ClassNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /**
     * The OK button was pressed
     */
    private void okButtonActionPerformed() {
        Locations location = findLocation();

        if(location != null) {
            linkContainersToLocation(location);
        }
    }

    /**
     * Method to link the containers with the barcodes to the particular location
     * @param location
     */
    private void linkContainersToLocation(final Locations location) {
        String barcodesText = barcodesTextArea.getText().trim();
        if(!barcodesText.isEmpty()) {
            final String[] sa = barcodesText.split("\\s*\n");

            // start a thread to link the containers and locations
            Thread performer = new Thread(new Runnable() {
                public void run() {
                    // disable the link button and reset the progress bar
                    okButton.setEnabled(false);
                    findButton.setEnabled(false);

                    progressBar.setMinimum(0);
                    progressBar.setMaximum(sa.length);
                    progressBar.setStringPainted(true);
                    progressBar.setString("Updating Location for " + sa.length + " Containers");

                    // now update the locations
                    barcodeLinker.linkContainers(location, sa, testOnlyCheckBox.isSelected());

                    // re-enable the button
                    okButton.setEnabled(true);
                    findButton.setEnabled(true);
                    progressBar.setValue(0);

                    // display the results
                    ImportExportLogDialog logDialog = new ImportExportLogDialog(null, ImportExportLogDialog.DIALOG_TYPE_IMPORT, barcodeLinker.getMessages());
                    logDialog.setTitle("Location to Container Linking Log");
                    logDialog.pack();
                    logDialog.setVisible(true);
                }
            });

            // start thread now
            performer.start();
        }
    }

    /**
     * Method to find the location
     * @return
     */
    private Locations findLocation() {
        String locationBarcode = locationBarcodeTextField.getText();
        Locations location = barcodeLinker.getLocationByBarcode(locationBarcode);

        if(location != null) {
            locationLabel.setText(location.toString());
        } else {
            locationLabel.setText("location not found ...");
        }

        return location;
    }

    /**
     * Method to search for instances
     */
    private void searchTextFieldActionPerformed() {
        final String barcode = searchTextField.getText();

        if(!barcode.isEmpty()) {
            Thread performer = new Thread(new Runnable() {
                public void run() {
                    // disable the link button and reset the progress bar
                    okButton.setEnabled(false);
                    findButton.setEnabled(false);

                    progressBar.setIndeterminate(true);
                    progressBar.setStringPainted(true);
                    progressBar.setString("Find Containers ...");

                    ArrayList<String> barcodeList = barcodeLinker.findUniqueBarcodes(barcode);

                    String barcodes = "";
                    for(String foundBarcode: barcodeList) {
                        barcodes += foundBarcode + "\n";
                    }

                    instanceCountLabel.setText("Containers (" + barcodeList.size() + ")");
                    barcodesTextArea.setText(barcodes.trim());

                    okButton.setEnabled(true);
                    findButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setStringPainted(false);
                }
            });

            performer.start();
        }
    }

    /**
     * Find the location and display it
     */
    private void locationBarcodeTextFieldActionPerformed() {
        findLocation();
    }

    /**
     * The cancel button pressed so hide the button
     */
    private void cancelButtonActionPerformed() {
        setVisible(false);
        dispose();
    }

    /**
     * Method to stop the linking progress
     */
    private void stopButtonActionPerformed() {
        barcodeLinker.stopOperation();
    }

    /**
     * Method to find locations based on a container barcode
     */
    private void findButtonActionPerformed() {
        String barcodesText = barcodesTextArea.getText().trim();
        if(!barcodesText.isEmpty()) {
            final String[] sa = barcodesText.split("\\s*\n");

            // start a thread to link the containers and locations
            Thread performer = new Thread(new Runnable() {
                public void run() {
                    // disable the link button and reset the progress bar
                    okButton.setEnabled(false);
                    findButton.setEnabled(true);

                    progressBar.setMinimum(0);
                    progressBar.setMaximum(sa.length);
                    progressBar.setStringPainted(true);
                    progressBar.setString("Finding Locations for " + sa.length + " Containers");

                    // now update the locations
                    barcodeLinker.findContainerLocations(sa);

                    // re-enable the button
                    okButton.setEnabled(true);
                    progressBar.setValue(0);

                    // display the results
                    ImportExportLogDialog logDialog = new ImportExportLogDialog(null, ImportExportLogDialog.DIALOG_TYPE_IMPORT, barcodeLinker.getMessages());
                    logDialog.setTitle("Container Locations Log");
                    logDialog.pack();
                    logDialog.setVisible(true);
                }
            });

            // start thread now
            performer.start();
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        label1 = new JLabel();
        locationBarcodeTextField = new JTextField();
        locationLabel = new JLabel();
        label2 = new JLabel();
        scrollPane1 = new JScrollPane();
        barcodesTextArea = new JTextArea();
        instanceCountLabel = new JLabel();
        searchTextField = new JTextField();
        progressBar = new JProgressBar();
        buttonBar = new JPanel();
        testOnlyCheckBox = new JCheckBox();
        okButton = new JButton();
        findButton = new JButton();
        stopButton = new JButton();
        cancelButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setTitle("Barcode Linker v0.1");
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
                        new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                    },
                    new RowSpec[] {
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        new RowSpec(RowSpec.TOP, Sizes.DEFAULT, FormSpec.NO_GROW),
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC
                    }));

                //---- label1 ----
                label1.setText("Location Barcode");
                contentPanel.add(label1, cc.xy(1, 1));

                //---- locationBarcodeTextField ----
                locationBarcodeTextField.setColumns(40);
                locationBarcodeTextField.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        locationBarcodeTextFieldActionPerformed();
                    }
                });
                contentPanel.add(locationBarcodeTextField, cc.xy(3, 1));

                //---- locationLabel ----
                locationLabel.setText("not found ...");
                contentPanel.add(locationLabel, cc.xy(3, 3));

                //---- label2 ----
                label2.setText("Container Barcodes");
                contentPanel.add(label2, cc.xy(1, 5));

                //======== scrollPane1 ========
                {

                    //---- barcodesTextArea ----
                    barcodesTextArea.setRows(15);
                    barcodesTextArea.setTabSize(4);
                    scrollPane1.setViewportView(barcodesTextArea);
                }
                contentPanel.add(scrollPane1, cc.xy(3, 5));

                //---- instanceCountLabel ----
                instanceCountLabel.setText("Find Containers");
                contentPanel.add(instanceCountLabel, cc.xy(1, 7));

                //---- searchTextField ----
                searchTextField.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        searchTextFieldActionPerformed();
                    }
                });
                contentPanel.add(searchTextField, cc.xy(3, 7));
                contentPanel.add(progressBar, cc.xywh(1, 9, 3, 1));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(Borders.BUTTON_BAR_GAP_BORDER);
                buttonBar.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.GLUE_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.BUTTON_COLSPEC,
                        FormFactory.RELATED_GAP_COLSPEC,
                        FormFactory.BUTTON_COLSPEC
                    },
                    RowSpec.decodeSpecs("pref")));

                //---- testOnlyCheckBox ----
                testOnlyCheckBox.setText("Test Linking Only");
                buttonBar.add(testOnlyCheckBox, cc.xy(2, 1));

                //---- okButton ----
                okButton.setText("Link Location");
                okButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        okButtonActionPerformed();
                    }
                });
                buttonBar.add(okButton, cc.xy(4, 1));

                //---- findButton ----
                findButton.setText("Find Location");
                findButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        findButtonActionPerformed();
                    }
                });
                buttonBar.add(findButton, cc.xy(6, 1));

                //---- stopButton ----
                stopButton.setText("Stop");
                stopButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        stopButtonActionPerformed();
                    }
                });
                buttonBar.add(stopButton, cc.xy(8, 1));

                //---- cancelButton ----
                cancelButton.setText("Close");
                cancelButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        cancelButtonActionPerformed();
                    }
                });
                buttonBar.add(cancelButton, cc.xy(10, 1));
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
    private JLabel label1;
    private JTextField locationBarcodeTextField;
    private JLabel locationLabel;
    private JLabel label2;
    private JScrollPane scrollPane1;
    private JTextArea barcodesTextArea;
    private JLabel instanceCountLabel;
    private JTextField searchTextField;
    private JProgressBar progressBar;
    private JPanel buttonBar;
    private JCheckBox testOnlyCheckBox;
    private JButton okButton;
    private JButton findButton;
    private JButton stopButton;
    private JButton cancelButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

    /**
     * Method to update the progress bar
     * @param i
     */
    public void updateProgress(int i) {
        progressBar.setValue(i);
    }
}
