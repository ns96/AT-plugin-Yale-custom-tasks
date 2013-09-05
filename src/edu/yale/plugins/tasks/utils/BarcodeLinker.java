package edu.yale.plugins.tasks.utils;

import edu.yale.plugins.tasks.BarcodeLinkerFrame;
import org.archiviststoolkit.hibernate.SessionFactory;
import org.archiviststoolkit.model.Locations;
import org.archiviststoolkit.mydomain.*;

import java.sql.*;
import java.util.ArrayList;

/**
 * A simple class to link the location barcode to a container
 *
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 8/10/13
 * Time: 1:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class BarcodeLinker {
    private BarcodeLinkerFrame barcodeLinkerFrame;

    private StringBuilder sb;

    private Connection connection;

    private DomainAccessObject locationDAO;

    private PreparedStatement instanceLookupByBarcode;

    private PreparedStatement instanceLocationUpdate;

    private boolean stopOperation = false;

    /**
     * The main constructor
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public BarcodeLinker(BarcodeLinkerFrame barcodeLinkerFrame) throws SQLException, ClassNotFoundException {
        this.barcodeLinkerFrame = barcodeLinkerFrame;

        // get the database connection
        Class.forName(SessionFactory.getDriverClass());
        connection = DriverManager.getConnection(SessionFactory.getDatabaseUrl(),
                SessionFactory.getUserName(),
                SessionFactory.getPassword());

        // initiate the instance lookup by barcode
        String sqlString = "SELECT * " +
                "FROM ArchDescriptionInstances\n" +
                "WHERE barcode = ? \n" +
                "AND instanceDescriminator = 'analog'";

        instanceLookupByBarcode = connection.prepareStatement(sqlString);

        sqlString = "UPDATE ArchDescriptionInstances " +
                "SET locationId = ? WHERE barcode = ?";

        instanceLocationUpdate = connection.prepareCall(sqlString);

        // initiate the domain access objects
        try {
            initDAO();
        } catch (PersistenceException e) {
            e.printStackTrace();
        }
    }

    /**
     * init the domain access objects for getting instances
     */
    public void initDAO() throws PersistenceException {
        locationDAO = DomainAccessObjectFactory.getInstance().getDomainAccessObject(Locations.class);
    }

    /**
     * Method to return the location object by the barcode
     *
     * @param barcode
     * @return
     */
    public Locations getLocationByBarcode(String barcode) {
        Locations location = null;

        try {
            DomainObject domainObject = locationDAO.findByUniquePropertyValue("barcode", barcode);
            location = (Locations)domainObject;
        } catch (LookupException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return location;
    }

    /**
     * Method to return the location object by the barcode
     *
     * @param id
     * @return
     */
    public Locations getLocationById(Long id) {
        if(id == null) return null;

        Locations location = null;

        try {
            DomainObject domainObject = locationDAO.findByPrimaryKey(id);
            location = (Locations)domainObject;
        } catch (LookupException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return location;
    }

    /**
     * Method to link containers to a certain location using sql call
     *
     * @param location
     * @param barcodeList
     * @param testOnly
     */
    public void linkContainers(Locations location, String[] barcodeList, boolean testOnly) {
        sb = new StringBuilder();
        stopOperation = false;

        Long locationId = location.getLocationId();

        for(int i = 0; i < barcodeList.length; i++) {
            if(stopOperation) break;

            String barcode = barcodeList[i];
            ArrayList<String> instanceList = findInstancesForBarcode(barcode);

            sb.append(i+1).append("\t");

            // if we not testing then make the sql call to update the record
            if(!testOnly) {
                if(instanceList.size() > 0) {
                    try {
                        instanceLocationUpdate.setLong(1, locationId);
                        instanceLocationUpdate.setString(2, barcode);

                        int rowCount = instanceLocationUpdate.executeUpdate();

                        sb.append("Location Updated for ").append(instanceList.size()).append(" containers/instances with barcode ")
                                .append(barcode).append("\n");
                    } catch (SQLException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                } else {
                    sb.append("No containers found with barcode ").append(barcode).append("\n");
                }
            } else {
                sb.append("Test Location Update for ").append(instanceList.size()).append(" containers/instances with barcode ")
                        .append(barcode).append("\n");
            }

            // updating the progress bar
            barcodeLinkerFrame.updateProgress(i+1);

            System.out.println("Barcode: " + barcode + " Instance Count: " + instanceList.size());
        }
    }

    /**
     * Method to locate container locations using their barcode
     *
     * @param barcodeList
     */
    public void findContainerLocations(String[] barcodeList) {
        sb = new StringBuilder();
        stopOperation = false;

        Locations location = null;

        for(int i = 0; i < barcodeList.length; i++) {
            if(stopOperation) break;

            String barcode = barcodeList[i];

            // find the container for the barcode ids[0] = container id, ids[1] = location id
            Long[] ids = findContainer(barcode);

            if(ids[0] != -1L) {
                // get the location object
                location = getLocationById(ids[1]);

                sb.append("Container Barcode: ").append(barcode).append("\t");

                if(location != null) {
                    sb.append("Location: ").append(location.toString()).append("\n");
                } else {
                    sb.append("No Location\n");
                }
            } else {
                sb.append("Container Barcode: ").append(barcode).append("\tContainer Not Found ...\n");
            }
            // updating the progress bar
            barcodeLinkerFrame.updateProgress(i+1);

            System.out.println("Container Barcode " + barcode + ", Location: "  + location);
        }
    }

    /**
     * Method to get all the instances with a certain barcode
     */
    public ArrayList<String> findInstancesForBarcode(String barcode)  {
        ArrayList<String> instanceList = new ArrayList<String>();

        try {
            instanceLookupByBarcode.setString(1, barcode);
            ResultSet instances = instanceLookupByBarcode.executeQuery();

            while (instances.next()) {
                Long id = instances.getLong("archDescriptionInstancesId");
                String foundBarcode = instances.getString("barcode");
                instanceList.add(id  + " : " + foundBarcode);
            }
        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return instanceList;
    }

    /**
     * Method to find instances of a certain searchFor
     * @param searchFor
     * @return
     */
    public ArrayList<String> findUniqueBarcodes(String searchFor) {
        ArrayList<String> barcodeList = new ArrayList<String>();

        String sqlString = "SELECT * " +
                "FROM ArchDescriptionInstances\n" +
                "WHERE barcode LIKE '" + searchFor + "' \n" +
                "AND instanceDescriminator = 'analog' GROUP BY barcode";

        Statement sqlStatement = null;

        try {
            sqlStatement = connection.createStatement();
            ResultSet instances = sqlStatement.executeQuery(sqlString);

            while (instances.next()) {
                String foundBarcode = instances.getString("barcode");
                barcodeList.add(foundBarcode);
            }
        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return barcodeList;
    }

    /**
     * Method to find container ids based on a barcode
     *
     * @param searchFor
     * @return Long array with container id and location id
     */
    public Long[] findContainer(String searchFor) {
        Long[] ids = new Long[]{-1L, -1L};

        String sqlString = "SELECT * " +
                "FROM ArchDescriptionInstances\n" +
                "WHERE barcode = '" + searchFor + "' \n" +
                "AND instanceDescriminator = 'analog' LIMIT 1";

        Statement sqlStatement = null;

        try {
            sqlStatement = connection.createStatement();
            ResultSet instances = sqlStatement.executeQuery(sqlString);

            if(instances.next()) {
                ids[0] = instances.getLong("archDescriptionInstancesId");
                ids[1] = instances.getLong("locationId");
            }

        } catch (SQLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return ids;
    }

    /**
     * Method to return messages for linking the barcodes
     *
     * @return
     */
    public String getMessages() {
        return sb.toString().trim();
    }

    /**
     * Stop any linking task in progress
     */
    public void stopOperation() {
        stopOperation = true;
    }
}
