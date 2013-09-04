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
import org.archiviststoolkit.model.*;
import org.archiviststoolkit.mydomain.*;
import org.archiviststoolkit.swing.InfiniteProgressPanel;
import org.archiviststoolkit.util.MyTimer;
import org.archiviststoolkit.util.StringHelper;
import org.archiviststoolkit.hibernate.SessionFactory;

import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.text.NumberFormat;

public class BoxLookupAndUpdate {
    // this is set to save the record to the database
    public boolean alwaysSaveCache = false;

    // this is used to force update of all record even if they are the same
    public boolean updateAllRecords = false;

    // this is used to avoid having to update the voyager information when we just running index
    public boolean setVoyagerInfo = true;

    private TreeMap<String, SeriesInfo> seriesInfo = new TreeMap<String, SeriesInfo>();

    private HashMap<Long, String> componentTitleLookup = new HashMap<Long, String>();

    private HashMap<String, String> componentInfoLookup = null;

    private String logMessage = "";

    private DomainAccessObject locationDAO;

    private DomainAccessObject instanceDAO;

    private DomainAccessObject componentDAO;

    private Connection con;

    // prepared statements used when searching
    private PreparedStatement resourceIdLookup;
    private PreparedStatement componentLookupByResource;
    private PreparedStatement componentLookupByComponent;
    private PreparedStatement instanceLookupByComponent;

    // keep track of the number of instances processed
    private int instanceCount = 0;

    public BoxLookupAndUpdate() throws SQLException, ClassNotFoundException {
        Class.forName(SessionFactory.getDriverClass());
        con = DriverManager.getConnection(SessionFactory.getDatabaseUrl(),
                SessionFactory.getUserName(),
                SessionFactory.getPassword());

        // initiate the domain access objects
        try {
            initDAO();
        } catch (PersistenceException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to initialize the set of prepared statement needed
     *
     * @throws Exception
     */
    private void initPreparedStatements() throws SQLException {
        String sqlString = "SELECT Resources.resourceId\n" +
                "FROM Resources\n" +
                "WHERE resourceIdentifier1 = ? and resourceIdentifier2 = ?";
        resourceIdLookup = con.prepareStatement(sqlString);

        sqlString = "SELECT ResourcesComponents.resourceComponentId, \n" +
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

        sqlString = "SELECT *" +
                "FROM ArchDescriptionInstances\n" +
                "WHERE resourceComponentId IN (?)";
        instanceLookupByComponent = con.prepareStatement(sqlString);
    }

    /**
     * init the domain access objects for getting instances
     */
    public void initDAO() throws PersistenceException {
        locationDAO = DomainAccessObjectFactory.getInstance().getDomainAccessObject(Locations.class);
        locationDAO.getLongSession();

        instanceDAO = DomainAccessObjectFactory.getInstance().getDomainAccessObject(ArchDescriptionAnalogInstances.class);
        instanceDAO.getLongSession();

        componentDAO = new ResourcesComponentsDAO();
        componentDAO.getLongSession();
    }

    public void doSearch(String msNumber, String ruNumber, String seriesTitle, String accessionNumber, String boxNumber, BoxLookupReturnScreen returnSrceen) {
        seriesInfo = new TreeMap<String, SeriesInfo>();

        componentTitleLookup = new HashMap<Long, String>();

        logMessage = "";

        Collection<BoxLookupReturnRecords> results = new ArrayList<BoxLookupReturnRecords>();

        try {
            // initialize the prepared statements
            initPreparedStatements();
            String sqlString = "";

            System.out.println("doing search");

            String resourceType = null;
            String resourceId2 = null;
            if (msNumber.length() != 0) {
                resourceType = "MS";
                resourceId2 = msNumber;
            } else if (ruNumber.length() != 0) {
                resourceType = "RU";
                resourceId2 = ruNumber;
            }
            resourceIdLookup.setString(1, resourceType);
            resourceIdLookup.setString(2, resourceId2);

            String uniqueId;
            String hashKey;

            String targetUniqueId = "";
            if (seriesTitle.length() != 0) {
                targetUniqueId = seriesTitle;
            } else if (accessionNumber.length() != 0) {
                targetUniqueId = accessionNumber;
            }

            MyTimer timer = new MyTimer();
            timer.reset();
            ResultSet rs = resourceIdLookup.executeQuery();
            if (rs.next()) {
                Long resourceId = rs.getLong(1);
                System.out.println("Resource ID: " + resourceId);
                componentLookupByResource.setLong(1, resourceId);
                ResultSet components = componentLookupByResource.executeQuery();

                while (components.next()) {
                    uniqueId = determineComponentUniqueIdentifier(resourceType, components.getString("subdivisionIdentifier"), components.getString("title"));

                    hashKey = uniqueId + seriesTitle;
                    if (!seriesInfo.containsKey(hashKey)) {
                        SeriesInfo si = new SeriesInfo(uniqueId, seriesTitle);
                        si.addComponentId(components.getLong("resourceComponentId"));
                        seriesInfo.put(hashKey, si);
                    }

                    if (targetUniqueId.equals("") || uniqueId.equalsIgnoreCase(targetUniqueId)) {
                        recurseThroughComponents(components.getLong("resourceComponentId"),
                                hashKey,
                                components.getBoolean("hasChild"),
                                componentLookupByComponent,
                                getComponentTitle(components));
                    }
                }

                ResultSet instances;
                TreeMap<String, ContainerInfo> containers;
                String containerLabel;
                Long componentId;
                String componentTitle;
                Double container1NumIndicator;
                String container1AlphaIndicator;

                for (SeriesInfo series : seriesInfo.values()) {
                    sqlString = "SELECT * " +
                            "FROM ArchDescriptionInstances\n" +
                            "WHERE resourceComponentId in (" + series.getComponentIds() + ") \n" +
                            "AND instanceDescriminator = 'analog'";

                    System.out.println(sqlString);

                    Statement sqlStatement = con.createStatement();
                    instances = sqlStatement.executeQuery(sqlString);
                    containers = new TreeMap<String, ContainerInfo>();
                    while (instances.next()) {

                        container1NumIndicator = instances.getDouble("container1NumericIndicator");
                        container1AlphaIndicator = instances.getString("container1AlphaNumIndicator");
                        if (boxNumber.equals("") || boxNumber.equalsIgnoreCase(determineBoxNumber(container1NumIndicator, container1AlphaIndicator))) {
                            containerLabel = createContainerLabel(instances.getString("container1Type"),
                                    container1NumIndicator,
                                    container1AlphaIndicator,
                                    instances.getString("instanceType"));
                            componentId = instances.getLong("resourceComponentId");
                            componentTitle = componentTitleLookup.get(componentId);

                            if (!containers.containsKey(containerLabel)) {
                                containers.put(containerLabel, new ContainerInfo(containerLabel,
                                        instances.getString("barcode"),
                                        instances.getBoolean("userDefinedBoolean1"),
                                        getLocationString(instances.getLong("locationId")),
                                        componentTitle,
                                        instances.getString("userDefinedString2"),
                                        instances.getString("userDefinedString1"),
                                        instances.getBoolean("userDefinedBoolean2")));
                            }
                        }
                    }
                    logMessage += "\n\n";
                    for (ContainerInfo container : containers.values()) {
                        BoxLookupReturnRecords boxLookupReturnRecord = new BoxLookupReturnRecords(createPaddedResourceIdentifier(resourceType, resourceId2),
                                series.getUniqueId(),
                                container.getComponentTitle(),
                                container.getLocation(),
                                container.getBarcode(),
                                container.isRestriction(),
                                container.getLabel(),
                                container.getContainerType());

                        // add the voyager information
                        boxLookupReturnRecord.setVoyagerInfo(container.getVoyagerInfo());
                        boxLookupReturnRecord.setExportedToVoyager(container.getExportedToVoyager());

                        results.add(boxLookupReturnRecord);

                        logMessage += "\nAccession Number: " + series.getUniqueId() +
                                " Series Title: " + series.getSeriesTitle() +
                                " Container: " + container.getLabel() +
                                " Barcode: " + container.getBarcode() +
                                " Restrictions: " + container.isRestriction();

                    }
                }
            }

            // update the user interface now
            returnSrceen.updateResultList(results);
            returnSrceen.setElapsedTimeText(MyTimer.toString(timer.elapsedTimeMillis()));
        } catch (SQLException e) {
            new ErrorDialog("", e).showDialog();
        } catch (LookupException e) {
            new ErrorDialog("", e).showDialog();
        }
    }

    /**
     * Method to locate all boxes for a particular resource. It is essentially the doSearch method but only for a
     * single resource, and the functionality can probable be shared, but for now keep it self contained
     *
     * @param record
     * @param monitor
     * @param useCache
     * @return Collection Containing resources
     */
    public BoxLookupReturnRecordsCollection gatherContainersBySeries(Resources record, InfiniteProgressPanel monitor, boolean useCache) {
        Long resourceId = record.getIdentifier();
        Long resourceVersion = record.getVersion();

        // try loading the box lookup return record
        BoxLookupReturnRecordsCollection boxC = loadBoxLookupReturnRecordFromDatabase(resourceId);

        // see if to just return the cache result
        if (useCache && boxC != null) {
            // now check to see if the version matches
            if(resourceVersion.equals(boxC.getResourceVersion())) {
                setVoyagerInformation(boxC);
                return boxC;
            } else {
                System.out.println("Resource version different, regenerating box lookup collection");
            }
        }

        seriesInfo = new TreeMap<String, SeriesInfo>();

        componentTitleLookup = new HashMap<Long, String>();

        //Collection<BoxLookupReturnRecords> boxRecords = new ArrayList<BoxLookupReturnRecords>();
        HashMap<String, BoxLookupReturnRecords> boxRecords = new HashMap<String, BoxLookupReturnRecords>();

        try {
            // initialize the prepared statements
            initPreparedStatements();
            String sqlString = "";

            // get the locations domain access object
            locationDAO = DomainAccessObjectFactory.getInstance().getDomainAccessObject(Locations.class);

            String message = "Processing Components ...";
            System.out.println(message);
            if(monitor != null) monitor.setTextLine(message, 2);

            String uniqueId;
            String hashKey;

            MyTimer timer = new MyTimer();
            timer.reset();

            componentLookupByResource.setLong(1, resourceId);
            ResultSet components = componentLookupByResource.executeQuery();

            while (components.next()) {
                String componentTitle = getComponentTitle(components);
                uniqueId = determineComponentUniqueIdentifier("", components.getString("subdivisionIdentifier"), componentTitle);
                //uniqueId = "" + components.getLong("resourceComponentId");

                hashKey = uniqueId;
                if (!seriesInfo.containsKey(hashKey)) {
                    SeriesInfo si = new SeriesInfo(uniqueId, getComponentTitle(components));
                    si.addComponentId(components.getLong("resourceComponentId"));
                    seriesInfo.put(hashKey, si);

                    message = "Gathering Series Info: " + componentTitle;
                    System.out.println(message);
                    if(monitor != null) monitor.setTextLine(message, 3);
                }

                recurseThroughComponents(components.getLong("resourceComponentId"),
                        hashKey,
                        components.getBoolean("hasChild"),
                        componentLookupByComponent,
                        componentTitle);
            }

            ResultSet instances;
            TreeMap<String, ContainerInfo> containers;
            String containerLabel;
            Long componentId;
            String componentTitle;
            Double container1NumIndicator;
            String container1AlphaIndicator;

            // indicate the number of series found
            message = "Number of Series Found: " + seriesInfo.size();
            System.out.println(message);
            if(monitor != null) monitor.setTextLine(message, 3);

            int instanceCount = 0;
            int containerCount = 0;

            for (SeriesInfo series : seriesInfo.values()) {
                sqlString = "SELECT * " +
                        "FROM ArchDescriptionInstances\n" +
                        "WHERE resourceComponentId in (" + series.getComponentIds() + ") \n" +
                        "AND instanceDescriminator = 'analog'";

                System.out.println(sqlString);

                Statement sqlStatement = con.createStatement();
                instances = sqlStatement.executeQuery(sqlString);

                // for all the instances found find the containers
                message = "Processing Instances for Series " + series.seriesTitle;
                System.out.println(message);
                if(monitor != null)monitor.setTextLine(message, 4);

                containers = new TreeMap<String, ContainerInfo>();
                BoxLookupReturnRecords boxLookupReturnRecord = null;

                while (instances.next()) {
                    instanceCount++;

                    Long instanceId = instances.getLong("archDescriptionInstancesId");
                    container1NumIndicator = instances.getDouble("container1NumericIndicator");
                    container1AlphaIndicator = instances.getString("container1AlphaNumIndicator");

                    containerLabel = createContainerLabel(instances.getString("container1Type"),
                            container1NumIndicator,
                            container1AlphaIndicator,
                            instances.getString("instanceType"));
                    componentId = instances.getLong("resourceComponentId");
                    componentTitle = componentTitleLookup.get(componentId);

                    if (!containers.containsKey(containerLabel)) {
                        containerCount++;

                        // create the container object
                        ContainerInfo containerInfo = new ContainerInfo(containerLabel,
                                instances.getString("barcode"),
                                instances.getBoolean("userDefinedBoolean1"),
                                getLocationString(instances.getLong("locationId")),
                                componentTitle,
                                instances.getString("userDefinedString2"));

                        containers.put(containerLabel, containerInfo);

                        // if the series and component title are the same then we don't have a series level
                        // component record
                        String seriesTitle = series.getUniqueId();
                        if(seriesTitle.equals(containerInfo.getComponentTitle())) {
                            seriesTitle = "";
                        }

                        // create and store the BoxLookupReturn Record
                        boxLookupReturnRecord = new BoxLookupReturnRecords(record.getResourceIdentifier(),
                                seriesTitle,
                                containerInfo.getComponentTitle(),
                                containerInfo.getLocation(),
                                containerInfo.getBarcode(),
                                containerInfo.isRestriction(),
                                containerInfo.getLabel(),
                                containerInfo.getContainerType());

                        boxLookupReturnRecord.addInstanceId(instanceId);
                        boxRecords.put(series.getUniqueId()+ "_" + containerLabel, boxLookupReturnRecord);

                        logMessage = "Accession Number: " + series.getUniqueId() +
                                " Series Title: " + series.getSeriesTitle() +
                                " Container: " + containerInfo.getLabel() +
                                " Barcode: " + containerInfo.getBarcode() +
                                " Restrictions: " + containerInfo.isRestriction();

                        message = "Processing Container # " + containerCount + " -- " + logMessage;
                        System.out.println(message);
                        if(monitor != null) monitor.setTextLine(message, 5);
                    } else {
                        // add the instance
                        if(boxRecords.containsKey(series.getUniqueId()+ "_" + containerLabel)) {
                            boxRecords.get(series.getUniqueId()+ "_" + containerLabel).addInstanceId(instanceId);
                        } else {
                            // we should never reach here
                            System.out.println("No container to place instance");
                        }

                        message = "Adding Instance -- " + containerLabel;
                        System.out.println(message);
                        if(monitor != null) monitor.setTextLine(message, 5);
                    }
                }
            }

            // create the box collection record
            BoxLookupReturnRecordsCollection boxCollection = new BoxLookupReturnRecordsCollection(boxRecords.values(),
                    resourceId, resourceVersion, instanceCount);

            // store a copy of this for future access on the database
            if (useCache || alwaysSaveCache) {
                boxCollection.setResourceVersion(resourceVersion);
                PluginDataUtils.saveBoxLookReturnRecord(boxCollection);
            }

            // set the voyager information for the containers now since we don't need this information to
            // to be saved
            setVoyagerInformation(boxCollection);

            System.out.println("Total Instances: " + instanceCount);
            System.out.println("Total Time: " + MyTimer.toString(timer.elapsedTimeMillis()));

            return boxCollection;
        } catch (Exception e) {
            new ErrorDialog("", e).showDialog();
        }

        return null;
    }

    /**
     * Method find instances and put them in record collection for report
     *
     * @param record
     * @param monitor
     * @return
     */
    public int gatherContainersBySeriesForReport(Resources record, PrintWriter writer, String DELIMITER, InfiniteProgressPanel monitor) {
        Long resourceId = record.getIdentifier();

        String collectionId = createPaddedResourceIdentifier(record.getResourceIdentifier1() + ".",
                record.getResourceIdentifier2());

        seriesInfo = new TreeMap<String, SeriesInfo>();

        componentInfoLookup = new HashMap<String, String>();

        int instanceTotal = 0;

        try {
            // initialize the prepared statements
            initPreparedStatements();
            String sqlString = "";

            // get the locations domain access object
            locationDAO = DomainAccessObjectFactory.getInstance().getDomainAccessObject(Locations.class);

            String message = "Processing Components ...";
            System.out.println(message);
            if(monitor != null) monitor.setTextLine(message, 2);

            String uniqueId;
            String hashKey;
            int instanceCount = 0;

            MyTimer timer = new MyTimer();
            timer.reset();

            // get all instances that are attached directly to the resource record
            ResultSet resourceInstances;
            ResultSet resourceNotes;

            // get all the instances
            sqlString = "SELECT * " +
                    "FROM ArchDescriptionInstances\n" +
                    "WHERE parentResourceId = '" + resourceId + "' \n" +
                    "AND instanceDescriminator = 'analog'";

            System.out.println(sqlString);

            Statement sqlStatement = con.createStatement();
            resourceInstances = sqlStatement.executeQuery(sqlString);

            message = "Processing Instances attached to Parent Resource ";
            System.out.println(message);
            if(monitor != null)monitor.setTextLine(message, 4);

            /*// get all the note content
            sqlString = "SELECT noteContent, resourceComponentId FROM `ArchDescriptionRepeatingData` \n" +
                    "WHERE resourceComponentId in (" + series.getComponentIds() + ") \n" +
                    "AND notesEtcTypeId='16'" ;

            Statement sqlStatement2 = con.createStatement();
            notes = sqlStatement2.executeQuery(sqlString);

            addNoteContentToComponentInfo(notes);*/


            while (resourceInstances.next()) {
                instanceTotal++;

                Double container1NumIndicator = resourceInstances.getDouble("container1NumericIndicator");
                String container1AlphaIndicator = resourceInstances.getString("container1AlphaNumIndicator");

                String containerLabel = createContainerLabel(resourceInstances.getString("container1Type"),
                        container1NumIndicator,
                        container1AlphaIndicator,
                        resourceInstances.getString("instanceType"));


                String componentTitle = "Parent Resource";
                String extentType = record.getExtentType();
                String extentNumber = ""  + record.getExtentNumber();
                String dateExpression = record.getDateExpression();
                String noteContent = "none";

                if (addToAVReport(resourceInstances, -1L)) {
                    // if the series and component title are the same then we don't have a series level
                    // component record
                    String seriesTitle = "Parent Resource";

                    writer.println(collectionId + DELIMITER +
                            seriesTitle + DELIMITER +
                            componentTitle + DELIMITER +
                            extentType + DELIMITER +
                            extentNumber + DELIMITER +
                            dateExpression + DELIMITER +
                            noteContent + DELIMITER +
                            getLocationString(resourceInstances.getLong("locationId")) + DELIMITER +
                            containerLabel + DELIMITER +
                            resourceInstances.getString("userDefinedString2") + DELIMITER +
                            resourceInstances.getString("barcode"));

                    instanceCount++;
                    message = "Resource Instances found " + instanceCount;
                    System.out.println(message);
                    if (monitor != null) monitor.setTextLine(message, 5);
                }
            }

            // now process instance attached to the resource components
            componentLookupByResource.setLong(1, resourceId);
            ResultSet components = componentLookupByResource.executeQuery();
            while (components.next()) {
                String componentTitle = getComponentTitle(components);
                uniqueId = determineComponentUniqueIdentifier("", components.getString("subdivisionIdentifier"), componentTitle);
                //uniqueId = "" + components.getLong("resourceComponentId");

                hashKey = uniqueId;
                if (!seriesInfo.containsKey(hashKey)) {
                    SeriesInfo si = new SeriesInfo(uniqueId, componentTitle);
                    si.addComponentId(components.getLong("resourceComponentId"));
                    seriesInfo.put(hashKey, si);

                    message = "Gathering Series Info: " + componentTitle;
                    System.out.println(message);
                    if(monitor != null) monitor.setTextLine(message, 3);
                }

                recurseThroughComponents(components.getLong("resourceComponentId"),
                        hashKey,
                        components.getBoolean("hasChild"),
                        componentLookupByComponent,
                        componentTitle);

            }

            ResultSet instances;
            ResultSet notes;

            String containerLabel;
            Long componentId;
            String componentTitle;
            Double container1NumIndicator;
            String container1AlphaIndicator;

            // indicate the number of series found
            message = "Number of Series Found: " + seriesInfo.size();
            System.out.println(message);
            if(monitor != null) monitor.setTextLine(message, 3);

            for (SeriesInfo series : seriesInfo.values()) {
                if(series.getSeriesTitle().toLowerCase().contains("microfilm")) {
                    continue;
                }

                // get all the instances
                sqlString = "SELECT * " +
                        "FROM ArchDescriptionInstances\n" +
                        "WHERE resourceComponentId in (" + series.getComponentIds() + ") \n" +
                        "AND instanceDescriminator = 'analog'";

                System.out.println(sqlString);

                sqlStatement = con.createStatement();
                instances = sqlStatement.executeQuery(sqlString);

                // get all the note content
                sqlString = "SELECT noteContent, resourceComponentId FROM `ArchDescriptionRepeatingData` \n" +
                        "WHERE resourceComponentId in (" + series.getComponentIds() + ") \n" +
                        "AND notesEtcTypeId='16'" ;

                sqlStatement = con.createStatement();
                notes = sqlStatement.executeQuery(sqlString);

                addNoteContentToComponentInfo(notes);

                // for all the instances found find the containers
                message = "Processing Instances for Series " + series.seriesTitle;
                System.out.println(message);
                if(monitor != null)monitor.setTextLine(message, 4);

                while (instances.next()) {
                    instanceTotal++;

                    container1NumIndicator = instances.getDouble("container1NumericIndicator");
                    container1AlphaIndicator = instances.getString("container1AlphaNumIndicator");

                    containerLabel = createContainerLabel(instances.getString("container1Type"),
                            container1NumIndicator,
                            container1AlphaIndicator,
                            instances.getString("instanceType"));

                    componentId = instances.getLong("resourceComponentId");

                    componentTitle = componentInfoLookup.get(componentId + "_title");
                    String extentType = componentInfoLookup.get(componentId + "_extentType");
                    String extentNumber = componentInfoLookup.get(componentId + "_extentNumber");
                    String dateExpression = componentInfoLookup.get(componentId + "_dateExpression");
                    String noteContent = componentInfoLookup.get(componentId + "_noteContent");

                    if (addToAVReport(instances, componentId)) {
                        // if the series and component title are the same then we don't have a series level
                        // component record
                        String seriesTitle = series.getUniqueId();
                        if (seriesTitle.equals(componentTitle)) {
                            seriesTitle = "";
                        }

                        writer.println(collectionId + DELIMITER +
                                seriesTitle + DELIMITER +
                                componentTitle + DELIMITER +
                                extentType + DELIMITER +
                                extentNumber + DELIMITER +
                                dateExpression + DELIMITER +
                                noteContent + DELIMITER +
                                getLocationString(instances.getLong("locationId")) + DELIMITER +
                                containerLabel + DELIMITER +
                                instances.getString("userDefinedString2") + DELIMITER +
                                instances.getString("barcode"));

                        instanceCount++;
                        message = "Instances found " + instanceCount;
                        System.out.println(message);
                        if (monitor != null) monitor.setTextLine(message, 5);
                    }
                }
            }

            System.out.println("Total Instances: " + instanceCount);
            System.out.println("Total Time: " + MyTimer.toString(timer.elapsedTimeMillis()));
        } catch (Exception e) {
            new ErrorDialog("", e).showDialog();
        }

        return instanceTotal;
    }

    /**
     * Add the noteContent to the conponent Info hash map
     * @param notes
     * @throws SQLException
     */
    private void addNoteContentToComponentInfo(ResultSet notes) throws SQLException {
        while(notes.next()) {
            Long id = notes.getLong("resourceComponentId");
            String noteContent = notes.getString("noteContent");

            if(noteContent != null) {
                noteContent = noteContent.replace("\n", " ");
                componentInfoLookup.put(id + "_noteContent", noteContent);
            }
        }
    }

    /**
     * Method to check to see if to add a particular instance to the AV report
     *
     *
     * @param instances
     * @param componentId
     * @return
     */
    private boolean addToAVReport(ResultSet instances, Long componentId) throws Exception {
        // first check the instance
        ArrayList<String> avTypes = new ArrayList<String>();
        avTypes.add(instances.getString("userDefinedString2"));
        avTypes.add(instances.getString("instanceType"));
        avTypes.add(instances.getString("container1Type"));

        for(String avType: avTypes) {
            if (avType != null && !avType.isEmpty()) {
                avType = avType.toLowerCase();

                if (!avType.contains("microfilm") && (
                        avType.contains("film") ||
                                avType.contains("vhs") ||
                                avType.contains("cassette") ||
                                avType.contains("video") ||
                                avType.contains("audio") ||
                                avType.contains("record"))) {
                    return true;
                }
            }
        }

        // if we get here then check that the resource component doesn't indicate it's an audio/video item
        String title = componentInfoLookup.get(componentId + "_title");
        if(title != null && !title.isEmpty()) {
            title = title.toLowerCase();

            // removed records from search term because this results in a lot of false hits
            if(!title.contains("microfilm") && (title.contains("audio") || title.contains("video") ||
                    title.contains("film") || title.contains("recordings") ||
                    title.contains("recorded"))) {
                return true;
            }
        }

        String noteContent = componentInfoLookup.get(componentId + "_noteContent");
        if(noteContent != null && !noteContent.isEmpty()) {
            noteContent = noteContent.toLowerCase();
            if(noteContent.contains("audio") || noteContent.contains("video") ||
                    noteContent.contains("dvd") || noteContent.contains("cd") ||
                    noteContent.contains("reel-to-reel") ||  noteContent.contains("film")) {
                return true;
            }
        }

        String extentType = componentInfoLookup.get(componentId + "_extentType");
        if(extentType != null && !extentType.isEmpty()) {
            if(extentType.contains("audio") || extentType.contains("video") ||
                    extentType.contains("DVD") || extentType.contains("CD")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Method to set the voyager information for the collection record. This is needed to avoid
     * having to update all the voyager information everytime the export voyager information button
     * is used
     *
     * @param boxCollection
     */
    private void setVoyagerInformation(BoxLookupReturnRecordsCollection boxCollection) {
        if(!setVoyagerInfo) return;

        for (BoxLookupReturnRecords container : boxCollection.getContainers()) {
            String[] ids = container.getInstanceIds().split(",\\s*");
            Long lid = new Long(ids[0]);

            try {
                ArchDescriptionAnalogInstances instance = (ArchDescriptionAnalogInstances) instanceDAO.findByPrimaryKeyLongSession(lid);
                String voyagerInfo = instance.getUserDefinedString1();
                Boolean exported = instance.getUserDefinedBoolean2();

                container.setVoyagerInfo(voyagerInfo);
                container.setExportedToVoyager(exported);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Method to load box lookup return record from the database
     *
     * @param resourceId
     */
    private BoxLookupReturnRecordsCollection loadBoxLookupReturnRecordFromDatabase(Long resourceId) {
        if(!updateAllRecords) {
            return PluginDataUtils.getBoxLookupReturnRecord(resourceId);
        } else {
            return null;
        }
    }

    /**
     * Method to do recursion through all resource components and create box lookup return
     * records for their instances
     *
     * @param monitor
     * @param resourceIdentifier
     * @param component
     * @param containers
     * @param depth
     * @return The number of instances found
     */
    private void gatherContainers(InfiniteProgressPanel monitor,
                                  String resourceIdentifier, ResourcesComponents component,
                                  TreeMap<String, BoxLookupReturnRecords> containers,
                                  int depth) {

        String containerName;
        ArchDescriptionAnalogInstances newInstance;
        BoxLookupReturnRecords group;
        monitor.setTextLine(component.getTitle(), depth);

        for (Object o : component.getInstances(ArchDescriptionAnalogInstances.class)) {
            newInstance = (ArchDescriptionAnalogInstances) o;
            containerName = newInstance.getTopLevelLabel();

            if (StringHelper.isNotEmpty(newInstance.getBarcode())) {
                containerName = newInstance.getTopLevelLabel() + " (" + newInstance.getBarcode() + ")";
            }

            String uniqueId = determineComponentUniqueIdentifier("", component.getComponentUniqueIdentifier(), component.getTitle());
            group = containers.get(containerName);

            if (group == null) {
                String locationName = "";
                Long locationId;

                if (newInstance.getLocation() != null) {
                    locationName = newInstance.getLocation().toString();
                    locationId = newInstance.getLocation().getLocationId();
                }

                // create and store the BoxLookupReturn Record
                group = new BoxLookupReturnRecords(resourceIdentifier,
                        uniqueId,
                        component.getTitle(),
                        locationName,
                        newInstance.getBarcode(),
                        newInstance.getUserDefinedBoolean1(),
                        newInstance.getTopLevelLabel(),
                        newInstance.getUserDefinedString2());

                group.addInstanceId(newInstance.getIdentifier());
                // need to add location id

                containers.put(containerName, group);
            } else {
                group.addInstanceId(newInstance.getIdentifier());
            }

            instanceCount++;
        }

        if (component.isHasChild()) {
            for (ResourcesComponents childComponent : component.getResourcesComponents()) {
                gatherContainers(monitor, resourceIdentifier, childComponent, containers, depth + 1);
            }
        }
    }

    /**
     * Method to get the indicator for container 1
     *
     * @return Either the Numeric or AlphaNumeric Indicator
     */
    public String determineBoxNumber(Double container1NumericIndicator, String container1AlphaNumericIndicator) {
        if (container1NumericIndicator != null && container1NumericIndicator != 0) {
            return StringHelper.handleDecimal(container1NumericIndicator.toString());
        } else if (container1AlphaNumericIndicator != null && StringHelper.isNotEmpty(container1AlphaNumericIndicator)) {
            return container1AlphaNumericIndicator;
        } else {
            return "";
        }
    }

    private String createPaddedResourceIdentifier(String id1, String id2) {
        String paddedId2;
        if (id2.length() == 1) {
            paddedId2 = "000" + id2;
        } else if (id2.length() == 2) {
            paddedId2 = "00" + id2;
        } else if (id2.length() == 3) {
            paddedId2 = "0" + id2;
        } else {
            paddedId2 = id2;
        }
        return id1 + paddedId2;
    }


    private String determineComponentUniqueIdentifier(String resourceType, String componenetUniqueId, String seriesTitle) {
        if (componenetUniqueId != null && componenetUniqueId.length() != 0) {
            return componenetUniqueId.replace("Accession ", "");
        } else if (resourceType.equalsIgnoreCase("ms")) {
            return seriesTitle.replace("Accession ", "");
        } else if (seriesTitle != null && !seriesTitle.isEmpty()) {
            return seriesTitle;
        } else {
            return "";
        }
    }

    private String getLocationString(Long locationId) throws LookupException {
        Locations location = (Locations) locationDAO.findByPrimaryKey(locationId);
        if (location != null) {
            return location.toString();
        } else {
            return "";
        }
    }

    private void recurseThroughComponents(Long componentID,
                                          String hashKey,
                                          Boolean hasChild,
                                          PreparedStatement componentLookup,
                                          String title) throws SQLException {
//		System.out.println("Component ID: " + componentID + " Level: " + level + " Title: " + title + " Has child: " + hasChild);
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
                componentTitleLookup.put(components.getLong("resourceComponentId"), componentTitle);

                // If we are generating an A/V report we will need more information
                // about the component so get it now
                if(componentInfoLookup != null) {
                    Long id = components.getLong("resourceComponentId");
                    componentInfoLookup.put(id + "_title", componentTitle);
                    componentInfoLookup.put(id + "_extentType", components.getString("extentType"));
                    componentInfoLookup.put(id + "_extentNumber", components.getString("extentNumber"));
                    componentInfoLookup.put(id + "_dateExpression", components.getString("dateExpression"));
                }
            }

            if (componentList.size() > 0) {
                for (ComponentInfo component : componentList) {
                    Long id = component.getComponentId();
                    SeriesInfo series = seriesInfo.get(hashKey);
                    series.addComponentId(id);

                    recurseThroughComponents(id, hashKey, component.isHasChild(), componentLookup, component.getTitle());
                }
            } else {
                //this is a hack because the has child flag for components may be set wrong
                SeriesInfo series = seriesInfo.get(hashKey);
                series.addComponentId(componentID);
            }
        } else {
            SeriesInfo series = seriesInfo.get(hashKey);
            series.addComponentId(componentID);

            // add the component title in case we don't have a structure that's series/subseries
            componentTitleLookup.put(componentID, title);
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

    private String createContainerLabel(String type, Double numericIndicator, String alphaIndicator, String instanceType) {

        Boolean hasNumbericIndicator;
        if (instanceType.equalsIgnoreCase("Digital Object")) {
            return "Digital Object(s)";
        } else {
            if (numericIndicator != null && numericIndicator != 0.0) {
                hasNumbericIndicator = true;
            } else {
                hasNumbericIndicator = false;
            }
            if (type != null && type.length() > 0 && (hasNumbericIndicator || alphaIndicator.length() > 0)) {
                try {
                    if (hasNumbericIndicator) {
                        //return type + " " + NumberFormat.getInstance().format(numericIndicator);
                        return createPaddedResourceIdentifier(type + " ", NumberFormat.getInstance().format(numericIndicator));
                    } else {
                        //return type + " " + alphaIndicator;
                        return createPaddedResourceIdentifier(type + " ", alphaIndicator);
                    }
                } catch (Exception e) {
                    new ErrorDialog("Error creating container label", e).showDialog();
                    return "no container";
                }
            } else {
                return "no container";
            }
        }
    }

    /**
     * Method to update instances of a box record
     *
     * @param instanceIds
     * @param barcode
     * @param container3Type
     * @param userDefinedString2
     * @param changeRestriction
     * @param restriction
     * @param changeExportedToVoyager
     * @param exportedToVoyager
     * @return String containing the top level container name
     * @throws Exception
     */
    public String updateInstanceInformation(String instanceIds, String barcode, String container3Type,
                                          String userDefinedString2, Boolean changeRestriction, Boolean restriction,
                                          Boolean changeExportedToVoyager, Boolean exportedToVoyager) throws Exception {

        // for each id get the analog instance object from the database
        String[] ids = instanceIds.split(",\\s*");
        String topLevelContainerName = null;

        for (String id : ids) {
            Long lid = new Long(id);

            ArchDescriptionAnalogInstances instance = (ArchDescriptionAnalogInstances) instanceDAO.findByPrimaryKeyLongSession(lid);

            // set the top level container name if we have not done so already
            if(topLevelContainerName == null) {
                topLevelContainerName = instance.getTopLevelLabel();
            }

            if (barcode.length() != 0) {
                instance.setBarcode(barcode);
                topLevelContainerName += " (" + barcode + ")";
            }

            // this is no longer used and will probable be removed
            if (container3Type.length() != 0) {
                instance.setContainer3Type(container3Type);
            }

            if (userDefinedString2.length() != 0) {
                instance.setUserDefinedString2(userDefinedString2);
            }

            if (changeRestriction) {
                instance.setUserDefinedBoolean1(restriction);
            }

            if (changeExportedToVoyager) {
                instance.setUserDefinedBoolean2(exportedToVoyager);
            }

            // update the record in the database now
            instanceDAO.updateLongSession(instance, false);
        }

        return topLevelContainerName;
    }

    /**
     * Method to update the code for the list of instances
     *
     * @param instanceIds
     * @param barcode
     */
    public void updateBarcode(String instanceIds, String barcode) throws Exception {
        String[] ids = instanceIds.split(",\\s*");

        // for each id get the analog instance object from the database
        for (String id : ids) {
            Long lid = new Long(id);

            ArchDescriptionAnalogInstances instance = (ArchDescriptionAnalogInstances) instanceDAO.findByPrimaryKeyLongSession(lid);

            instance.setBarcode(barcode);

            instanceDAO.updateLongSession(instance, false);
        }
    }

    /**
     * Method to update the voyager information for container records/instances
     *
     * @param instanceIds
     * @param bibHolding
     * @return the number of instances returned
     * @throws Exception
     */
    public int updateVoyagerInformation(String instanceIds, String bibHolding) throws Exception {
        String[] ids = instanceIds.split(",\\s*");

        // for each id get the analog instance object from the database
        int count = 0;

        for (String id : ids) {
            Long lid = new Long(id);

            ArchDescriptionAnalogInstances instance = (ArchDescriptionAnalogInstances) instanceDAO.findByPrimaryKeyLongSession(lid);

            instance.setUserDefinedString1(bibHolding);

            instanceDAO.updateLongSession(instance, false);

            count++;
        }

        return count;
    }

    /**
     * Method to verify that barcodes for all instances are equal
     *
     * @param instanceIds
     */
    public String verifyBarcodes(String instanceIds, InfiniteProgressPanel monitor) throws Exception {
        String message = "OK";

        // DEBUG
        //instanceIds += ", 4, 27, 2345";
        String[] ids = instanceIds.split(",\\s*");

        int totalCount = ids.length;
        instanceCount += totalCount;

        // use the sql query statement to find unique barcodes.
        Statement stmt = con.createStatement();
        String sqlString = "SELECT COUNT(DISTINCT barcode) FROM ArchDescriptionInstances " +
                "WHERE ArchDescriptionInstancesId IN (" + instanceIds + ")";

        ResultSet uniqueBarcodes = stmt.executeQuery(sqlString);
        uniqueBarcodes.first();

        int count = uniqueBarcodes.getInt(1);

        // print out some information
        String consoleMessage = count + " unique barcode(s) in " + totalCount + " instances";
        if(monitor != null) {
            monitor.setTextLine(consoleMessage, 5);
        }

        System.out.println(consoleMessage);

        if(count > 1) {
            String componentInformation  = getInstanceComponentInfo(stmt, instanceIds);
            message = componentInformation;

            if(monitor != null) {
                monitor.setTextLine(componentInformation, 5);
            }
        }

        return message;
    }

    /**
     * Method to get information about the parent and series component of an instance
     *
     * @param instanceIds
     * @return
     */
    private String getInstanceComponentInfo(Statement stmt, String instanceIds) throws Exception {
        String sqlString = "SELECT DISTINCT resourceComponentId, ArchDescriptionInstancesId FROM ArchDescriptionInstances " +
                "WHERE ArchDescriptionInstancesId IN (" + instanceIds + ")";

        ResultSet uniqueComponents = stmt.executeQuery(sqlString);

        // get all the information for the components
        StringBuilder sb = new StringBuilder();

        while(uniqueComponents.next()) {
            Long cid = uniqueComponents.getLong(1); // component id
            Long id = uniqueComponents.getLong(2); // instance id

            ResourcesComponents component = (ResourcesComponents) componentDAO.findByPrimaryKeyLongSession(cid);
            ArchDescriptionAnalogInstances instance = (ArchDescriptionAnalogInstances) instanceDAO.findByPrimaryKeyLongSession(id);

            // add component information
            String componentInfo = component.getTitle() + " :: (" + component.getPersistentId() + ", " + component.getIdentifier() + ")";
            if(component.getResourceComponentParent() != null) {
                String seriesName = component.getResourceComponentParent().getTitle();
                sb.append(seriesName).append(" / ").append(componentInfo).append("\n");
            } else {
                sb.append(componentInfo).append("\n");
            }

            // add instance information
            String barcode = instance.getBarcode();
            if(barcode.isEmpty()) {
                barcode = "NULL";
            }
            sb.append(instance.getInstanceLabel()).append(" || Barcode: ").append(barcode).append("\n");
        }

        return sb.toString();
    }

    /**
     * Method to return the number of instances checked
     * @return
     */
    public int getNumberOfInstanceChecked() {
        return instanceCount;
    }

    /**
     * Method add a location to a set of analog instance
     *
     * @param instanceIds
     * @param selectedLocation
     * @return
     * @throws PersistenceException
     */
    public int updateInstanceLocation(String instanceIds, Locations selectedLocation) throws Exception {
        String[] ids = instanceIds.split(",\\s*");

        // for each id get the analog instance object from the database
        int count = 0;

        for (String id : ids) {
            Long lid = new Long(id);

            ArchDescriptionAnalogInstances instance = (ArchDescriptionAnalogInstances) instanceDAO.findByPrimaryKeyLongSession(lid);

            instance.setLocation(selectedLocation);

            instanceDAO.updateLongSession(instance, false);

            count++;
        }

        return count;
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

    private class ContainerInfo {

        private String label;
        private String barcode;
        private Boolean restriction;
        private String location;
        private String componentTitle;
        private String containerType;
        private String voyagerInfo;
        private Boolean exportedToVoyager;

        private ContainerInfo(String label, String barcode, Boolean restriction, String location, String componentTitle, String containerType) {
            this.label = label;
            if (barcode == null || barcode.equals("0.0")) {
                this.barcode = "";
            } else {
                this.barcode = barcode;
            }

            this.restriction = restriction;
            this.location = location;
            this.componentTitle = componentTitle;
            this.containerType = containerType;
        }

        private ContainerInfo(String label, String barcode, Boolean restriction, String location,
                              String componentTitle, String containerType, String voyagerInfo, Boolean exportedToVoyager) {
            this.label = label;
            if (barcode == null || barcode.equals("0.0")) {
                this.barcode = "";
            } else {
                this.barcode = barcode;
            }

            this.restriction = restriction;
            this.location = location;
            this.componentTitle = componentTitle;
            this.containerType = containerType;
            this.voyagerInfo = voyagerInfo;
            this.exportedToVoyager = exportedToVoyager;
        }

        public String getLabel() {
            return label;
        }

        public String getBarcode() {
            return barcode;
        }

        public Boolean isRestriction() {
            return restriction;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getComponentTitle() {
            return componentTitle;
        }

        public void setComponentTitle(String componentTitle) {
            this.componentTitle = componentTitle;
        }

        public String getContainerType() {
            return containerType;
        }

        public void setContainerType(String containerType) {
            this.containerType = containerType;
        }

        public String getVoyagerInfo() {
            return voyagerInfo;
        }

        public Boolean getExportedToVoyager() {
            return exportedToVoyager;
        }
    }

    private class SeriesInfo {

        private String uniqueId;
        private String seriesTitle;
        private String componentIds = null;

        private SeriesInfo(String uniqueId, String seriesTitle) {
            this.uniqueId = uniqueId;
            this.seriesTitle = seriesTitle;
        }

        public String getUniqueId() {
            return uniqueId;
        }

        public void setUniqueId(String uniqueId) {
            this.uniqueId = uniqueId;
        }

        public String getSeriesTitle() {
            return seriesTitle;
        }

        public void setSeriesTitle(String seriesTitle) {
            this.seriesTitle = seriesTitle;
        }

        public String getComponentIds() {
            return componentIds;
        }

        public void addComponentId(Long componentId) {
            if (this.componentIds == null) {
                this.componentIds = componentId.toString();
            } else {
                this.componentIds += ", " + componentId;
            }
        }
    }

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
