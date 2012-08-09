package org.jumpmind.symmetric;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlTemplate;


/**
 * Generate data for populating databases.
 */ 
class DbFill {
    
    private String catalog;
    
    private String schema;
    
    private IDatabasePlatform platform;
    
    private boolean ignoreMissingTables;
    
    private int inputLength = 10;
    
    public DbFill() {
    }

    public DbFill(IDatabasePlatform platform) {
        this.platform = platform;
    }

    public DbFill(DataSource dataSource) {
        platform = JdbcDatabasePlatformFactory.createNewPlatformInstance(dataSource, null, true);
    }
    
    public void setDataSource(DataSource dataSource) {
        platform = JdbcDatabasePlatformFactory.createNewPlatformInstance(dataSource, null, true);
    }
    
    public void fillTables(String[] tableNames) {
        ArrayList<Table> tableList = new ArrayList<Table>();
        
        for (String tableName : tableNames) {
            Table table = platform.readTableFromDatabase(getCatalogToUse(), getSchemaToUse(), tableName);
            if (table != null) {
                tableList.add(table);
            } else if (! ignoreMissingTables){
                throw new RuntimeException("Cannot find table " + tableName + " in catalog " + getCatalogToUse() +
                        " and schema " + getSchemaToUse());
            }
        }
        
        fillTables(tableList.toArray(new Table[tableList.size()]));
    }
    
    private void fillTables(Table[] tables) {
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        tables = Database.sortByForeignKeys(tables);
        
        // Maintain a map of previously inserted values for foreign key constraints
        Map<String,Object> insertedColumns = new HashMap<String,Object>(tables.length);
        
        // Insert multiple records
        for (int i = 0; i < inputLength; i++) {
            
            for (Table table : tables) {
                DmlStatement statement = platform.createDmlStatement(DmlType.INSERT, table);
                
                Column[] columns = table.getColumns();
                Object[] values = generateRandomValues(insertedColumns, table);
                for (int j=0; j<columns.length; j++) {
                    String qualifiedColumnName = table.getName() + "." + columns[j].getName();
                    insertedColumns.put(qualifiedColumnName, values[j]);
                }
                int rows = sqlTemplate.update(statement.getSql(), values);
                System.out.println(rows + " inserted in " + table.getFullyQualifiedTableName() + ".");
            }

            insertedColumns.clear();
        }
    }
    
    private Object[] generateRandomValues(Map<String,Object> insertedColumns, Table table) {

        Column[] columns = table.getColumns();
        List<Object> list = new ArrayList<Object>(columns.length);
        
        for_column:
        for (Column column : columns) {
            
            Object objectValue = null;

            for (ForeignKey fk : table.getForeignKeys() ) {
                for (Reference ref : fk.getReferences()) {
                    objectValue = insertedColumns.get(fk.getForeignTableName() + "." + ref.getForeignColumnName());
                    if (objectValue != null) {
                        list.add(objectValue);
                        continue for_column;
                    } else {
                        throw new RuntimeException("The foreign key column, " + column.getName() + " found in " + table.getName() + " references " + 
                                                   "table " + fk.getForeignTableName() + " which was not included.");
                    }
                }
            }

            int type = column.getMappedTypeCode();
            if (type == Types.DATE || type == Types.TIMESTAMP || type == Types.TIME) {
                objectValue = randomDate();
            } else if (type == Types.CHAR) {
                objectValue = randomChar().toString();
            } else if (type == Types.INTEGER || type == Types.SMALLINT
                    || type == Types.BIT) {
                objectValue = randomInt();
            } else if (type == Types.TINYINT) {
                objectValue = randomTinyInt();
            } else if (type == Types.NUMERIC || type == Types.DECIMAL
                    || type == Types.FLOAT || type == Types.DOUBLE 
                    || type == Types.REAL) {
                objectValue = randomBigDecimal();
            } else if (type == Types.BOOLEAN) {
                objectValue = randomBoolean();
            } else if (type == Types.BLOB || type == Types.LONGVARBINARY
                    || type == Types.BINARY || type == Types.VARBINARY ||
                    type == Types.CLOB ||
                    // SQLServer next type
                    type == -10) {
                    objectValue = randomBytes();
            } else if (type == Types.ARRAY) {
                objectValue = null;
            } else if (type == Types.VARCHAR) {
                objectValue = randomString(column.getSizeAsInt());
            }
            if (objectValue == null) {
                throw new RuntimeException("No random value generated for the object.");
            }
            list.add(objectValue);
        }
        return list.toArray();
    }
    
    private Object randomTinyInt() {
        // TINYINT (-128 to 127)
        return new Integer(new java.util.Random().nextInt(256) - 128);
    }

    private String randomString(int maxLength) {
        StringBuilder str = new StringBuilder(maxLength);
        for (int i=0; i<maxLength; i++) {
            str.append(randomChar());
        }
        return str.toString();
    }

    private byte[] randomBytes() {
        int length = 10;
        java.util.Random rnd = new java.util.Random();
        byte array[]=new byte[length];
        for (int i=0; i<length; i++) {
            array[i] = (byte)rnd.nextInt(256);
        }
        return array;
    }

    private boolean randomBoolean() {
        return new java.util.Random().nextBoolean() ;
    }

    private BigDecimal randomBigDecimal() {
        return new BigDecimal(new java.util.Random().nextDouble());
    }

    private Character randomChar() {
        int rnd = new java.util.Random().nextInt(52);
        char base = (rnd < 26) ? 'A' : 'a';
        return (char) (base + rnd % 26);
    }

    private Date randomDate() {
        // Random date between 1970 and 2020
        long l = Math.abs(new java.util.Random().nextLong());
        long ms = (50L * 365 * 24 * 60 * 60 * 1000);
        return new Date(l % ms);
    }
    
    private Integer randomInt() {
        return new Integer(new java.util.Random().nextInt(1000000));
    }
    
    protected String getSchemaToUse() {
        if (StringUtils.isBlank(schema)) {
            return platform.getDefaultSchema();
        } else {
            return schema;
        }
    }
    
    protected String getCatalogToUse() {
        if (StringUtils.isBlank(catalog)) {
            return platform.getDefaultCatalog();
        } else {
            return catalog;
        }
    }    

    public void setPlatform(IDatabasePlatform platform) {
        this.platform = platform;
    }

    public int getRecordCount() {
        return inputLength;
    }

    public void setRecordCount(int recordCount) {
        this.inputLength = recordCount;
    }
    
    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }
    
    public void setSchema(String schema) {
        this.schema = schema;
    }
    
}