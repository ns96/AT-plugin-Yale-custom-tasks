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

import edu.yale.plugins.tasks.model.BoxLookupReturnRecords;
import edu.yale.plugins.tasks.model.BoxLookupReturnRecordsCollection;
import edu.yale.plugins.tasks.search.BoxLookupReturnScreen;
import org.archiviststoolkit.dialog.ErrorDialog;
import org.archiviststoolkit.hibernate.SessionFactory;
import org.archiviststoolkit.model.ArchDescriptionAnalogInstances;
import org.archiviststoolkit.model.Locations;
import org.archiviststoolkit.model.Resources;
import org.archiviststoolkit.model.ResourcesComponents;
import org.archiviststoolkit.mydomain.*;
import org.archiviststoolkit.swing.InfiniteProgressPanel;
import org.archiviststoolkit.util.MyTimer;
import org.archiviststoolkit.util.StringHelper;

import java.io.PrintWriter;
import java.sql.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;

public class ResourceParentUpdater {
    private String logMessage = "";

    private Connection con;

    // prepared statements used when searching
    private PreparedStatement componentLookupByResource;
    private PreparedStatement componentLookupByComponent;

    // keep track of the number of instances processed
    private int componentCount = 0;

    public ResourceParentUpdater() throws SQLException, ClassNotFoundException {
        Class.forName(SessionFactory.getDriverClass());
        con = DriverManager.getConnection(SessionFactory.getDatabaseUrl(),
                SessionFactory.getUserName(),
                SessionFactory.getPassword());
    }

    /**
     * Method to initialize the set of prepared statement needed
     *
     * @throws Exception
     */
    private void initPreparedStatements() throws SQLException {
        String sqlString = "SELECT ResourcesComponents.resourceComponentId, \n" +
                "\tResourcesComponents.resourceLevel, \n" +
                "\tResourcesComponents.title, \n" +
                "\tResourcesComponents.hasChild, \n" +
                "\tResourcesComponents.extentType, \n" +
                "\tResourcesComponents.extentNumber, \n" +
                "\tResourcesComponents.dateExpression, \n" +
                "\tResourcesComponents.dateBegin, \n" +
                "\tResourcesComponents.dateEnd, \n" +
                "\tResourcesComponents.subdivisionIdentifier\n" +
                "FROM ResourcesComponents\n" +
                "WHERE ResourcesComponents.resourceId = ?";
        componentLookupByResource = con.prepareStatement(sqlString);

        sqlString = "SELECT ResourcesComponents.resourceComponentId, \n" +
                "\tResourcesComponents.resourceLevel, \n" +
                "\tResourcesComponents.title, \n" +
                "\tResourcesComponents.extentType, \n" +
                "\tResourcesComponents.extentNumber, \n" +
                "\tResourcesComponents.dateExpression, \n" +
                "\tResourcesComponents.dateBegin, \n" +
                "\tResourcesComponents.dateEnd, \n" +
                "\tResourcesComponents.hasChild\n" +
                "FROM ResourcesComponents\n" +
                "WHERE ResourcesComponents.parentResourceComponentId = ?";
        componentLookupByComponent = con.prepareStatement(sqlString);
    }

    /**
     * Method to locate all boxes for a particular resource. It is essentially the doSearch method but only for a
     * single resource, and the functionality can probable be shared, but for now keep it self contained
     *
     * @param resourceId
     * @param monitor
     * @return Collection Containing resources
     */
    public void updateComponentsBySeries(Long resourceId, InfiniteProgressPanel monitor) {
        try {
            // initialize the prepared statements
            initPreparedStatements();
            String sqlString = "";

            String message = "Processing Components ...";
            System.out.println(message);
            if(monitor != null) monitor.setTextLine(message, 2);

            MyTimer timer = new MyTimer();
            timer.reset();

            componentLookupByResource.setLong(1, resourceId);
            ResultSet components = componentLookupByResource.executeQuery();

            while (components.next()) {
                String componentTitle = getComponentTitle(components);

                message = "Processing Series: " + componentTitle;
                System.out.println(message);
                if(monitor != null) monitor.setTextLine(message, 3);

                recurseThroughComponents(resourceId, components.getLong("resourceComponentId"),
                        components.getBoolean("hasChild"),
                        componentLookupByComponent,
                        componentTitle);
            }

            System.out.println("Total Components: " + componentCount);
            System.out.println("Total Time: " + MyTimer.toString(timer.elapsedTimeMillis()));
        } catch (Exception e) {
            new ErrorDialog("", e).showDialog();
        }
    }

    /**
     * Method to recourse through the resource components
     *
     * @param resourceID
     * @param componentID
     * @param hasChild
     * @param componentLookup
     * @param title
     * @throws SQLException
     */
    private void recurseThroughComponents(Long resourceID, Long componentID,
                                          Boolean hasChild,
                                          PreparedStatement componentLookup,
                                          String title) throws SQLException {
//		System.out.println("Component ID: " + componentID + " Level: " + level + " Title: " + title + " Has child: " + hasChild);
        // update the component count
        componentCount++;

        if (hasChild) {
            componentLookup.setLong(1, componentID);
            ResultSet components = componentLookup.executeQuery();
            ArrayList<ComponentInfo> componentList = new ArrayList<ComponentInfo>();

            while (components.next()) {
                String componentTitle = getComponentTitle(components);

                componentList.add(new ComponentInfo(components.getLong("resourceComponentId"),
                        components.getString("resourceLevel"),
                        componentTitle,
                        components.getBoolean("hasChild")));
            }

            if (componentList.size() > 0) {
                for (ComponentInfo component : componentList) {
                    Long id = component.getComponentId();
                    recurseThroughComponents(resourceID, id, component.isHasChild(), componentLookup, component.getTitle());
                }
            } else {
                //this is a hack because the has child flag for components may be set wrong

                //addComponentId(componentID);
            }
        } else {
            //set the parent resource record id
            //addComponentId(componentID);
        }
    }

    /**
     * Method to return the title for a component which could be the title, date expression or dates
     * @return
     */
    public String getComponentTitle(ResultSet components) throws SQLException {
        String title = components.getString("title");
        String dateExpression = components.getString("dateExpression");
        int dateBegin = components.getInt("dateBegin");
        int dateEnd = components.getInt("dateEnd");

        if (title != null && title.length() > 0) {
            String cleanTitle = StringHelper.tagRemover(title);
            return cleanTitle.replace("\n", " ");
        } else {
            if (dateExpression != null && dateExpression.length() > 0) {
                return dateExpression;
            } else if (dateBegin == 0) {
                return "";
            } else if (dateBegin == dateEnd) {
                return "" + dateBegin;
            } else {
                return dateBegin + "-" + dateEnd;
            }
        }
    }

    /**
     * Method to close the database connection
     */
    public void closeConnection() {
        try {
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Used to store component information
     */
    private class ComponentInfo {

        private Long componentId;
        private String resourceLevel;
        private String title;
        private Boolean hasChild;

        private ComponentInfo(Long componentId, String resourceLevel, String title, Boolean hasChild) {
            this.componentId = componentId;
            this.resourceLevel = resourceLevel;
            this.title = title;
            this.hasChild = hasChild;
        }

        public Long getComponentId() {
            return componentId;
        }

        public void setComponentId(Long componentId) {
            this.componentId = componentId;
        }

        public String getResourceLevel() {
            return resourceLevel;
        }

        public void setResourceLevel(String resourceLevel) {
            this.resourceLevel = resourceLevel;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Boolean isHasChild() {
            return hasChild;
        }

        public void setHasChild(Boolean hasChild) {
            this.hasChild = hasChild;
        }
    }
}
