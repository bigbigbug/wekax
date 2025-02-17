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
 *    ForwardSelection.java
 *    Copyright (C) 1999 Mark Hall
 *
 */

package  weka.attributeSelection;

import  java.io.*;
import  java.util.*;
import  weka.core.*;

/** 
 * Class for performing a forward selection hill climbing search. <p>
 *
 * Valid options are: <p>
 *
 * -P <start set> <br>
 * Specify a starting set of attributes. Eg 1,4,7-9. <p>
 *
 * -R <br>
 * Produce a ranked list of attributes. <p>
 * 
 * -T <threshold> <br>
 * Specify a threshold by which the AttributeSelection module can. <br>
 * discard attributes. Use in conjunction with -R <p>
 *
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class ForwardSelection extends ASSearch 
  implements RankedOutputSearch, StartSetHandler, OptionHandler {

 /** does the data have a class */
  private boolean m_hasClass;
 
  /** holds the class index */
  private int m_classIndex;
 
  /** number of attributes in the data */
  private int m_numAttribs;

  /** true if the user has requested a ranked list of attributes */
  private boolean m_rankingRequested;

  /** 
   * go from one side of the search space to the other in order to generate
   * a ranking
   */
  private boolean m_doRank;

  /** used to indicate whether or not ranking has been performed */
  private boolean m_doneRanking;

  /**
   * A threshold by which to discard attributes---used by the
   * AttributeSelection module
   */
  private double m_threshold;

  /** The number of attributes to select. -1 indicates that all attributes
      are to be retained. Has precedence over m_threshold */
  private int m_numToSelect = -1;

  private int m_calculatedNumToSelect;

  /** the merit of the best subset found */
  private double m_bestMerit;

  /** a ranked list of attribute indexes */
  private double [][] m_rankedAtts;
  private int m_rankedSoFar;

  /** the best subset found */
  private BitSet m_best_group;
  private ASEvaluation m_ASEval;

  private Instances m_Instances;

  /** holds the start set for the search as a Range */
  private Range m_startRange;

  /** holds an array of starting attributes */
  private int [] m_starting;

  /**
   * Returns a string describing this search method
   * @return a description of the search suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return "ForwardSelection :\n\nPerforms a greedy forward search through "
      +"the space of attribute subsets. May start with no attributes or from "
      +"an arbitrary point in the space. Stops when the addition of any "
      +"remaining attributes results in a decrease in evaluation. "
      +"Can also produce a ranked list of "
      +"attributes by traversing the space from one side to the other and "
      +"recording the order that attributes are selected.\n";
  }

  public ForwardSelection () {
    m_threshold = -Double.MAX_VALUE;
    m_doneRanking = false;
    m_startRange = new Range();
    m_starting = null;
    resetOptions();
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String thresholdTipText() {
    return "Set threshold by which attributes can be discarded. Default value "
      + "results in no attributes being discarded. Use in conjunction with "
      + "generateRanking";
  }

  /**
   * Set the threshold by which the AttributeSelection module can discard
   * attributes.
   * @param threshold the threshold.
   */
  public void setThreshold(double threshold) {
    m_threshold = threshold;
  }

  /**
   * Returns the threshold so that the AttributeSelection module can
   * discard attributes from the ranking.
   */
  public double getThreshold() {
    return m_threshold;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String numToSelectTipText() {
    return "Specify the number of attributes to retain. The default value "
      +"(-1) indicates that all attributes are to be retained. Use either "
      +"this option or a threshold to reduce the attribute set.";
  }

  /**
   * Specify the number of attributes to select from the ranked list
   * (if generating a ranking). -1
   * indicates that all attributes are to be retained.
   * @param n the number of attributes to retain
   */
  public void setNumToSelect(int n) {
    m_numToSelect = n;
  }

  /**
   * Gets the number of attributes to be retained.
   * @return the number of attributes to retain
   */
  public int getNumToSelect() {
    return m_numToSelect;
  }

  /**
   * Gets the calculated number of attributes to retain. This is the
   * actual number of attributes to retain. This is the same as
   * getNumToSelect if the user specifies a number which is not less
   * than zero. Otherwise it should be the number of attributes in the
   * (potentially transformed) data.
   */
  public int getCalculatedNumToSelect() {
    if (m_numToSelect >= 0) {
      m_calculatedNumToSelect = m_numToSelect;
    }
    return m_calculatedNumToSelect;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String generateRankingTipText() {
    return "Set to true if a ranked list is required.";
  }
  
  /**
   * Records whether the user has requested a ranked list of attributes.
   * @param doRank true if ranking is requested
   */
  public void setGenerateRanking(boolean doRank) {
    m_rankingRequested = doRank;
  }

  /**
   * Gets whether ranking has been requested. This is used by the
   * AttributeSelection module to determine if rankedAttributes()
   * should be called.
   * @return true if ranking has been requested.
   */
  public boolean getGenerateRanking() {
    return m_rankingRequested;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String startSetTipText() {
    return "Set the start point for the search. This is specified as a comma "
      +"seperated list off attribute indexes starting at 1. It can include "
      +"ranges. Eg. 1,2,5-9,17.";
  }

  /**
   * Sets a starting set of attributes for the search. It is the
   * search method's responsibility to report this start set (if any)
   * in its toString() method.
   * @param startSet a string containing a list of attributes (and or ranges),
   * eg. 1,2,6,10-15.
   * @exception Exception if start set can't be set.
   */
  public void setStartSet (String startSet) throws Exception {
    m_startRange.setRanges(startSet);
  }

  /**
   * Returns a list of attributes (and or attribute ranges) as a String
   * @return a list of attributes (and or attribute ranges)
   */
  public String getStartSet () {
    return m_startRange.getRanges();
  }

  /**
   * Returns an enumeration describing the available options.
   * @return an enumeration of all the available options.
   **/
  public Enumeration listOptions () {
    Vector newVector = new Vector(3);

    newVector
      .addElement(new Option("\tSpecify a starting set of attributes." 
			     + "\n\tEg. 1,3,5-7."
			     ,"P",1
			     , "-P <start set>"));

    newVector.addElement(new Option("\tProduce a ranked list of attributes."
				    ,"R",0,"-R"));
    newVector
      .addElement(new Option("\tSpecify a theshold by which attributes" 
			     + "\n\tmay be discarded from the ranking."
			     +"\n\tUse in conjuction with -R","T",1
			     , "-T <threshold>"));

    newVector
      .addElement(new Option("\tSpecify number of attributes to select" 
			     ,"N",1
			     , "-N <num to select>"));

    return newVector.elements();

  }
  
  /**
   * Parses a given list of options.
   *
   * Valid options are: <p>
   * 
   * -P <start set> <br>
   * Specify a starting set of attributes. Eg 1,4,7-9. <p>
   *
   * -R <br>
   * Produce a ranked list of attributes. <p>
   * 
   * -T <threshold> <br>
   * Specify a threshold by which the AttributeSelection module can <br>
   * discard attributes. Use in conjunction with -R <p>
   *
   * -N <number to retain> <br>
   * Specify the number of attributes to retain. Overides any threshold. <br>
   * <p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   *
   **/
  public void setOptions (String[] options)
    throws Exception {
    String optionString;
    resetOptions();

    optionString = Utils.getOption('P', options);
    if (optionString.length() != 0) {
      setStartSet(optionString);
    }

    setGenerateRanking(Utils.getFlag('R', options));

    optionString = Utils.getOption('T', options);
    if (optionString.length() != 0) {
      Double temp;
      temp = Double.valueOf(optionString);
      setThreshold(temp.doubleValue());
    }

    optionString = Utils.getOption('N', options);
    if (optionString.length() != 0) {
      setNumToSelect(Integer.parseInt(optionString));
    }
  }

  /**
   * Gets the current settings of ReliefFAttributeEval.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String[] getOptions () {
    String[] options = new String[7];
    int current = 0;
    
    if (!(getStartSet().equals(""))) {
      options[current++] = "-P";
      options[current++] = ""+startSetToString();
    }

    if (getGenerateRanking()) {
      options[current++] = "-R";
    }
    options[current++] = "-T";
    options[current++] = "" + getThreshold();

    options[current++] = "-N";
    options[current++] = ""+getNumToSelect();

    while (current < options.length) {
      options[current++] = "";
    }
    return  options;
  }

  /**
   * converts the array of starting attributes to a string. This is
   * used by getOptions to return the actual attributes specified
   * as the starting set. This is better than using m_startRanges.getRanges()
   * as the same start set can be specified in different ways from the
   * command line---eg 1,2,3 == 1-3. This is to ensure that stuff that
   * is stored in a database is comparable.
   * @return a comma seperated list of individual attribute numbers as a String
   */
  private String startSetToString() {
    StringBuffer FString = new StringBuffer();
    boolean didPrint;
    
    if (m_starting == null) {
      return getStartSet();
    }
    for (int i = 0; i < m_starting.length; i++) {
      didPrint = false;
      
      if ((m_hasClass == false) || 
	  (m_hasClass == true && i != m_classIndex)) {
	FString.append((m_starting[i] + 1));
	didPrint = true;
      }
      
      if (i == (m_starting.length - 1)) {
	FString.append("");
      }
      else {
	if (didPrint) {
	  FString.append(",");
	  }
      }
    }

    return FString.toString();
  }

  /**
   * returns a description of the search.
   * @return a description of the search as a String.
   */
  public String toString() {
    StringBuffer FString = new StringBuffer();
    FString.append("\tForward Selection.\n\tStart set: ");

    if (m_starting == null) {
      FString.append("no attributes\n");
    }
    else {
      FString.append(startSetToString()+"\n");
    }
    if (!m_doneRanking) {
      FString.append("\tMerit of best subset found: "
		     +Utils.doubleToString(Math.abs(m_bestMerit),8,3)+"\n");
    }
    
    if ((m_threshold != -Double.MAX_VALUE) && (m_doneRanking)) {
      FString.append("\tThreshold for discarding attributes: "
		     + Utils.doubleToString(m_threshold,8,4)+"\n");
    }

    return FString.toString();
  }


  /**
   * Searches the attribute subset space by forward selection.
   *
   * @param ASEvaluator the attribute evaluator to guide the search
   * @param data the training instances.
   * @return an array (not necessarily ordered) of selected attribute indexes
   * @exception Exception if the search can't be completed
   */
  public int[] search (ASEvaluation ASEval, Instances data)
    throws Exception {

    int i;
    double best_merit = -Double.MAX_VALUE;
    double temp_best,temp_merit;
    int temp_index=0;
    BitSet temp_group;

    if (data != null) { // this is a fresh run so reset
      resetOptions();
      m_Instances = data;
    }
    m_ASEval = ASEval;

    m_numAttribs = m_Instances.numAttributes();

    if (m_best_group == null) {
      m_best_group = new BitSet(m_numAttribs);
    }

    if (!(m_ASEval instanceof SubsetEvaluator)) {
      throw  new Exception(m_ASEval.getClass().getName() 
			   + " is not a " 
			   + "Subset evaluator!");
    }

    m_startRange.setUpper(m_numAttribs-1);
    if (!(getStartSet().equals(""))) {
      m_starting = m_startRange.getSelection();
    }

    if (m_ASEval instanceof UnsupervisedSubsetEvaluator) {
      m_hasClass = false;
    }
    else {
      m_hasClass = true;
      m_classIndex = m_Instances.classIndex();
    }

    SubsetEvaluator ASEvaluator = (SubsetEvaluator)m_ASEval;

    if (m_rankedAtts == null) {
      m_rankedAtts = new double[m_numAttribs][2];
      m_rankedSoFar = 0;
    }

    // If a starting subset has been supplied, then initialise the bitset
    if (m_starting != null) {
      for (i = 0; i < m_starting.length; i++) {
	if ((m_starting[i]) != m_classIndex) {
	  m_best_group.set(m_starting[i]);
	}
      }
    }

    // Evaluate the initial subset
    best_merit = ASEvaluator.evaluateSubset(m_best_group);

    // main search loop
    boolean done = false;
    boolean addone = false;
    while (!done) {
      temp_group = (BitSet)m_best_group.clone();
      temp_best = best_merit;
      if (m_doRank) {
	temp_best = -Double.MAX_VALUE;
      }
      done = true;
      addone = false;
      for (i=0;i<m_numAttribs;i++) {
	if ((i != m_classIndex) && (!temp_group.get(i))) {
	  // set the bit
	  temp_group.set(i);
	  temp_merit = ASEvaluator.evaluateSubset(temp_group);
	  if (temp_merit > temp_best) {
	    temp_best = temp_merit;
	    temp_index = i;
	    addone = true;
	    done = false;
	  }

	  // unset the bit
	  temp_group.clear(i);
	  if (m_doRank) {
	    done = false;
	  }
	}
      }
      if (addone) {
	m_best_group.set(temp_index);
	best_merit = temp_best;
	m_rankedAtts[m_rankedSoFar][0] = temp_index;
	m_rankedAtts[m_rankedSoFar][1] = best_merit;
	m_rankedSoFar++;
      }
    }
    m_bestMerit = best_merit;
    return attributeList(m_best_group);
  }

  /**
   * Produces a ranked list of attributes. Search must have been performed
   * prior to calling this function. Search is called by this function to
   * complete the traversal of the the search space. A list of
   * attributes and merits are returned. The attributes a ranked by the
   * order they are added to the subset during a forward selection search.
   * Individual merit values reflect the merit associated with adding the
   * corresponding attribute to the subset; because of this, merit values
   * may initially increase but then decrease as the best subset is
   * "passed by" on the way to the far side of the search space.
   *
   * @return an array of attribute indexes and associated merit values
   * @exception Exception if something goes wrong.
   */
  public double [][] rankedAttributes() throws Exception {
    
    if (m_rankedAtts == null || m_rankedSoFar == -1) {
      throw new Exception("Search must be performed before attributes "
			  +"can be ranked.");
    }
    m_doRank = true;
    search (m_ASEval, null);

    double [][] final_rank = new double [m_rankedSoFar][2];
    for (int i=0;i<m_rankedSoFar;i++) {
      final_rank[i][0] = m_rankedAtts[i][0];
      final_rank[i][1] = m_rankedAtts[i][1];
    }
    
    resetOptions();
    m_doneRanking = true;

    if (m_numToSelect > final_rank.length) {
      throw new Exception("More attributes requested than exist in the data");
    }

    if (m_numToSelect <= 0) {
      if (m_threshold == -Double.MAX_VALUE) {
	m_calculatedNumToSelect = final_rank.length;
      } else {
	determineNumToSelectFromThreshold(final_rank);
      }
    }

    return final_rank;
  }

  private void determineNumToSelectFromThreshold(double [][] ranking) {
    int count = 0;
    for (int i = 0; i < ranking.length; i++) {
      if (ranking[i][1] > m_threshold) {
	count++;
      }
    }
    m_calculatedNumToSelect = count;
  }

  /**
   * converts a BitSet into a list of attribute indexes 
   * @param group the BitSet to convert
   * @return an array of attribute indexes
   **/
  private int[] attributeList (BitSet group) {
    int count = 0;

    // count how many were selected
    for (int i = 0; i < m_numAttribs; i++) {
      if (group.get(i)) {
	count++;
      }
    }

    int[] list = new int[count];
    count = 0;

    for (int i = 0; i < m_numAttribs; i++) {
      if (group.get(i)) {
	list[count++] = i;
      }
    }

    return  list;
  }

  /**
   * Resets options
   */
  private void resetOptions() {
    m_doRank = false;
    m_best_group = null;
    m_ASEval = null;
    m_Instances = null;
    m_rankedSoFar = -1;
    m_rankedAtts = null;
  }
}
