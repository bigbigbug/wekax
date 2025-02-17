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
 *    FAT.java
 *    Copyright (C) 1999 Eibe Frank
 *    Modified by Prem Melville
 *
 */

package weka.classifiers.trees.j48;

import java.util.*;
import weka.core.*;
import weka.classifiers.*;

/**
 * Feature Aquisition Trees based on C4.5
 *
 * Class for generating an unpruned or a pruned C4.5 decision tree.
 * For more information, see<p>
 *
 * Ross Quinlan (1993). <i>C4.5: Programs for Machine Learning</i>, 
 * Morgan Kaufmann Publishers, San Mateo, CA. </p>
 *
 * Valid options are: <p>
 *
 * -U <br>
 * Use unpruned tree.<p>
 *
 * -C confidence <br>
 * Set confidence threshold for pruning. (Default: 0.25) <p>
 *
 * -M number <br>
 * Set minimum number of instances per leaf. (Default: 2) <p>
 *
 * -R <br>
 * Use reduced error pruning. No subtree raising is performed. <p>
 *
 * -N number <br>
 * Set number of folds for reduced error pruning. One fold is
 * used as the pruning set. (Default: 3) <p>
 *
 * -B <br>
 * Use binary splits for nominal attributes. <p>
 *
 * -S <br>
 * Don't perform subtree raising. <p>
 *
 * -L <br>
 * Do not clean up after the tree has been built. <p>
 *
 * -A <br>
 * If set, Laplace smoothing is used for predicted probabilites. <p>
 *
 *
 * @author Prem Melville (melville@cs.utexas.edu)
 * @version $Revision: 1.7 $
 */
public class FAT extends EnsembleClassifier implements OptionHandler, 
  Drawable, Matchable, Sourcable, WeightedInstancesHandler, Summarizable,
  AdditionalMeasureProducer, ActiveFeatureAcquirer {

    //=============== BEGIN EDIT melville ===============
    
    /** To use weighted sampling instead of deterministic selection of instance */ 
    protected boolean m_UseSampling = false;
    
    /** Random number generator - for sampling */
    protected Random m_Random = new Random(0);
    
    protected boolean m_Debug = true;

    /** Enumeration of selective sampling schemes */
    static final int LABEL_MARGIN = 0,
	RANDOM = 1,
	ABS_LABEL_MARGIN = 2,
	UNLABELED_MARGIN = 3,
	ENTROPY = 4,
	MAX_CERTAINTY = 5,
	HYBRID = 6,
	RANDOM_HYBRID = 7;
    
    /* LABEL_MARGIN = INCORRECT_CERTAINTY
       UNLABELED_MARGIN = UNCERTAINITY
       HYBRID = ASYMMETRIC CERTAINTY
       RANDOM_HYBRID = ERROR SAMPLING
    */
    
    /** The selective sampling scheme to use. */
    protected int m_SelectionScheme = LABEL_MARGIN;
    //=============== END EDIT melville ===============	
    
  // To maintain the same version number after adding m_ClassAttribute
  static final long serialVersionUID = -217733168393644444L;

  /** The decision tree */
  private ClassifierTree m_root;
  
  /** Unpruned tree? */
  private boolean m_unpruned = false;

  /** Confidence level */
  private float m_CF = 0.25f;

  /** Minimum number of instances */
  private int m_minNumObj = 2;

  /** Determines whether probabilities are smoothed using
      Laplace correction when predictions are generated */
  private boolean m_useLaplace = false;

  /** Use reduced error pruning? */
  private boolean m_reducedErrorPruning = false;

  /** Number of folds for reduced error pruning. */
  private int m_numFolds = 3;

  /** Binary splits on nominal attributes? */
  private boolean m_binarySplits = false;

  /** Subtree raising to be performed? */
  private boolean m_subtreeRaising = true;

  /** Cleanup after the tree has been built. */
  boolean m_noCleanup = false;
  
    
    /** 
     * Given a set of unlabeled examples, select a specified number of examples to be labeled.
     * @param activePool pool of unlabeled examples
     * @param num number of examples to selcted for labeling
     * @exception Exception if selective sampling fails
     */
    public int [] selectInstancesForFeatures(Instances activePool,int num) throws Exception{
	//Make a list of pairs of indices and the corresponding measure of informativenes of examples
	//Sort this in the order of informativeness and return the list of num indices
	int poolSize = activePool.numInstances();
	Pair []pairs = new Pair[poolSize];
	for(int i=0; i<poolSize; i++){
	    pairs[i] = new Pair(i,calculateScore(activePool.instance(i)));
	}
	
	//sort in AScending order
	Arrays.sort(pairs, new Comparator() {
                public int compare(Object o1, Object o2) {
		    double diff = ((Pair)o2).second - ((Pair)o1).second; 
		    return(diff < 0 ? 1 : diff > 0 ? -1 : 0);
		}
            });

	int []selected = new int[num];
	if(m_UseSampling){
	    if(pairs[0].first < 0)
		throw new Exception("Sampling with negative scores has not been defined.");
	    
	    //If sampling is being used then convert scores to
	    //probabilities (inversely proportional)
	    Vector v = new Vector(poolSize);
	    double sum = 0;
	    for(int i=0; i<poolSize; i++){
		if(pairs[i].second == 0){
		    pairs[i].second = Double.MAX_VALUE/poolSize;
		    //Account for probability values of 0 - to avoid divide-by-zero errors
		    //Divide by probs.length to make sure normalizing works properly
		}else{
		    pairs[i].second = 1.0 / pairs[i].second;
		}
		//Convert pairs from array to vector
		v.add(pairs[i]);
	    }

	    /* For j=1 to n
	     *     Create a cdf
	     *     Randomly pick instance based on cdf
	     *     Note index and remove element 
	     */
	    for(int j=0; j<num; j++){
		sum = 0;
		for(int i=0; i<v.size(); i++)
		    sum += ((Pair) v.get(i)).second;
		
		//normalize
		if (Double.isNaN(sum)) {
		    throw new IllegalArgumentException("Can't normalize array. Sum is NaN.");
		}
		if (sum == 0) {
		    throw new IllegalArgumentException("Can't normalize array. Sum is zero.");
		}
		for(int i=0; i<v.size(); i++)
		    ((Pair) v.get(i)).second /= sum;
		
		//create a cdf
		double []cdf = new double[v.size()];
		cdf[0] = ((Pair) v.get(0)).second;
		for(int i=1; i<v.size(); i++)
		    cdf[i] = ((Pair) v.get(i)).second + cdf[i-1];
		
		double rnd = m_Random.nextDouble();
		int index = 0;
		while(index < cdf.length && rnd > cdf[index]){
		    index++;
		}
		selected[j] = (int) (((Pair) v.get(index)).first);
		v.remove(index);
	    }
	    assert v.size()+num==poolSize : v.size()+" + "+num+" != "+poolSize+"\n";
	    if(m_Debug) {
		System.out.println("Sampled list:");
		for(int j=0; j<num; j++){
		    System.out.println("\t"+selected[j]);
		}
	    }
	    
	}else{//take the first num
	    if(m_Debug) System.out.println("Sorted list:");
	    for(int j=0; j<num; j++){
		if(m_Debug) System.out.println("\t"+pairs[j].second+"\t"+pairs[j].first);
		selected[j] = (int) pairs[j].first;
	    }
	}
	return selected;
    }

    /**
     * Calculate the feature acquisition score for 
     * given examples depending on the chosen selection scheme.
     * @param instance local instances from the current pool
     * @return score
     * @exception Exception if score could not be calculated properly */
    protected double calculateScore(Instance instance) throws Exception{
	double score;
	
	switch(m_SelectionScheme){
	case RANDOM_HYBRID:
	    score = calculateRandomHybridScore(instance);
	    break;
	case HYBRID:
	    score = calculateHybridScore(instance);
	    break;
	case ENTROPY:
	    score = -1.0*calculateEntropy(instance);
	    break;
	case UNLABELED_MARGIN:
	    score = calculateMargin(instance);
	    break;
	case MAX_CERTAINTY:
	    score = -1.0*calculateMargin(instance);
	    break;
	case ABS_LABEL_MARGIN:
	    score = Math.abs(calculateLabeledInstanceMargin(instance));
	    break;
	case LABEL_MARGIN:
	    score = calculateLabeledInstanceMargin(instance);
	    break;
	case RANDOM:
	    score = 0;//all instances are equal
	    break;
	default:
	    score = 0;//all instances are equal
	    break; 
	}
	return score;
    }   
 

  /**
   * Generates the classifier.
   *
   * @exception Exception if classifier can't be built successfully
   */
  public void buildClassifier(Instances instances) 
       throws Exception {

    ModelSelection modSelection;	 

    if (m_binarySplits)
      modSelection = new BinC45ModelSelection(m_minNumObj, instances);
    else
      modSelection = new C45ModelSelection(m_minNumObj, instances);
    if (!m_reducedErrorPruning)
      m_root = new C45PruneableClassifierTree(modSelection, !m_unpruned, m_CF,
					    m_subtreeRaising, !m_noCleanup);
    else
      m_root = new PruneableClassifierTree(modSelection, !m_unpruned, m_numFolds,
					   !m_noCleanup);
    m_root.buildClassifier(instances);
    if (m_binarySplits) {
      ((BinC45ModelSelection)modSelection).cleanup();
    } else {
      ((C45ModelSelection)modSelection).cleanup();
    }
  }

    //=============== BEGIN EDIT melville ===============
    /** Returns class predictions of each ensemble member */
    public double []getEnsemblePredictions(Instance instance) throws Exception{
	double preds[] = new double[1];
	preds[0] = classifyInstance(instance);
	
	return preds;
    }

    /** 
     * Returns vote weights of ensemble members.
     *
     * @return vote weights of ensemble members
     */
    public double []getEnsembleWts(){
	double []ensembleWts = {1.0};
	return ensembleWts;
    }
    
    /** Returns size of ensemble */
    public double getEnsembleSize(){
	return 1.0;
    }
    //=============== END EDIT melville ===============


  /**
   * Classifies an instance.
   *
   * @exception Exception if instance can't be classified successfully
   */
  public double classifyInstance(Instance instance) throws Exception {

    return m_root.classifyInstance(instance);
  }

  /** 
   * Returns class probabilities for an instance.
   *
   * @exception Exception if distribution can't be computed successfully
   */
  public final double [] distributionForInstance(Instance instance) 
       throws Exception {

    return m_root.distributionForInstance(instance, m_useLaplace);
  }

    /**
     *  Returns the type of graph this classifier
     *  represents.
     *  @return Drawable.TREE
     */   
    //  public int graphType() {
    //  	return Drawable.TREE;
    //    }
    
  /**
   * Returns graph describing the tree.
   *
   * @exception Exception if graph can't be computed
   */
  public String graph() throws Exception {

    return m_root.graph();
  }

  /**
   * Returns tree in prefix order.
   *
   * @exception Exception if something goes wrong
   */
  public String prefix() throws Exception {
    
    return m_root.prefix();
  }


  /**
   * Returns tree as an if-then statement.
   *
   * @return the tree as a Java if-then type statement
   * @exception Exception if something goes wrong
   */
  public String toSource(String className) throws Exception {

    StringBuffer [] source = m_root.toSource(className);
    return 
    "class " + className + " {\n\n"
    +"  public static double classify(Object [] i)\n"
    +"    throws Exception {\n\n"
    +"    double p = Double.NaN;\n"
    + source[0]  // Assignment code
    +"    return p;\n"
    +"  }\n"
    + source[1]  // Support code
    +"}\n";
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * Valid options are: <p>
   *
   * -U <br>
   * Use unpruned tree.<p>
   *
   * -C confidence <br>
   * Set confidence threshold for pruning. (Default: 0.25) <p>
   *
   * -M number <br>
   * Set minimum number of instances per leaf. (Default: 2) <p>
   *
   * -R <br>
   * Use reduced error pruning. No subtree raising is performed. <p>
   *
   * -N number <br>
   * Set number of folds for reduced error pruning. One fold is
   * used as the pruning set. (Default: 3) <p>
   *
   * -B <br>
   * Use binary splits for nominal attributes. <p>
   *
   * -S <br>
   * Don't perform subtree raising. <p>
   *
   * -L <br>
   * Do not clean up after the tree has been built.
   *
   * -A <br>
   * If set, Laplace smoothing is used for predicted probabilites. <p>
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(9);

    newVector.
	addElement(new Option("\tUse unpruned tree.",
			      "U", 0, "-U"));
    newVector.
	addElement(new Option("\tSet confidence threshold for pruning.\n" +
			      "\t(default 0.25)",
			      "C", 1, "-C <pruning confidence>"));
    newVector.
	addElement(new Option("\tSet minimum number of instances per leaf.\n" +
			      "\t(default 2)",
			      "M", 1, "-M <minimum number of instances>"));
    newVector.
	addElement(new Option("\tUse reduced error pruning.",
			      "R", 0, "-R"));
    newVector.
	addElement(new Option("\tSet number of folds for reduced error\n" +
			      "\tpruning. One fold is used as pruning set.\n" +
			      "\t(default 3)",
			      "N", 1, "-N <number of folds>"));
    newVector.
	addElement(new Option("\tUse binary splits only.",
			      "B", 0, "-B"));
    newVector.
        addElement(new Option("\tDon't perform subtree raising.",
			      "S", 0, "-S"));
    newVector.
        addElement(new Option("\tDo not clean up after the tree has been built.",
			      "L", 0, "-L"));
    newVector.
        addElement(new Option("\tLaplace smoothing for predicted probabilities.",
			      "A", 0, "-A"));
    newVector.
	addElement(new Option("\tUse sampling for selection of instances.",
			      "P", 0, "-P"));
    return newVector.elements();
  }

  /**
   * Parses a given list of options.
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {
    
    // Other options
    String minNumString = Utils.getOption('M', options);
    if (minNumString.length() != 0) {
      m_minNumObj = Integer.parseInt(minNumString);
    } else {
      m_minNumObj = 2;
    }
    m_binarySplits = Utils.getFlag('B', options);
    m_useLaplace = Utils.getFlag('A', options);

    // Pruning options
    m_unpruned = Utils.getFlag('U', options);
    m_subtreeRaising = !Utils.getFlag('S', options);
    m_noCleanup = Utils.getFlag('L', options);
    if ((m_unpruned) && (!m_subtreeRaising)) {
      throw new Exception("Subtree raising doesn't need to be unset for unpruned tree!");
    }
    m_reducedErrorPruning = Utils.getFlag('R', options);
    if ((m_unpruned) && (m_reducedErrorPruning)) {
      throw new Exception("Unpruned tree and reduced error pruning can't be selected " +
			  "simultaneously!");
    }
    String confidenceString = Utils.getOption('C', options);
    if (confidenceString.length() != 0) {
      if (m_reducedErrorPruning) {
	throw new Exception("Setting the confidence doesn't make sense " +
			    "for reduced error pruning.");
      } else if (m_unpruned) {
	throw new Exception("Doesn't make sense to change confidence for unpruned "
			    +"tree!");
      } else {
	m_CF = (new Float(confidenceString)).floatValue();
	if ((m_CF <= 0) || (m_CF >= 1)) {
	  throw new Exception("Confidence has to be greater than zero and smaller " +
			      "than one!");
	}
      }
    } else {
      m_CF = 0.25f;
    }
    String numFoldsString = Utils.getOption('N', options);
    if (numFoldsString.length() != 0) {
      if (!m_reducedErrorPruning) {
	throw new Exception("Setting the number of folds" +
			    " doesn't make sense if" +
			    " reduced error pruning is not selected.");
      } else {
	m_numFolds = Integer.parseInt(numFoldsString);
      }
    } else {
      m_numFolds = 3;
    }
    
    //=============== BEGIN EDIT melville ===============
    String selectionScheme = Utils.getOption('E', options);
    if (selectionScheme.length() != 0) {
	setSelectionScheme(Integer.parseInt(selectionScheme));
    } else {
	setSelectionScheme(0);
    }
    
    m_UseSampling = Utils.getFlag('P', options);
    //=============== END EDIT melville ===============
  }
    
    //=============== BEGIN EDIT melville ===============
        
    /**
     * Get the value of useSampling.
     * @return value of useSampling.
     */
    public boolean getUseSampling() {
	return m_UseSampling;
    }
    
    /**
     * Set the value of useSampling.
     * @param v  Value to assign to useSampling.
     */
    public void setUseSampling(boolean  v) {
	m_UseSampling = v;
    }
    
    /**
     * Get the value of m_SelectionScheme.
     * @return value of m_SelectionScheme.
     */
    public int getSelectionScheme() {
	return m_SelectionScheme;
    }
    
    /**
     * Set the value of m_SelectionScheme.
     * @param v  Value to assign to m_SelectionScheme.
     */
    public void setSelectionScheme(int  v) {
	m_SelectionScheme = v;
    }
    //=============== END EDIT melville ===============
    
  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {
      //=============== BEGIN EDIT melville ===============
    String [] options = new String [12];
    //=============== END EDIT melville ===============
    int current = 0;

    if (m_noCleanup) {
      options[current++] = "-L";
    }
    if (m_unpruned) {
      options[current++] = "-U";
    } else {
      if (!m_subtreeRaising) {
	options[current++] = "-S";
      }
      if (m_reducedErrorPruning) {
	options[current++] = "-R";
	options[current++] = "-N"; options[current++] = "" + m_numFolds;
      } else {
	options[current++] = "-C"; options[current++] = "" + m_CF;
      }
    }
    if (m_binarySplits) {
      options[current++] = "-B";
    }
    options[current++] = "-M"; options[current++] = "" + m_minNumObj;
    if (m_useLaplace) {
      options[current++] = "-A";
    }
    
    //=============== BEGIN EDIT melville ===============
    options[current++] = "-E"; options[current++] = "" + getSelectionScheme();
    if (m_UseSampling) {
	options[current++] = "-P";
    }
    //=============== END EDIT melville ===============
    
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }
  
  /**
   * Get the value of useLaplace.
   *
   * @return Value of useLaplace.
   */
  public boolean getUseLaplace() {
    
    return m_useLaplace;
  }
  
  /**
   * Set the value of useLaplace.
   *
   * @param newuseLaplace Value to assign to useLaplace.
   */
  public void setUseLaplace(boolean newuseLaplace) {
    
    m_useLaplace = newuseLaplace;
  }
  
  /**
   * Returns a description of the classifier.
   */
  public String toString() {

    if (m_root == null) {
      return "No classifier built";
    }
    if (m_unpruned)
      return "J48 unpruned tree\n------------------\n" + m_root.toString();
    else
      return "J48 pruned tree\n------------------\n" + m_root.toString();
  }

  /**
   * Returns a superconcise version of the model
   */
  public String toSummaryString() {

    return "Number of leaves: " + m_root.numLeaves() + "\n"
         + "Size of the tree: " + m_root.numNodes() + "\n";
  }

  /**
   * Returns the size of the tree
   * @return the size of the tree
   */
  public double measureTreeSize() {
    return m_root.numNodes();
  }

  /**
   * Returns the number of leaves
   * @return the number of leaves
   */
  public double measureNumLeaves() {
    return m_root.numLeaves();
  }

  /**
   * Returns the number of rules (same as number of leaves)
   * @return the number of rules
   */
  public double measureNumRules() {
    return m_root.numLeaves();
  }
  
  /**
   * Returns an enumeration of the additional measure names
   * @return an enumeration of the measure names
   */
  public Enumeration enumerateMeasures() {
    Vector newVector = new Vector(3);
    newVector.addElement("measureTreeSize");
    newVector.addElement("measureNumLeaves");
    newVector.addElement("measureNumRules");
    return newVector.elements();
  }

  /**
   * Returns the value of the named measure
   * @param measureName the name of the measure to query for its value
   * @return the value of the named measure
   * @exception IllegalArgumentException if the named measure is not supported
   */
  public double getMeasure(String additionalMeasureName) {
    if (additionalMeasureName.compareTo("measureNumRules") == 0) {
      return measureNumRules();
    } else if (additionalMeasureName.compareTo("measureTreeSize") == 0) {
      return measureTreeSize();
    } else if (additionalMeasureName.compareTo("measureNumLeaves") == 0) {
      return measureNumLeaves();
    } else {
      throw new IllegalArgumentException(additionalMeasureName 
			  + " not supported (j48)");
    }
  }

  /**
   * Get the value of unpruned.
   *
   * @return Value of unpruned.
   */
  public boolean getUnpruned() {
    
    return m_unpruned;
  }
  
  /**
   * Set the value of unpruned. Turns reduced-error pruning
   * off if set.
   * @param v  Value to assign to unpruned.
   */
  public void setUnpruned(boolean v) {

    if (v) {
      m_reducedErrorPruning = false;
    }
    m_unpruned = v;
  }
  
  /**
   * Get the value of CF.
   *
   * @return Value of CF.
   */
  public float getConfidenceFactor() {
    
    return m_CF;
  }
  
  /**
   * Set the value of CF.
   *
   * @param v  Value to assign to CF.
   */
  public void setConfidenceFactor(float v) {
    
    m_CF = v;
  }
  
  /**
   * Get the value of minNumObj.
   *
   * @return Value of minNumObj.
   */
  public int getMinNumObj() {
    
    return m_minNumObj;
  }
  
  /**
   * Set the value of minNumObj.
   *
   * @param v  Value to assign to minNumObj.
   */
  public void setMinNumObj(int v) {
    
    m_minNumObj = v;
  }
  
  /**
   * Get the value of reducedErrorPruning. 
   *
   * @return Value of reducedErrorPruning.
   */
  public boolean getReducedErrorPruning() {
    
    return m_reducedErrorPruning;
  }
  
  /**
   * Set the value of reducedErrorPruning. Turns
   * unpruned trees off if set.
   *
   * @param v  Value to assign to reducedErrorPruning.
   */
  public void setReducedErrorPruning(boolean v) {
    
    if (v) {
      m_unpruned = false;
    }
    m_reducedErrorPruning = v;
  }
  
  /**
   * Get the value of numFolds.
   *
   * @return Value of numFolds.
   */
  public int getNumFolds() {
    
    return m_numFolds;
  }
  
  /**
   * Set the value of numFolds.
   *
   * @param v  Value to assign to numFolds.
   */
  public void setNumFolds(int v) {
    
    m_numFolds = v;
  }
  
  /**
   * Get the value of binarySplits.
   *
   * @return Value of binarySplits.
   */
  public boolean getBinarySplits() {
    
    return m_binarySplits;
  }
  
  /**
   * Set the value of binarySplits.
   *
   * @param v  Value to assign to binarySplits.
   */
  public void setBinarySplits(boolean v) {
    
    m_binarySplits = v;
  }
  
  /**
   * Get the value of subtreeRaising.
   *
   * @return Value of subtreeRaising.
   */
  public boolean getSubtreeRaising() {
    
    return m_subtreeRaising;
  }
  
  /**
   * Set the value of subtreeRaising.
   *
   * @param v  Value to assign to subtreeRaising.
   */
  public void setSubtreeRaising(boolean v) {
    
    m_subtreeRaising = v;
  }
  
  /**
   * Check whether instance data is to be saved.
   *
   * @return true if instance data is saved
   */
  public boolean getSaveInstanceData() {
    
    return m_noCleanup;
  }
  
  /**
   * Set whether instance data is to be saved.
   * @param v true if instance data is to be saved
   */
  public void setSaveInstanceData(boolean v) {
    
    m_noCleanup = v;
  }
 
  /**
   * Main method for testing this class
   *
   * @param String options 
   */
  public static void main(String [] argv){

    try {
      System.out.println(Evaluation.evaluateModel(new FAT(), argv));
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }
}


  






