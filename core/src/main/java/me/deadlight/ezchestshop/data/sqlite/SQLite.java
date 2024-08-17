package me.deadlight.ezchestshop.data.sqlite;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.sqlite.structure.SQLColumn;
import me.deadlight.ezchestshop.data.sqlite.structure.SQLTable;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Location;
import org.sqlite.JDBC;

public class SQLite extends DatabaseManager {
    private final String dbname;
    private final EzChestShop plugin;
    private HikariDataSource dataSource;
    private final List<String> statements = new ArrayList<>();

    public SQLite(EzChestShop instance) {
        plugin = instance;
        dbname = "ecs-database"; // Set the table name here
    }

    public void initialize() {
        // Set the logger level for HikariCP to warn to reduce console noise.
        Configurator.setLevel("me.deadlight.ezchestshop.internal.hikari", org.apache.logging.log4j.Level.WARN);

        File databaseFile = new File(plugin.getDataFolder(), dbname + ".db");
        HikariConfig config = new HikariConfig();
        config.setDataSourceClassName("org.sqlite.SQLiteDataSource");
        config.addDataSourceProperty("url", JDBC.PREFIX + databaseFile.getAbsolutePath());
        config.addDataSourceProperty("encoding", "UTF-8");
        config.addDataSourceProperty("enforceForeignKeys", "true");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("journalMode", "WAL");
        config.setPoolName("EzChestShop");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String product = meta.getDatabaseProductName();
            String version = meta.getDatabaseProductVersion();
            plugin.getLogger().info(String.format("Database: %s v%s.", product, version));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to determine database version.", e);
        }
    }

    @Override
    public void load() {
        initialize();

        try (Connection connection = dataSource.getConnection()) {
            initStatements();
            for (String i : statements) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(i);
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate")) {
                        plugin.getLogger().log(Level.WARNING, "Error running SQLite query", e);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error running SQLite query", e);
        }
    }

    public void disconnect() {
        HikariDataSource source = dataSource;
        dataSource = null;
        if (source != null) {
            source.close();
        }
    }

    private void initStatements() {
        // Add all the tables if they don't already exist.
        this.statements.addAll(convertObjecttoInsertStatement());
        // Alter the tables:
        this.statements.addAll(convertObjecttoAlterStatement());
        this.statements.removeIf(Objects::isNull);
    }


    //TODO Don't forget to change this when adding a new database table that works with Player data!
    public static final List<String> playerTables = Collections.singletonList("playerdata");

    /**************************************
     *         DATABASE STRUCTURE         *
     *           Very Important           *
     **************************************/
    public LinkedHashMap<String, SQLTable> getTableObjects() {
        LinkedHashMap<String, SQLTable> tables = new LinkedHashMap<>();
        tables.put("shopdata", new SQLTable(new LinkedHashMap<String, SQLColumn>() {
            {
                put("location", new SQLColumn("STRING (32)", true, false));
                put("owner", new SQLColumn("STRING (32)", false, false));
                put("item", new SQLColumn("STRING (32)", false, false));
                put("buyPrice", new SQLColumn("DOUBLE", false, false));
                put("sellPrice", new SQLColumn("DOUBLE", false, false));
                put("msgToggle", new SQLColumn("BOOLEAN", false, false));
                put("buyDisabled", new SQLColumn("BOOLEAN", false, false));
                put("sellDisabled", new SQLColumn("BOOLEAN", false, false));
                put("admins", new SQLColumn("STRING (32)", false, false));
                put("shareIncome", new SQLColumn("BOOLEAN", false, false));
                put("adminshop", new SQLColumn("BOOLEAN", false, false));
                put("rotation", new SQLColumn("STRING (32)", false, false));
                put("customMessages", new SQLColumn("STRING (32)", false, false));

            }
        }));
        tables.put("playerdata", new SQLTable(new LinkedHashMap<String, SQLColumn>() {
            {
                put("uuid", new SQLColumn("STRING (32)", true, false));
                put("checkprofits", new SQLColumn("STRING (32)", false, false));
            }
        }));

        return tables;
    }


    //Insert statements:
    private List<String> convertObjecttoInsertStatement() {
        return getTableObjects().entrySet().stream().map(x -> {// Get the tables
            return "CREATE TABLE IF NOT EXISTS " + x.getKey() + " ("
                    + x.getValue().getTable().entrySet().stream().map(y -> {// Start collecting the lines
                SQLColumn col = y.getValue();// get one column
                String line = y.getKey() + " ";
                line = line.concat(col.getType()); // add the type
                if (col.isCanbenull()) {
                    line = line.concat(" NOT NULL ");// add null possibility
                }
                if (col.isPrimarykey()) {
                    line = line.concat(" PRIMARY KEY "); // add primary key possibility
                }
                return line; // return this colomn as a string line.
            }).collect(Collectors.joining(", ")) + ");";// collect them columns together and join to a table
        }).collect(Collectors.toList()); // Join the tables to a list together.
    }

    //Alter statements:
    private List<String> convertObjecttoAlterStatement() {
        return getTableObjects().entrySet().stream().map(x -> x.getValue().getTable()
                .entrySet().stream().map(y -> {
                    SQLColumn col = y.getValue();// get one column
                    if (col.isPrimarykey())
                        return null; // primary key's can't be changed anyway.
                    String line = "ALTER TABLE " + x.getKey() + " ADD COLUMN " + y.getKey() + " ";
                    line = line.concat(col.getType()); // add the type
                    if (col.isCanbenull()) {
                        line = line.concat(" NOT NULL ");// add null possibility
                    }
                    if (col.isPrimarykey()) {
                        line = line.concat(" PRIMARY KEY "); // add primary key possibility
                    }
                    line = line.concat(";");
                    return line; // return this colomn as a string line.
                }).collect(Collectors.toList())).flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * Query the Database for a String value
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @return the resulting String or null
     */
    @Override
    public String getString(String primaryKey, String key, String column, String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + primaryKey + " = ?")) {
            statement.setString(1, key);

            try (ResultSet caret = statement.executeQuery()) {
                if (caret.next()) {
                    return caret.getString(column);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return null;
    }

    /**
     * Query the Database for a Integer value
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @return the resulting Integer or null
     */
    public Integer getInt(String primaryKey, String key, String column, String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + primaryKey + " = ?")) {
            statement.setString(1, key);

            try (ResultSet caret = statement.executeQuery()) {
                if (caret.next()) {
                    return caret.getInt(column);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return null;
    }

    /**
     * Query the Database for a Boolean value
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @return the resulting Boolean or false
     */
    public boolean getBool(String primaryKey, String key, String column, String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + primaryKey + " = ?")) {
            statement.setString(1, key);

            try (ResultSet caret = statement.executeQuery()) {
                if (caret.next()) {
                    return caret.getBoolean(column);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return false;
    }

    /**
     * Query the Database for a Long value
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @return the resulting long or 0
     */
    public long getBigInt(String primaryKey, String key, String column, String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + primaryKey + " = ?")) {
            statement.setString(1, key);

            try (ResultSet caret = statement.executeQuery()) {
                if (caret.next()) {
                    return caret.getLong(column);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return 0;
    }

    /**
     * Query the Database for a Double value
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @return the resulting long or 0
     */
    public double getDouble(String primaryKey, String key, String column, String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + primaryKey + " = ?")) {
            statement.setString(1, key);

            try (ResultSet caret = statement.executeQuery()) {
                if (caret.next()) {
                    return caret.getDouble(column);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return 0;
    }


    /**
     * Set a String in the Database.
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @param data        the new String to be set
     */
    @Override
    public void setString(String primaryKey, String key, String column, String table, String data) {
        setString(primaryKey, key, column, table, data, false);
    }

    /**
     * Set a String in the Database and make sure to check if the value exists, else add it.
     * (not completely working)
     *
     * @param primaryKey  the name of the primary key row.
     * @param key          the value of the primary key that is to query
     * @param column       the name of the column whose data needs to be queried
     * @param table        the table that is to be queried
     * @param data         the new String to be set
     * @param checkExsting boolean checking if a the row has any entries so far already.
     */
    public void setString(String primaryKey, String key, String column, String table, String data, boolean checkExsting) {
        try (Connection connection = dataSource.getConnection()) {
            //Check if existing -> if not insert new entry
            if (checkExsting && !hasKey(table, primaryKey, key)) {
                try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO " + table + " (" + primaryKey + ", " + column + ") VALUES(?,?)")) {
                    statement.setString(1, key);
                    statement.setString(2, data);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET " + column + " = ? WHERE " + primaryKey + " = ?")) {
                    //if existing, update old data
                    statement.setString(1, data);
                    statement.setString(2, key);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Set a Int in the Database.
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @param data        the int to be set
     */
    @Override
    public void setInt(String primaryKey, String key, String column, String table, int data) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET " + column + " = ? WHERE " + primaryKey + " = ?")) {
            statement.setInt(1, data);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Set a Double in the Database.
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @param data        the int to be set
     */
    @Override
    public void setDouble(String primaryKey, String key, String column, String table, double data) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET " + column + " = ? WHERE " + primaryKey + " = ?")) {
            statement.setDouble(1, data);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Set a Boolean in the Database.
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @param data        the Boolean to be set
     */
    @Override
    public void setBool(String primaryKey, String key, String column, String table, Boolean data) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET " + column + " = ? WHERE " + primaryKey + " = ?")) {
            statement.setBoolean(1, data);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Get a Int in the Database and increment it's value by a given value.
     *
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @param increment   the int value that the resulting data will be incremented by.
     */
    public void incrementInt(String primaryKey, String key, String column, String table, int increment) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET " + column + " = " + column + " + ? WHERE " + primaryKey + " = ?")) {
            statement.setInt(1, increment);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Reset a row in the Database.
     *
     * @param primary_key the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @param table       the table that is to be queried
     */
    public void deleteEntry(String primary_key, String key, String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE " + primary_key + " = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Prepares a Column for future data insertion.
     *
     * @param table       the table that is to be queried
     * @param primary_key the name of the primary key row.
     * @param key         the value of the primary key that is to query
     */
    public void prepareColumn(String table, String primary_key, String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("REPLACE INTO " + table + " (" + primary_key + ") VALUES(?)")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    /**
     * Query the Database and return a List of all primary Keys that have a value present in a given column.
     *
     * @param primaryKey the name of the primary key row.
     * @param column      the name of the column whose data needs to be queried
     * @param table       the table that is to be queried
     * @return a List of all resulting Keys, if none, the List will be empty
     */
    public List<String> getKeysByExistance(String primaryKey, String column, String table) {
        List<String> keys = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + primaryKey + " FROM " + table + " WHERE " + column + " IS NOT NULL");
             ResultSet caret = statement.executeQuery()) {
            while (caret.next()) {
                keys.add(caret.getString(primaryKey));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return keys;
    }

    /**
     * Query the Database for a List of all primary Keys of a given table
     *
     * @param primaryKey the name of the primary key row.
     * @param table       the table that is to be queried
     * @return the resulting keys, if none the List will be Empty
     */
    public List<String> getKeys(String primaryKey, String table) {
        List<String> keys = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + primaryKey + " FROM " + table);
             ResultSet caret = statement.executeQuery()) {
            while (caret.next()) {
                keys.add(caret.getString(primaryKey));
            }
            return keys;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return keys;
    }

    @Override
    public void preparePlayerData(String table, String uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("REPLACE INTO " + table + " (uuid) VALUES(?)")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    // Check if Player is in DB:
    @Override
    public boolean hasPlayer(String table, UUID key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, key.toString());

            try (ResultSet caret = statement.executeQuery()) {
                return caret.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return false;
    }

    /**
     * Check if the Database contains a given primary key.
     *
     * @param table       the table that is to be queried
     * @param primaryKey the name of the primary key row.
     * @param key         the value of the primary key that is to query
     * @return a boolean if the primary key exists
     */
    public boolean hasKey(String table, String primaryKey, String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + primaryKey + " = ?")) {
            statement.setString(1, key);

            try (ResultSet caret = statement.executeQuery()) {
                return caret.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return false;
    }

    /**
     * Check if the Database contains a given Table
     *
     * @param table the table that is to be queried
     * @return a boolean based on the existence of the queried table
     */
    @Override
    public boolean hasTable(String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);

            try (ResultSet caret = statement.executeQuery()) {
                return caret.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return false;
    }

    @Override
    public void insertShop(String sloc, String owner, String item, double buyprice, double sellprice, boolean msgtoggle,
                           boolean dbuy, boolean dsell, String admins, boolean shareincome, boolean adminshop, String rotation, List<String> customMessages) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "REPLACE INTO shopdata (location,owner,item,buyPrice,sellPrice,msgToggle,"
                + "buyDisabled,sellDisabled,admins,shareIncome,adminshop,rotation,customMessages) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, sloc);
            statement.setString(2, owner);
            statement.setString(3, item);
            statement.setDouble(4, buyprice);
            statement.setDouble(5, sellprice);
            statement.setBoolean(6, msgtoggle);
            statement.setBoolean(7, dbuy);
            statement.setBoolean(8, dsell);
            statement.setString(9, admins);
            statement.setBoolean(10, shareincome);
            statement.setBoolean(11, adminshop);
            statement.setString(12, rotation);
            statement.setString(13, String.join("#,#", customMessages));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
    }

    public HashMap<Location, EzShop> queryShops() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM shopdata");
             ResultSet caret = statement.executeQuery()) {
            HashMap<Location, EzShop> map = new HashMap<>();
            while (caret.next()) {
                String sloc = caret.getString("location");
                String customMessages = caret.getString("customMessages");
                if (customMessages == null)
                    customMessages = "";
                map.put(Utils.StringtoLocation(sloc),
                        new EzShop(Utils.StringtoLocation(sloc), caret.getString("owner"), Utils.decodeItem(caret.getString("item")),
                        caret.getDouble("buyPrice"), caret.getDouble("sellPrice"), new ShopSettings(sloc, caret.getBoolean("msgToggle"),
                        caret.getBoolean("buyDisabled"), caret.getBoolean("sellDisabled"), caret.getString("admins"),
                        caret.getBoolean("shareIncome"), caret.getBoolean("adminshop"),
                        caret.getString("rotation"), Arrays.asList(customMessages.split("#,#")))));
            }
            return map;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return null;
    }

}
