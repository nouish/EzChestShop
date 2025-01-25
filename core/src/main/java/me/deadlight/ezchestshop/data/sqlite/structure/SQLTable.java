package me.deadlight.ezchestshop.data.sqlite.structure;

import java.util.Map;

public class SQLTable {
    private Map<String, SQLColumn> table;

    public SQLTable(Map<String, SQLColumn> table) {
        this.table = table;
    }

    public Map<String, SQLColumn> getTable() {
        return table;
    }

    public void setTable(Map<String, SQLColumn> table) {
        this.table = table;
    }
}
