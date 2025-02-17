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
 *    NumericTransform.java
 *    Copyright (C) 1999 Eibe Frank
 *
 */

package weka.filters.unsupervised.attribute;

import weka.filters.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import weka.core.*;

/** 
 * Transforms numeric attributes using a
 * given transformation method.<p>
 *
 * Valid filter-specific options are: <p>
 *
 * -R index1,index2-index4,...<br>
 * Specify list of columns to transform. First and last are valid indexes.
 * (default none). Non-numeric columns are skipped.<p>
 *
 * -V<br>
 * Invert matching sense.<p>
 *
 * -C string <br>
 * Name of the class containing the method used for transformation. 
 * (default java.lang.Math) <p>
 *
 * -M string <br>
 * Name of the method used for the transformation.
 * (default abs) <p>
 *
 * @author Eibe Frank (eibe@cs.waikato.ac.nz) 
 * @version $Revision: 1.1.1.1 $
 */
public class NumericTransform extends Filter
  implements UnsupervisedFilter, StreamableFilter, OptionHandler {

  /** Stores which columns to transform. */
  private Range m_Cols = new Range();

  /** Class containing transformation method. */
  private Class m_Class;

  /** Transformation method. */
  private Method m_Method;

  /** Parameter types. */
  private static Class[] PARAM = new Class[] {Double.TYPE};

  /**
   * Default constructor -- sets the default transform method
   * to java.lang.Math.abs().
   */
  public NumericTransform() {

    try {
      m_Class = Math.class;
      m_Method = Math.class.getMethod("abs", PARAM);
    } catch (NoSuchMethodException ex) {
      System.err.println("One of those errors that you should never see");
      ex.printStackTrace();
    }
  }

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input 
   * instance structure (any instances contained in the object are 
   * ignored - only the structure is required).
   * @return true if the outputFormat may be collected immediately
   * @exception Exception if the input format can't be set 
   * successfully
   */
  public boolean setInputFormat(Instances instanceInfo) 
       throws Exception {

    if (m_Class == null) {
      throw new IllegalStateException("No class has been set.");
    }
    if (m_Method == null) {
      throw new IllegalStateException("No method has been set.");
    }
    super.setInputFormat(instanceInfo);
    m_Cols.setUpper(instanceInfo.numAttributes() - 1);
    setOutputFormat(instanceInfo);
    return true;
  }

  /**
   * Input an instance for filtering. The instance is processed
   * and made available for output immediately.
   *
   * @param instance the input instance
   * @return true if the filtered instance may now be
   * collected with output().
   * @exception IllegalStateException if no input format has been set.
   * @exception InvocationTargetException if there is a problem applying
   * the configured transform method.
   */
  public boolean input(Instance instance) throws Exception {

    if (getInputFormat() == null) {
      throw new IllegalStateException("No input instance format defined");
    }
    if (m_NewBatch) {
      resetQueue();
      m_NewBatch = false;
    }

    double []vals = new double[instance.numAttributes()];
    Double []params = new Double[1];
    Double newVal;
    for(int i = 0; i < instance.numAttributes(); i++) {
      if (instance.isMissing(i)) {
	vals[i] = Instance.missingValue();
      } else {
	if (m_Cols.isInRange(i) &&
	    instance.attribute(i).isNumeric()) {
	  params[0] = new Double(instance.value(i));
	  newVal = (Double) m_Method.invoke(null, params);
	  if (newVal.isNaN() || newVal.isInfinite()) {
	    vals[i] = Instance.missingValue();
	  } else {
	    vals[i] = newVal.doubleValue(); 
	  }
	} else {
	  vals[i] = instance.value(i);
	}
      }
    }
    Instance inst = null;
    if (instance instanceof SparseInstance) {
      inst = new SparseInstance(instance.weight(), vals);
    } else {
      inst = new Instance(instance.weight(), vals);
    }
    inst.setDataset(instance.dataset());
    push(inst);
    return true;
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(4);

    newVector.addElement(new Option(
              "\tSpecify list of columns to transform. First and last are\n"
	      + "\tvalid indexes (default none). Non-numeric columns are \n"
	      + "\tskipped.",
              "R", 1, "-R <index1,index2-index4,...>"));

    newVector.addElement(new Option(
	      "\tInvert matching sense.",
              "V", 0, "-V"));

    newVector.addElement(new Option(
              "\tSets the class containing transformation method.\n"+
              "\t(default java.lang.Math)",
              "C", 1, "-C <string>"));

    newVector.addElement(new Option(
              "\tSets the method. (default abs)",
              "M", 1, "-M <string>"));

    return newVector.elements();
  }


  /**
   * Parses the options for this object. Valid options are: <p>
   *
   * -R index1,index2-index4,...<br>
   * Specify list of columns to transform. First and last are valid indexes.
   * (default none). Non-numeric columns are skipped.<p>
   *
   * -V<br>
   * Invert matching sense.<p>
   *
   * -C string <br>
   * Name of the class containing the method used for transformation.
   * (default java.lang.Math) <p>
   *
   * -M string <br>
   * Name of the method used for the transformation.
   * (default abs) <p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {
    
    setAttributeIndices(Utils.getOption('R', options));
    setInvertSelection(Utils.getFlag('V', options));
    setClassName(Utils.getOption('C', options));
    setMethodName(Utils.getOption('M', options));

    if (getInputFormat() != null) {
      setInputFormat(getInputFormat());
    }
  }

  /**
   * Gets the current settings of the filter.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    String [] options = new String [7];
    int current = 0;

    if (getInvertSelection()) {
      options[current++] = "-V";
    }
    if (!getAttributeIndices().equals("")) {
      options[current++] = "-R"; options[current++] = getAttributeIndices();
    }
    if (m_Class != null) {
      options[current++] = "-C"; options[current++] = getClassName();
    }
    if (m_Method != null) {
      options[current++] = "-M"; options[current++] = getMethodName();
    }

    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Get the class containing the transformation method.
   *
   * @return string describing the class
   */
  public String getClassName() {

    return m_Class.getName();
  }
 
  /**
   * Sets the class containing the transformation method.
   *
   * @param name the name of the class
   * @exception ClassNotFoundException if class can't be found
   */
  public void setClassName(String name) throws ClassNotFoundException {
  
    m_Class = Class.forName(name);
  }
 
  /**
   * Get the transformation method.
   *
   * @return string describing the transformation method.
   */
  public String getMethodName() {

    return m_Method.getName();
  }

  /**
   * Set the transformation method.
   *
   * @param name the name of the method
   * @exception NoSuchMethodException if method can't be found in class
   */
  public void setMethodName(String name) throws NoSuchMethodException {

    m_Method = m_Class.getMethod(name, PARAM);
  }

  /**
   * Get whether the supplied columns are to be transformed or not
   *
   * @return true if the supplied columns will be kept
   */
  public boolean getInvertSelection() {

    return m_Cols.getInvert();
  }

  /**
   * Set whether selected columns should be transformed or not. 
   *
   * @param invert the new invert setting
   */
  public void setInvertSelection(boolean invert) {

    m_Cols.setInvert(invert);
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String attributeIndicesTipText() {
    return "Specify range of attributes to act on."
      + " This is a comma separated list of attribute indices, with"
      + " \"first\" and \"last\" valid values. Specify an inclusive"
      + " range with \"-\". E.g: \"first-3,5,6-10,last\".";
  }

  /**
   * Get the current range selection
   *
   * @return a string containing a comma separated list of ranges
   */
  public String getAttributeIndices() {

    return m_Cols.getRanges();
  }

  /**
   * Set which attributes are to be transformed (or kept if invert is true). 
   *
   * @param rangeList a string representing the list of attributes. Since
   * the string will typically come from a user, attributes are indexed from
   * 1. <br> eg: 
   * first-3,5,6-last
   * @exception InvalidArgumentException if an invalid range list is supplied
   */

  public void setAttributeIndices(String rangeList) {

    m_Cols.setRanges(rangeList);
  }

  /**
   * Set which attributes are to be transformed (or kept if invert is true)
   *
   * @param attributes an array containing indexes of attributes to select.
   * Since the array will typically come from a program, attributes are indexed
   * from 0.
   * @exception InvalidArgumentException if an invalid set of ranges is supplied
   */
  public void setAttributeIndicesArray(int [] attributes) {

    setAttributeIndices(Range.indicesToRangeList(attributes));
  }
  

  /**
   * Main method for testing this class.
   *
   * @param argv should contain arguments to the filter: use -h for help
   */
  public static void main(String [] argv) {

    try {
      if (Utils.getFlag('b', argv)) {
	Filter.batchFilterFile(new NumericTransform(), argv);
      } else {
	Filter.filterFile(new NumericTransform(), argv);
      }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}








