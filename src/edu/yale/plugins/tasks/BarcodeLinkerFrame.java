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
            barcodeLinker = new BarcodeLinker();
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
    private void linkContainersToLocation(Locations location) {
        String barcodesText = barcodesTextArea.getText().trim();
        if(!barcodesText.isEmpty()) {
            String[] sa = barcodesText.split("\\s*\n");
            barcodeLinker.linkContainers(location, sa);
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
        String barcode = searchTextField.getText();

        if(!barcode.isEmpty()) {
            ArrayList<String> barcodeList = barcodeLinker.findInstacesByBarcode(barcode);

            String barcodes = "";
            for(String foundBarcode: barcodeList) {
                barcodes += foundBarcode + "\n";
            }

            instanceCountLabel.setText("Instances (" + barcodeList.size() + ")");
            barcodesTextArea.setText(barcodes.trim());
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
        buttonBar = new JPanel();
        testOnlyCheckBox = new JCheckBox();
        okButton = new JButton();
        cancelButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setTitle("Barcode Linker");
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
                    barcodesTextArea.setRows(25);
                    scrollPane1.setViewportView(barcodesTextArea);
                }
                contentPanel.add(scrollPane1, cc.xy(3, 5));

                //---- instanceCountLabel ----
                instanceCountLabel.setText("Find Instances");
                contentPanel.add(instanceCountLabel, cc.xy(1, 7));

                //---- searchTextField ----
                searchTextField.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        searchTextFieldActionPerformed();
                    }
                });
                contentPanel.add(searchTextField, cc.xy(3, 7));
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
                        FormFactory.BUTTON_COLSPEC,
                        FormFactory.RELATED_GAP_COLSPEC,
                        FormFactory.BUTTON_COLSPEC
                    },
                    RowSpec.decodeSpecs("pref")));

                //---- testOnlyCheckBox ----
                testOnlyCheckBox.setText("Test Linking Only");
                buttonBar.add(testOnlyCheckBox, cc.xy(2, 1));

                //---- okButton ----
                okButton.setText("Link");
                okButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        okButtonActionPerformed();
                    }
                });
                buttonBar.add(okButton, cc.xy(4, 1));

                //---- cancelButton ----
                cancelButton.setText("Close");
                cancelButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        cancelButtonActionPerformed();
                    }
                });
                buttonBar.add(cancelButton, cc.xy(6, 1));
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
    private JPanel buttonBar;
    private JCheckBox testOnlyCheckBox;
    private JButton okButton;
    private JButton cancelButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
