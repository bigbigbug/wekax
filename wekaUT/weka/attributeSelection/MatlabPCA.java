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
 *    MatlabPCA.java
 *    Copyright (C) 2002 Mikhail Bilenko
 *
 */

package weka.attributeSelection;

import  java.io.*;
import  java.util.*;
import  weka.core.*;
import  weka.filters.unsupervised.attribute.ReplaceMissingValues;
import  weka.filters.unsupervised.attribute.Normalize;
import  weka.filters.unsupervised.attribute.NominalToBinary;
import  weka.filters.unsupervised.attribute.Remove;
import  weka.filters.Filter;

/**
 * Class for performing principal components analysis/transformation. <p>
 *
 * Valid options are:<p>
 * -N <br>
 * Don't normalize the input data. <p>
 *
 * -R <variance> <br>
 * Retain enough pcs to account for this proportion of the variance. <p>
 *
 * -T <br>
 * Transform through the PC space and back to the original space. <p>
 *
 * @author Misha Bilenko (mbilenko@cs.utexas.edu)
 * @author Sugato Basu (sugato@cs.utexas.edu)
 * @version $Revision: 1.1.1.1 $
 */
public class MatlabPCA extends AttributeEvaluator 
  implements AttributeTransformer, OptionHandler {

  /** Turns the debugging output on/off */
  private boolean m_debug = true;
  
  /** The data to transform analyse/transform */
  private Instances m_trainInstances;

  /** Keep a copy for the class attribute (if set) */
  private Instances m_trainCopy;

  /** The header for the transformed data format */
  private Instances m_transformedFormat;

  /** The header for data transformed back to the original space */
  private Instances m_originalSpaceFormat;

  /** Data has a class set */
  private boolean m_hasClass;

  /** Class index */
  private int m_classIndex;

  /** Number of attributes */
  private int m_numAttribs;

  /** Number of instances */
  private int m_numInstances;

  /** Name of the Matlab program file that computes PCA */
  protected String m_PCAMFile = new String("/var/local/MatlabPCA.m");

  /** Will hold the ordered linear transformations of the (normalized)
      original data */
  private double [][] m_eigenvectors;

  /** Eigenvalues for the corresponding eigenvectors */
  private double [] m_eigenvalues = null;

  /** A timestamp suffix for matching vectors with attributes */
  String m_timestamp = null;

  /** Name of the file where attribute names will be stored */
  String m_pcaAttributeFilename = null;
  String m_pcaAttributeFilenameBase = new String("/var/local/PCAattributes");

  /** Name of the file where original data will be stored */
  String m_dataFilename = new String("/var/local/PCAdataMatrix.txt");

  /** Name of the file where eigenvectors will be stored */
  public String m_eigenvectorFilename = null;
  public String m_eigenvectorFilenameBase = new String("/var/local/PCAeigenVectors");
  
  /** Name of the file where eigenvalues will be stored */
  public String m_eigenvalueFilename = new String("/var/local/PCAeigenValues.txt");
  
  /** sum of the eigenvalues */
  private double m_sumOfEigenValues = 0.0;
  
  /** Filters for original data */
  private ReplaceMissingValues m_replaceMissingFilter;
  private Normalize m_normalizeFilter;
  private Remove m_attributeFilter;
  
  /** The number of attributes in the pc transformed data */
  private int m_outputNumAtts = -1;
  
  /** normalize the input data? */
  private boolean m_normalize = false;

  /** the amount of variance to cover in the original data when
      retaining the best n PC's */
  private double m_coverVariance = 0.95;

  /** transform the data through the pc space and back to the original
      space ? */
  private boolean m_transBackToOriginal = false;

  /** holds the transposed eigenvectors for converting back to the
      original space */
  private double [][] m_eTranspose;

  /**
   * Returns a string describing this attribute transformer
   * @return a description of the evaluator suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return "Performs a principal components analysis and transformation of "
      +"the data. Use in conjunction with a Ranker search. Dimensionality "
      +"reduction is accomplished by choosing enough eigenvectors to "
      +"account for some percentage of the variance in the original data---"
      +"default 0.95 (95%). Attribute noise can be filtered by transforming "
      +"to the PC space, eliminating some of the worst eigenvectors, and "
      +"then transforming back to the original space.";
  }

  /**
   * Returns an enumeration describing the available options. <p>
   *
   * @return an enumeration of all the available options.
   **/
  public Enumeration listOptions () {
    Vector newVector = new Vector(3);
    newVector.addElement(new Option("\tDon't normalize input data." 
				    , "D", 0, "-D"));

    newVector.addElement(new Option("\tRetain enough PC attributes to account "
				    +"\n\tfor this proportion of variance in "
				    +"the original data. (default = 0.95)",
				    "R",1,"-R"));
    
    newVector.addElement(new Option("\tTransform through the PC space and "
				    +"\n\tback to the original space."
				    , "O", 0, "-O"));
    return  newVector.elements();
  }

  /**
   * Parses a given list of options.
   *
   * Valid options are:<p>
   * -N <br>
   * Don't normalize the input data. <p>
   *
   * -R <variance> <br>
   * Retain enough pcs to account for this proportion of the variance. <p>
   *
   * -T <br>
   * Transform through the PC space and back to the original space. <p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions (String[] options)
    throws Exception
  {
    resetOptions();
    String optionString;

    optionString = Utils.getOption('R', options);
    if (optionString.length() != 0) {
      Double temp;
      temp = Double.valueOf(optionString);
      setVarianceCovered(temp.doubleValue());
    }
    setNormalize(!Utils.getFlag('D', options));

    setTransformBackToOriginal(Utils.getFlag('O', options));
  }

  /**
   * Reset to defaults
   */
  private void resetOptions() {
    m_coverVariance = 0.95;
    m_normalize = false;
    m_sumOfEigenValues = 0.0;
    m_transBackToOriginal = false;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String normalizeTipText() {
    return "Normalize input data.";
  }

  /**
   * Set whether input data will be normalized.
   * @param n true if input data is to be normalized
   */
  public void setNormalize(boolean n) {
    m_normalize = n;
  }

  /**
   * Gets whether or not input data is to be normalized
   * @return true if input data is to be normalized
   */
  public boolean getNormalize() {
    return m_normalize;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String varianceCoveredTipText() {
    return "Retain enough PC attributes to account for this proportion of "
      +"variance.";
  }

  /**
   * Sets the amount of variance to account for when retaining
   * principal components
   * @param vc the proportion of total variance to account for
   */
  public void setVarianceCovered(double vc) {
    m_coverVariance = vc;
  }

  /**
   * Gets the proportion of total variance to account for when
   * retaining principal components
   * @return the proportion of variance to account for
   */
  public double getVarianceCovered() {
    return m_coverVariance;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String transformBackToOriginalTipText() {
    return "Transform through the PC space and back to the original space. "
      +"If only the best n PCs are retained (by setting varianceCovered < 1) "
      +"then this option will give a dataset in the original space but with "
      +"less attribute noise.";
  }

  /**
   * Sets whether the data should be transformed back to the original
   * space
   * @param b true if the data should be transformed back to the
   * original space
   */
  public void setTransformBackToOriginal(boolean b) {
    m_transBackToOriginal = b;
  }
  
  /**
   * Gets whether the data is to be transformed back to the original
   * space.
   * @return true if the data is to be transformed back to the original space
   */
  public boolean getTransformBackToOriginal() {
    return m_transBackToOriginal;
  }

  /**
   * Gets the current settings of MatlabPCA
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String[] getOptions () {

    String[] options = new String[4];
    int current = 0;

    if (!getNormalize()) {
      options[current++] = "-D";
    }

    options[current++] = "-R"; options[current++] = ""+getVarianceCovered();

    if (getTransformBackToOriginal()) {
      options[current++] = "-O";
    }
    
    while (current < options.length) {
      options[current++] = "";
    }
    
    return  options;
  }

  /**
   * Initializes principal components and performs the analysis
   * @param data the instances to analyse/transform
   * @exception Exception if analysis fails
   */
  public void buildEvaluator(Instances data) throws Exception {
    buildAttributeConstructor(data);
  }

  private void buildAttributeConstructor (Instances data) throws Exception {
    m_eigenvalues = null;
    m_outputNumAtts = -1;
    m_attributeFilter = null;
    m_sumOfEigenValues = 0.0;

    if (data.checkForStringAttributes()) {
      throw  new UnsupportedAttributeTypeException("Can't handle string attributes!");
    }
    m_trainInstances = data;
    m_debug = true;
    // make a copy of the training data so that we can get the class
    // column to append to the transformed data (if necessary)
    m_trainCopy = new Instances(m_trainInstances);
    if (m_debug) System.out.println("Copied " + m_trainInstances.numInstances() + " instances");
    m_replaceMissingFilter = new ReplaceMissingValues();
    m_replaceMissingFilter.setInputFormat(m_trainInstances);
    m_trainInstances = Filter.useFilter(m_trainInstances, 
					m_replaceMissingFilter);
    if (m_debug) System.out.println("Replaced missing values");

    if (m_normalize) {
      m_normalizeFilter = new Normalize();
      m_normalizeFilter.setInputFormat(m_trainInstances);
      m_trainInstances = Filter.useFilter(m_trainInstances, m_normalizeFilter);
      if (m_debug) System.out.println("Normalized");
    }

    // get rid of the class column
    if (m_trainInstances.classIndex() >=0) {
      m_hasClass = true;
      m_classIndex = m_trainInstances.classIndex();
      m_attributeFilter = new Remove();
      int [] todelete = new int [1];
      todelete[0] = m_classIndex;
       m_attributeFilter.setAttributeIndicesArray(todelete);
      m_attributeFilter.setInvertSelection(false);
      m_attributeFilter.setInputFormat(m_trainInstances);
      m_trainInstances = Filter.useFilter(m_trainInstances, m_attributeFilter);
      if (m_debug) System.out.println("Deleted class attribute");
    }
    
    // delete any attributes with only one distinct value or are all missing
    Vector deleteCols = new Vector();
    int numDeletedAttributes = 0;
    for (int i=0;i<m_trainInstances.numAttributes();i++) {
      if (m_trainInstances.numDistinctValues(i) <=1) {
	deleteCols.addElement(new Integer(i));
	numDeletedAttributes++;
      }
    }
    if (numDeletedAttributes > 0) { 
      if (m_debug) System.out.println("Deleted " + numDeletedAttributes + " single-value attributes");
    }

    // remove columns selected for deletion from the data if necessary
    if (deleteCols.size() > 0) {
      m_attributeFilter = new Remove();
      int [] todelete = new int [deleteCols.size()];
      for (int i=0;i<deleteCols.size();i++) {
	todelete[i] = ((Integer)(deleteCols.elementAt(i))).intValue();
      }
      m_attributeFilter.setAttributeIndicesArray(todelete);
      m_attributeFilter.setInvertSelection(false);
      m_attributeFilter.setInputFormat(m_trainInstances);
      m_trainInstances = Filter.useFilter(m_trainInstances, m_attributeFilter);
    }
    if (m_debug) System.out.println("Removed attributes filtered above");
    
    m_numInstances = m_trainInstances.numInstances();
    m_numAttribs = m_trainInstances.numAttributes();

    if (m_timestamp == null) { 
      m_timestamp = getLogTimestamp();
      m_pcaAttributeFilename = new String(m_pcaAttributeFilenameBase + m_timestamp + ".txt");
      m_eigenvectorFilename = new String(m_eigenvectorFilenameBase + m_timestamp + ".txt");
    }
    dumpAttributeNames(m_trainInstances, m_pcaAttributeFilename);
    
    if (m_debug) System.out.println("About to run PCA in matlab for " + m_numInstances +
		       " instances with " + m_numAttribs + " attributes");
    dumpInstances(m_dataFilename);
    prepareMatlab();
    runMatlab(m_PCAMFile, "PCAMatlab.output");

    m_eigenvectors = readColumnVectors(m_eigenvectorFilename, -1);
    m_eigenvalues = readVector(m_eigenvalueFilename);
    m_sumOfEigenValues = Utils.sum(m_eigenvalues);
    if (m_debug) System.out.println("Successfully parsed matlab output files");

    m_transformedFormat = setOutputFormat();
    // Transform data into the original format if necessary
    if (m_transBackToOriginal) {
      m_originalSpaceFormat = setOutputFormatOriginal();
      
      // new ordered eigenvector matrix
      int numVectors = (m_transformedFormat.classIndex() < 0) 
	? m_transformedFormat.numAttributes()
	: m_transformedFormat.numAttributes() - 1;

      // transpose the matrix
      int nr = m_eigenvectors.length;
      int nc = m_eigenvectors[0].length;
      m_eTranspose = 
	new double [nc][nr];
      for (int i = 0; i < nc; i++) {
	for (int j = 0; j < nr; j++) {
	  m_eTranspose[i][j] = m_eigenvectors[j][i];
	}
      }
    }
  }


  /** Read column vectors from a text file
   * @param name file name
   * @param maxVectors max number of vectors to read, -1 to read all\
   * @returns double[][] array corresponding to vectors
   */
  public double[][] readColumnVectors(String name, int maxVectors) throws Exception {
    BufferedReader r = new BufferedReader(new FileReader(name));
    int numAttributes=-1, numVectors=-1;
    String s;

    ArrayList linesList = new ArrayList();
    while ((s = r.readLine()) != null) {
      StringTokenizer tokenizer = new StringTokenizer(s);
      ArrayList lineList = new ArrayList();
      while (tokenizer.hasMoreTokens()) {
	String value = tokenizer.nextToken();
	try {
	  lineList.add(new Double(value));
	} catch (Exception e) {
	  System.err.println("Couldn't parse " + value + " as double");
	}
      }
      linesList.add(lineList);
    }
    numAttributes = linesList.size();
    numVectors = ((ArrayList)linesList.get(0)).size();
    double[][] vectors = new double[numAttributes][numVectors];
    for (int i = 0; i < numAttributes; i++) {
      ArrayList line = (ArrayList)linesList.get(i);
      for (int j = 0; j < numVectors; j++) {
	vectors[i][j] = ((Double)line.get(j)).doubleValue();
      }
    } 
    return vectors;
  }


   /** Read a column vector from a text file
   * @param name file name
   * @returns double[] array corresponding to a vector
   */
  public double[] readVector(String name) throws Exception {
     BufferedReader r = new BufferedReader(new FileReader(name));
     int numAttributes = -1;
     
     ArrayList vectorList = new ArrayList();
     String s;
     while ((s = r.readLine()) != null) {
       try { 
	 vectorList.add(new Double(s));
       } catch (Exception e) {
	 System.err.println("Couldn't parse " + s + " as double");
       }
     }
     int length = vectorList.size();
     double [] vector = new double[length];
     for (int i = 0; i < length; i++) {
       vector[i] = ((Double) vectorList.get(i)).doubleValue();
     } 
     return vector;
  }


  /** Dump attribute names into a text file
   * @param data instances for which to dump attributes
   * @param filename name of the file where the attribute column goes
   */
  public static void dumpAttributeNames(Instances data, String filename) {
    try {
      PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(filename)));
      Enumeration attributes = data.enumerateAttributes();
      while (attributes.hasMoreElements()) {
	Attribute attr = (Attribute) attributes.nextElement();
	writer.println(attr.name());
      }
      writer.close();      
    } catch (Exception e) {
      System.err.println("Error dumping attribute names into " + filename);
      e.printStackTrace();
    }
  } 
   
  /**
   * Returns just the header for the transformed data (ie. an empty
   * set of instances. This is so that AttributeSelection can
   * determine the structure of the transformed data without actually
   * having to get all the transformed data through getTransformedData().
   * @return the header of the transformed data.
   * @exception Exception if the header of the transformed data can't
   * be determined.
   */
  public Instances transformedHeader() throws Exception {
    if (m_eigenvalues == null) {
      throw new Exception("Principal components hasn't been built yet");
    }
    if (m_transBackToOriginal) {
      return m_originalSpaceFormat;
    } else {
      return m_transformedFormat;
    }
  }

  /**
   * Gets the transformed training data.
   * @return the transformed training data
   * @exception Exception if transformed data can't be returned
   */
  public Instances transformedData() throws Exception {
    if (m_eigenvalues == null) {
      throw new Exception("Principal components hasn't been built yet");
    }
    Instances output;

    if (m_transBackToOriginal) {
      output = new Instances(m_originalSpaceFormat);
    } else {
      output = new Instances(m_transformedFormat);
    }
    for (int i=0;i<m_trainCopy.numInstances();i++) {
      Instance converted = convertInstance(m_trainCopy.instance(i));
      output.add(converted);
    }

    return output;
  }

  /**
   * Evaluates the merit of a transformed attribute. This is defined
   * to be 1 minus the cumulative variance explained. Merit can't
   * be meaningfully evaluated if the data is to be transformed back
   * to the original space.
   * @param att the attribute to be evaluated
   * @return the merit of a transformed attribute
   * @exception Exception if attribute can't be evaluated
   */
  public double evaluateAttribute(int att) throws Exception {
    if (m_eigenvalues == null) {
      throw new Exception("Principal components hasn't been built yet!");
    }

    if (m_transBackToOriginal) {
      return 1.0; // can't evaluate back in the original space!
    }

    // return 1-cumulative variance explained for this transformed att
    double cumulative = 0.0;
    for (int i = 0; i < att ; i++) {
      cumulative += m_eigenvalues[i];
    }

    return 1.0 - cumulative / m_sumOfEigenValues;
  }

  /**
   * Dump covariance matrix into a file
   */
  private void dumpInstances(String tempFile) {
    try { 
      PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(tempFile)));
      for (int k = 0; k < m_numInstances; k++) {
	Instance instance = m_trainInstances.instance(k);
	for (int j = 0; j < m_numAttribs; j++) {
	  writer.print(instance.value(j) + " ");
	}
	writer.println();
      }
      writer.close();
    } catch (Exception e) {
      System.err.println("Could not create a temporary file for dumping the covariance matrix: " + e);
    }
  }

  
  /** Create matlab m-file for PCA
   * @param filename file where matlab script is created
   */
  public void prepareMatlab() {
    try{
      PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(m_PCAMFile)));
      writer.println("function MatlabPCA()");
      writer.println("DATA = load('" + m_dataFilename + "');");
      writer.println("[m,n] = size(DATA);");
      writer.println("r = min(m-1,n);     % max possible rank of x");
      writer.println("avg = mean(DATA);");
      writer.println("centerx = (DATA - avg(ones(m,1),:));");
      writer.println();
      writer.println("[U,latent,pc] = svd(centerx./sqrt(m-1),0);");
      writer.println("score = centerx*pc;");
      writer.println();
      writer.println("if nargout < 3, return; end");
      writer.println("latent = diag(latent).^2;");
      writer.println("if (r<n)");
      writer.println("   latent = [latent(1:r); zeros(n-r,1)];");
      writer.println("   score(:,r+1:end) = 0;");
      writer.println("end");
      writer.println();
      writer.println("if nargout < 4, return; end");
      writer.println("tmp = sqrt(diag(1./latent(1:r)))*score(:,1:r)';");
      writer.println("tsquare = sum(tmp.*tmp)';");
      writer.println();
      writer.println("[numAttributes, numVectors] = size(pc);");
      writer.println("[numValues, dummy] = size(latent);");
      writer.println();
      writer.println("save " + m_eigenvectorFilename + " pc -ASCII -DOUBLE;");
      writer.println("save " + m_eigenvalueFilename + " latent -ASCII -DOUBLE;");
      writer.println("\n\n");
      writer.close();
    } 
    catch (Exception e) {
      System.err.println("Could not create matlab file: " + e);
    }
  
  }
  

  /** Run matlab in command line with a given argument
   * @param inFile file to be input to Matlab
   * @param outFile file where results are stored
   */
  public void runMatlab(String inFile, String outFile) {
    // call matlab to do the dirty work
    try {
      int exitValue;
      do {
	Process proc = Runtime.getRuntime().exec("matlab -tty < " + inFile + " > " + outFile);
	exitValue = proc.waitFor();
	if (exitValue != 0) {
	  System.err.println("WARNING!!!!!  Matlab returned exit value 1, trying again later!");
	  Thread.sleep(300000);
	}
      } while (exitValue != 0);
      if (m_debug) System.out.println("Matlab process done, exitValue=" + exitValue);
    } catch (Exception e) {
      System.err.println("Problems running matlab: " + e);
    }
  } 
      

  /**
   * Return a summary of the analysis
   * @return a summary of the analysis.
   */
  private String principalComponentsSummary() {
    StringBuffer result = new StringBuffer();
    double cumulative = 0.0;
    Instances output = null;
    int numVectors=0;

    try {
      output = setOutputFormat();
      numVectors = (output.classIndex() < 0) 
	? output.numAttributes()
	: output.numAttributes()-1;
    } catch (Exception ex) {
    }

    //tomorrow
    result.append("eigenvalue\tproportion\tcumulative\n");
    for (int i = 0; i < numVectors; i++) {
      cumulative+=m_eigenvalues[i];
      result.append(Utils.doubleToString(m_eigenvalues[i],9,5) +"\t"+
		    Utils.doubleToString((m_eigenvalues[i] / m_sumOfEigenValues),9,5) +"\t"+
		    Utils.doubleToString((cumulative / m_sumOfEigenValues),9,5) +"\t"+
		    output.attribute(i).name()+"\n");
    }

    result.append("\nEigenvectors\n");
    for (int j = 1;j <= numVectors;j++) {
      result.append(" V"+j+'\t');
    }
    result.append("\n");
    for (int j = 0; j < m_numAttribs; j++) {
      for (int i = 0; i < numVectors; i++) {
	result.append(Utils.
		      doubleToString(m_eigenvectors[j][i],7,4)
		      +"\t");
      }
      result.append(m_trainInstances.attribute(j).name()+'\n');
    }

    if (m_transBackToOriginal) {
      result.append("\nPC space transformed back to original space.\n"
		    +"(Note: can't evaluate attributes in the original "
		    +"space)\n");
    }
    return result.toString();
  }

  /**
   * Returns a description of this attribute transformer
   * @return a String describing this attribute transformer
   */
  public String toString() {
    if (m_eigenvalues == null) {
      return "Principal components hasn't been built yet!";
    } else {
      return "\tPrincipal Components Attribute Transformer\n\n"
	+principalComponentsSummary();
    }
  }

  /**
   * Return a matrix as a String
   * @param matrix that is decribed as a string
   * @return a String describing a matrix
   */
  private String matrixToString(double [][] matrix) {
    StringBuffer result = new StringBuffer();
    int last = matrix.length - 1;

    for (int i = 0; i <= last; i++) {
      for (int j = 0; j <= last; j++) {
	result.append(Utils.doubleToString(matrix[i][j],6,2)+" ");
	if (j == last) {
	  result.append('\n');
	}
      }
    }
    return result.toString();
  }

  /**
   * Convert a pc transformed instance back to the original space
   */
  private Instance convertInstanceToOriginal(Instance inst)
    throws Exception {
    double[] newVals = null;

    if (m_hasClass) {
      newVals = new double[m_numAttribs+1];
    } else {
      newVals = new double[m_numAttribs];
    }

    if (m_hasClass) {
      // class is always appended as the last attribute
      newVals[m_numAttribs] = inst.value(inst.numAttributes() - 1);
    }

    for (int i = 0; i < m_eTranspose[0].length; i++) {
      double tempval = 0.0;
      for (int j = 1; j < m_eTranspose.length; j++) {
	tempval += (m_eTranspose[j][i] * 
		    inst.value(j - 1));
       }
      newVals[i] = tempval;
    }
    
    if (inst instanceof SparseInstance) {
      return new SparseInstance(inst.weight(), newVals);
    } else {
      return new Instance(inst.weight(), newVals);
    }      
  }

  /**
   * Transform an instance in original (unormalized) format. Convert back
   * to the original space if requested.
   * @param instance an instance in the original (unormalized) format
   * @return a transformed instance
   * @exception Exception if instance cant be transformed
   */
  public Instance convertInstance(Instance instance) throws Exception {

    if (m_eigenvalues == null) {
      throw new Exception("convertInstance: Principal components not "
			  +"built yet");
    }

    double[] newVals = new double[m_outputNumAtts];
    Instance tempInst = (Instance)instance.copy();
    if (!instance.equalHeaders(m_trainCopy.instance(0))) {
      throw new Exception("Can't convert instance: header's don't match: "
			  +"MatlabPCA");
    }

    m_replaceMissingFilter.input(tempInst);
    m_replaceMissingFilter.batchFinished();
    tempInst = m_replaceMissingFilter.output();

    if (m_normalize) {
      m_normalizeFilter.input(tempInst);
      m_normalizeFilter.batchFinished();
      tempInst = m_normalizeFilter.output();
    }

    if (m_attributeFilter != null) {
      m_attributeFilter.input(tempInst);
      m_attributeFilter.batchFinished();
      tempInst = m_attributeFilter.output();
    }

    //  double cumulative = 0;
    for (int i = 0; i < m_outputNumAtts; i++) {
      for (int j = 0; j < m_numAttribs; j++) {
	newVals[i] += (m_eigenvectors[j][i] * tempInst.value(j));
       }
    }

    if (m_hasClass) {
       newVals[m_outputNumAtts - 1] = instance.value(instance.classIndex());
    }
    
    if (!m_transBackToOriginal) {
      if (instance instanceof SparseInstance) {
	return new SparseInstance(instance.weight(), newVals);
      } else {
	return new Instance(instance.weight(), newVals);
      }      
    } else {
      if (instance instanceof SparseInstance) {
	return convertInstanceToOriginal(new SparseInstance(instance.weight(), 
							    newVals));
      } else {
	return convertInstanceToOriginal(new Instance(instance.weight(),
						      newVals));
      }
    }
  }

  protected String valsToString(double vals[]) {
    String s= new String("[ ");
    for (int i = 0 ; i < vals.length; i++) {
      s = s + vals[i] + "  ";
    }
    return (s + "]");
  }

  /**
   * Set up the header for the PC->original space dataset
   */
  private Instances setOutputFormatOriginal() throws Exception {
    FastVector attributes = new FastVector();
    
    for (int i = 0; i < m_numAttribs; i++) {
      String att = m_trainInstances.attribute(i).name();
      attributes.addElement(new Attribute(att));
    }
    
    if (m_hasClass) {
      attributes.addElement(m_trainCopy.classAttribute().copy());
    }

    Instances outputFormat = 
      new Instances(m_trainCopy.relationName()+"->PC->original space",
		    attributes, 0);
    
    // set the class to be the last attribute if necessary
    if (m_hasClass) {
      outputFormat.setClassIndex(outputFormat.numAttributes()-1);
    }

    return outputFormat;
  }

  /**
   * Set the format for the transformed data
   * @return a set of empty Instances (header only) in the new format
   * @exception Exception if the output format can't be set
   */
  private Instances setOutputFormat() throws Exception {
    if (m_eigenvalues == null) {
      return null;
    }

    double cumulative = 0.0;
    FastVector attributes = new FastVector();

    // Create the string representations for the new attributes
    // (only up to those that sum up to m_coverVariance
    for (int i = 0; i < m_numAttribs; i++) {
      StringBuffer attName = new StringBuffer();
      for (int j = 0; j < m_numAttribs; j++) {
	attName.append(Utils.doubleToString(m_eigenvectors[j][i], 5,3)
		       + m_trainInstances.attribute(j).name());
	if (j != m_numAttribs - 1) {
	  if (m_eigenvectors[j+1][i] >= 0) {
	    attName.append(" + ");
	  }
	}
      }
      attributes.addElement(new Attribute(attName.toString()));
      cumulative+=m_eigenvalues[i];
      
      if ((cumulative / m_sumOfEigenValues) >= m_coverVariance) {
	break;
      } 
    }

    System.err.println("PCA (" + m_coverVariance + "):  went from " + m_numAttribs +
		       " to " + attributes.size() + " attributes");
    if (m_hasClass) {
      attributes.addElement(m_trainCopy.classAttribute().copy());
    }

    Instances outputFormat = 
      new Instances(m_trainInstances.relationName()+"_principal components",
		    attributes, 0);

    // set the class to be the last attribute if necessary
    if (m_hasClass) {
      outputFormat.setClassIndex(outputFormat.numAttributes()-1);
    }
     
    m_outputNumAtts = outputFormat.numAttributes();
    return outputFormat;
  }


  /** Get a timestamp string as a weak uniqueid
   * @returns a timestamp string in the form "mmddhhmmssS"
   */     
  public static String getLogTimestamp() {
	Calendar cal = Calendar.getInstance(TimeZone.getDefault());
       	String DATE_FORMAT = "MMddHHmmssS";
	java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
	
	sdf.setTimeZone(TimeZone.getDefault());          
	return (sdf.format(cal.getTime()));
    }
  
  /**
   * Main method for testing this class
   * @param argv should contain the command line arguments to the
   * evaluator/transformer (see AttributeSelection)
   */
  public static void main(String [] argv) {
    try {
      System.out.println(AttributeSelection.
			 SelectAttributes(new MatlabPCA(), argv));
    }
    catch (Exception e) {
      e.printStackTrace();
      System.out.println(e.getMessage());
    }
  }
  
}


