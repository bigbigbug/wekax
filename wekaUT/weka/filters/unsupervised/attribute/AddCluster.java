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
 *    AddCluster.java
 *    Copyright (C) 2002 Richard Kirkby
 *
 */

package weka.filters.unsupervised.attribute;

import weka.filters.*;
import weka.clusterers.Clusterer;
import weka.core.*;
import java.util.Enumeration;
import java.util.Vector;

/** 
 * A filter that adds a new nominal attribute representing the cluster assigned
 * to each instance by the specified clustering algorithm.<p>
 *
 * Valid filter-specific options are: <p>
 *
 * -W clusterer string <br>
 * Full class name of clusterer to use, followed by scheme options. (required)<p>
 *
 * -I range string <br>
 * The range of attributes the clusterer should ignore.<p>
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class AddCluster extends Filter implements UnsupervisedFilter, OptionHandler {

  /** The clusterer used to do the cleansing */
  protected Clusterer m_Clusterer = new weka.clusterers.SimpleKMeans();

  /** Range of attributes to ignore */
  protected Range m_IgnoreAttributesRange = null;

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input instance
   * structure (any instances contained in the object are ignored - only the
   * structure is required).
   * @return true if the outputFormat may be collected immediately
   * @exception Exception if the inputFormat can't be set successfully 
   */ 
  public boolean setInputFormat(Instances instanceInfo) throws Exception {
    
    super.setInputFormat(instanceInfo);

    return false;
  }
  
  /**
   * Signify that this batch of input to the filter is finished.
   *
   * @return true if there are instances pending output
   * @exception IllegalStateException if no input structure has been defined 
   */  
  public boolean batchFinished() throws Exception {

    if (getInputFormat() == null) {
      throw new IllegalStateException("No input instance format defined");
    }

    Instances toFilter = getInputFormat();
    Instances toFilterIgnoringAttributes = toFilter;

    // filter out attributes if necessary
    if (m_IgnoreAttributesRange != null) {
      toFilterIgnoringAttributes = new Instances(toFilter);
      Filter removeAttributes = new Remove();
      ((Remove)removeAttributes).setAttributeIndices(m_IgnoreAttributesRange.getRanges());
      ((Remove)removeAttributes).setInvertSelection(false);
      removeAttributes.setInputFormat(toFilter);
      for (int i = 0; i < toFilter.numInstances(); i++) {
	removeAttributes.input(toFilter.instance(i));
      }
      removeAttributes.batchFinished();
      toFilterIgnoringAttributes = removeAttributes.getOutputFormat();
      Instance tempInst;
      while ((tempInst = removeAttributes.output()) != null) {
	toFilterIgnoringAttributes.add(tempInst);
      }
    }

    // build the clusterer
    m_Clusterer.buildClusterer(toFilterIgnoringAttributes);

    // create output dataset with new attribute
    Instances filtered = new Instances(toFilter, 0); 
    FastVector nominal_values = new FastVector(m_Clusterer.numberOfClusters());
    for (int i=0; i<m_Clusterer.numberOfClusters(); i++) {
      nominal_values.addElement("cluster" + (i+1)); 
    }
    filtered.insertAttributeAt(new Attribute("cluster", nominal_values),
			       filtered.numAttributes());

    setOutputFormat(filtered);

    Instance original, processed;

    // build new dataset
    for (int i=0; i<toFilter.numInstances(); i++) {
      
      original = toFilter.instance(i);

      // copy values
      double[] instanceVals = new double[filtered.numAttributes()];
      for(int j = 0; j < toFilter.numAttributes(); j++) {
	instanceVals[j] = original.value(j);
      }
      // add cluster to end
      instanceVals[toFilter.numAttributes()]
	= m_Clusterer.clusterInstance(toFilterIgnoringAttributes.instance(i));
      
      // create new instance
      if (original instanceof SparseInstance) {
	processed = new SparseInstance(original.weight(), instanceVals);
      } else {
	processed = new Instance(original.weight(), instanceVals);
      }
      copyStringValues(original, false, original.dataset(), getOutputStringIndex(),
		       getOutputFormat(), getOutputStringIndex());
      processed.setDataset(filtered);
      
      push(processed);
    }

    return (numPendingOutput() != 0);
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {
    
    Vector newVector = new Vector(2);
    
    newVector.addElement(new Option(
	      "\tFull class name of clusterer to use, followed\n"
	      + "\tby scheme options. (required)\n"
	      + "\teg: \"weka.clusterers.SimpleKMeans -N 3\"",
	      "W", 1, "-W <clusterer specification>"));
    
    newVector.addElement(new Option(
	      "\tThe range of attributes the clusterer should ignore.\n",
	      "I", 1,"-I <att1,att2-att4,...>"));

    return newVector.elements();
  }


  /**
   * Parses the options for this object. Valid options are: <p>
   *
   * -W clusterer string <br>
   * Full class name of clusterer to use, followed by scheme options. (required)<p>
   *   
   * -I range string <br>
   * The range of attributes the clusterer should ignore.<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    String clustererString = Utils.getOption('W', options);
    if (clustererString.length() == 0) {
      throw new Exception("A clusterer must be specified"
			  + " with the -W option.");
    }
    String[] clustererSpec = Utils.splitOptions(clustererString);
    if (clustererSpec.length == 0) {
      throw new Exception("Invalid clusterer specification string");
    }
    String clustererName = clustererSpec[0];
    clustererSpec[0] = "";
    setClusterer(Clusterer.forName(clustererName, clustererSpec));
        
    setIgnoredAttributeIndices(Utils.getOption('I', options));

    Utils.checkForRemainingOptions(options);
  }

  /**
   * Gets the current settings of the filter.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    String [] options = new String [5];
    int current = 0;

    options[current++] = "-W"; options[current++] = "" + getClustererSpec();
    
    if (!getIgnoredAttributeIndices().equals("")) {
      options[current++] = "-I"; options[current++] = getIgnoredAttributeIndices();
    }

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

    return "A filter that adds a new nominal attribute representing the cluster "
      + "assigned to each instance by the specified clustering algorithm.";
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String clustererTipText() {

    return "The clusterer to assign clusters with.";
  }

  /**
   * Sets the clusterer to assign clusters with.
   *
   * @param clusterer The clusterer to be used (with its options set).
   */
  public void setClusterer(Clusterer clusterer) {

    m_Clusterer = clusterer;
  }
  
  /**
   * Gets the clusterer used by the filter.
   *
   * @return The clusterer being used.
   */
  public Clusterer getClusterer() {

    return m_Clusterer;
  }

  /**
   * Gets the clusterer specification string, which contains the class name of
   * the clusterer and any options to the clusterer.
   *
   * @return the clusterer string.
   */
  protected String getClustererSpec() {
    
    Clusterer c = getClusterer();
    if (c instanceof OptionHandler) {
      return c.getClass().getName() + " "
	+ Utils.joinOptions(((OptionHandler)c).getOptions());
    }
    return c.getClass().getName();
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String ignoredAttributeIndicesTipText() {

    return "The range of attributes to be ignored by the clusterer. eg: first-3,5,9-last";
  }

  /**
   * Gets ranges of attributes to be ignored.
   *
   * @return a string containing a comma-separated list of ranges
   */
  public String getIgnoredAttributeIndices() {

    if (m_IgnoreAttributesRange == null) {
      return "";
    } else {
      return m_IgnoreAttributesRange.getRanges();
    }
  }

  /**
   * Sets the ranges of attributes to be ignored. If provided string
   * is null, no attributes will be ignored.
   *
   * @param rangeList a string representing the list of attributes. 
   * eg: first-3,5,6-last
   * @exception IllegalArgumentException if an invalid range list is supplied 
   */
  public void setIgnoredAttributeIndices(String rangeList) {

    if ((rangeList == null) || (rangeList.length() == 0)) {
      m_IgnoreAttributesRange = null;
    } else {
      m_IgnoreAttributesRange = new Range();
      m_IgnoreAttributesRange.setRanges(rangeList);
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
 	Filter.batchFilterFile(new AddCluster(), argv); 
      } else {
	Filter.filterFile(new AddCluster(), argv);
      }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}
