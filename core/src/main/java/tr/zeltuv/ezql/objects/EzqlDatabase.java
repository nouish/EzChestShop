package tr.zeltuv.ezql.objects;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.deadlight.ezchestshop.EzChestShop;
import org.slf4j.Logger;
import tr.zeltuv.ezql.settings.CustomHikariSettings;
import tr.zeltuv.ezql.settings.DefaultHikariSettings;
import tr.zeltuv.ezql.settings.EzqlCredentials;

public class EzqlDatabase {
    private static final Logger LOGGER = EzChestShop.logger();

    private final EzqlCredentials credentials;
    private final EzqlQuery ezqlQuery = new EzqlQuery(this);
    private final CustomHikariSettings customHikariSettings;
    private HikariDataSource hikariDataSource;
    private final Map<String, EzqlTable> tables = new HashMap<>();

    /**
     * Main constructor, will apply defaults settings for hikari config
     *
     * @param credentials Needed for the API to connect to your database server
     */
    public EzqlDatabase(EzqlCredentials credentials) {
        this.credentials = credentials;
        this.customHikariSettings = new DefaultHikariSettings();
    }

    /**
     * @return Create a connection to the database
     */
    public Connection getConnection() throws SQLException {
        try {
            if (hikariDataSource == null) {
                connect();
            }
            return hikariDataSource.getConnection();
        } catch (SQLException e) {
            LOGGER.warn("Encountered an exception trying to get connection", e);
        }
        return null;
    }

    /**
     * Connect to the MySQL server
     */
    public void connect() {
        HikariConfig hikariConfig = customHikariSettings.getHikariConfig(credentials);
        hikariDataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Disconnect from the MySQL server
     */
    public void disconnect() {
        hikariDataSource.close();
    }

    /**
     * @param name The table name
     * @return returns an EzqlTable object
     */
    public EzqlTable getTable(String name) {
        return tables.get(name);
    }

    /**
     * @param name        The table mame
     * @param ezqlColumns The table columns
     * @return returns an EzqlTable object
     */
    public EzqlTable addTable(String name, EzqlColumn... ezqlColumns) {
        EzqlTable ezqlTable = new EzqlTable(name, this, ezqlColumns);
        tables.put(name, ezqlTable);
        ezqlQuery.createTable(ezqlTable);
        return ezqlTable;
    }


    protected EzqlQuery getEzqlQuery() {
        return ezqlQuery;
    }

    public boolean hasTable(String table) {
        return tables.containsKey(table);
    }
}
