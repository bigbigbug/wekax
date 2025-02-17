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
 *    GraphEvent.java
 *    Copyright (C) 2002 Mark Hall
 *
 */

package weka.gui.beans;

import java.util.EventObject;

/**
 * Event for graphs
 *
 * @author <a href="mailto:mhall@cs.waikato.ac.nz">Mark Hall</a>
 * @version $Revision: 1.1.1.1 $
 */
public class GraphEvent extends EventObject {

  protected String m_graphString;
  protected String m_graphTitle;

  /**
   * Creates a new <code>GraphEvent</code> instance.
   *
   * @param source the source of the event
   * @param graphString a string describing the graph in "dot" format
   * @param graphTitle the title for the graph
   */
  public GraphEvent(Object source, String graphString,
		    String graphTitle) {
    super(source);
    m_graphString = graphString;
    m_graphTitle = graphTitle;
  }

  /**
   * Return the dot string for the graph
   *
   * @return a <code>String</code> value
   */
  public String getGraphString() {
    return m_graphString;
  }

  /**
   * Return the graph title
   *
   * @return a <code>String</code> value
   */
  public String getGraphTitle() {
    return m_graphTitle;
  }
}

