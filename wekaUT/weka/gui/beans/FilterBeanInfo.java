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
 *    FilterBeanInfo.java
 *    Copyright (C) 2002 Mark Hall
 *
 */

package weka.gui.beans;

import java.beans.*;

/**
 * Bean info class for the Filter bean
 *
 * @author <a href="mailto:mhall@cs.waikato.ac.nz">Mark Hall</a>
 * @versio $Revision: 1.1.1.1 $
 */
public class FilterBeanInfo extends SimpleBeanInfo {
   
  /**
   * Get the event set descriptors for this bean
   *
   * @return an <code>EventSetDescriptor[]</code> value
   */
  public EventSetDescriptor [] getEventSetDescriptors() {
    try {
      EventSetDescriptor [] esds = { 
	new EventSetDescriptor(TrainingSetProducer.class, 
			       "trainingSet", 
			       TrainingSetListener.class, 
			       "acceptTrainingSet"),
	new EventSetDescriptor(TestSetProducer.class, 
			       "testSet", 
			       TestSetListener.class, 
			       "acceptTestSet"),
	new EventSetDescriptor(DataSource.class,
			       "dataSet",
			       DataSourceListener.class,
			       "acceptDataSet"),
	new EventSetDescriptor(DataSource.class, 
			       "instance", 
			       InstanceListener.class, 
			       "acceptInstance")
	  };
      return esds;
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }

  /**
   * Get the bean descriptor for this bean
   *
   * @return a <code>BeanDescriptor</code> value
   */
  public BeanDescriptor getBeanDescriptor() {
    return new BeanDescriptor(weka.gui.beans.Filter.class,
			      FilterCustomizer.class);
  }
}
