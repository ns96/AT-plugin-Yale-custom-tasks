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

import org.archiviststoolkit.dialog.ErrorDialog;
import org.archiviststoolkit.hibernate.SessionFactory;
import org.archiviststoolkit.swing.InfiniteProgressPanel;
import org.archiviststoolkit.util.MyTimer;
import org.archiviststoolkit.util.StringHelper;

import java.sql.*;
import java.util.ArrayList;
import java.util.TreeMap;

public class ResourceParentUpdater {
    private TreeMap<String, SeriesInfo> seriesInfo = new TreeMap<String, SeriesInfo>();

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
    public int updateComponentsBySeries(Long resourceId, InfiniteProgressPanel monitor) {
        seriesInfo = new TreeMap<String, SeriesInfo>();
        componentCount = 0;

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
                String hashKey = "component_" + components.getLong("resourceComponentId");
                String componentTitle = getComponentTitle(components);

                message = "Processing Series: " + componentTitle;
                System.out.println(message);
                if(monitor != null) monitor.setTextLine(message, 3);

                SeriesInfo si = new SeriesInfo(resourceId, hashKey, componentTitle);
                seriesInfo.put(hashKey, si);

                recurseThroughComponents(hashKey, components.getLong("resourceComponentId"),
                        components.getBoolean("hasChild"),
                        componentLookupByComponent,
                        componentTitle);
            }

            // now update all the components that are in a series. We could have just made one giant
            // call for all components but that may have killed the server
            updateComponentsInSeries(monitor);

            System.out.println("Total Components: " + componentCount);
            System.out.println("Total Time: " + MyTimer.toString(timer.elapsedTimeMillis()));
        } catch (Exception e) {
            new ErrorDialog("", e).showDialog();
        }

        return componentCount;
    }

    /**
     * Method to make direct SQL calls to update the components
     *
     * @param monitor
     * @throws Exception
     */
    private void updateComponentsInSeries(InfiniteProgressPanel monitor) throws  Exception {
        for (SeriesInfo series : seriesInfo.values()) {
            String sqlString = "UPDATE ResourcesComponents " +
                    "SET resourceId = " + series.getResourceId() + "\n" +
                    "WHERE resourceComponentId in (" + series.getComponentIds() + ")";

            //System.out.println(sqlString);

            Statement sqlStatement = con.createStatement();
            int rowCount = sqlStatement.executeUpdate(sqlString);

            // for all the instances found find the containers
            String message = "Processing Components for Series " + series.getSeriesTitle() + ", Updated " + rowCount;
            System.out.println(message);
            if(monitor != null)monitor.setTextLine(message, 4);
        }
    }

    /**
     * Method to recourse through the resource components
     *
     *
     * @param hashKey
     * @param componentID
     * @param hasChild
     * @param componentLookup
     * @param title
     * @throws SQLException
     */
    private void recurseThroughComponents(String hashKey, Long componentID,
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

                    SeriesInfo series = seriesInfo.get(hashKey);
                    series.addComponentId(id);

                    recurseThroughComponents(hashKey, id, component.isHasChild(), componentLookup, component.getTitle());
                }
            } else {
                //this is a hack because the has child flag for components may be set wrong
                SeriesInfo series = seriesInfo.get(hashKey);
                series.addComponentId(componentID);
            }
        } else {
            SeriesInfo series = seriesInfo.get(hashKey);
            series.addComponentId(componentID);
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
}
