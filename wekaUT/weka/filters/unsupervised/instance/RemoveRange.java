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
 *    RemoveRange.java
 *    Copyright (C) 1999 Eibe Frank
 *
 */


package weka.filters.unsupervised.instance;

import weka.filters.*;
import weka.core.*;
import java.util.*;

/**
 * This filter takes a dataset and outputs a subset of it.
 *
 * Valid options are: <p>
 *
 * -R inst1,inst2-inst4,... <br>
 * Specifies list of instances to select. First
 * and last are valid indexes. (required)<p>
 *
 * -V <br>
 * Specifies if inverse of selection is to be output.<p>
 *
 * @author Eibe Frank (eibe@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $ 
*/
public class RemoveRange extends Filter
  implements UnsupervisedFilter, OptionHandler {

  /** Range of instances provided by user. */
  private Range m_Range = new Range();

  /** Indicates if inverse of selection is to be output. */
  private boolean m_Inverse = false;

  /**
   * Gets an enumeration describing the available options..
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(6);

    newVector.addElement(new Option(
              "\tSpecifies list of instances to select. First and last\n"
	      +"\tare valid indexes. (required)\n",
              "R", 1, "-R <inst1,inst2-inst4,...>"));

    newVector.addElement(new Option(
	      "\tSpecifies if inverse of selection is to be output.\n",
	      "V", 0, "-V"));

    return newVector.elements();
  }

  /**
   * Parses the options for this object. Valid options are: <p>
   *
   * -R inst1,inst2-inst4,... <br>
   * Specifies list of instances to select. First
   * and last are valid indexes. (required)<p>
   *
   * -V <br>
   * Specifies if inverse of selection is to be output.<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    setInstancesIndices(Utils.getOption('R', options));
    setInvertSelection(Utils.getFlag('V', options));

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

    String [] options = new String [8];
    int current = 0;

    if (getInvertSelection()) {
      options[current++] = "-V";
    }
    options[current++] = "-R"; options[current++] = getInstancesIndices();
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Returns a string describing this filter
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {

    return "A filter that outputs a given range of instances of a dataset.";
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String instancesIndicesTipText() {

    return "The range of instances to select. First and last are valid indexes.";
  }

  /**
   * Gets ranges of instances selected.
   *
   * @return a string containing a comma-separated list of ranges
   */
  public String getInstancesIndices() {

    return m_Range.getRanges();
  }

  /**
   * Sets the ranges of instances to be selected. If provided string
   * is null, ranges won't be used for selecting instances.
   *
   * @param rangeList a string representing the list of instances. 
   * eg: first-3,5,6-last
   * @exception IllegalArgumentException if an invalid range list is supplied 
   */
  public void setInstancesIndices(String rangeList) {

    m_Range.setRanges(rangeList);
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String invertSelectionTipText() {

    return "Whether to invert the selection.";
  }

  /**
   * Gets if selection is to be inverted.
   *
   * @return true if the selection is to be inverted
   */
  public boolean getInvertSelection() {

    return m_Inverse;
  }

  /**
   * Sets if selection is to be inverted.
   *
   * @param inverse true if inversion is to be performed
   */
  public void setInvertSelection(boolean inverse) {
    
    m_Inverse = inverse;
  }

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input instance
   * structure (any instances contained in the object are ignored - only the
   * structure is required).
   * @return true because outputFormat can be collected immediately
   * @exception Exception if the input format can't be set successfully
   */  
  public boolean setInputFormat(Instances instanceInfo) throws Exception {

    super.setInputFormat(instanceInfo);
    setOutputFormat(instanceInfo);
    return true;
  }

  /**
   * Signify that this batch of input to the filter is
   * finished. Output() may now be called to retrieve the filtered
   * instances.
   *
   * @return true if there are instances pending output
   * @exception IllegalStateException if no input structure has been defined 
   */
  public boolean batchFinished() {

    if (getInputFormat() == null) {
      throw new IllegalStateException("No input instance format defined");
    }
    // Push instances for output into output queue
    m_Range.setInvert(m_Inverse);
    m_Range.setUpper(getInputFormat().numInstances() - 1);
    for (int i = 0; i < getInputFormat().numInstances(); i++) {
      if (m_Range.isInRange(i)) {
	push(getInputFormat().instance(i));
      }
    }
    m_NewBatch = true;
    return (numPendingOutput() != 0);
  }

  /**
   * Main method for testing this class.
   *
   * @param argv should contain arguments to the filter: use -h for help
   */
  public static void main(String [] argv) {

    try {
      if (Utils.getFlag('b', argv)) {
 	Filter.batchFilterFile(new RemoveRange(), argv);
      } else {
	Filter.filterFile(new RemoveRange(), argv);
      }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}
