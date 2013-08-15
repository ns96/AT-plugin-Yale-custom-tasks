package edu.yale.plugins.tasks.utils;

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
    private StringBuilder sb;
    private Connection connection;
    private DomainAccessObject locationDAO;

    /**
     * The main constructor
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public BarcodeLinker() throws SQLException, ClassNotFoundException {
        Class.forName(SessionFactory.getDriverClass());
        connection = DriverManager.getConnection(SessionFactory.getDatabaseUrl(),
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
     */
    public void linkContainers(Locations location, String[] barcodeList) {
        Long locationId = location.getLocationId();

        for(String barcode: barcodeList) {

        }
    }

    /**
     * Method to find instances of a certain barcode
     * @param barcode
     * @return
     */
    public ArrayList<String> findInstacesByBarcode(String barcode) {
        ArrayList<String> barcodeList = new ArrayList<String>();

        String sqlString = "SELECT * " +
                "FROM ArchDescriptionInstances\n" +
                "WHERE barcode LIKE '" + barcode + "' \n" +
                "AND instanceDescriminator = 'analog'";

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
    private String getMessages() {
        return sb.toString();
    }
}
