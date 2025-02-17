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
 *    Clusterer.java
 *    Copyright (C) 1999 Mark Hall
 *
 */

package weka.clusterers;

import java.io.Serializable;
import java.util.*;
import weka.core.*;
import weka.core.metrics.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;
import weka.clusterers.initializers.*;
import weka.clusterers.ClusterEvaluation;


/** 
 * Abstract clusterer.
 *
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public abstract class Clusterer implements Cloneable, Serializable,OptionHandler{

    public Instances [] instanceses;
    public int K=0;
    public Instances instances;
    public ArrayList [] clusters;
    public int [] assignments;
    public Metric metric=new Euclidean();
    public Instances centroids=new Instances("Centroids");
	public Initializer initializer=new RandomInitializer();
	public Filter filter=new ReplaceMissingValues();
  // ===============
  // Public methods.
  // ===============
 
  /**
   * Generates a clusterer. Has to initialize all fields of the clusterer
   * that are not being set via options.
   *
   * @param data set of instances serving as training data 
   * @exception Exception if the clusterer has not been 
   * generated successfully
   */
  public void buildClusterer(Instances instances)throws Exception{
	if(filter instanceof Filter){
		filter.setInputFormat(instances);
		instances=Filter.useFilter(instances,filter);
	}
	this.instances=instances;
	metric.build(instances);
	if(centroids.numInstances()==0){
		initializer.setClusterer(this);
		centroids=initializer.initialize();
	}
	instanceses=new Instances[K];
	clusters=new ArrayList[K];
	assignments=new int[instances.numInstances()];
	int loop=0;
	while(true){
		System.out.println("===***=== "+loop+" ===***===");
		if(clusterInstances(instances))break;
		clusterCentroids();
		for(int i=0;i<K;i++){
			System.out.print(instanceses[i].numInstances()+"\t");
		}
		System.out.println();
		loop++;
	}
  }
  public void buildClusterer()throws Exception{
	  buildClusterer(instances);
  }

  public String evaluate()throws Exception{
      return ClusterEvaluation.evaluateClusterer(this);
  }
  /**
   * Classifies a given instance.
   *
   * @param instance the instance to be assigned to a cluster
   * @return the number of the assigned cluster as an interger
   * if the class is enumerated, otherwise the predicted value
   * @exception Exception if instance could not be classified
   * successfully
   */
  public int clusterInstance(Instance instance)throws Exception{
      double min=Integer.MAX_VALUE;
      int assignment=0;
      for(int i=0;i<K;i++){
          double d=metric.distance(instance,centroids.instance(i));
          if(d<min){
              min=d;
              assignment=i;
          }
      }
      return assignment;
  }
  public boolean clusterInstances()throws Exception{
      return clusterInstances(instances);
  }
  public boolean clusterInstances(Instances instances)throws Exception{
      for(int i=0;i<K;i++){
          instanceses[i]=new Instances(instances,0);
          clusters[i]=new ArrayList();
      }
      boolean done=true;
      for(int i=0;i<instances.numInstances();i++){
          Instance instance=instances.instance(i);
          int assignment=clusterInstance(instance);
          instanceses[assignment].add(instance);
          clusters[assignment].add(new Integer(i));
          if(assignment!=assignments[i]){
              done=false;
              assignments[i]=assignment;
          }
      }
      return done;
  }
  public void clusterCentroids(){
      centroids=new Instances(instances,0);
      for(int i=0;i<K;i++)centroids.add(instanceses[i].meanOrMode());
  }
  /**
   * Returns the number of clusters.
   *
   * @return the number of clusters generated for a training dataset.
   * @exception Exception if number of clusters could not be returned
   * successfully
   */
  public int numberOfClusters() throws Exception{
      return K;
  }
  public double [] getAssignments(){
      double[] array=new double[assignments.length];
      for(int i=0;i<assignments.length;i++)array[i]=assignments[i];
      return array;
  }

  /**
   * Creates a new instance of a clusterer given it's class name and
   * (optional) arguments to pass to it's setOptions method. If the
   * clusterer implements OptionHandler and the options parameter is
   * non-null, the clusterer will have it's options set.
   *
   * @param searchName the fully qualified class name of the clusterer
   * @param options an array of options suitable for passing to setOptions. May
   * be null.
   * @return the newly created search object, ready for use.
   * @exception Exception if the clusterer class name is invalid, or the 
   * options supplied are not acceptable to the clusterer.
   */
  public static Clusterer forName(String clustererName,String[] options)throws Exception{
    return (Clusterer)Utils.forName(Clusterer.class,clustererName,options);
  }

  /**
   * Creates copies of the current clusterer. Note that this method
   * now uses Serialization to perform a deep copy, so the Clusterer
   * object must be fully Serializable. Any currently built model will
   * now be copied as well.
   *
   * @param model an example clusterer to copy
   * @param num the number of clusterer copies to create.
   * @return an array of clusterers.
   * @exception Exception if an error occurs 
   */
  public static Clusterer[] makeCopies(Clusterer model,int num)throws Exception{
	if(model==null)throw new Exception("No model clusterer set");
    Clusterer[] clusterers=new Clusterer[num];
    SerializedObject so=new SerializedObject(model);
    for(int i=0;i<clusterers.length;i++){
      clusterers[i]=(Clusterer)so.getObject();
    }
    return clusterers;
  }
  public Enumeration listOptions(){
	Vector vector=new Vector(3);
	vector.addElement(new Option("\tnumber of clusters.","N",1,"-N <num>"));
	vector.addElement(new Option("\tmetric.\tdefault=weka.core.metrics.Euclidean","M",1,"-M <metric class>"));
	vector.addElement(new Option("\tinitializer.\tdefault=weka.clusters.initializers.RandomInitializer","I",1,"-I <initializer class>"));
	return vector.elements();
  }
  public void setOptions(String [] options) throws Exception{
	String string;
	string=Utils.getOption('N',options);
	if(string.length()!=0)K=Integer.parseInt(string);
	string=Utils.getOption('M',options);
	if(string.length()!=0)metric=(Metric)Utils.forName(Metric.class,string,options);
	string=Utils.getOption('I',options);
	if(string.length()!=0)initializer=(Initializer)Utils.forName(Initializer.class,string,options);
  }
  public String [] getOptions(){
	String [] options=new String[6];
	int current=0;
	options[current++]="-N";
	options[current++]=Integer.toString(K);
	options[current++]="-M";
	options[current++]=metric.getClass().getName();
	options[current++]="-I";
	options[current++]=initializer.getClass().getName();
	return options;
  }
}


