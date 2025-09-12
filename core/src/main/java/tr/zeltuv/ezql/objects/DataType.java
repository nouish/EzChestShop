package tr.zeltuv.ezql.objects;

public enum DataType {

    //Integer
    TINYINT,
    SMALLINT,
    MEDIUMINT,
    INT,
    BIGINT,
    BIT,

    //Real
    FLOAT,
    DOUBLE,
    DECIMAL,

    //Text
    VARCHAR,
    CHAR,
    TINYTEXT,
    TEXT,
    MEDIUMTEXT,
    LONGTEXT,

    //Binary
    BINARY,
    VARBINARY,
    TINYBLOB,
    BLOB,
    MEDIUMBLOB,
    LONGBLOB,

    //Temporal
    DATE,
    TIME,
    YEAR,
    DATETIME,
    TIMESTAMP,

    //Spatial
    POINT,
    LINESTRING,
    POLYGON,
    GEOMETRY,
    MULTIPOINT,
    MULTILINESTRING,
    MULTIPOLYGON,
    GEOMETRYCOLLECTION,

    //Other
    UNKNOWN,
    ENUM,
    SET
}
