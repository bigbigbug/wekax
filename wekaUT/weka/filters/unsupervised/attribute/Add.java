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
 *    Add.java
 *    Copyright (C) 1999 Len Trigg
 *
 */


package weka.filters.unsupervised.attribute;

import weka.filters.*;
import java.io.*;
import java.util.*;
import weka.core.*;

/** 
 * An instance filter that adds a new attribute to the dataset. 
 * The new attribute contains all missing values.<p>
 *
 * Valid filter-specific options are:<p>
 *
 * -C index <br>
 * Specify where to insert the column. First and last are valid indexes.
 * (default last)<p>
 *
 * -L label1,label2,...<br>
 * Create nominal attribute with the given labels
 * (default numeric attribute)<p>
 *
 * -N name<br>
 * Name of the new attribute. (default = 'Unnamed')<p>
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class Add extends Filter implements UnsupervisedFilter,
					   StreamableFilter, OptionHandler {

  /** Record the type of attribute to insert */
  protected int m_AttributeType = Attribute.NUMERIC;

  /** The name for the new attribute */
  protected String m_Name = "unnamed";

  /** The location to insert the new attribute */
  protected int m_Insert = -1;

  /** The list of labels for nominal attribute */
  protected FastVector m_Labels = new FastVector(5);

  /**
   * Returns a string describing this filter
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {

    return "An instance filter that adds a new attribute to the dataset."
      + " The new attribute will contain all missing values.";
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(3);

    newVector.addElement(new Option(
              "\tSpecify where to insert the column. First and last\n"
	      +"\tare valid indexes.(default last)",
              "C", 1, "-C <index>"));
    newVector.addElement(new Option(
	      "\tCreate nominal attribute with given labels\n"
	      +"\t(default numeric attribute)",
              "L", 1, "-L <label1,label2,...>"));
    newVector.addElement(new Option(
              "\tName of the new attribute.\n"
              +"\t(default = 'Unnamed')",
              "N", 1,"-N <name>"));

    return newVector.elements();
  }


  /**
   * Parses a list of options for this object. Valid options are:<p>
   *
   * -C index <br>
   * Specify where to insert the column. First and last are valid indexes.
   * (default last)<p>
   *
   * -L label1,label2,...<br>
   * Create nominal attribute with the given labels
   * (default numeric attribute)<p>
   *
   * -N name<br>
   * Name of the new attribute. (default = 'Unnamed')<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {
    
    String insertString = Utils.getOption('C', options);
    if (insertString.length() != 0) {
      if (insertString.toLowerCase().equals("last")) {
	setAttributeIndex(-1);
      } else if (insertString.toLowerCase().equals("first")) {
	setAttributeIndex(0);
      } else {
	setAttributeIndex(Integer.parseInt(insertString) - 1); 
      }
    }

    setNominalLabels(Utils.getOption('L', options));
    setAttributeName(Utils.getOption('N', options));

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

    String [] options = new String [6];
    int current = 0;

    options[current++] = "-N"; options[current++] = getAttributeName();
    if (m_AttributeType == Attribute.NOMINAL) {
      options[current++] = "-L"; options[current++] = getNominalLabels();
    }
    options[current++] = "-C";
    options[current++] = "" + (getAttributeIndex() + 1);

    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input instance
   * structure (any instances contained in the object are ignored - only the
   * structure is required).
   * @return true if the outputFormat may be collected immediately
   * @exception Exception if the format couldn't be set successfully
   */
  public boolean setInputFormat(Instances instanceInfo) throws Exception {

    super.setInputFormat(instanceInfo);

    Instances outputFormat = new Instances(instanceInfo, 0);
    Attribute newAttribute = null;
    switch (m_AttributeType) {
    case Attribute.NUMERIC:
      newAttribute = new Attribute(m_Name);
      break;
    case Attribute.NOMINAL:
      newAttribute = new Attribute(m_Name, m_Labels);
      break;
    default:
      throw new Error("Unknown attribute type in Add");
    }

    if ((m_Insert < 0) || (m_Insert > getInputFormat().numAttributes())) {
      m_Insert = getInputFormat().numAttributes();
    }
    outputFormat.insertAttributeAt(newAttribute, m_Insert);
    setOutputFormat(outputFormat);
    return true;
  }

  /**
   * Input an instance for filtering. Ordinarily the instance is processed
   * and made available for output immediately. Some filters require all
   * instances be read before producing output.
   *
   * @param instance the input instance
   * @return true if the filtered instance may now be
   * collected with output().
   * @exception IllegalStateException if no input format has been defined.
   */
  public boolean input(Instance instance) {

    if (getInputFormat() == null) {
      throw new IllegalStateException("No input instance format defined");
    }
    if (m_NewBatch) {
      resetQueue();
      m_NewBatch = false;
    }

    Instance inst = (Instance)instance.copy();

    // First copy string values from input to output
    // Will break if the attribute being created is of type STRING (currently
    // Add only adds NOMINAL or NUMERIC types)
    copyStringValues(inst, true, inst.dataset(), getOutputFormat());

    // Insert the new attribute and reassign to output
    inst.setDataset(null);
    inst.insertAttributeAt(m_Insert);
    inst.setDataset(getOutputFormat());
    push(inst);
    return true;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String attributeNameTipText() {

    return "Set the new attribute's name.";
  }

  /**
   * Get the name of the attribute to be created
   *
   * @return the new attribute name
   */
  public String getAttributeName() {

    return m_Name;
  }

  /** 
   * Set the new attribute's name
   *
   * @param name the new name
   */
  public void setAttributeName(String name) {

    String newName = name.trim();
    if (newName.indexOf(' ') >= 0) {
      if (newName.indexOf('\'') != 0) {
	newName = newName.replace('\'',' ');
      }
      newName = '\'' + newName + '\'';
    }
    if (newName.equals("")) {
      newName = "unnamed";
    }
    m_Name = newName;
    
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String attributeIndexTipText() {

    return "The position (from 0) where the attribute will be inserted."
      + " Use -1 to indicate the last attribute.";
  }

  /**
   * Get the index where the attribute will be inserted
   *
   * @return the attribute insertion index
   */
  public int getAttributeIndex() {

    return m_Insert;
  }

  /**
   * Set the index where the attribute will be inserted
   *
   * @param attributeIndex the insertion index (-1 means last)
   */
  public void setAttributeIndex(int attributeIndex) {

    m_Insert = attributeIndex;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String nominalLabelsTipText() {
    return "The list of value labels (nominal attribute creation only). "
      + " The list must be comma-separated, eg: \"red,green,blue\"."
      + " If this is empty, the created attribute will be numeric.";
  }

  /**
   * Get the list of labels for nominal attribute creation
   *
   * @return the list of labels for nominal attribute creation
   */
  public String getNominalLabels() {

    String labelList = "";
    for(int i = 0; i < m_Labels.size(); i++) {
      if (i == 0) {
	labelList = (String)m_Labels.elementAt(i);
      } else {
	labelList += "," + (String)m_Labels.elementAt(i); 
      }
    }
    return labelList;
  }

  /**
   * Set the labels for nominal attribute creation.
   *
   * @param labelList a comma separated list of labels
   * @exception IllegalArgumentException if the labelList was invalid
   */
  public void setNominalLabels(String labelList) {

    FastVector labels = new FastVector (10);

    // Split the labelList up into the vector
    int commaLoc;
    while ((commaLoc = labelList.indexOf(',')) >= 0) {
      String label = labelList.substring(0, commaLoc).trim();
      if (!label.equals("")) {
	labels.addElement(label);
      } else {
	throw new IllegalArgumentException("Invalid label list at "+
                                           labelList.substring(commaLoc));
      }
      labelList = labelList.substring(commaLoc + 1);
    }
    String label = labelList.trim();
    if (!label.equals("")) {
      labels.addElement(label);
    }

    // If everything is OK, make the type change
    m_Labels = labels;
    if (labels.size() == 0) {
      m_AttributeType = Attribute.NUMERIC;
    } else {
      m_AttributeType = Attribute.NOMINAL; 
    }
  }

  /**
   * Main method for testing this class.
   *
   * @param argv should contain arguments to the filter: use -h for help
   */
  public static void main(String [] argv) {

    try {
      if (Utils.getFlag('b', argv)) {
 	Filter.batchFilterFile(new Add(), argv);
      } else {
	Filter.filterFile(new Add(), argv);
      }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}








