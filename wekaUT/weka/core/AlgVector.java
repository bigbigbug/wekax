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
 *    AlgVector.java
 *    Copyright (C) 2002 University of Waikato
 *
 */

package weka.core;

import java.util.Random;
//import java.io.Writer;
//import java.io.Reader;
//import java.io.LineNumberReader;
import java.io.Serializable;
//import java.util.StringTokenizer;

/**
 * Class for performing operations on an algebraic vector
 * of floating-point values.
 *
 * @author Gabi Schmidberger (gabi@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class AlgVector implements Cloneable, Serializable {

  /**
   * The values of the matrix */
  protected double [] m_Elements;

  /**
   * Constructs a vector and initializes it with default values.
   *
   * @param n the number of elements
   */
  public AlgVector(int n) {

    m_Elements = new double[n];
    initialize();
  }

 /**
   * Constructs a vector using a given array.
   *
   * @param array the values of the matrix
   */
  public AlgVector(double[] array) throws Exception {
    
    m_Elements = new double[array.length];
    for (int i = 0; i < array.length; i++) {
      m_Elements[i] = array[i];
    }
  }

  /**
   * Constructs a vector using a given data format.
   * The vector has an element for each numerical attribute.
   * The other attributes (nominal, string) are ignored.
   * Random is used to initialize the attributes.
   *
   * @param array the values of the matrix
   */
  public AlgVector(Instances format, Random random) throws Exception {
    
    int len = format.numAttributes();
    for (int i = 0; i < format.numAttributes(); i++) {
      if (!format.attribute(i).isNumeric()) len--;
    }
    if (len > 0) {
      m_Elements = new double[len];
      initialize(random);
    }
  }

  /**
   * Constructs a vector using an instance.
   * The vector has an element for each numerical attribute.
   * The other attributes (nominal, string) are ignored.
   *
   * @param instance with numeric attributes, that AlgVector gets build from
   * @exception if instance doesn't have access to the data format
   */
  public AlgVector(Instance instance) throws Exception {
    
    int len = instance.numAttributes();
    for (int i = 0; i < instance.numAttributes(); i++) {
      if (!instance.attribute(i).isNumeric()) len--;
    }
    if (len > 0) {
      m_Elements = new double[len];
      for (int i = 0; i < len; i++) {
	m_Elements[i] = instance.valueSparse(i);
      }
    }
  }

  /**
   * Creates and returns a clone of this object.
   *
   * @return a clone of this instance.
   * @exception CloneNotSupportedException if an error occurs
   */
  public Object clone() throws CloneNotSupportedException {

    AlgVector v = (AlgVector)super.clone();
    v.m_Elements = new double[numElements()];
    for (int i = 0; i < numElements(); i++) {
        v.m_Elements[i] = m_Elements[i];
      }
    
    return v;
  }

  /**
   * Writes out a matrix.
   *
   * @param w the output Writer
   * @exception Exception if an error occurs
   *
  public void write(Writer w) throws Exception {

    w.write("% Rows\tColumns\n");
    w.write("" + numRows() + "\t" + numColumns() + "\n");
    w.write("% Matrix elements\n");
    for(int i = 0; i < numRows(); i++) {
      for(int j = 0; j < numColumns(); j++) {
	w.write("" + m_Elements[i][j] + "\t");
      }
      w.write("\n");
    }
    w.flush();
  }

  /**
   * Resets the elements to the default value which is 0.0.
   */
  protected void initialize() {

    for (int i = 0; i < m_Elements.length; i++) {
      m_Elements[i] = 0.0;
    }
  }

  /**
   * Resets the elements to the default value which is 0.0.
   */
  protected void initialize(Random random) {

    for (int i = 0; i < m_Elements.length; i++) {
      m_Elements[i] = random.nextDouble();
    }
  }

  /**
   * Returns the value of a cell in the matrix.
   *
   * @param index the row's index
   * @return the value of the cell of the vector
   */
  public final double getElement(int index) {
    
    return m_Elements[index];
  }


  /**
   * Returns the number of elements in the vector.
   *
   * @return the number of rows
   */
  public final int numElements() {
  
    return m_Elements.length;
  }


  /**
   * Sets an element of the matrix to the given value.
   *
   * @param index the elements index
   * @param value the new value
   */
  public final void setElement(int index, double value) {
    
    m_Elements[index] = value;
  }

  /**
   * Sets the elements of the vector to values of the given array.
   * Performs a deep copy.
   *
   * @param elements an array of doubles
   */
  public final void setElements(double[] elements) {

    for (int i = 0; i < elements.length; i++) {
      m_Elements[i] = elements[i];
    }
  }
  
  /**
   * Gets the elements of the vector and returns them as double array.
   *
   * @return an array of doubles
   */
  public double[] getElements(int index) {

    double [] elements = new double[this.numElements()];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = m_Elements[i];
    }
    return elements;
  }

  /**
   * Gets the elements of the vector as an instance.
   *
   * !! NON-numeric data is ignored sofar
   * @return an array of doubles
   * @exception if length of vector is not number of numerical attributes
   */
  public Instance getAsInstance(Instances model, Random random) 
    throws Exception {

    Instance newInst = new Instance(model.numAttributes());
    newInst.setDataset(model);

    for (int i = 0, j = 0; i < model.numAttributes(); i++) {
      if (model.attribute(i).isNumeric()) {
	if (j >= m_Elements.length)
	  throw new Exception("Datatypes are not compatible."); 
	newInst.setValue(i, m_Elements[j++]);
      }
      if (model.attribute(i).isNominal()) {
        int newVal = (int) 
	  (random.nextDouble() * (double) (model.attribute(i).numValues()));
	if (newVal == (int) model.attribute(i).numValues())
          newVal -= 1;
	newInst.setValue(i, newVal);
      }
    }
    return newInst;
  }

  /** 
   * Converts a vector to a string
   *
   * @return the converted string
   */
  public String toString() {

    StringBuffer text = new StringBuffer();
    for (int i = 0; i < m_Elements.length; i++) {
      if (i > 0) text.append(",");
      text.append(Utils.doubleToString(m_Elements[i],6));
    }

    text.append("\n");
    return text.toString();
  }
    
  /**
   * Returns the sum of this vector with another.
   *
   * @return a vector containing the sum.
   */
  public final AlgVector add(AlgVector other) {
  
    int n = m_Elements.length;
    AlgVector b;
    try {
      b = (AlgVector)clone();
    } catch (CloneNotSupportedException ex) {
      b = new AlgVector(n);
    }
    
    for(int i = 0; i < n; i++) {
      b.m_Elements[i] = m_Elements[i] + other.m_Elements[i];
    }
    
    return b;
  }

  /**
   * Returns the difference of this vector minus another.
   *
   * @return a vector containing the difference vector.
   */
  public final AlgVector substract(AlgVector other) {
  
    int n = m_Elements.length;
    AlgVector b;
    try {
      b = (AlgVector)clone();
    } catch (CloneNotSupportedException ex) {
      b = new AlgVector(n);
    }
    
    for(int i = 0; i < n; i++) {
      b.m_Elements[i] = m_Elements[i] - other.m_Elements[i];
    }
    
    return b;
  }
  
 
  /**
   * Returns the inner (or dot) product of two vectors
   *
   * @param b the multiplication matrix
   * @return the double representing the dot product
   */
  public final double dotMultiply(AlgVector b) {
   
    int n = m_Elements.length;
    int bn = b.m_Elements.length;
    double sum = 0.0;

    for(int i = 0; i < n; i++) {
      sum += m_Elements[i] * b.m_Elements[i];
    }
    
    return sum;
  }

  /**
   * Computes the scalar product of this vector with a scalar
   *
   * @param s the scalar
   */
  public final void scalarMultiply(double s) {
   
    int n = m_Elements.length;

    for(int i = 0; i < n; i++) {
      m_Elements[i] = s * m_Elements[i];
    }
  }

  /**
   * Changes the length of a vector.
   *
   * @param the new length of the vector
   * @return the norm of the vector
   */
  public void changeLength(double len) {
   
    double factor = this.norm();
    factor = len / factor;
    scalarMultiply(factor);
  }

  /**
   * Returns the norm of the vector
   *
   * @return the norm of the vector
   */
  public double norm() {
   
    int n = m_Elements.length;
    double sum = 0.0;
    
    for(int i = 0; i < n; i++) {
      sum += m_Elements[i] * m_Elements[i];
    }
    return Math.pow(sum, 0.5);
  }

  /**
   * Norms this vector to length 1.0
   *
   */
  public final void normVector() {
   
    double len = this.norm();
    this.scalarMultiply(1 / len);
  }



  /**
   * Main method for testing this class.
   */
  public static void main(String[] ops) {
  
    double[] first = {2.3, 1.2, 5.0};
    
    try {
      AlgVector test = new AlgVector(first);
      System.out.println("test:\n " + test);
     
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}



