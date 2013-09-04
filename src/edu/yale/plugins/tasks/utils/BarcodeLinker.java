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
    private boolean stopLinking = false;

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
     * Method to link containers to a certain location using sql call
     *
     * @param location
     * @param barcodeList
     * @param testOnly
     */
    public void linkContainers(Locations location, String[] barcodeList, boolean testOnly) {
        sb = new StringBuilder();
        stopLinking = false;

        Long locationId = location.getLocationId();

        for(int i = 0; i < barcodeList.length; i++) {
            if(stopLinking) break;

            String barcode = barcodeList[i];
            ArrayList<String> instanceList = findInstancesForBarcode(barcode);

            sb.append(i+1).append("\t");
            sb.append("Updated ").append(instanceList.size()).append(" instances with barcode ")
                    .append(barcode).append("\n");

            // updating the progress bar
            barcodeLinkerFrame.updateProgress(i+1);

            System.out.println("Barcode: " + barcode + " Instance Count: " + instanceList.size());
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
    public void stopLinking() {
        stopLinking = true;
    }
}
