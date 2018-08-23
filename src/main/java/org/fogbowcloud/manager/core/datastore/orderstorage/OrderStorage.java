package org.fogbowcloud.manager.core.datastore.orderstorage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.PropertiesHolder;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.models.orders.Order;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.Map;

public class OrderStorage {

    private static final Logger LOGGER = Logger.getLogger(OrderStorage.class);

    private static final String MANAGER_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";

    private String databaseUrl;
    private String databaseUsername;
    private String databasePassword;

    public OrderStorage() {
        PropertiesHolder propertiesHolder = PropertiesHolder.getInstance();
        this.databaseUrl = propertiesHolder.getProperty(ConfigurationConstants.DATABASE_URL);
        this.databaseUsername = propertiesHolder.getProperty(ConfigurationConstants.DATABASE_USERNAME);
        this.databasePassword = propertiesHolder.getProperty(ConfigurationConstants.DATABASE_PASSWORD);

        try {
            Class.forName(MANAGER_DATASTORE_SQLITE_DRIVER);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Invalid datastore driver", e);
        }
    }

    /**
     * Add all order attributes that are commom for all orders.
     */
    protected void addOverallOrderAttributes(PreparedStatement orderStatement, Order order) throws SQLException {
        orderStatement.setString(1, order.getId());
        orderStatement.setString(2, order.getInstanceId());
        orderStatement.setString(3, order.getOrderState().name());
        orderStatement.setString(4, order.getFederationUserToken().getUserId());
        orderStatement.setString(5, order.getRequestingMember());
        orderStatement.setString(6, order.getProvidingMember());
    }

    protected Map<String, String> getFederationUserAttrFromString(String jsonString) {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>(){}.getType();

        return gson.fromJson(jsonString, mapType);
    }

    protected Connection getConnection() throws SQLException {    	
        try {
            return DriverManager.getConnection(this.databaseUrl,
                    this.databaseUsername, this.databasePassword);
        } catch (SQLException e) {
            LOGGER.error("Error while getting a new connection from the connection pool.", e);
            throw e;
        }
    }

    protected void closeConnection(Statement statement, Connection connection) throws SQLException {
        if (statement != null) {
            try {
                if (!statement.isClosed()) {
                    statement.close();
                }
            } catch (SQLException e) {
                LOGGER.error("Couldn't close statement", e);
                throw e;
            }
        }

        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                LOGGER.error("Couldn't close connection", e);
                throw e;
            }
        }
    }
}
