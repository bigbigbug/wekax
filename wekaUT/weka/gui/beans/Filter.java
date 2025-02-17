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
 *    Filter.java
 *    Copyright (C) 2002 Mark Hall
 *
 */

package weka.gui.beans;


import java.util.Vector;
import java.util.EventObject;
import java.util.Hashtable;
import java.util.Enumeration;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.*;
import java.io.Serializable;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import java.beans.EventSetDescriptor;

import weka.core.Instance;
import weka.core.Instances;
import weka.filters.*;
import weka.gui.Logger;

/**
 * A wrapper bean for Weka filters
 *
 * @author <a href="mailto:mhall@cs.waikato.ac.nz">Mark Hall</a>
 * @version $Revision: 1.1.1.1 $
 */
public class Filter extends JPanel
  implements BeanCommon, Visible, WekaWrapper,
	     Serializable, UserRequestAcceptor,
	     TrainingSetListener, TestSetListener,
	     TrainingSetProducer, TestSetProducer,
	     DataSource, DataSourceListener, 
	     InstanceListener, EventConstraints {

  protected BeanVisual m_visual = 
    new BeanVisual("Filter",
		   BeanVisual.ICON_PATH+"DefaultFilter.gif",
		   BeanVisual.ICON_PATH+"DefaultFilter_animated.gif");

  private static int IDLE = 0;
  private static int FILTERING_TRAINING = 1;
  private static int FILTERING_TEST = 2;
  private int m_state = IDLE;

  protected Thread m_filterThread = null;

  private transient Instances m_trainingSet;
  private transient Instances m_testingSet;

  /**
   * Objects talking to us
   */
  private Hashtable m_listenees = new Hashtable();

  /**
   * Objects listening for training set events
   */
  private Vector m_trainingListeners = new Vector();

  /**
   * Objects listening for test set events
   */
  private Vector m_testListeners = new Vector();

  /**
   * Objects listening for instance events
   */
  private Vector m_instanceListeners = new Vector();

  /**
   * Objects listening for data set events
   */
  private Vector m_dataListeners = new Vector();

  /**
   * The filter to use.
   */
  private weka.filters.Filter m_Filter = new AllFilter();

  /**
   * Instance event object for passing on filtered instance streams
   */
  private InstanceEvent m_ie = new InstanceEvent(this);

  private transient Logger m_log = null;

  public Filter() {
    setLayout(new BorderLayout());
    add(m_visual, BorderLayout.CENTER);
    setFilter(m_Filter);
  }

  /**
   * Set the filter to be wrapped by this bean
   *
   * @param c a <code>weka.filters.Filter</code> value
   */
  public void setFilter(weka.filters.Filter c) {
    boolean loadImages = true;
    if (c.getClass().getName().
	compareTo(m_Filter.getClass().getName()) == 0) {
      loadImages = false;
    }
    m_Filter = c;
    String filterName = c.getClass().toString();
    filterName = filterName.substring(filterName.
				      lastIndexOf('.')+1, 
				      filterName.length());
    if (loadImages) {
      if (!m_visual.loadIcons(BeanVisual.ICON_PATH+filterName+".gif",
		       BeanVisual.ICON_PATH+filterName+"_animated.gif")) {
	useDefaultVisual();
      }
    }
    m_visual.setText(filterName);

    if (!(m_Filter instanceof StreamableFilter) &&
	(m_listenees.containsKey("instance"))) {
      if (m_log != null) {
	m_log.logMessage("WARNING : "+m_Filter.getClass().getName()
			 +" is not an incremental filter");
      }
    }
  }

  public weka.filters.Filter getFilter() {
    return m_Filter;
  }

  /**
   * Set the filter to be wrapped by this bean
   *
   * @param algorithm a weka.filters.Filter
   * @exception IllegalArgumentException if an error occurs
   */
  public void setWrappedAlgorithm(Object algorithm) 
    throws IllegalArgumentException {
    
    if (!(algorithm instanceof weka.filters.Filter)) { 
      throw new IllegalArgumentException(algorithm.getClass()+" : incorrect "
					 +"type of algorithm (Filter)");
    }
    setFilter((weka.filters.Filter)algorithm);
  }

  /**
   * Get the filter wrapped by this bean
   *
   * @return an <code>Object</code> value
   */
  public Object getWrappedAlgorithm() {
    return getFilter();
  }

  /**
   * Accept a training set
   *
   * @param e a <code>TrainingSetEvent</code> value
   */
  public void acceptTrainingSet(TrainingSetEvent e) {
    processTrainingOrDataSourceEvents(e);
  }

  /**
   * Accept an instance for processing by StreamableFilters only
   *
   * @param e an <code>InstanceEvent</code> value
   */
  public void acceptInstance(InstanceEvent e) {
    // to do!
    if (m_filterThread != null) {
      String messg = "Filter is currently batch processing!";
      if (m_log != null) {
	m_log.logMessage(messg);
      } else {
	System.err.println(messg);
      }
      return;
    }
    if (!(m_Filter instanceof StreamableFilter)) {
      if (m_log != null) {
	m_log.logMessage("ERROR : "+m_Filter.getClass().getName()
			 +"can't process streamed instances; can't continue");
      }
      return;
    }
    if (e.getStatus() == InstanceEvent.FORMAT_AVAILABLE) {
      try {
	Instances dataset = e.getInstance().dataset();
	if (m_Filter instanceof SupervisedFilter) {
	  // defualt to last column if no class is set
	  if (dataset.classIndex() < 0) {
	    dataset.setClassIndex(dataset.numAttributes()-1);
	  }
	}
	// initialize filter
	m_Filter.setInputFormat(dataset);
      } catch (Exception ex) {
	ex.printStackTrace();
      }
    }
   
    // pass instance through the filter
    try {
      if (!m_Filter.input(e.getInstance())) {
	if (m_log != null) {
	  m_log.logMessage("ERROR : filter not ready to output instance");
	}
	return;
      }
      
      // collect output instance.
      Instance filteredInstance = m_Filter.output();
      if (filteredInstance == null) {
	return;
      }

      m_ie.setInstance(filteredInstance);
      m_ie.setStatus(e.getStatus());
      notifyInstanceListeners(m_ie);
    } catch (Exception ex) {
      if (m_log != null) {
	m_log.logMessage(ex.toString());
      }
      ex.printStackTrace();
    }
  }

  private void processTrainingOrDataSourceEvents(final EventObject e) {
    if (m_filterThread == null) {
      try {
	if (m_state == IDLE) {
	  synchronized (this) {
	    m_state = FILTERING_TRAINING;
	  }
	  m_trainingSet = (e instanceof TrainingSetEvent) 
	    ? ((TrainingSetEvent)e).getTrainingSet()
	    : ((DataSetEvent)e).getDataSet();

	  final String oldText = m_visual.getText();
	  m_filterThread = new Thread() {
	      public void run() {
		try {
		  if (m_trainingSet != null) {
		    m_visual.setAnimated();
		    m_visual.setText("Filtering training data...");
		    if (m_log != null) {
		      m_log.statusMessage("Filter : filtering training data ("
					  +m_trainingSet.relationName());
		    }
		    m_Filter.setInputFormat(m_trainingSet);
		    Instances filteredData = 
		      weka.filters.Filter.useFilter(m_trainingSet, m_Filter);
		    m_visual.setText(oldText);
		    m_visual.setStatic();
		    EventObject ne;
		    if (e instanceof TrainingSetEvent) {
		      ne = new TrainingSetEvent(weka.gui.beans.Filter.this, 
						filteredData);
		      ((TrainingSetEvent)ne).m_setNumber =
			((TrainingSetEvent)e).m_setNumber;
		      ((TrainingSetEvent)ne).m_maxSetNumber = 
			((TrainingSetEvent)e).m_maxSetNumber;
		    } else {
		      ne = new DataSetEvent(weka.gui.beans.Filter.this,
					    filteredData);
		    }

		    notifyDataOrTrainingListeners(ne);
		  }
		} catch (Exception ex) {
		  ex.printStackTrace();
		} finally {
		  m_visual.setText(oldText);
		  m_visual.setStatic();
		  m_state = IDLE;
		  if (isInterrupted()) {
		    m_trainingSet = null;
		    if (m_log != null) {
		      m_log.logMessage("Filter training set interrupted!");
		      m_log.statusMessage("OK");
		    }
		  }
		  block(false);
		}
	      }
	    };
	  m_filterThread.setPriority(Thread.MIN_PRIORITY);
	  m_filterThread.start();
	  block(true);
	  m_filterThread = null;
	  m_state = IDLE;
	}
      } catch (Exception ex) {
	ex.printStackTrace();
      }
    }
  }

  /**
   * Accept a test set
   *
   * @param e a <code>TestSetEvent</code> value
   */
  public void acceptTestSet(final TestSetEvent e) {
    if (m_trainingSet != null && 
	m_trainingSet.equalHeaders(e.getTestSet()) && 
	m_filterThread == null) {
      try {
	if (m_state == IDLE) {
	  m_state = FILTERING_TEST;
	}
	m_testingSet = e.getTestSet();
	final String oldText = m_visual.getText();
	m_filterThread = new Thread() {
	    public void run() {
	      try {
		if (m_testingSet != null) {
		  m_visual.setAnimated();
		  m_visual.setText("Filtering test data...");
		  if (m_log != null) {
		    m_log.statusMessage("Filter : filtering test data ("
					+m_testingSet.relationName());
		  }
		  Instances filteredTest = 
		    weka.filters.Filter.useFilter(m_testingSet, m_Filter);
		  m_visual.setText(oldText);
		  m_visual.setStatic();
		  TestSetEvent ne =
		    new TestSetEvent(weka.gui.beans.Filter.this,
				     filteredTest);
		  ne.m_setNumber = e.m_setNumber;
		  ne.m_maxSetNumber = e.m_maxSetNumber;
		  notifyTestListeners(ne);
		}
	      } catch (Exception ex) {
		ex.printStackTrace();
	      } finally {
		m_visual.setText(oldText);
		m_visual.setStatic();
		m_state = IDLE;
		if (isInterrupted()) {
		  m_trainingSet = null;
		  if (m_log != null) {
		    m_log.logMessage("Filter test set interrupted!");
		    m_log.statusMessage("OK");
		  }
		}
		block(false);
	      }
	    }
	  };
	m_filterThread.setPriority(Thread.MIN_PRIORITY);
	m_filterThread.start();
	block(true);
	m_filterThread = null;
	m_state = IDLE;
      } catch (Exception ex) {
	ex.printStackTrace();
      }
    }
  }

  /**
   * Accept a data set
   *
   * @param e a <code>DataSetEvent</code> value
   */
  public void acceptDataSet(DataSetEvent e) {
    processTrainingOrDataSourceEvents(e);
  }

  /**
   * Set the visual appearance of this bean
   *
   * @param newVisual a <code>BeanVisual</code> value
   */
  public void setVisual(BeanVisual newVisual) {
    m_visual = newVisual;
  }

  /**
   * Get the visual appearance of this bean
   *
   * @return a <code>BeanVisual</code> value
   */
  public BeanVisual getVisual() {
    return m_visual;
  }

  /**
   * Use the default visual appearance
   */
  public void useDefaultVisual() {
    m_visual.loadIcons(BeanVisual.ICON_PATH+"DefaultFilter.gif",
		       BeanVisual.ICON_PATH+"DefaultFilter_animated.gif");
  }

  /**
   * Add a training set listener
   *
   * @param tsl a <code>TrainingSetListener</code> value
   */
  public synchronized void addTrainingSetListener(TrainingSetListener tsl) {
    m_trainingListeners.addElement(tsl);
  }
  
  /**
   * Remove a training set listener
   *
   * @param tsl a <code>TrainingSetListener</code> value
   */
  public synchronized void removeTrainingSetListener(TrainingSetListener tsl) {
     m_trainingListeners.removeElement(tsl);
  }

  /**
   * Add a test set listener
   *
   * @param tsl a <code>TestSetListener</code> value
   */
  public synchronized void addTestSetListener(TestSetListener tsl) {
    m_testListeners.addElement(tsl);
  }
  
  /**
   * Remove a test set listener
   *
   * @param tsl a <code>TestSetListener</code> value
   */
  public synchronized void removeTestSetListener(TestSetListener tsl) {
    m_testListeners.removeElement(tsl);
  }

  /**
   * Add a data source listener
   *
   * @param dsl a <code>DataSourceListener</code> value
   */
  public synchronized void addDataSourceListener(DataSourceListener dsl) {
    m_dataListeners.addElement(dsl);
  }

  /**
   * Remove a data source listener
   *
   * @param dsl a <code>DataSourceListener</code> value
   */
  public synchronized void removeDataSourceListener(DataSourceListener dsl) {
    m_dataListeners.remove(dsl);
  }

  /**
   * Add an instance listener
   *
   * @param tsl an <code>InstanceListener</code> value
   */
  public synchronized void addInstanceListener(InstanceListener tsl) {
    m_instanceListeners.addElement(tsl);
  }

  /**
   * Remove an instance listener
   *
   * @param tsl an <code>InstanceListener</code> value
   */
  public synchronized void removeInstanceListener(InstanceListener tsl) {
    m_instanceListeners.removeElement(tsl);
  }

  private void notifyDataOrTrainingListeners(EventObject ce) {
    Vector l;
    synchronized (this) {
      l = (ce instanceof TrainingSetEvent)
	? (Vector)m_trainingListeners.clone()
	: (Vector)m_dataListeners.clone();
    }
    if (l.size() > 0) {
      for(int i = 0; i < l.size(); i++) {
	if (ce instanceof TrainingSetEvent) {
	  ((TrainingSetListener)l.elementAt(i)).
	    acceptTrainingSet((TrainingSetEvent)ce);
	} else {
	  ((DataSourceListener)l.elementAt(i)).acceptDataSet((DataSetEvent)ce);
	}
      }
    }
  }

  private void notifyTestListeners(TestSetEvent ce) {
    Vector l;
    synchronized (this) {
      l = (Vector)m_testListeners.clone();
    }
    if (l.size() > 0) {
      for(int i = 0; i < l.size(); i++) {
	((TestSetListener)l.elementAt(i)).acceptTestSet(ce);
      }
    }
  }

  protected void notifyInstanceListeners(InstanceEvent tse) {
    Vector l;
    synchronized (this) {
      l = (Vector)m_instanceListeners.clone();
    }
    if (l.size() > 0) {
      for(int i = 0; i < l.size(); i++) {
	//	System.err.println("Notifying instance listeners "
	//			   +"(Filter)");
	((InstanceListener)l.elementAt(i)).acceptInstance(tse);
      }
    }
  }
  
  /**
   * Returns true if, at this time, 
   * the object will accept a connection with respect to the supplied
   * event name
   *
   * @param eventName the event
   * @return true if the object will accept a connection
   */
  public boolean connectionAllowed(String eventName) {

    if (m_listenees.containsKey(eventName)) {
      return false;
    }

    /* reject a test event if we don't have a training or data set event
    if (eventName.compareTo("testSet") == 0) {
      if (!m_listenees.containsKey("trainingSet") &&
	  !m_listenees.containsKey("dataSet")) {
	return false;
      }
      } */
    
    // will need to reject train/test listener if we have a
    // data source listener and vis versa
    if (m_listenees.containsKey("dataSet") &&
	(eventName.compareTo("trainingSet") == 0 ||
	 eventName.compareTo("testSet") == 0 ||
	eventName.compareTo("instance") == 0)) {
      return false;
    }

    if ((m_listenees.containsKey("trainingSet") ||
	 m_listenees.containsKey("testSet")) &&
	(eventName.compareTo("dataSet") == 0 || 
	eventName.compareTo("instance") == 0)) {
      return false;
    }

    if (m_listenees.containsKey("instance") &&
	(eventName.compareTo("trainingSet") == 0 ||
	 eventName.compareTo("testSet") == 0 ||
	 eventName.compareTo("dataSet") == 0)) {
      return false;
    }

    // reject an instance event connection if our filter isn't
    // streamable
    if (eventName.compareTo("instance") == 0 &&
	!(m_Filter instanceof StreamableFilter)) {
      return false;
    }
    return true;
  }

  /**
   * Notify this object that it has been registered as a listener with
   * a source with respect to the supplied event name
   *
   * @param eventName
   * @param source the source with which this object has been registered as
   * a listener
   */
  public synchronized void connectionNotification(String eventName,
						  Object source) {
    if (connectionAllowed(eventName)) {
      m_listenees.put(eventName, source);
    }
  }

  /**
   * Notify this object that it has been deregistered as a listener with
   * a source with respect to the supplied event name
   *
   * @param eventName the event
   * @param source the source with which this object has been registered as
   * a listener
   */
  public synchronized void disconnectionNotification(String eventName,
						     Object source) {
    m_listenees.remove(eventName);
  }

  /**
   * Function used to stop code that calls acceptTrainingSet, acceptTestSet,
   * or acceptDataSet. This is 
   * needed as filtering is performed inside a separate
   * thread of execution.
   *
   * @param tf a <code>boolean</code> value
   */
  private synchronized void block(boolean tf) {
    if (tf) {
      try {
	// only block if thread is still doing something useful!
	if (m_filterThread.isAlive() && m_state != IDLE) {
	  wait();
	}
      } catch (InterruptedException ex) {
      }
    } else {
      notifyAll();
    }
  }

  /**
   * Stop all action if possible
   */
  public void stop() {
    // tell all listenees (upstream beans) to stop
    Enumeration en = m_listenees.keys();
    while (en.hasMoreElements()) {
      Object tempO = m_listenees.get(en.nextElement());
      if (tempO instanceof BeanCommon) {
	System.err.println("Listener is BeanCommon");
	((BeanCommon)tempO).stop();
      }
    }
    
    //
  }
  
  /**
   * Set a logger
   *
   * @param logger a <code>Logger</code> value
   */
  public void setLog(Logger logger) {
    m_log = logger;
  }

  /**
   * Return an enumeration of user requests
   *
   * @return an <code>Enumeration</code> value
   */
  public Enumeration enumerateRequests() {
    Vector newVector = new Vector(0);
    if (m_filterThread != null) {
      newVector.addElement("Stop");
    }
    return newVector.elements();
  }

  /**
   * Perform the named request
   *
   * @param request a <code>String</code> value
   * @exception IllegalArgumentException if an error occurs
   */
  public void performRequest(String request) throws IllegalArgumentException {
    if (request.compareTo("Stop") == 0) {
      stop();
    } else {
      throw new IllegalArgumentException(request
					 + " not supported (Filter)");
    }
  }

  /**
   * Returns true, if at the current time, the named event could
   * be generated. Assumes that supplied event names are names of
   * events that could be generated by this bean.
   *
   * @param eventName the name of the event in question
   * @return true if the named event could be generated at this point in
   * time
   */
  public boolean eventGeneratable(String eventName) {
    // can't generate the named even if we are not receiving it as an
    // input!
    if (!m_listenees.containsKey(eventName)) {
      return false;
    }
    Object source = m_listenees.get(eventName);
    if (source instanceof EventConstraints) {
      if (!((EventConstraints)source).eventGeneratable(eventName)) {
	return false;
      }
    }
    if (eventName.compareTo("instance") == 0) {
      if (!(m_Filter instanceof StreamableFilter)) {
	return false;
      }
    }
    return true;
  }
}
