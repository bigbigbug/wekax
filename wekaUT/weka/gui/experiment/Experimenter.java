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
 *    You should have received ao copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    Experimenter.java
 *    Copyright (C) 1999 Len Trigg
 *
 */


package weka.gui.experiment;

import weka.experiment.Experiment;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import java.net.*;

/** 
 * The main class for the experiment environment. Lets the user create,
 * open, save, configure, run experiments, and analyse experimental results.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @version $Revision: 1.2 $
 */
public class Experimenter extends JPanel {

  /** The panel for configuring the experiment */
  protected SetupModePanel m_SetupPanel;

  /** The panel for running the experiment */
  protected RunPanel m_RunPanel;

  /** The panel for analysing experimental results */
  protected ResultsPanel m_ResultsPanel;

  /** The tabbed pane that controls which sub-pane we are working with */
  protected JTabbedPane m_TabbedPane = new JTabbedPane();

  /** True if the class attribute is the first attribute for all
      datasets involved in this experiment. */
  protected boolean m_ClassFirst = false;

  /**
   * Creates the experiment environment gui with no initial experiment
   */
  public Experimenter(boolean classFirst) {

    m_SetupPanel = new SetupModePanel();
    m_RunPanel = new RunPanel();
    m_ResultsPanel = new ResultsPanel();

    m_ClassFirst = classFirst;

    m_TabbedPane.addTab("Setup", null, m_SetupPanel, "Set up the experiment");
    m_TabbedPane.addTab("Run", null, m_RunPanel, "Run the experiment");
    m_TabbedPane.addTab("Analyse", null, m_ResultsPanel,
			"Analyse experiment results");
    m_TabbedPane.setSelectedIndex(0);
    m_TabbedPane.setEnabledAt(1, false);
    m_SetupPanel.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
	System.err.println("Updated experiment");
	Experiment exp = m_SetupPanel.getExperiment();
	exp.classFirst(m_ClassFirst);
	m_RunPanel.setExperiment(exp);
	m_ResultsPanel.setExperiment(exp);
	m_TabbedPane.setEnabledAt(1, true);
      }
    });
    m_SetupPanel.switchToAdvanced(null);
    setLayout(new BorderLayout());
    add(m_TabbedPane, BorderLayout.CENTER);
  }

  /**
   * Tests out the experiment environment.
   *
   * @param args ignored.
   */
  public static void main(String [] args) {

    try {
      
      boolean classFirst = false;
      if (args.length > 0) {
	classFirst = args[0].equals("CLASS_FIRST");
      }
      InetAddress host = InetAddress.getLocalHost();
      final JFrame jf = new JFrame("Weka Experiment Environment @" + host.getHostName());
      jf.getContentPane().setLayout(new BorderLayout());
      jf.getContentPane().add(new Experimenter(classFirst), BorderLayout.CENTER);
      jf.addWindowListener(new WindowAdapter() {
	public void windowClosing(WindowEvent e) {
	  jf.dispose();
	  System.exit(0);
	}
      });
      jf.pack();
      jf.setSize(800, 640);
      jf.setVisible(true);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.err.println(ex.getMessage());
    }
  }
}

















