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

/**
 *    LeastMedSq.java
 *
 *    Copyright (C) 2001 Tony Voyle
 */

package weka.classifiers.functions;

import weka.classifiers.functions.LinearRegression;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.filters.supervised.attribute.NominalToBinary;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;
import weka.filters.unsupervised.instance.RemoveRange;
import weka.filters.Filter;
import weka.core.*;
import java.io.*;
import java.util.*;


/**
 * Implements a least median sqaured linear regression utilising the
 * existing weka LinearRegression class to form predictions.
 * The basis of the algorithm is Robust regression and outlier detection
 * Peter J. Rousseeuw, Annick M. Leroy. c1987
 *
 * @author Tony Voyle (tv6@waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class LeastMedSq extends Classifier implements OptionHandler {
  
  private double[] m_Residuals;
  
  private double[] m_weight;
  
  private double m_SSR;
  
  private double m_scalefactor;
  
  private double m_bestMedian = Double.POSITIVE_INFINITY;
  
  private LinearRegression m_currentRegression;
  
  private LinearRegression m_bestRegression;
  
  private LinearRegression m_ls;

  private Instances m_Data;

  private Instances m_RLSData;

  private Instances m_SubSample;

  private ReplaceMissingValues m_MissingFilter;

  private NominalToBinary m_TransformFilter;

  private RemoveRange m_SplitFilter;

  private int m_samplesize = 4;

  private int m_samples;

  private boolean m_israndom = false;

  private boolean m_debug = false;

  private Random m_random;

  private long m_randomseed = 0;

  /**
   * Build lms regression
   *
   * @param data training data
   * @exception Exception if an error occurs
   */
  public void buildClassifier(Instances data)throws Exception{

    if (!data.classAttribute().isNumeric())
      throw new UnsupportedClassTypeException("Class attribute has to be numeric for regression!");
    if (data.numInstances() == 0)
      throw new Exception("No instances in training file!");
    if (data.checkForStringAttributes())
      throw new UnsupportedAttributeTypeException("Cannot handle string attributes!");

    cleanUpData(data);

    getSamples();

    findBestRegression();

    buildRLSRegression();

  } // buildClassifier

  /**
   * Classify a given instance using the best generated
   * LinearRegression Classifier.
   *
   * @param instance instance to be classified
   * @return class value
   * @exception Exception if an error occurs
   */
  public double classifyInstance(Instance instance)throws Exception{

    Instance transformedInstance = instance;
    m_TransformFilter.input(transformedInstance);
    transformedInstance = m_TransformFilter.output();
    m_MissingFilter.input(transformedInstance);
    transformedInstance = m_MissingFilter.output();

    return m_ls.classifyInstance(transformedInstance);
  } // classifyInstance

  /**
   * Cleans up data
   *
   * @param data data to be cleaned up
   * @exception Exception if an error occurs
   */
  private void cleanUpData(Instances data)throws Exception{

    m_Data = data;
    m_TransformFilter = new NominalToBinary();
    m_TransformFilter.setInputFormat(m_Data);
    m_Data = Filter.useFilter(m_Data, m_TransformFilter);
    m_MissingFilter = new ReplaceMissingValues();
    m_MissingFilter.setInputFormat(m_Data);
    m_Data = Filter.useFilter(m_Data, m_MissingFilter);
    m_Data.deleteWithMissingClass();
  }

  /**
   * Gets the number of samples to use.
   *
   */
  private void getSamples()throws Exception{

    int stuf[] = new int[] {500,50,22,17,15,14};
    int x = m_samplesize * 500;
    if ( m_samplesize < 7){
      if ( m_Data.numInstances() < stuf[m_samplesize - 1])
	m_samples = combinations(m_Data.numInstances(), m_samplesize);
      else
	m_samples = m_samplesize * 500;

    } else m_samples = 3000;
    if (m_debug){
      System.out.println("m_samplesize: " + m_samplesize);
      System.out.println("m_samples: " + m_samples);
      System.out.println("m_randomseed: " + m_randomseed);
    }

  }

  /**
   * Set up the random number generator
   *
   */
  private void setRandom(){

    if (m_israndom){
      m_random = new Random();
    } else{
      m_random = new Random(getRandomSeed());
    }
  }

  /**
   * Finds the best regression generated from m_samples
   * random samples from the training data
   *
   * @exception Exception if an error occurs
   */
  private void findBestRegression()throws Exception{

    setRandom();
    m_bestMedian = Double.POSITIVE_INFINITY;
    if (m_debug) {
      System.out.println("Starting:");
    }
    for(int s = 0, r = 0; s < m_samples; s++, r++){
      if (m_debug) {
	if(s%(m_samples/100)==0)
	  System.out.print("*");
      }
      genRegression();
      getMedian();
    }
    if (m_debug) {
      System.out.println("");
    }
    m_currentRegression = m_bestRegression;
  }

  /**
   * Generates a LinearRegression classifier from
   * the current m_SubSample
   *
   * @exception Exception if an error occurs
   */
  private void genRegression()throws Exception{

    m_currentRegression = new LinearRegression();
    m_currentRegression.setOptions(new String[]{"-S", "1"});
    selectSubSample(m_Data);
    m_currentRegression.buildClassifier(m_SubSample);
  }

  /**
   * Finds residuals (squared) for the current
   * regression.
   *
   * @exception Exception if an error occurs
   */
  private void findResiduals()throws Exception{

    m_SSR = 0;
    m_Residuals = new double [m_Data.numInstances()];
    for(int i = 0; i < m_Data.numInstances(); i++){
      m_Residuals[i] = m_currentRegression.classifyInstance(m_Data.instance(i));
      m_Residuals[i] -= m_Data.instance(i).value(m_Data.classAttribute());
      m_Residuals[i] *= m_Residuals[i];
      m_SSR += m_Residuals[i];
    }
  }

  /**
   * finds the median residual squared for the
   * current regression
   *
   * @exception Exception if an error occurs
   */
  private void getMedian()throws Exception{

    findResiduals();
    int p = m_Residuals.length;
    select(m_Residuals, 0, p - 1, p / 2);
    if(m_Residuals[p / 2] < m_bestMedian){
      m_bestMedian = m_Residuals[p / 2];
      m_bestRegression = m_currentRegression;
    }
  }

  /**
   * Returns a string representing the best
   * LinearRegression classifier found.
   *
   * @return String representing the regression
   */
  public String toString(){

    if( m_ls == null){
      return "model has not been built";
    }
    return m_ls.toString();
  }

  /**
   * Builds a weight function removing instances with an
   * abnormally high scaled residual
   *
   */
  private void buildWeight()throws Exception{

    findResiduals();
    m_scalefactor = 1.4826 * ( 1 + 5 / (m_Data.numInstances()
					- m_Data.numAttributes()))
      * Math.sqrt(m_bestMedian);
    m_weight = new double[m_Residuals.length];
    for (int i = 0; i < m_Residuals.length; i++)
      m_weight[i] = ((Math.sqrt(m_Residuals[i])/m_scalefactor < 2.5)?1.0:0.0);
  }

  /**
   * Builds a new LinearRegression without the 'bad' data
   * found by buildWeight
   *
   */
  private void buildRLSRegression()throws Exception{

    buildWeight();
    m_RLSData = new Instances(m_Data);
    int x = 0;
    int y = 0;
    int n = m_RLSData.numInstances();
    while(y < n){
      if (m_weight[x] == 0){
	m_RLSData.delete(y);
	n = m_RLSData.numInstances();
	y--;
      }
      x++;
      y++;
    }
    if ( m_RLSData.numInstances() == 0){
      System.err.println("rls regression unbuilt");
      m_ls = m_currentRegression;
    }else{
      m_ls = new LinearRegression();
      m_ls.setOptions(new String[]{"-S", "1"});
      m_ls.buildClassifier(m_RLSData);
      m_currentRegression = m_ls;
    }

  }

  /**
   * Finds the kth number in an array
   *
   * @param a an array of numbers
   * @param l left pointer
   * @param r right pointer
   * @param k position of number to be found
   */
  private static void select( double [] a, int l, int r, int k){

    if (r <=l) return;
    int i = partition( a, l, r);
    if (i > k) select(a, l, i-1, k);
    if (i < k) select(a, i+1, r, k);
  }

  /**
   * Partitions an array of numbers such that all numbers
   * less than that at index r, between indexes l and r
   * will have a smaller index and all numbers greater than
   * will have a larger index
   *
   * @param a an array of numbers
   * @param l left pointer
   * @param r right pointer
   * @return final index of number originally at r
   */
  private static int partition( double [] a, int l, int r ){

    int i = l-1, j = r;
    double v = a[r], temp;
    while(true){
      while(a[++i] < v);
      while(v < a[--j]) if(j == l) break;
      if(i >= j) break;
      temp = a[i];
      a[i] = a[j];
      a[j] = temp;
    }
    temp = a[i];
    a[i] = a[r];
    a[r] = temp;
    return i;
  }

  /**
   * Produces a random sample from m_Data
   * in m_SubSample
   *
   * @param data data from which to take sample
   * @exception Exception if an error occurs
   */
  private void selectSubSample(Instances data)throws Exception{

    m_SplitFilter = new RemoveRange();
    m_SubSample = data;
    m_SplitFilter.setInputFormat(m_SubSample);
    m_SplitFilter.setInstancesIndices(selectIndices(m_SubSample));
    m_SubSample = Filter.useFilter(m_SubSample, m_SplitFilter);
  }

  /**
   * Returns a string suitable for passing to RemoveRange consisting
   * of m_samplesize indices.
   *
   * @param data dataset from which to take indicese
   * @return string of indices suitable for passing to RemoveRange 
   */
  private String selectIndices(Instances data){

    StringBuffer text = new StringBuffer();
    for(int i = 0, x = 0; i < m_samplesize; i++){
      do{x = (int) (m_random.nextDouble() * data.numInstances());}
      while(x==0);
      text.append(Integer.toString(x));
      if(i < m_samplesize - 1)
	text.append(",");
      else
	text.append("\n");
    }
    return text.toString();
  }


  /**
   * sets number of samples
   *
   * @param samplesize value
   */
  public void setSampleSize(int samplesize){

    m_samplesize = samplesize;
  }

  /**
   * gets number of samples
   *
   * @return value
   */
  public int getSampleSize(){

    return m_samplesize;
  }


  /**
   * Sets whether sample selection is random
   *
   * @param israndom true if sample selection is random
   */
  public void setIsRandom(boolean israndom){

    m_israndom = israndom;
  }

  /**
   * Returns whether or not samples are selected randomly
   *
   * @return true if sample selection is random
   */
  public boolean getIsRandom(){

    return m_israndom;
  }

  /**
   * Set the seed for the random number generator
   *
   * @param randomseed the seed
   */
  public void setRandomSeed(long randomseed){

    m_randomseed = randomseed;
  }

  /**
   * get the seed for the random number generator
   *
   * @return the seed value
   */
  public long getRandomSeed(){

    return m_randomseed;
  }

  /**
   * sets  whether or not debugging output shouild be printed
   *
   * @param debug true if debugging output selected
   */
  public void setDebug(boolean debug){

    m_debug = debug;
  }

  /**
   * Returns whether or not debugging output shouild be printed
   *
   * @return true if debuging output selected
   */
  public boolean getDebug(){

    return m_debug;
  }

  /**
   * Returns an enumeration of all the available options..
   *
   * @return an enumeration of all available options.
   */
  public Enumeration listOptions(){

    Vector newVector = new Vector(1);
    newVector.addElement(new Option("\tSet sample size\n"
				    + "\t(default: 4)\n",
				    "S", 4, "-S <sample size>"));
    newVector.addElement(new Option("\tUse a random seed to generate samples\n"
				    + "\t(default use a non-random seed)\n",
				    "R", 0, "-R"));
    newVector.addElement(new Option("\tSet the seed used to generate samples\n"
				    + "\t(default: 0)\n",
				    "G", 0, "-G <seed>"));
    newVector.addElement(new Option("\tProduce debugging output\n"
				    + "\t(default no debugging output)\n",
				    "D", 0, "-D"));

    return newVector.elements();
  }

  /**
   * Sets the OptionHandler's options using the given list. All options
   * will be set (or reset) during this call (i.e. incremental setting
   * of options is not possible).
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    String curropt = Utils.getOption('S', options);
    if ( curropt.length() != 0){
      setSampleSize(Integer.parseInt(curropt));
    } else
      setSampleSize(4);


    setIsRandom(Utils.getFlag('R', options));

    curropt = Utils.getOption('G', options);
    if ( curropt.length() != 0){
      setRandomSeed(Long.parseLong(curropt));
    } else
      setRandomSeed(0);

    setDebug(Utils.getFlag('D', options));

  }

  /**
   * Gets the current option settings for the OptionHandler.
   *
   * @return the list of current option settings as an array of strings
   */
  public String[] getOptions(){

    String options[] = new String[9];
    int current = 0;

    options[current++] = "-S";
    options[current++] = "" + getSampleSize();

    if (getIsRandom()) {
      options[current++] = "-R";
    }

    options[current++] = "-G";
    options[current++] = "" + getRandomSeed();

    if (getDebug()) {
      options[current++] = "-D";
    }

    while (current < options.length) {
      options[current++] = "";
    }

    return options;
  }

  /**
   * Produces the combination nCr
   *
   * @param n
   * @param n
   * @return the combination
   * @exception Exception if r is greater than n
   */
  public static int combinations (int n, int r)throws Exception {

    int c = 1, denom = 1, num = 1, i,orig=r;
    if (r > n) throw new Exception("r must be less that or equal to n.");
    r = Math.min( r , n - r);

    for (i = 1 ; i <= r; i++){

      num *= n-i+1;
      denom *= i;
    }

    c = num / denom;
    if(false)
      System.out.println( "n: "+n+" r: "+orig+" num: "+num+
			  " denom: "+denom+" c: "+c);
    return c;
  }

  /**
   * generate a Linear regression predictor for testing
   *
   * @param argv options
   */
  public static void main(String [] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new LeastMedSq(), argv));
    } catch (Exception e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
    }
  } // main
} // lmr
