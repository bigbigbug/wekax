
/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 * Scoreable.java
 * Copyright (C) 2001 Remco Bouckaert
 * 
 */
package weka.classifiers.bayes;

/**
 * Interface for allowing to score a classifier
 * 
 * @author Remco Bouckaert (rrb@xm.co.nz)
 * @version $Revision: 1.1.1.1 $
 */
public interface Scoreable {

  /**
   * score types
   */
  int BAYES = 0;
  int MDL = 1;
  int ENTROPY = 2;
  int AIC = 3;

  /**
   * Returns log-score
   */
  double logScore(int nType);
}    // interface Scoreable




