package tr.zeltuv.ezql.objects;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.logging.ExtendedLogger;
import org.jetbrains.annotations.NotNull;

public class EzqlQuery {
    private static final ExtendedLogger LOGGER = EzChestShop.logger();

    private final EzqlDatabase database;

    public EzqlQuery(@NotNull EzqlDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public void createTable(EzqlTable table) {
        String name = table.getName();
        List<EzqlColumn> columns = table.getColumns();
        StringJoiner stringJoiner = new StringJoiner(",");

        for (EzqlColumn column : columns) {
            stringJoiner.add(column.getName() + " " + column.getDataType() + (column.getLength() == 0 ? "" : "(" + column.getLength() + ")"));
            if (column.isPrimary()) {
                stringJoiner.add("PRIMARY KEY(" + column.getName() + ")");
            }
        }

        try (var connection = database.getConnection();
             var statement = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + name + " (" + stringJoiner + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }
    }

    public void addRow(EzqlTable ezqlTable, Object[] values) {
        StringJoiner fields = new StringJoiner(",");
        StringJoiner questionMarks = new StringJoiner(",");
        String name = ezqlTable.getName();

        for (EzqlColumn ezqlColumn : ezqlTable.getColumns()) {
            questionMarks.add("?");
            fields.add("`" + ezqlColumn.getName() + "`");
        }

        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("INSERT INTO " + name + " (" + fields + ") VALUES (" + questionMarks + ")")) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }
    }

    public List<EzqlRow> getRows(EzqlTable table, String where, Object whereValue, Set<String> neededColumns) {
        List<EzqlRow> resultRows = new ArrayList<>();

        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("SELECT * FROM " + table.getName() + " WHERE `" + where + "`= ?")) {
            statement.setObject(1, whereValue);

            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    EzqlRow results = new EzqlRow();

                    for (String column : neededColumns) {
                        if (!column.equals(where)) {
                            Object result = resultSet.getObject(column);
                            results.addValue(column, result);
                        } else {
                            results.addValue(column, whereValue);
                        }
                    }

                    resultRows.add(results);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }

        return resultRows;
    }

    public EzqlRow getSingleRow(EzqlTable table, String where, Object whereValue, Set<String> neededColumns) {
        EzqlRow results = new EzqlRow();

        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("SELECT * FROM " + table.getName() + " WHERE `" + where + "`= ?")) {
            statement.setObject(1, whereValue);

            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return results;
                }

                for (String column : neededColumns) {
                    if (!column.equals(where)) {
                        Object result = resultSet.getObject(column);
                        results.addValue(column, result);
                    } else {
                        results.addValue(column, whereValue);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }

        return results;
    }

    public boolean exists(EzqlTable ezqlTable, String where, Object value) {
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("SELECT * FROM " + ezqlTable.getName() + " WHERE `" + where + "`= ?")) {
            statement.setObject(1, value);

            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }

        return false;
    }

    public LinkedList<EzqlRow> getAllRows(EzqlTable table, Set<String> neededColumns) {
        LinkedList<EzqlRow> resultRows = new LinkedList<>();

        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("SELECT * FROM " + table.getName());
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                EzqlRow results = new EzqlRow();

                for (String column : neededColumns) {
                    Object result = resultSet.getObject(column);
                    results.addValue(column, result);
                }

                resultRows.add(results);
            }
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }

        return resultRows;
    }

    public void truncate(EzqlTable table) {
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("TRUNCATE TABLE " + table.getName())) {
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }
    }

    public void remove(EzqlTable table, String where, Object whereValue) {
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("DELETE FROM `" + table.getName() + "` WHERE `" + where + "`= ?")) {
            statement.setObject(1, whereValue);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }
    }

    public void update(EzqlTable table, String where, Object whereValue, EzqlRow ezqlRow) {
        try (var connection = database.getConnection()) {
            for (String key : ezqlRow.getValues().keySet()) {
                try (var statement = connection.prepareStatement("UPDATE " + table.getName() + " SET " + key + "=? WHERE " + where + "=?")) {
                    statement.setObject(1, ezqlRow.getValue(key));
                    statement.setObject(2, whereValue);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Caught an SQLException", e);
        }
    }
}
