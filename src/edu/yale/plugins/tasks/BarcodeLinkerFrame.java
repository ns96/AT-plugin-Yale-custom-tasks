/*
 * Created by JFormDesigner on Fri Aug 09 14:19:06 EDT 2013
 */

package edu.yale.plugins.tasks;

import java.awt.*;
import javax.swing.*;
import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;

/**
 * @author Nathan Stevens
 */
public class BarcodeLinkerFrame extends JFrame {
    public BarcodeLinkerFrame() {
        initComponents();
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        label1 = new JLabel();
        locationBarcodeTextField = new JTextField();
        label2 = new JLabel();
        scrollPane1 = new JScrollPane();
        textArea1 = new JTextArea();
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
                        new RowSpec(RowSpec.TOP, Sizes.DEFAULT, FormSpec.NO_GROW)
                    }));

                //---- label1 ----
                label1.setText("Location Barcode");
                contentPanel.add(label1, cc.xy(1, 1));

                //---- locationBarcodeTextField ----
                locationBarcodeTextField.setColumns(40);
                contentPanel.add(locationBarcodeTextField, cc.xy(3, 1));

                //---- label2 ----
                label2.setText("Container Barcodes");
                contentPanel.add(label2, cc.xy(1, 3));

                //======== scrollPane1 ========
                {

                    //---- textArea1 ----
                    textArea1.setRows(25);
                    scrollPane1.setViewportView(textArea1);
                }
                contentPanel.add(scrollPane1, cc.xy(3, 3));
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
                buttonBar.add(okButton, cc.xy(4, 1));

                //---- cancelButton ----
                cancelButton.setText("Close");
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
    private JLabel label2;
    private JScrollPane scrollPane1;
    private JTextArea textArea1;
    private JPanel buttonBar;
    private JCheckBox testOnlyCheckBox;
    private JButton okButton;
    private JButton cancelButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
