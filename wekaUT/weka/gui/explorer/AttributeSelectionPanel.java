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
 *    AttributeSelectionPanel.java
 *    Copyright (C) 1999 Mark Hall
 *
 */


package weka.gui.explorer;

import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.Attribute;
import weka.core.Utils;
import weka.core.Range;
import weka.core.FastVector;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeTransformer;
import weka.attributeSelection.AttributeSelection;
import weka.filters.Filter;
import weka.gui.Logger;
import weka.gui.TaskLogger;
import weka.gui.SysErrLog;
import weka.gui.GenericObjectEditor;
import weka.gui.PropertyPanel;
import weka.gui.ResultHistoryPanel;
import weka.gui.SetInstancesPanel;
import weka.gui.SaveBuffer;
import weka.gui.FileEditor;
import weka.gui.visualize.VisualizePanel;
import weka.gui.visualize.PlotData2D;

import java.util.Random;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.awt.FlowLayout;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.PrintWriter;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.JFrame;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Point;
import java.awt.Dimension;
import javax.swing.JPopupMenu;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** 
 * This panel allows the user to select and configure an attribute
 * evaluator and a search method, set the
 * attribute of the current dataset to be used as the class, and perform
 * attribute selection using one of two  selection modes (select using all the
 * training data or perform a n-fold cross validation---on each trial
 * selecting features using n-1 folds of the data).
 * The results of attribute selection runs are stored in a results history
 * so that previous results are accessible.
 *
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class AttributeSelectionPanel extends JPanel {

  /** Lets the user configure the attribute evaluator */
  protected GenericObjectEditor m_AttributeEvaluatorEditor =
    new GenericObjectEditor();

  /** Lets the user configure the search method */
  protected GenericObjectEditor m_AttributeSearchEditor = 
    new GenericObjectEditor();

  /** The panel showing the current attribute evaluation method */
  protected PropertyPanel m_AEEPanel = 
    new PropertyPanel(m_AttributeEvaluatorEditor);

  /** The panel showing the current search method */
  protected PropertyPanel m_ASEPanel = 
    new PropertyPanel(m_AttributeSearchEditor);
  
  /** The output area for attribute selection results */
  protected JTextArea m_OutText = new JTextArea(20, 40);

  /** The destination for log/status messages */
  protected Logger m_Log = new SysErrLog();

  /** The buffer saving object for saving output */
  SaveBuffer m_SaveOut = new SaveBuffer(m_Log, this);

  /** A panel controlling results viewing */
  protected ResultHistoryPanel m_History = new ResultHistoryPanel(m_OutText);

  /** Lets the user select the class column */
  protected JComboBox m_ClassCombo = new JComboBox();

  /** Click to set evaluation mode to cross-validation */
  protected JRadioButton m_CVBut = new JRadioButton("Cross-validation");

  /** Click to set test mode to test on training data */
  protected JRadioButton m_TrainBut = 
    new JRadioButton("Use full training set");

  /** Label by where the cv folds are entered */
  protected JLabel m_CVLab = new JLabel("Folds", SwingConstants.RIGHT);

  /** The field where the cv folds are entered */
  protected JTextField m_CVText = new JTextField("10");

  /** Label by where cv random seed is entered */
  protected JLabel m_SeedLab = new JLabel("Seed",SwingConstants.RIGHT);

  /** The field where the seed value is entered */
  protected JTextField m_SeedText = new JTextField("1");

  /**
   * Alters the enabled/disabled status of elements associated with each
   * radio button
   */
  ActionListener m_RadioListener = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      updateRadioLinks();
    }
  };

  /** Click to start running the attribute selector */
  protected JButton m_StartBut = new JButton("Start");

  /** Click to stop a running classifier */
  protected JButton m_StopBut = new JButton("Stop");

 /** Stop the class combo from taking up to much space */
  private Dimension COMBO_SIZE = new Dimension(150, m_StartBut
					       .getPreferredSize().height);

  /** The current visualization object */
  protected VisualizePanel m_CurrentVis = null;
  
  /** The main set of instances we're playing with */
  protected Instances m_Instances;

  /** A thread that attribute selection runs in */
  protected Thread m_RunThread;

  /* Register the property editors we need */
  static {
    java.beans.PropertyEditorManager
      .registerEditor(java.io.File.class,
                      FileEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.core.SelectedTag.class,
		      weka.gui.SelectedTagEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.filters.Filter.class,
		      weka.gui.GenericObjectEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.classifiers.Classifier [].class,
		      weka.gui.GenericArrayEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.classifiers.DistributionClassifier.class,
		      weka.gui.GenericObjectEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.classifiers.Classifier.class,
		      weka.gui.GenericObjectEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.attributeSelection.ASEvaluation.class,
		      weka.gui.GenericObjectEditor.class);
    java.beans.PropertyEditorManager
      .registerEditor(weka.attributeSelection.ASSearch.class,
		      weka.gui.GenericObjectEditor.class);
  }
  
  /**
   * Creates the classifier panel
   */
  public AttributeSelectionPanel() {

    // Connect / configure the components
    m_OutText.setEditable(false);
    m_OutText.setFont(new Font("Monospaced", Font.PLAIN, 12));
    m_OutText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    m_OutText.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
	if ((e.getModifiers() & InputEvent.BUTTON1_MASK)
	    != InputEvent.BUTTON1_MASK) {
	  m_OutText.selectAll();
	}
      }
    });
    m_History.setBorder(BorderFactory.createTitledBorder("Result list (right-click for options)"));
    m_AttributeEvaluatorEditor.setClassType(ASEvaluation.class);
    m_AttributeEvaluatorEditor.setValue(new weka.attributeSelection.
					CfsSubsetEval());
    m_AttributeEvaluatorEditor.
      addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
	updateRadioLinks();
	repaint();
      }
    });

    m_AttributeSearchEditor.setClassType(ASSearch.class);
    m_AttributeSearchEditor.setValue(new weka.attributeSelection.BestFirst());
    m_AttributeSearchEditor.
      addPropertyChangeListener(new PropertyChangeListener() {
	public void propertyChange(PropertyChangeEvent e) {
	  repaint();
	}
      });

    m_ClassCombo.setToolTipText("Select the attribute to use as the class");
    m_TrainBut.setToolTipText("select attributes using the full training "
			      + "dataset");
    m_CVBut.setToolTipText("Perform a n-fold cross-validation");

    m_StartBut.setToolTipText("Starts attribute selection");
    m_StopBut.setToolTipText("Stops a attribute selection task");
    
    m_ClassCombo.setPreferredSize(COMBO_SIZE);
    m_ClassCombo.setMaximumSize(COMBO_SIZE);
    m_ClassCombo.setMinimumSize(COMBO_SIZE);
    
    m_ClassCombo.setEnabled(false);
    m_TrainBut.setSelected(true);
    updateRadioLinks();
    ButtonGroup bg = new ButtonGroup();
    bg.add(m_TrainBut);
    bg.add(m_CVBut);

    m_TrainBut.addActionListener(m_RadioListener);
    m_CVBut.addActionListener(m_RadioListener);

    m_StartBut.setEnabled(false);
    m_StopBut.setEnabled(false);

    m_StartBut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
	startAttributeSelection();
      }
    });
    m_StopBut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
	stopAttributeSelection();
      }
    });
    
    m_History.setHandleRightClicks(false);
    // see if we can popup a menu for the selected result
    m_History.getList().addMouseListener(new MouseAdapter() {
	public void mouseClicked(MouseEvent e) {
	  if ((e.getModifiers() & InputEvent.BUTTON1_MASK)
	      == InputEvent.BUTTON1_MASK) {
	  } else {
	    int index = m_History.getList().locationToIndex(e.getPoint());
	    if (index != -1) {
	      String name = m_History.getNameAtIndex(index);
	      visualize(name, e.getX(), e.getY());
	    } else {
	      visualize(null, e.getX(), e.getY());
	    }
	  }
	}
      });

    // Layout the GUI
    JPanel p1 = new JPanel();
    p1.setBorder(BorderFactory.createCompoundBorder(
		 BorderFactory.createTitledBorder("Attribute Evaluator"),
		 BorderFactory.createEmptyBorder(0, 5, 5, 5)
		 ));
    p1.setLayout(new BorderLayout());
    p1.add(m_AEEPanel, BorderLayout.NORTH);

    JPanel p1_1 = new JPanel();
    p1_1.setBorder(BorderFactory.createCompoundBorder(
		 BorderFactory.createTitledBorder("Search Method"),
		 BorderFactory.createEmptyBorder(0, 5, 5, 5)
		 ));
    p1_1.setLayout(new BorderLayout());
    p1_1.add(m_ASEPanel, BorderLayout.NORTH);

    JPanel p_new = new JPanel();
    p_new.setLayout(new BorderLayout());
    p_new.add(p1, BorderLayout.NORTH);
    p_new.add(p1_1, BorderLayout.CENTER);

    JPanel p2 = new JPanel();
    GridBagLayout gbL = new GridBagLayout();
    p2.setLayout(gbL);
    p2.setBorder(BorderFactory.createCompoundBorder(
		 BorderFactory.createTitledBorder("Attribute Selection Mode"),
		 BorderFactory.createEmptyBorder(0, 5, 5, 5)
		 ));
    GridBagConstraints gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.WEST;
    gbC.gridy = 2;     gbC.gridx = 0;
    gbL.setConstraints(m_TrainBut, gbC);
    p2.add(m_TrainBut);

    gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.WEST;
    gbC.gridy = 4;     gbC.gridx = 0;
    gbL.setConstraints(m_CVBut, gbC);
    p2.add(m_CVBut);

    gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.EAST;
    gbC.fill = GridBagConstraints.HORIZONTAL;
    gbC.gridy = 4;     gbC.gridx = 1;
    gbC.insets = new Insets(2, 10, 2, 10);
    gbL.setConstraints(m_CVLab, gbC);
    p2.add(m_CVLab);

    gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.EAST;
    gbC.fill = GridBagConstraints.HORIZONTAL;
    gbC.gridy = 4;     gbC.gridx = 2;  gbC.weightx = 100;
    gbC.ipadx = 20;
    gbL.setConstraints(m_CVText, gbC);
    p2.add(m_CVText);

    gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.EAST;
    gbC.fill = GridBagConstraints.HORIZONTAL;
    gbC.gridy = 6;     gbC.gridx = 1;
    gbC.insets = new Insets(2, 10, 2, 10);
    gbL.setConstraints(m_SeedLab, gbC);
    p2.add(m_SeedLab);

    gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.EAST;
    gbC.fill = GridBagConstraints.HORIZONTAL;
    gbC.gridy = 6;     gbC.gridx = 2;  gbC.weightx = 100;
    gbC.ipadx = 20;
    gbL.setConstraints(m_SeedText, gbC);
    p2.add(m_SeedText);


    JPanel buttons = new JPanel();
    buttons.setLayout(new GridLayout(2, 2));
    buttons.add(m_ClassCombo);
    m_ClassCombo.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JPanel ssButs = new JPanel();
    ssButs.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    ssButs.setLayout(new GridLayout(1, 2, 5, 5));
    ssButs.add(m_StartBut);
    ssButs.add(m_StopBut);
    buttons.add(ssButs);
    
    JPanel p3 = new JPanel();
    p3.setBorder(BorderFactory.
		 createTitledBorder("Attribute selection output"));
    p3.setLayout(new BorderLayout());
    final JScrollPane js = new JScrollPane(m_OutText);
    p3.add(js, BorderLayout.CENTER);
    js.getViewport().addChangeListener(new ChangeListener() {
      private int lastHeight;
      public void stateChanged(ChangeEvent e) {
	JViewport vp = (JViewport)e.getSource();
	int h = vp.getViewSize().height; 
	if (h != lastHeight) { // i.e. an addition not just a user scrolling
	  lastHeight = h;
	  int x = h - vp.getExtentSize().height;
	  vp.setViewPosition(new Point(0, x));
	}
      }
    });

    JPanel mondo = new JPanel();
    gbL = new GridBagLayout();
    mondo.setLayout(gbL);
    gbC = new GridBagConstraints();
    gbC.fill = GridBagConstraints.HORIZONTAL;
    gbC.gridy = 0;     gbC.gridx = 0; gbC.weightx = 0;
    gbL.setConstraints(p2, gbC);
    mondo.add(p2);
    gbC = new GridBagConstraints();
    gbC.anchor = GridBagConstraints.NORTH;
    gbC.fill = GridBagConstraints.HORIZONTAL;
    gbC.gridy = 1;     gbC.gridx = 0; gbC.weightx = 0;
    gbL.setConstraints(buttons, gbC);
    mondo.add(buttons);
    gbC = new GridBagConstraints();
    gbC.fill = GridBagConstraints.BOTH;
    gbC.gridy = 2;     gbC.gridx = 0; gbC.weightx = 0; gbC.weighty = 100;
    gbL.setConstraints(m_History, gbC);
    mondo.add(m_History);
    gbC = new GridBagConstraints();
    gbC.fill = GridBagConstraints.BOTH;
    gbC.gridy = 0;     gbC.gridx = 1;
    gbC.gridheight = 3;
    gbC.weightx = 100; gbC.weighty = 100;
    gbL.setConstraints(p3, gbC);
    mondo.add(p3);

    setLayout(new BorderLayout());
    add(p_new, BorderLayout.NORTH);
    add(mondo, BorderLayout.CENTER);
  }

  
  /**
   * Updates the enabled status of the input fields and labels.
   */
  protected void updateRadioLinks() {
    m_CVBut.setEnabled(true);
    m_CVText.setEnabled(m_CVBut.isSelected());
    m_CVLab.setEnabled(m_CVBut.isSelected());
    m_SeedText.setEnabled(m_CVBut.isSelected());
    m_SeedLab.setEnabled(m_CVBut.isSelected());
    
    if (m_AttributeEvaluatorEditor.getValue() 
	instanceof AttributeTransformer) {
      m_CVBut.setSelected(false);
      m_CVBut.setEnabled(false);
      m_CVText.setEnabled(false);
      m_CVLab.setEnabled(false);
      m_SeedText.setEnabled(false);
      m_SeedLab.setEnabled(false);
      m_TrainBut.setSelected(true);
    }
  }
  
  /**
   * Sets the Logger to receive informational messages
   *
   * @param newLog the Logger that will now get info messages
   */
  public void setLog(Logger newLog) {

    m_Log = newLog;
  }
  
  /**
   * Tells the panel to use a new set of instances.
   *
   * @param inst a set of Instances
   */
  public void setInstances(Instances inst) {
    
    m_Instances = inst;
    String [] attribNames = new String [m_Instances.numAttributes()];
    for (int i = 0; i < attribNames.length; i++) {
      String type = "";
      switch (m_Instances.attribute(i).type()) {
      case Attribute.NOMINAL:
	type = "(Nom) ";
	break;
      case Attribute.NUMERIC:
	type = "(Num) ";
	break;
      case Attribute.STRING:
	type = "(Str) ";
	break;
      default:
	type = "(???) ";
      }
      String attnm = m_Instances.attribute(i).name();
     
      attribNames[i] = type + attnm;
    }
    m_StartBut.setEnabled(m_RunThread == null);
    m_StopBut.setEnabled(m_RunThread != null);
    m_ClassCombo.setModel(new DefaultComboBoxModel(attribNames));
    m_ClassCombo.setSelectedIndex(attribNames.length - 1);
    m_ClassCombo.setEnabled(true);
  }
  
  /**
   * Starts running the currently configured attribute evaluator and
   * search method. This is run in a separate thread, and will only start if
   * there is no attribute selection  already running. The attribute selection
   * output is sent to the results history panel.
   */
  protected void startAttributeSelection() {

    if (m_RunThread == null) {
      m_StartBut.setEnabled(false);
      m_StopBut.setEnabled(true);
      m_RunThread = new Thread() {
	public void run() {
	  // Copy the current state of things
	  m_Log.statusMessage("Setting up...");
	  Instances inst = new Instances(m_Instances);

	  int testMode = 0;
	  int numFolds = 10;
	  int seed = 1;
	  int classIndex = m_ClassCombo.getSelectedIndex();
	  ASEvaluation evaluator = 
	     (ASEvaluation) m_AttributeEvaluatorEditor.getValue();

	  ASSearch search = (ASSearch) m_AttributeSearchEditor.getValue();

	  StringBuffer outBuff = new StringBuffer();
	  String name = (new SimpleDateFormat("HH:mm:ss - "))
	  .format(new Date());
	  String sname = search.getClass().getName();
	  if (sname.startsWith("weka.attributeSelection.")) {
	    name += sname.substring("weka.attributeSelection.".length());
	  } else {
	    name += sname;
	  }
	  String ename = evaluator.getClass().getName();
	  if (ename.startsWith("weka.attributeSelection.")) {
	    name += (" + "
		     +ename.substring("weka.attributeSelection.".length()));
	  } else {
	    name += (" + "+ename);
	  }
	  try {
	    if (m_CVBut.isSelected()) {
	      testMode = 1;
	      numFolds = Integer.parseInt(m_CVText.getText());
	      seed = Integer.parseInt(m_SeedText.getText());
	      if (numFolds <= 1) {
		throw new Exception("Number of folds must be greater than 1");
	      }
	    } 
	    inst.setClassIndex(classIndex);

	    // Output some header information
	    m_Log.logMessage("Started " + ename);
	    if (m_Log instanceof TaskLogger) {
	      ((TaskLogger)m_Log).taskStarted();
	    }
	    outBuff.append("=== Run information ===\n\n");
	    outBuff.append("Evaluator:    " + ename);
	    if (evaluator instanceof OptionHandler) {
	      String [] o = ((OptionHandler) evaluator).getOptions();
	      outBuff.append(" " + Utils.joinOptions(o));
	    }
	    outBuff.append("\nSearch:       " + sname);
	    if (search instanceof OptionHandler) {
	      String [] o = ((OptionHandler) search).getOptions();
	      outBuff.append(" " + Utils.joinOptions(o));
	    }
	    outBuff.append("\n");
	    outBuff.append("Relation:     " + inst.relationName() + '\n');
	    outBuff.append("Instances:    " + inst.numInstances() + '\n');
	    outBuff.append("Attributes:   " + inst.numAttributes() + '\n');
	    if (inst.numAttributes() < 100) {
	      for (int i = 0; i < inst.numAttributes(); i++) {
		outBuff.append("              " + inst.attribute(i).name()
			       + '\n');
	      }
	    } else {
	      outBuff.append("              [list of attributes omitted]\n");
	    }
	    outBuff.append("Evaluation mode:    ");
	    switch (testMode) {
	      case 0: // select using all training
	      outBuff.append("evaluate on all training data\n");
	      break;
	      case 1: // CV mode
	      outBuff.append("" + numFolds + "-fold cross-validation\n");
	      break;
	    }
	    outBuff.append("\n");
	    m_History.addResult(name, outBuff);
	    m_History.setSingle(name);
	    
	    // Do the feature selection and output the results.
	    m_Log.statusMessage("Doing feature selection...");
	    m_History.updateResult(name);
	    
	    AttributeSelection eval = new AttributeSelection();
	    eval.setEvaluator(evaluator);
	    eval.setSearch(search);
	    eval.setFolds(numFolds);
	    eval.setSeed(seed);
	    if (testMode == 1) {
	      eval.setXval(true);
	    }
	    	    
	    switch (testMode) {
	      case 0: // select using training
	      m_Log.statusMessage("Evaluating on training data...");
	      eval.SelectAttributes(inst);
	      break;

	      case 1: // CV mode
	      m_Log.statusMessage("Randomizing instances...");
	      inst.randomize(new Random(seed));
	      if (inst.attribute(classIndex).isNominal()) {
		m_Log.statusMessage("Stratifying instances...");
		inst.stratify(numFolds);
	      }
	      for (int fold = 0; fold < numFolds;fold++) {
		m_Log.statusMessage("Creating splits for fold "
				    + (fold + 1) + "...");
		Instances train = inst.trainCV(numFolds, fold);
		m_Log.statusMessage("Selecting attributes using all but fold "
				    + (fold + 1) + "...");
		
		eval.selectAttributesCVSplit(train);
	      }
	      break;
	      default:
	      throw new Exception("Test mode not implemented");
	    }

	    if (testMode == 0) {
	      outBuff.append(eval.toResultsString());
	    } else {
	      outBuff.append(eval.CVResultsString());
	    }
	  
	    outBuff.append("\n");
	    m_History.updateResult(name);
	    m_Log.logMessage("Finished " + ename+" "+sname);
	    m_Log.statusMessage("OK");
	  } catch (Exception ex) {
	    m_Log.logMessage(ex.getMessage());
	    m_Log.statusMessage("See error log");
	  } finally {
	    if (evaluator instanceof AttributeTransformer) {
	      m_CurrentVis = new VisualizePanel();
	      try {
		Instances transformed = 
		  ((AttributeTransformer)evaluator).transformedData();

		PlotData2D tempd = new PlotData2D(transformed);
		tempd.setPlotName(name+" ("+transformed.relationName()+")");
		tempd.addInstanceNumberAttribute();
		m_CurrentVis.setLog(m_Log);
		try {
		  m_CurrentVis.addPlot(tempd);
		  m_CurrentVis.setColourIndex(transformed.classIndex()+1);
		} catch (Exception ex) {
		  ex.printStackTrace();
		}
		m_CurrentVis.setName(name+" ("+transformed.relationName()+")");
		FastVector vv = new FastVector();
		vv.addElement(m_CurrentVis);
		m_History.addObject(name, vv);
	      } catch (Exception ex) {
		System.err.println(ex);
		ex.printStackTrace();
	      }
	    }
	    if (isInterrupted()) {
	      m_Log.logMessage("Interrupted " + ename+" "+sname);
	      m_Log.statusMessage("See error log");
	    }
	    m_RunThread = null;
	    m_StartBut.setEnabled(true);
	    m_StopBut.setEnabled(false);
	    if (m_Log instanceof TaskLogger) {
	      ((TaskLogger)m_Log).taskFinished();
	    }
	  }
	}
      };
      m_RunThread.setPriority(Thread.MIN_PRIORITY);
      m_RunThread.start();
    }
  }
  
  /**
   * Stops the currently running attribute selection (if any).
   */
  protected void stopAttributeSelection() {

    if (m_RunThread != null) {
      m_RunThread.interrupt();
      
      // This is deprecated (and theoretically the interrupt should do).
      m_RunThread.stop();
      
    }
  }
  
  /**
   * Save the named buffer to a file.
   * @param name the name of the buffer to be saved.
   */
  protected void saveBuffer(String name) {
    StringBuffer sb = m_History.getNamedBuffer(name);
    if (sb != null) {
      if (m_SaveOut.save(sb)) {
	m_Log.logMessage("Save succesful.");
      }
    }
  }

  /**
   * Popup a visualize panel for viewing transformed data
   * @param sp the VisualizePanel to display
   */
  protected void visualizeTransformedData(VisualizePanel sp) {
    if (sp != null) {
      String plotName = sp.getName();
      final javax.swing.JFrame jf = 
	new javax.swing.JFrame("Weka Attribute Selection Visualize: "
			       +plotName);
      jf.setSize(500,400);
      jf.getContentPane().setLayout(new BorderLayout());
      jf.getContentPane().add(sp, BorderLayout.CENTER);
      jf.addWindowListener(new java.awt.event.WindowAdapter() {
	  public void windowClosing(java.awt.event.WindowEvent e) {
	    jf.dispose();
	  }
	});

      jf.setVisible(true);
    }
  }

  /**
   * Handles constructing a popup menu with visualization options
   * @param name the name of the result history list entry clicked on by
   * the user.
   * @param x the x coordinate for popping up the menu
   * @param y the y coordinate for popping up the menu
   */
  protected void visualize(String name, int x, int y) {
    final String selectedName = name;
    JPopupMenu resultListMenu = new JPopupMenu();

    JMenuItem visMainBuffer = new JMenuItem("View in main window");
    if (selectedName != null) {
      visMainBuffer.addActionListener(new ActionListener() {
	  public void actionPerformed(ActionEvent e) {
	    m_History.setSingle(selectedName);
	  }
	});
    } else {
      visMainBuffer.setEnabled(false);
    }
    resultListMenu.add(visMainBuffer);

    JMenuItem visSepBuffer = new JMenuItem("View in separate window");
    if (selectedName != null) {
    visSepBuffer.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  m_History.openFrame(selectedName);
	}
      });
    } else {
      visSepBuffer.setEnabled(false);
    }
    resultListMenu.add(visSepBuffer);

    JMenuItem saveOutput = new JMenuItem("Save result buffer");
    if (selectedName != null) {
    saveOutput.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  saveBuffer(selectedName);
	}
      });
    } else {
      saveOutput.setEnabled(false);
    }
    resultListMenu.add(saveOutput);
    
    resultListMenu.addSeparator();

    FastVector o = null;
    if (selectedName != null) {
      o = (FastVector)m_History.getNamedObject(selectedName);
    }    

    VisualizePanel temp_vp = null;
     
    if (o != null) {
      for (int i = 0; i < o.size(); i++) {
	Object temp = o.elementAt(i);
	if (temp instanceof VisualizePanel) {
	  temp_vp = (VisualizePanel)temp;
	} 
      }
    }
    
    final VisualizePanel vp = temp_vp;
    
    JMenuItem visTrans = new JMenuItem("Visualize transformed data");
    if (vp != null) {
      visTrans.addActionListener(new ActionListener() {
	  public void actionPerformed(ActionEvent e) {
	    visualizeTransformedData(vp);
	  }
	});
    } else {
      visTrans.setEnabled(false);
    }
    resultListMenu.add(visTrans);
    
    resultListMenu.show(m_History.getList(), x, y);
  }

  /**
   * Tests out the attribute selection panel from the command line.
   *
   * @param args may optionally contain the name of a dataset to load.
   */
  public static void main(String [] args) {

    try {
      final javax.swing.JFrame jf =
	new javax.swing.JFrame("Weka Knowledge Explorer: Select attributes");
      jf.getContentPane().setLayout(new BorderLayout());
      final AttributeSelectionPanel sp = new AttributeSelectionPanel();
      jf.getContentPane().add(sp, BorderLayout.CENTER);
      weka.gui.LogPanel lp = new weka.gui.LogPanel();
      sp.setLog(lp);
      jf.getContentPane().add(lp, BorderLayout.SOUTH);
      jf.addWindowListener(new java.awt.event.WindowAdapter() {
	public void windowClosing(java.awt.event.WindowEvent e) {
	  jf.dispose();
	  System.exit(0);
	}
      });
      jf.pack();
      jf.setVisible(true);
      if (args.length == 1) {
	System.err.println("Loading instances from " + args[0]);
	java.io.Reader r = new java.io.BufferedReader(
			   new java.io.FileReader(args[0]));
	Instances i = new Instances(r);
	sp.setInstances(i);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.err.println(ex.getMessage());
    }
  }
}
