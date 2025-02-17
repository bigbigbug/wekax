/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    DatabaseUtils.java
 *    Copyright (C) 1999 Len Trigg
 *
 */


package weka.experiment;

import weka.core.Utils;
import java.util.Properties;
import java.util.Vector;
import java.util.StringTokenizer;
import java.io.Serializable;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSetMetaData;

/**
 * DatabaseUtils provides utility functions for accessing the experiment
 * database. The jdbc
 * driver and database to be used default to "jdbc.idbDriver" and
 * "jdbc:idb=experiments.prp". These may be changed by creating
 * a java properties file called DatabaseUtils.props in user.home or
 * the current directory. eg:<p>
 *
 * <code><pre>
 * jdbcDriver=jdbc.idbDriver
 * jdbcURL=jdbc:idb=experiments.prp
 * </pre></code><p>
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @version $Revision: 1.2 $
 */
public class DatabaseUtils implements Serializable {

  /** The name of the table containing the index to experiments */
  public static final String EXP_INDEX_TABLE = "Experiment_index";

  /** The name of the column containing the experiment type (ResultProducer) */
  public static final String EXP_TYPE_COL    = "Experiment_type";

  /** The name of the column containing the experiment setup (parameters) */
  public static final String EXP_SETUP_COL   = "Experiment_setup";
  
  /** The name of the column containing the results table name */
  public static final String EXP_RESULT_COL  = "Result_table";

  /** The prefix for result table names */
  public static final String EXP_RESULT_PREFIX = "Results";

  /** The name of the properties file */
  protected static String PROPERTY_FILE
    = "weka/experiment/DatabaseUtils.props";

  /** Holds the jdbc drivers to be used (only to stop them being gc'ed) */
  protected static Vector DRIVERS = new Vector();

  /** Properties associated with the database connection */
  protected static Properties PROPERTIES;

  /* Load the database drivers -- the properties files only get consulted
   * when the class is initially loaded, not for every object instantiated
   */
  static {

    try {
      PROPERTIES = Utils.readProperties(PROPERTY_FILE);
   
      // Register the drivers in jdbc DriverManager
      String drivers = PROPERTIES.getProperty("jdbcDriver",
					    "jdbc.idbDriver");

      if (drivers == null) {
	throw new Exception("No jdbc drivers specified");
      }
      // The call to newInstance() is necessary on some platforms
      // (with some java VM implementations)
      StringTokenizer st = new StringTokenizer(drivers, ", ");
      while (st.hasMoreTokens()) {
	String driver = st.nextToken();
	try {
	  DRIVERS.addElement(Class.forName(driver).newInstance());
	  System.err.println("Loaded driver: " + driver);
	} catch (Exception ex) {
	  // Drop through
	}
      }
    } catch (Exception ex) {
      System.err.println("Problem reading properties. Fix before continuing.");
      System.err.println(ex);
    }
  }
  
  /** Database URL */
  protected String m_DatabaseURL;
  
  /** The database connection */
  protected Connection m_Connection;

  /** The statement used for database queries */
  protected Statement m_Statement;

  /** True if debugging output should be printed */
  protected boolean m_Debug = true;

  
  /**
   * Sets up the database drivers
   *
   * @exception Exception if an error occurs
   */
  public DatabaseUtils() throws Exception {

    m_DatabaseURL = PROPERTIES.getProperty("jdbcURL",
					   "jdbc:idb=experiments.prp");
  }

  /**
   * Converts an array of objects to a string by inserting a space
   * between each element. Null elements are printed as ?
   *
   * @param array the array of objects
   * @return a value of type 'String'
   */
  public static String arrayToString(Object [] array) {

    String result = "";
    if (array == null) {
      result = "<null>";
    } else {
      for (int i = 0; i < array.length; i++) {
	if (array[i] == null) {
	  result += " ?";
	} else {
	  result += " " + array[i];
	}
      }
    }
    return result;
  }

  /**
   * Returns the name associated with a SQL type.
   *
   * @param type the SQL type
   * @return the name of the type
   */
  public static String typeName(int type) {
  
    switch (type) {
    case Types.BIGINT :
      return "BIGINT ";
    case Types.BINARY:
      return "BINARY";
    case Types.BIT:
      return "BIT";
    case Types.CHAR:
      return "CHAR";
    case Types.DATE:
      return "DATE";
    case Types.DECIMAL:
      return "DECIMAL";
    case Types.DOUBLE:
      return "DOUBLE";
    case Types.FLOAT:
      return "FLOAT";
    case Types.INTEGER:
      return "INTEGER";
    case Types.LONGVARBINARY:
      return "LONGVARBINARY";
    case Types.LONGVARCHAR:
      return "LONGVARCHAR";
    case Types.NULL:
      return "NULL";
    case Types.NUMERIC:
      return "NUMERIC";
    case Types.OTHER:
      return "OTHER";
    case Types.REAL:
      return "REAL";
    case Types.SMALLINT:
      return "SMALLINT";
    case Types.TIME:
      return "TIME";
    case Types.TIMESTAMP:
      return "TIMESTAMP";
    case Types.TINYINT:
      return "TINYINT";
    case Types.VARBINARY:
      return "VARBINARY";
    case Types.VARCHAR:
      return "VARCHAR";
    default:
      return "Unknown";
    }
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String databaseURLTipText() {
    return "Set the URL to the database.";
  }

  /**
   * Get the value of DatabaseURL.
   *
   * @return Value of DatabaseURL.
   */
  public String getDatabaseURL() {
    
    return m_DatabaseURL;
  }
  
  /**
   * Set the value of DatabaseURL.
   *
   * @param newDatabaseURL Value to assign to DatabaseURL.
   */
  public void setDatabaseURL(String newDatabaseURL) {
    
    m_DatabaseURL = newDatabaseURL;
  }

  /**
   * Opens a connection to the database
   *
   * @exception Exception if an error occurs
   */
  public void connectToDatabase() throws Exception {
    if (PROPERTIES == null) { 
      try {
	PROPERTIES = Utils.readProperties(PROPERTY_FILE);
   
	// Register the drivers in jdbc DriverManager
	String drivers = PROPERTIES.getProperty("jdbcDriver",
						"jdbc.idbDriver");

	if (drivers == null) {
	  throw new Exception("No jdbc drivers specified");
	}
	// The call to newInstance() is necessary on some platforms
	// (with some java VM implementations)
	StringTokenizer st = new StringTokenizer(drivers, ", ");
	while (st.hasMoreTokens()) {
	  String driver = st.nextToken();
	  try {
	    DRIVERS.addElement(Class.forName(driver).newInstance());
	    System.err.println("Loaded driver: " + driver);
	  } catch (Exception ex) {
	    // Drop through
	  }
	}
      } catch (Exception ex) {
	System.err.println("Problem reading properties. Fix before continuing.");
	System.err.println(ex);
      }
    }
    
    if (m_Debug) {
      System.err.println("Connecting to " + m_DatabaseURL);
    }
    if (m_Connection == null) {
      m_Connection = DriverManager.getConnection(m_DatabaseURL);
      m_Statement = m_Connection.createStatement();
    }
  }

  /**
   * Closes the connection to the database.
   *
   * @exception Exception if an error occurs
   */
  public void disconnectFromDatabase() throws Exception {

    if (m_Debug) {
      System.err.println("Disconnecting from " + m_DatabaseURL);
    }
    if (m_Connection != null) {
      m_Connection.close();
      m_Connection = null;
      m_Statement = null;
    }
  }
  
  /**
   * Returns true if a database connection is active.
   *
   * @return a value of type 'boolean'
   */
  public boolean isConnected() {

    return (m_Connection != null);
  }

  /**
   * Executes a SQL query.
   *
   * @param query the SQL query
   * @return true if the query generated results
   * @exception SQLException if an error occurs
   */
  public boolean execute(String query) throws SQLException {

    return m_Statement.execute(query);
  }

  /**
   * Gets the results generated by a previous query.
   *
   * @return the result set.
   * @exception SQLException if an error occurs
   */
  public ResultSet getResultSet() throws SQLException {

    return m_Statement.getResultSet();
  }
  
  /**
   * Checks that a given table exists.
   *
   * @param tableName the name of the table to look for.
   * @return true if the table exists.
   * @exception Exception if an error occurs.
   */
  public boolean tableExists(String tableName) throws Exception {

    if (m_Debug) {
      System.err.println("Checking if table " + tableName + " exists...");
    }
    DatabaseMetaData dbmd = m_Connection.getMetaData();
    ResultSet rs = dbmd.getTables (null, null, tableName, null);
    boolean tableExists = rs.next();
    if (rs.next()) {
      throw new Exception("This table seems to exist more than once!");
    }
    rs.close();
    if (m_Debug) {
      if (tableExists) {
	System.err.println("... " + tableName + " exists");
      } else {
	System.err.println("... " + tableName + " does not exist");
      }
    }
    return tableExists;
  }
  
  /**
   * Executes a database query to see whether a result for the supplied key
   * is already in the database.           
   *
   * @param tableName the name of the table to search for the key in
   * @param rp the ResultProducer who will generate the result if required
   * @param key the key for the result
   * @return true if the result with that key is in the database already
   * @exception Exception if an error occurs
   */
  protected boolean isKeyInTable(String tableName,
				 ResultProducer rp,
				 Object[] key)
    throws Exception {

    String query = "SELECT Key_Run"
      + " FROM " + tableName;
    String [] keyNames = rp.getKeyNames();
    if (keyNames.length != key.length) {
      throw new Exception("Key names and key values of different lengths");
    }
    boolean first = true;
    for (int i = 0; i < key.length; i++) {
      if (key[i] != null) {
	if (first) {
	  query += " WHERE ";
	  first = false;
	} else {
	  query += " AND ";
	}
	query += "Key_" + keyNames[i] + '=';
	if (key[i] instanceof String) {
	  query += '\'' + key[i].toString() + '\'';
	} else {
	  query += key[i].toString();
	}
      }
    }
    boolean retval = false;
    if (m_Statement.execute(query)) {
      ResultSet rs = m_Statement.getResultSet();
      int numAttributes = rs.getMetaData().getColumnCount();
      if (rs.next()) {
	retval = true;
	if (rs.next()) {
	  throw new Exception("More than one result entry "
			      + "for result key: " + query);
	}
      }
      rs.close();
    }
    return retval;
  }

  /**
   * Executes a database query to extract a result for the supplied key
   * from the database.           
   *
   * @param tableName the name of the table where the result is stored
   * @param rp the ResultProducer who will generate the result if required
   * @param key the key for the result
   * @return true if the result with that key is in the database already
   * @exception Exception if an error occurs
   */
  public Object [] getResultFromTable(String tableName,
					 ResultProducer rp,
					 Object [] key)
    throws Exception {

    String query = "SELECT ";
    String [] resultNames = rp.getResultNames();
    for (int i = 0; i < resultNames.length; i++) {
      if (i != 0) {
	query += ", ";
      }
      query += resultNames[i];
    }
    query += " FROM " + tableName;
    String [] keyNames = rp.getKeyNames();
    if (keyNames.length != key.length) {
      throw new Exception("Key names and key values of different lengths");
    }
    boolean first = true;
    for (int i = 0; i < key.length; i++) {
      if (key[i] != null) {
	if (first) {
	  query += " WHERE ";
	  first = false;
	} else {
	  query += " AND ";
	}
	query += "Key_" + keyNames[i] + '=';
	if (key[i] instanceof String) {
	  query += '"' + key[i].toString() + '"';
	} else {
	  query += key[i].toString();
	}
      }
    }
    if (!m_Statement.execute(query)) {
      throw new Exception("Couldn't execute query: " + query);
    }
    ResultSet rs = m_Statement.getResultSet();
    ResultSetMetaData md = rs.getMetaData();
    int numAttributes = md.getColumnCount();
    if (!rs.next()) {
      throw new Exception("No result for query: " + query);
    }
    // Extract the columns for the result
    Object [] result = new Object [numAttributes];
    for(int i = 1; i <= numAttributes; i++) {
      switch (md.getColumnType(i)) {
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.BINARY:
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
	result[i - 1] = rs.getString(i);
	if (rs.wasNull()) {
	  result[i - 1] = null;
	}
	break;
      case Types.FLOAT:
      case Types.DOUBLE:
	result[i - 1] = new Double(rs.getDouble(i));
	if (rs.wasNull()) {
	  result[i - 1] = null;
	}
	break;
      default:
	throw new Exception("Unhandled SQL result type (field " + (i + 1)
			    + "): "
			    + DatabaseUtils.typeName(md.getColumnType(i)));
      }
    }
    if (rs.next()) {
      throw new Exception("More than one result entry "
			  + "for result key: " + query);
    }
    rs.close();
    return result;
  }

  /**
   * Executes a database query to insert a result for the supplied key
   * into the database.           
   *
   * @param tableName the name of the table where the result is stored
   * @param rp the ResultProducer who will generate the result if required
   * @param key the key for the result
   * @param result the result to store
   * @return true if the result with that key is in the database already
   * @exception Exception if an error occurs
   */
  public void putResultInTable(String tableName,
			       ResultProducer rp,
			       Object [] key,
			       Object [] result)
    throws Exception {
    
    String query = "INSERT INTO " + tableName
      + " VALUES ( ";
    // Add the results to the table
    for (int i = 0; i < key.length; i++) {
      if (i != 0) {
	query += ',';
      }
      if (key[i] != null) {
	if (key[i] instanceof String) {
	  query += '\'' + key[i].toString() + '\'';
	} else if (key[i] instanceof Double) {
	  query += safeDoubleToString((Double)key[i]);
	} else {
	  query += key[i].toString();
	}
      } else {
	query += "NULL";
      }
    }
    for (int i = 0; i < result.length; i++) {
      query +=  ',';
      if (result[i] != null) {
	if (result[i] instanceof String) {
	  query += '"' + result[i].toString() + '"';
	} else  if (result[i] instanceof Double) {
	  query += safeDoubleToString((Double)result[i]);
	} else {
	  query += result[i].toString();
	}
      } else {
	query += "NULL";
      }
    }
    query += ')';
    if (m_Debug) {
      System.err.println("Submitting result: " + query);
    }
    if (m_Statement.execute(query)) {
      if (m_Debug) {
	System.err.println("...acceptResult returned resultset");
      }
    }
  }
  
  /**
   * Inserts a + if the double is in scientific notation.
   * MySQL doesn't understand the number otherwise.
   */
  private String safeDoubleToString(Double number) {

    String orig = number.toString();

    int pos = orig.indexOf('E');
    if ((pos == -1) || (orig.charAt(pos + 1) == '-')) {
      return orig;
    } else {
      StringBuffer buff = new StringBuffer(orig);
      buff.insert(pos + 1, '+');
      return new String(buff);
    }
  }

  /**
   * Returns true if the experiment index exists.
   *
   * @return true if the index exists
   * @exception Exception if an error occurs
   */
  public boolean experimentIndexExists() throws Exception {

    return tableExists(EXP_INDEX_TABLE);
  }
  
  /**
   * Attempts to create the experiment index table
   *
   * @exception Exception if an error occurs.
   */
  public void createExperimentIndex() throws Exception {

    if (m_Debug) {
      System.err.println("Creating experiment index table...");
    }
    String query;

    // Workaround for MySQL (doesn't support LONGVARBINARY)
    // Also for InstantDB which attempts to interpret numbers when storing
    // in LONGVARBINARY
    if (m_Connection.getMetaData().getDriverName().
	equals("Mark Matthews' MySQL Driver")
	|| (m_Connection.getMetaData().getDriverName().
	indexOf("InstantDB JDBC Driver") != -1) ||
	(m_Connection.getMetaData().getDriverName().
	indexOf("MySQL-AB JDBC Driver") != -1)) {
      query = "CREATE TABLE " + EXP_INDEX_TABLE 
	+ " ( " + EXP_TYPE_COL + " TEXT,"
	+ "  " + EXP_SETUP_COL + " TEXT,"
	+ "  " + EXP_RESULT_COL + " INT )";
    } else {
      query = "CREATE TABLE " + EXP_INDEX_TABLE 
	+ " ( " + EXP_TYPE_COL + " LONGVARBINARY,"
	+ "  " + EXP_SETUP_COL + " LONGVARBINARY,"
	+ "  " + EXP_RESULT_COL + " INT )";
    }
    // Other possible fields:
    //   creator user name (from System properties)
    //   creation date
    if (m_Statement.execute(query)) {
      if (m_Debug) {
	System.err.println("...create returned resultset");
      }
    }
  }

  /**
   * Attempts to insert a results entry for the table into the
   * experiment index.
   *
   * @param rp the ResultProducer generating the results
   * @return the name of the created results table
   * @exception Exception if an error occurs.
   */
  public String createExperimentIndexEntry(ResultProducer rp)
    throws Exception {

    if (m_Debug) {
      System.err.println("Creating experiment index entry...");
    }

    // Execute compound transaction
    int numRows = 0;
    
    // Workaround for MySQL (doesn't support transactions)
    if (m_Connection.getMetaData().getDriverName().
	equals("Mark Matthews' MySQL Driver")) {
      m_Statement.execute("LOCK TABLES " + EXP_INDEX_TABLE + " WRITE");
      System.err.println("LOCKING TABLE");
    } else {
      m_Connection.setAutoCommit(false);
    }

    // Get the number of rows
    String query = "SELECT COUNT(*) FROM " + EXP_INDEX_TABLE;
    if (m_Statement.execute(query)) {
      if (m_Debug) {
	System.err.println("...getting number of rows");
      }
      ResultSet rs = m_Statement.getResultSet();
      if (rs.next()) {
	numRows = rs.getInt(1);
      }
      rs.close();
    }

    // Add an entry in the index table
    String expType = rp.getClass().getName();
    String expParams = rp.getCompatibilityState();
    query = "INSERT INTO " + EXP_INDEX_TABLE
      + " VALUES ( \""
      + expType + "\", \"" + expParams
      + "\", " + numRows + " )";
    if (m_Statement.execute(query)) {
      if (m_Debug) {
	System.err.println("...create returned resultset");
      }
    }
    
    // Finished compound transaction
    // Workaround for MySQL (doesn't support transactions)
    if (m_Connection.getMetaData().getDriverName().
	equals("Mark Matthews' MySQL Driver")) {
      m_Statement.execute("UNLOCK TABLES");
      System.err.println("UNLOCKING TABLE");
    } else {
      m_Connection.commit();
      m_Connection.setAutoCommit(true);
    }

    String tableName = getResultsTableName(rp);
    if (tableName == null) {
      throw new Exception("Problem adding experiment index entry");
    }

    // Drop any existing table by that name (shouldn't occur unless
    // the experiment index is destroyed, in which case the experimental
    // conditions of the existing table are unknown)
    try {
      query = "DROP TABLE " + tableName;
      if (m_Debug) {
	System.err.println(query);
      }
      m_Statement.execute(query);
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
    return tableName;
  }

  /**
   * Gets the name of the experiment table that stores results from a
   * particular ResultProducer.
   *
   * @param rp the ResultProducer
   * @return the name of the table where the results for this ResultProducer
   * are stored, or null if there is no table for this ResultProducer.
   * @exception Exception if an error occurs
   */
  public String getResultsTableName(ResultProducer rp) throws Exception {

    // Get the experiment table name, or create a new table if necessary.
    if (m_Debug) {
      System.err.println("Getting results table name...");
    }
    String expType = rp.getClass().getName();
    String expParams = rp.getCompatibilityState();
    String query = "SELECT " + EXP_RESULT_COL 
      + " FROM " + EXP_INDEX_TABLE
      + " WHERE " + EXP_TYPE_COL + "=\"" + expType 
      + "\" AND " + EXP_SETUP_COL + "=\"" + expParams + '"';
    String tableName = null;
    if (m_Statement.execute(query)) {
      ResultSet rs = m_Statement.getResultSet();
      int numAttributes = rs.getMetaData().getColumnCount();
      if (rs.next()) {
	tableName = rs.getString(1);
	if (rs.next()) {
	  throw new Exception("More than one index entry "
			      + "for experiment config: " + query);
	}
      }
      rs.close();
    }
    if (m_Debug) {
      System.err.println("...results table = " + ((tableName == null) 
						  ? "<null>" 
						  : EXP_RESULT_PREFIX
						  + tableName));
    }
    return (tableName == null) ? tableName : EXP_RESULT_PREFIX + tableName;
  }

  /**
   * Creates a results table for the supplied result producer.
   *
   * @param rp the ResultProducer generating the results
   * @param tableName the name of the resultsTable
   * @return the name of the created results table
   * @exception Exception if an error occurs.
   */
  public String createResultsTable(ResultProducer rp, String tableName)
    throws Exception {

    if (m_Debug) {
      System.err.println("Creating results table " + tableName + "...");
    }
    String query = "CREATE TABLE " + tableName + " ( ";
    // Loop over the key fields
    String [] names = rp.getKeyNames();
    Object [] types = rp.getKeyTypes();
    if (names.length != types.length) {
      throw new Exception("key names types differ in length");
    }
    for (int i = 0; i < names.length; i++) {
      query += "Key_" + names[i] + " ";
      if (types[i] instanceof Double) {
	query += "DOUBLE";
      } else if (types[i] instanceof String) {

	// Workaround for MySQL (doesn't support LONGVARCHAR)
	// Also for InstantDB which attempts to interpret numbers when storing
	// in LONGVARBINARY
	if (m_Connection.getMetaData().getDriverName().
	    equals("Mark Matthews' MySQL Driver")
	    || (m_Connection.getMetaData().getDriverName().
		indexOf("InstantDB JDBC Driver")) != -1
	    || (m_Connection.getMetaData().getDriverName().
		indexOf("MySQL-AB JDBC Driver") != -1)) {
	  query += "TEXT ";
	} else {
	  query += "LONGVARBINARY ";
	}
      } else {
	throw new Exception("Unknown/unsupported field type in key");
      }
      query += ", ";
    }
    // Loop over the result fields
    names = rp.getResultNames();
    types = rp.getResultTypes();
    if (names.length != types.length) {
      throw new Exception("result names and types differ in length");
    }
    for (int i = 0; i < names.length; i++) {
      query += names[i] + " ";
      if (types[i] instanceof Double) {
	query += "DOUBLE";
      } else if (types[i] instanceof String) {
	
	// Workaround for MySQL (doesn't support LONGVARCHAR)
	// Also for InstantDB which attempts to interpret numbers when storing
	// in LONGVARBINARY
	if (m_Connection.getMetaData().getDriverName().
	    equals("Mark Matthews' MySQL Driver")
	    || (m_Connection.getMetaData().getDriverName().
		equals("InstantDB JDBC Driver")) ||
	    (m_Connection.getMetaData().getDriverName().
	     indexOf("MySQL-AB JDBC Driver") != -1)) {
	  query += "TEXT ";
	} else {
	  query += "LONGVARBINARY ";
	}
      } else {
	throw new Exception("Unknown/unsupported field type in key");
      }
      if (i < names.length - 1) {
	query += ", ";
      }
    }
    query += " )";
    System.err.println(query);
    if (m_Statement.execute(query)) {
      if (m_Debug) {
	System.err.println("...create returned resultset");
      }
    }
    return tableName;
  }


} // DatabaseUtils
