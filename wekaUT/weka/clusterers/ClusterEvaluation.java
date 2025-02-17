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
 *    ClusterEvaluation.java
 *    Copyright (C) 1999 Mark Hall
 *
 */

package  weka.clusterers;

import  java.util.*;
import  java.io.*;
import  weka.core.*;
import  weka.filters.Filter;
import  weka.filters.unsupervised.attribute.Remove;

/**
 * Class for evaluating clustering models.<p>
 *
 * Valid options are: <p>
 *
 * -t <name of the training file> <br>
 * Specify the training file. <p>
 *
 * -T <name of the test file> <br>
 * Specify the test file to apply clusterer to. <p>
 *
 * -d <name of file to save clustering model to> <br>
 * Specify output file. <p>
 *
 * -l <name of file to load clustering model from> <br>
 * Specifiy input file. <p>
 *
 * -p <attribute range> <br>
 * Output predictions. Predictions are for the training file if only the
 * training file is specified, otherwise they are for the test file. The range
 * specifies attribute values to be output with the predictions.
 * Use '-p 0' for none. <p>
 *
 * -x <num folds> <br>
 * Set the number of folds for a cross validation of the training data.
 * Cross validation can only be done for distribution clusterers and will
 * be performed if the test file is missing. <p>
 *
 * -c <class> <br>
 * Set the class attribute. If set, then class based evaluation of clustering
 * is performed. <p>
 *
 * @author   Mark Hall (mhall@cs.waikato.ac.nz)
 * @version  $Revision: 1.1.1.1 $
 */
public class ClusterEvaluation {

  /** the instances to cluster */
  private Instances instances;

  /** the clusterer */
  private Clusterer clusterer;

  /** do cross validation (DistributionClusterers only) */
  private boolean m_doXval;

  /** the number of folds to use for cross validation */
  private int m_numFolds;

  /** seed to use for cross validation */
  private int m_seed;

  /** holds a string describing the results of clustering the training data */
  private StringBuffer results;

  private int numInstances;
  private int numClusters;
  private int numClasses;

  /** holds the assigments of instances to clusters for a particular testing
      dataset */
  private double [] assignments;

  /** will hold the mapping of classes to clusters (for class based 
      evaluation) */
  private int [] m_classToCluster = null;

  /**
   * set the clusterer
   * @param clusterer the clusterer to use
   */
  public void setClusterer(Clusterer clusterer) {
    this.clusterer=clusterer;
	numClusters=clusterer.K;
	setInstances(clusterer.instances);
  }
  public void setInstances(Instances instances){
	this.instances=instances;
	numInstances=instances.numInstances();
	if(instances.classIndex()>-1)numClasses=instances.classAttribute().numValues();
  }

  /**
   * set whether or not to do cross validation
   * @param x true if cross validation is to be done
   */
  public void setDoXval(boolean x) {
    m_doXval = x;
  }

  /**
   * set the number of folds to use for cross validation
   * @param folds the number of folds
   */
  public void setFolds(int folds) {
    m_numFolds = folds;
  }

  /**
   * set the seed to use for cross validation
   * @param s the seed.
   */
  public void setSeed(int s) {
    m_seed = s;
  }

  /**
   * return the results of clustering.
   * @return a string detailing the results of clustering a data set
   */
  public String clusterResultsToString() {
    return results.toString();
  }

  /**
   * Return the number of clusters found for the most recent call to
   * evaluateClusterer
   * @return the number of clusters found
   */
  public int getNumClusters() {
    return numClusters;
  }

  /**
   * Return an array of cluster assignments corresponding to the most
   * recent set of instances clustered.
   * @return an array of cluster assignments
   */
  public double [] getClusterAssignments() {
    return assignments;
  }

  /**
   * Return the array (ordered by cluster number) of minimum error class to
   * cluster mappings
   * @return an array of class to cluster mappings
   */
  public int [] getClassesToClusters() {
    return m_classToCluster;
  }

  /**
   * Constructor. Sets defaults for each member variable. Default Clusterer
   * is EM.
   */
  public ClusterEvaluation () {
	  this(new EM());
  }
  public ClusterEvaluation(Clusterer clusterer){
	  this(clusterer,clusterer.instances);
  }
  public ClusterEvaluation(Clusterer clusterer,Instances instances){
    setFolds(10);
    setDoXval(false);
    setSeed(1);
    setClusterer(clusterer);
	setInstances(instances);
    results=new StringBuffer();
    assignments=null;
  }

  /**
   * Evaluate the clusterer on a set of instances. Calculates clustering
   * statistics and stores cluster assigments for the instances in
   * assignments
   * @param test the set of instances to cluster
   * @exception Exception if something goes wrong
   */
  public void evaluateClusterer(Instances test) throws Exception {
      setInstances(test);
      clusterer.clusterInstances(test);
      evaluateClusterer();
  }
  public void evaluateClusterer()throws Exception{
    int i = 0;
    double[] dist;
    double temp;
    int numInstFieldWidth = (int)((Math.log(numInstances)/Math.log(10))+1);
    double[] instanceStats = new double[numClusters];
    int unclusteredInstances = 0;
    assignments=clusterer.getAssignments();
    for (i=0;i<numInstances;i++) {
      if(assignments[i]==-1){
        unclusteredInstances++;
      }else{
        instanceStats[(int)assignments[i]]++;
      }
    }
    double sum = Utils.sum(instanceStats);
    results.append(clusterer.toString());
    results.append(" Clustered Instances\n\n");
    int clustFieldWidth = (int)((Math.log(numClusters)/Math.log(10))+1);
    for (i = 0; i < numClusters; i++) {
	results.append(Utils.doubleToString((double)i,clustFieldWidth,0));
        results.append("\t");
        results.append(Utils.doubleToString(instanceStats[i],numInstFieldWidth,0));
        results.append(" (");
        results.append(Utils.doubleToString((instanceStats[i]/sum*100.0),3,0));
        results.append("%)\n");
    }
    results.append("\nUnclustered instances : "+unclusteredInstances);
    if(clusterer instanceof DistributionClusterer){
      int loglk=0;
      double t=0;
      for(i=0;i<numInstances;i++){
        t=((DistributionClusterer)clusterer).densityForInstance(instances.instance(i));
        if(t>0)loglk+=Math.log(t);
      }
      loglk/=sum;
      results.append("\nLog likelihood: "+Utils.doubleToString(loglk,1,5)+"\n");
    }
    if(instances.classIndex()!=-1)evaluateClustersWithRespectToClass();
  }
  public static String evaluateClusterer(Clusterer clusterer)throws Exception{
      return evaluateClusterer(clusterer,clusterer.instances);
  }
  public static String evaluateClusterer(Clusterer clusterer,Instances instances)throws Exception{
	  ClusterEvaluation ce=new ClusterEvaluation(clusterer,instances);
	  ce.assignments=ce.clusterer.getAssignments();
	  ce.evaluateClustersWithRespectToClass();
	  return ce.results.toString();
  }
  /**
   * Evaluates cluster assignments with respect to actual class labels.
   * Assumes that clusterer has been trained and tested on
   * inst (minus the class).
   * @param inst the instances (including class) to evaluate with respect to
   * @exception Exception if something goes wrong
   */
  public void evaluateClustersWithRespectToClass()throws Exception{
	  evaluateClustersWithRespectToClass(instances.allClasses(),instances.classes());
  }
  public void evaluateClustersWithRespectToClass(double[] allClasses,String[] classes)throws Exception{
    int [][] counts = new int [numClusters][numClasses];
    int [] clusterTotals = new int[numClusters];
    int [] classTotals=new int[numClasses];
    double [] best = new double[numClusters+1];
    double [] current = new double[numClusters+1];
    for(int i=0;i<numInstances;i++){
      int clusterId=(int)assignments[i];
      int classId=(int)allClasses[i];
      counts[clusterId][classId]++;
      clusterTotals[clusterId]++;
      classTotals[classId]++;
    }
    best[numClusters] = Double.MAX_VALUE;
    mapClasses(0, counts, clusterTotals, current, best, 0);
    results.append("\n\nClasses: ");
    for(int i=0;i<numClasses;i++)results.append(classes[i]).append("\t");
    results.append("\nClasses to Clusters:\n");
    results.append(Utils.toString(counts,"Cluster","Class"));

    int Cwidth = 1 + (int)(Math.log(numClusters) / Math.log(10));
    // add the minimum error assignment
    for (int i = 0; i < numClusters; i++) {
      if (clusterTotals[i] > 0) {
	results.append("\nCluster ");
	results.append(Utils.toString((double)i,Cwidth));
	results.append(" <-- ");
	
	if (best[i] < 0) {
	  results.append("No class\n");
	} else {
	  results.append(classes[(int)best[i]]).append("\n");
	}
      }
    }
    results.append("\nIncorrectly clustered instances :\t");
	results.append(best[numClusters]);
    results.append("\t");
	results.append(Utils.doubleToString((best[numClusters] /
						       numInstances * 
						       100.0), 8, 4));
	results.append(" %\n");
    
    double [][] p=new double[numClusters][numClasses];
    double [][] r=new double[numClusters][numClasses];
    double [][] f=new double[numClusters][numClasses];
    double [] p1=new double[numClusters];
    double [] p2=new double[numClasses];
    double [] Es=new double[numClusters];
    double [] Ps=new double[numClusters];
    double [] Fs=new double[numClasses];
    double E=0,P=0,F=0,H1=0,H2=0,NMI;
    for(int i=0;i<numClusters;i++){
        p1[i]=(double)clusterTotals[i]/numInstances;
        for(int j=0;j<numClasses;j++){
            if(counts[i][j]==0){
                p[i][j]=r[i][j]=0;
                f[i][j]=0;
            }else{
                p[i][j]=(double)counts[i][j]/clusterTotals[i];
                r[i][j]=(double)counts[i][j]/classTotals[j];
                f[i][j]=2*p[i][j]*r[i][j]/(p[i][j]+r[i][j]);
                Es[i]+=-p[i][j]*Math.log(p[i][j])/Math.log(2);
                if(p[i][j]>Ps[i])Ps[i]=p[i][j];
                if(f[i][j]>Fs[j])Fs[j]=f[i][j];
            }
        }
        E+=p1[i]*Es[i];
        P+=p1[i]*Ps[i];
        H1+=-p1[i]*Math.log(p1[i])/Math.log(2);
    }
    for(int j=0;j<numClasses;j++){
        p2[j]=(double)classTotals[j]/numInstances;
        F+=p2[j]*Fs[j];
        H2+=-p2[j]*Math.log(p2[j])/Math.log(2);
    }
    NMI=(H2-E)/Math.sqrt(H1*H2);
    results.append("E="+E+" P="+P+" F="+F+" H1="+H1+" H2="+H2+" NMI="+NMI+"\n");

    // copy the class assignments
    m_classToCluster = new int [numClusters];
    for (int i = 0; i < numClusters; i++) {
      m_classToCluster[i] = (int)best[i];
    }
  }

  /**
   * Finds the minimum error mapping of classes to clusters. Recursively
   * considers all possible class to cluster assignments.
   * @param lev the cluster being processed
   * @param counts the counts of classes in clusters
   * @param clusterTotals the total number of examples in each cluster
   * @param current the current path through the class to cluster assignment
   * tree
   * @param best the best assignment path seen
   * @param error accumulates the error for a particular path
   */
  private void mapClasses(int lev, int [][] counts, int [] clusterTotals,
			  double [] current, double [] best, int error) {
    // leaf
    if (lev == numClusters) {
      if (error < best[numClusters]) {
	best[numClusters] = error;
	for (int i = 0; i < numClusters; i++) {
	  best[i] = current[i];
	}
      }
    } else {
      // empty cluster -- ignore
      if (clusterTotals[lev] == 0) {
	current[lev] = -1; // cluster ignored
	mapClasses(lev+1, counts, clusterTotals, current, best,
		   error);
      } else {
	// first try no class assignment to this cluster
	current[lev] = -1; // cluster assigned no class (ie all errors)
	mapClasses(lev+1, counts, clusterTotals, current, best,
		   error+clusterTotals[lev]);
	// now loop through the classes in this cluster
	for (int i = 0; i < counts[0].length; i++) {
	  if (counts[lev][i] > 0) {
	    boolean ok = true;
	    // check to see if this class has already been assigned
	    for (int j = 0; j < lev; j++) {
	      if ((int)current[j] == i) {
		ok = false;
		break;
	      }
	    }
	    if (ok) {
	      current[lev] = i;
	      mapClasses(lev+1, counts, clusterTotals, current, best, 
			 (error + (clusterTotals[lev] - counts[lev][i])));
	    }
	  }
	}
      }
    }
  }

  /**
   * Evaluates a clusterer with the options given in an array of
   * strings. It takes the string indicated by "-t" as training file, the
   * string indicated by "-T" as test file.
   * If the test file is missing, a stratified ten-fold
   * cross-validation is performed (distribution clusterers only).
   * Using "-x" you can change the number of
   * folds to be used, and using "-s" the random seed.
   * If the "-p" option is present it outputs the classification for
   * each test instance. If you provide the name of an object file using
   * "-l", a clusterer will be loaded from the given file. If you provide the
   * name of an object file using "-d", the clusterer built from the
   * training data will be saved to the given file.
   *
   * @param clusterer machine learning clusterer
   * @param options the array of string containing the options
   * @exception Exception if model could not be evaluated successfully
   * @return a string describing the results 
   */
  public static String evaluateClusterer (Clusterer clusterer, 
					  String[] options)
    throws Exception {
    int seed = 1, folds = 10;
    boolean doXval = false;
    Instances train = null;
    Instances test = null;
    Random random;
    String trainFileName, testFileName, seedString, foldsString, objectInputFileName, objectOutputFileName, attributeRangeString;
    String[] savedOptions = null;
    boolean printClusterAssignments = false;
    Range attributesToOutput = null;
    ObjectInputStream objectInputStream = null;
    ObjectOutputStream objectOutputStream = null;
    StringBuffer text = new StringBuffer();
    int theClass = -1; // class based evaluation of clustering

    try {
      if (Utils.getFlag('h', options)) {
        throw  new Exception("Help requested.");
      }

      // Get basic options (options the same for all clusterers
      objectInputFileName = Utils.getOption('l', options);
      objectOutputFileName = Utils.getOption('d', options);
      trainFileName = Utils.getOption('t', options);
      testFileName = Utils.getOption('T', options);

      // Check -p option
      try{
          attributeRangeString=Utils.getOption('p',options);
      }catch(Exception e){
          throw new Exception(e.getMessage()+"\nNOTE: the -p option expects a parameter specifying a range of attributes to list with the predictions. Use '-p 0' for none.");
      }
      if(attributeRangeString.length()!=0){
          printClusterAssignments=true;
          if(!attributeRangeString.equals("0"))attributesToOutput=new Range(attributeRangeString);
      }

      if (trainFileName.length() == 0) {
        if (objectInputFileName.length() == 0) {
          throw  new Exception("No training file and no object " 
			       + "input file given.");
        }

        if (testFileName.length() == 0) {
          throw  new Exception("No training file and no test file given.");
        }
      }
      else {
	if ((objectInputFileName.length() != 0) 
	    && (printClusterAssignments == false)) {
	  throw  new Exception("Can't use both train and model file " 
			       + "unless -p specified.");
	}
      }

      seedString = Utils.getOption('s', options);

      if (seedString.length() != 0) {
	seed = Integer.parseInt(seedString);
      }

      foldsString = Utils.getOption('x', options);

      if (foldsString.length() != 0) {
	folds = Integer.parseInt(foldsString);
	doXval = true;
      }
    }
    catch (Exception e) {
      throw new Exception('\n'+e.getMessage()+makeOptionString(clusterer));
    }

    try{
        if(trainFileName.length()!=0){
            System.out.println("Reading train file: "+trainFileName);
            train=new Instances(new BufferedReader(new FileReader(trainFileName)));
            System.out.println("Relation: "+train.relationName());
            System.out.println("\tnumInstances: "+train.numInstances());
            System.out.println("\tnumAttributes: "+train.numAttributes());
	String classString = Utils.getOption('c',options);
	if (classString.length() != 0) {
	  if (classString.compareTo("last") == 0) {
	    theClass = train.numAttributes();
	  } else if (classString.compareTo("first") == 0) {
	    theClass = 1;
	  } else {
	    theClass = Integer.parseInt(classString);
	  }
	  if (doXval || testFileName.length() != 0) {
	    throw new Exception("Can only do class based evaluation on the "
				+"training data");
	  }
	  
	  if (objectInputFileName.length() != 0) {
	    throw new Exception("Can't load a clusterer and do class based "
				+"evaluation");
	  }
	}
    if(theClass!=-1){
		if(!train.attribute(theClass-1).isNominal())throw new Exception("Class must be nominal!");
        System.out.println("Setting classIndex: "+(theClass-1));
        train.setClassIndex(theClass-1);
    }
        }

      if (objectInputFileName.length() != 0) {
	objectInputStream = new ObjectInputStream(new FileInputStream(objectInputFileName));
      }

      if (objectOutputFileName.length() != 0) {
	objectOutputStream = new 
	  ObjectOutputStream(new FileOutputStream(objectOutputFileName));
      }
    }
    catch (Exception e) {
      throw  new Exception("ClusterEvaluation: " + e.getMessage() + '.');
    }

    // Save options
    if (options != null) {
      savedOptions = new String[options.length];
      System.arraycopy(options, 0, savedOptions, 0, options.length);
    }

    if (objectInputFileName.length() != 0) {
      Utils.checkForRemainingOptions(options);
      clusterer = (Clusterer)objectInputStream.readObject();
      objectInputStream.close();
    }else{
      System.out.println("About to set options for clusterer ...");
      if(clusterer instanceof OptionHandler)((OptionHandler)clusterer).setOptions(options);
      Utils.checkForRemainingOptions(options);
      // Build the clusterer if no object file provided
      if(train.classIndex()==-1)clusterer.buildClusterer(train);
      else{
        System.out.println("About to remove classAttribute at position "+theClass);
		Remove filter=new Remove();
		filter.setAttributeIndices(""+theClass);
		filter.setInvertSelection(false);
		filter.setInputFormat(train);
		Instances clusterTrain = Filter.useFilter(train,filter);
        System.out.println("\tnumAttributes: "+clusterTrain.numAttributes());
        System.out.println("\tclassIndex: "+clusterTrain.classIndex());
		if(clusterer.K==0)clusterer.K=train.classAttribute().numValues();
		clusterer.buildClusterer(clusterTrain);
		ClusterEvaluation ce=new ClusterEvaluation(clusterer,train);
		ce.evaluateClusterer();
		return "\n\n=== Clustering stats for training data ===\n\n"+ce.clusterResultsToString();
      }
    }

    /* Output cluster predictions only (for the test data if specified,
       otherwise for the training data */
    if (printClusterAssignments) {
      return  printClusterings(clusterer, train, testFileName, attributesToOutput);
    }

    text.append(clusterer.toString());
    text.append("\n\n=== Clustering stats for training data ===\n\n" 
		+ printClusterStats(clusterer, trainFileName));

    if (testFileName.length() != 0) {
      text.append("\n\n=== Clustering stats for testing data ===\n\n" 
		  + printClusterStats(clusterer, testFileName));
    }

    if ((clusterer instanceof DistributionClusterer) && 
	(doXval == true) && 
	(testFileName.length() == 0) && 
	(objectInputFileName.length() == 0)) {
      // cross validate the log likelihood on the training data
      random = new Random(seed);
      random.setSeed(seed);
      train.randomize(random);
      text.append(crossValidateModel(clusterer.getClass().getName()
				     , train, folds, savedOptions));
    }

    // Save the clusterer if an object output file is provided
    if (objectOutputFileName.length() != 0) {
      objectOutputStream.writeObject(clusterer);
      objectOutputStream.flush();
      objectOutputStream.close();
    }

    return  text.toString();
  }


  /**
   * Performs a cross-validation 
   * for a distribution clusterer on a set of instances.
   *
   * @param clustererString a string naming the class of the clusterer
   * @param data the data on which the cross-validation is to be 
   * performed 
   * @param numFolds the number of folds for the cross-validation
   * @param options the options to the clusterer
   * @return a string containing the cross validated log likelihood
   * @exception Exception if a clusterer could not be generated 
   */
  public static String crossValidateModel (String clustererString, 
					   Instances data, 
					   int numFolds, 
					   String[] options)
    throws Exception {
    Clusterer clusterer = null;
    Instances train, test;
    String[] savedOptions = null;
    double foldAv;
    double CvAv = 0.0;
    double[] tempDist;
    StringBuffer CvString = new StringBuffer();

    if (options != null) {
      savedOptions = new String[options.length];
    }

    data = new Instances(data);

    for (int i = 0; i < numFolds; i++) {
      // create clusterer
      try {
	clusterer = (Clusterer)Class.forName(clustererString).newInstance();
      }
      catch (Exception e) {
	throw  new Exception("Can't find class with name " 
			     + clustererString + '.');
      }

      if (!(clusterer instanceof DistributionClusterer)) {
	throw  new Exception(clustererString 
			     + " must be a distrinbution " 
			     + "clusterer.");
      }

      // Save options
      if (options != null) {
	System.arraycopy(options, 0, savedOptions, 0, options.length);
      }

      // Parse options
      if (clusterer instanceof OptionHandler) {
	try {
	  ((OptionHandler)clusterer).setOptions(savedOptions);
	  Utils.checkForRemainingOptions(savedOptions);
	}
	catch (Exception e) {
	  throw  new Exception("Can't parse given options in " 
			       + "cross-validation!");
	}
      }

      // Build and test classifier 
      train = data.trainCV(numFolds, i);
      clusterer.buildClusterer(train);
      test = data.testCV(numFolds, i);
      foldAv = 0.0;

      for (int j = 0; j < test.numInstances(); j++) {
	try {
	  double temp = ((DistributionClusterer)clusterer).
	    densityForInstance(test.instance(j));
	  //	double temp = Utils.sum(tempDist);

	  if (temp > 0) {
	    foldAv += Math.log(temp);
	  }
	} catch (Exception ex) {
	  // unclustered instances
	}
      }

      CvAv += (foldAv/test.numInstances());
    }

    CvAv /= numFolds;
    CvString.append("\n" + numFolds 
		    + " fold CV Log Likelihood: " 
		    + Utils.doubleToString(CvAv, 6, 4) 
		    + "\n");
    return  CvString.toString();
  }


  // ===============
  // Private methods
  // ===============
  /**
   * Print the cluster statistics for either the training
   * or the testing data.
   *
   * @param clusterer the clusterer to use for generating statistics.
   * @return a string containing cluster statistics.
   * @exception if statistics can't be generated.
   */
  private static String printClusterStats (Clusterer clusterer, 
					   String fileName)
    throws Exception {
    StringBuffer text = new StringBuffer();
    int i = 0;
    int cnum;
    double loglk = 0.0;
    double[] dist;
    double temp;
    int numClusters = clusterer.numberOfClusters();
    double[] instanceStats = new double[numClusters];
    int unclusteredInstances = 0;

    if (fileName.length() != 0) {
      BufferedReader inStream = null;

      try {
	inStream = new BufferedReader(new FileReader(fileName));
      }
      catch (Exception e) {
	throw  new Exception("Can't open file " + e.getMessage() + '.');
      }

      Instances inst = new Instances(inStream, 1);

      while (inst.readInstance(inStream)) {
	try {
	  cnum = clusterer.clusterInstance(inst.instance(0));

	  if (clusterer instanceof DistributionClusterer) {
	    temp = ((DistributionClusterer)clusterer).
	      densityForInstance(inst.instance(0));
	    //	    temp = Utils.sum(dist);

	    if (temp > 0) {
	      loglk += Math.log(temp);
	    }
	  }
	  instanceStats[cnum]++;
	}
	catch (Exception e) {
	  unclusteredInstances++;
	}
	inst.delete(0);
	i++;
      }

      /*
      // count the actual number of used clusters
      int count = 0;
      for (i = 0; i < numClusters; i++) {
	if (instanceStats[i] > 0) {
	  count++;
	}
      }
      if (count > 0) {
	double [] tempStats = new double [count];
	count=0;
	for (i=0;i<numClusters;i++) {
	  if (instanceStats[i] > 0) {
	    tempStats[count++] = instanceStats[i];
	}
	}
	instanceStats = tempStats;
	numClusters = instanceStats.length;
	} */

      int clustFieldWidth = (int)((Math.log(numClusters)/Math.log(10))+1);
      int numInstFieldWidth = (int)((Math.log(i)/Math.log(10))+1);
      double sum = Utils.sum(instanceStats);
      loglk /= sum;
      text.append("Clustered Instances\n");

      for (i = 0; i < numClusters; i++) {
	if (instanceStats[i] > 0) {
	  text.append(Utils.doubleToString((double)i, 
					   clustFieldWidth, 0) 
		      + "      " 
		      + Utils.doubleToString(instanceStats[i], 
					     numInstFieldWidth, 0) 
		      + " (" 
		    + Utils.doubleToString((instanceStats[i]/sum*100.0)
					   , 3, 0) + "%)\n");
	}
      }
      if (unclusteredInstances > 0) {
	text.append("\nUnclustered Instances : "+unclusteredInstances);
      }

      if (clusterer instanceof DistributionClusterer) {
	text.append("\n\nLog likelihood: " 
		    + Utils.doubleToString(loglk, 1, 5) 
		    + "\n");
      }
    }

    return  text.toString();
  }


  /**
   * Print the cluster assignments for either the training
   * or the testing data.
   *
   * @param clusterer the clusterer to use for cluster assignments
   * @return a string containing the instance indexes and cluster assigns.
   * @exception if cluster assignments can't be printed
   */
  private static String printClusterings (Clusterer clusterer, Instances train,
					  String testFileName, Range attributesToOutput)
    throws Exception {
    StringBuffer text = new StringBuffer();
    int i = 0;
    int cnum;

    if (testFileName.length() != 0) {
      BufferedReader testStream = null;

      try {
	testStream = new BufferedReader(new FileReader(testFileName));
      }
      catch (Exception e) {
	throw  new Exception("Can't open file " + e.getMessage() + '.');
      }

      Instances test = new Instances(testStream, 1);

      while (test.readInstance(testStream)) {
	try {
	  cnum = clusterer.clusterInstance(test.instance(0));
	
	  text.append(i + " " + cnum + " "
		      + attributeValuesString(test.instance(0), attributesToOutput) + "\n");
	}
	catch (Exception e) {
	  /*	  throw  new Exception('\n' + "Unable to cluster instance\n" 
		  + e.getMessage()); */
	  text.append(i + " Unclustered "
		      + attributeValuesString(test.instance(0), attributesToOutput) + "\n");
	}
	test.delete(0);
	i++;
      }
    }
    else// output for training data
      {
	for (i = 0; i < train.numInstances(); i++) {
	  try {
	    cnum = clusterer.clusterInstance(train.instance(i));
	 
	    text.append(i + " " + cnum + " "
			+ attributeValuesString(train.instance(i), attributesToOutput)
			+ "\n");
	  }
	  catch (Exception e) {
	    /*  throw  new Exception('\n' 
				 + "Unable to cluster instance\n" 
				 + e.getMessage()); */
	    text.append(i + " Unclustered "
			+ attributeValuesString(train.instance(i), attributesToOutput)
			+ "\n");
	  }
	}
      }

    return  text.toString();
  }

  /**
   * Builds a string listing the attribute values in a specified range of indices,
   * separated by commas and enclosed in brackets.
   *
   * @param instance the instance to print the values from
   * @param attributes the range of the attributes to list
   * @return a string listing values of the attributes in the range
   */
  private static String attributeValuesString(Instance instance, Range attRange) {
    StringBuffer text = new StringBuffer();
    if (attRange != null) {
      boolean firstOutput = true;
      attRange.setUpper(instance.numAttributes() - 1);
      for (int i=0; i<instance.numAttributes(); i++)
	if (attRange.isInRange(i)) {
	  if (firstOutput) text.append("(");
	  else text.append(",");
	  text.append(instance.toString(i));
	  firstOutput = false;
	}
      if (!firstOutput) text.append(")");
    }
    return text.toString();
  }

  /**
   * Make up the help string giving all the command line options
   *
   * @param clusterer the clusterer to include options for
   * @return a string detailing the valid command line options
   */
  private static String makeOptionString (Clusterer clusterer) {
    StringBuffer optionsText = new StringBuffer("");
    // General options
    optionsText.append("\n\nGeneral options:\n\n");
    optionsText.append("-t <name of training file>\n");
    optionsText.append("\tSets training file.\n");
    optionsText.append("-T <name of test file>\n");
    optionsText.append("-l <name of input file>\n");
    optionsText.append("\tSets model input file.\n");
    optionsText.append("-d <name of output file>\n");
    optionsText.append("\tSets model output file.\n");
    optionsText.append("-p <attribute range>\n");
    optionsText.append("\tOutput predictions. Predictions are for " 
		       + "training file" 
		       + "\n\tif only training file is specified," 
		       + "\n\totherwise predictions are for the test file."
		       + "\n\tThe range specifies attribute values to be output"
		       + "\n\twith the predictions. Use '-p 0' for none.\n");
    optionsText.append("-x <number of folds>\n");
    optionsText.append("\tOnly Distribution Clusterers can be cross " 
		       + "validated.\n");
    optionsText.append("-s <random number seed>\n");
    optionsText.append("-c <class index>\n");
    optionsText.append("\tSet class attribute. If supplied, class is ignored");
    optionsText.append("\n\tduring clustering but is used in a classes to");
    optionsText.append("\n\tclusters evaluation.\n");

    // Get scheme-specific options
    if (clusterer instanceof OptionHandler) {
      optionsText.append("\nOptions specific to " 
			 + clusterer.getClass().getName() + ":\n\n");
      Enumeration enum = ((OptionHandler)clusterer).listOptions();

      while (enum.hasMoreElements()) {
	Option option = (Option)enum.nextElement();
	optionsText.append(option.synopsis() + '\n');
	optionsText.append(option.description() + "\n");
      }
    }

    return  optionsText.toString();
  }


  /**
   * Main method for testing this class.
   *
   * @param args the options
   */
  public static void main (String[] args) {
    try {
      if (args.length == 0) {
	throw  new Exception("The first argument must be the name of a " 
			     + "clusterer");
      }

      String ClustererString = args[0];
      args[0] = "";
      Clusterer newClusterer = Clusterer.forName(ClustererString, null);
      System.out.println(evaluateClusterer(newClusterer, args));
    }
    catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

}

