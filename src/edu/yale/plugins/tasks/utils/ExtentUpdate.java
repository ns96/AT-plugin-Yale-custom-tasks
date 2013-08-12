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
 * @author Lee Mandell
 * Date: Oct 16, 2009
 * Time: 1:35:14 PM
 */

package edu.yale.plugins.tasks.utils;

import org.archiviststoolkit.hibernate.SessionFactory;
import org.archiviststoolkit.mydomain.DomainAccessObject;
import org.archiviststoolkit.util.StringHelper;

import java.sql.*;
import java.util.HashMap;

public class ExtentUpdate {
    private HashMap<Long, String> componentTitleLookup = new HashMap<Long, String>();

    private HashMap<String, String> componentInfoLookup = null;

    private String logMessage = "";

    private DomainAccessObject locationDAO;

    private DomainAccessObject instanceDAO;

    private Connection con;

    // prepared statements used when searching
    private PreparedStatement resourceIdLookup;
    private PreparedStatement componentLookupByResource;
    private PreparedStatement componentLookupByComponent;
    private PreparedStatement instanceLookupByComponent;

    // keep track of the number of instances processed
    private int noteCount = 0;

    /**
     * The main constructor
     *
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public ExtentUpdate() throws SQLException, ClassNotFoundException {
        Class.forName(SessionFactory.getDriverClass());
        con = DriverManager.getConnection(SessionFactory.getDatabaseUrl(),
                SessionFactory.getUserName(),
                SessionFactory.getPassword());
    }

    /**
     * Method to find all  notes containing extent information
     * @throws SQLException
     */
    public void findNotesContainingExtentInfo() throws SQLException {
        String sqlString = "SELECT noteContent, resourceComponentId FROM `ArchDescriptionRepeatingData` \n" +
                "WHERE notesEtcTypeId='16'" ;

        Statement sqlStatement = con.createStatement();
        ResultSet notes = sqlStatement.executeQuery(sqlString);
        extractExtentInformationFromNote(notes);
    }

    /**
     * Add the noteContent to the conponent Info hash map
     * @param notes
     * @throws SQLException
     */
    private void extractExtentInformationFromNote(ResultSet notes) throws SQLException {
        int count = 0;
        while(notes.next()) {
            Long id = notes.getLong("resourceComponentId");
            String noteContent = notes.getString("noteContent");

            if(noteContent != null && !noteContent.isEmpty()) {
                noteContent = noteContent.replace("\n", " ");

                if(noteContent.contains("<extent>")) {
                    count++;
                    System.out.println(count + " \tContent:: " + noteContent);

                    noteContent = StringHelper.tagRemover(noteContent);
                    String[] sa = noteContent.split("\\s+", 2);

                    // get the extent number
                    Double extentNumber = 1.0;
                    String extentType = noteContent;

                    if(sa.length == 2) {
                        try {
                            extentNumber = Double.parseDouble(sa[0]);
                            extentType = sa[1];
                        } catch(NumberFormatException nfe) { }
                    }

                    System.out.println("\tExtent Number = " + extentNumber);
                    System.out.println("\tExtent Type = " + extentType + "\n");
                }

            }
        }
    }
}
