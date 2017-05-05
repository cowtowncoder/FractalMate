/*************************************************
                                       
Project:
    FractalMate; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    Fractlet.java

Description:
    This class contains the high-level functionality,
    and with FractletCanvas implements most of the
    functionality of the application.

Last changed:
    12-Nov-1999, TSa
  
Changes:

  20-Jul-1999, TSa:
    Apparently Windows-platform requires a button
    to have focus before it's allowed to be pressed.
    Same applies to Choices... A pity, because this
    means that the focus can not be automatically
    moved to the canvas (not always, that is). :-/
  20 - 24 -Jul-1999, TSa:
    Many new features...
  26-Jul-1999, TSa:
    Rotation around selected point(s),
    now uses NumberField for changing recursion
    level count.
  ... - 16 -Aug-1999, TSa:
    Finally got the HTML classes done, so help files
    can now be browsed from Fractlet...
  05-Oct-1999, TSa:
    Created TSProperty & TSProperties - classes, and
    am now converting ad hoc properties to TSProperty
    instances.
  08-Nov-1999, TSa:
    Many small fixes; cursor handling now works on
    Win JDK, component backgrounds work ok again,
    HTML-loading from WindowsFS works etc.
  12-Nov-1999, TSa:
    The Loader and supporting classes moved to
    ts.io - package. Loader also supports more
    versatile and more OO approach, hopefully
    leading to better reusability. Thus, version
    number changed to 1.02.

*************************************************/

package com.cowtowncoder.fractalmate;

import java.awt.*;
import java.applet.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import com.cowtowncoder.fractalmate.ext.PNGSaver;
import com.cowtowncoder.fractalmate.gui.*;
import com.cowtowncoder.fractalmate.io.LoadedDef;
import com.cowtowncoder.fractalmate.util.TSArray;

public final class FractalMate
  extends Applet
  implements
    FocusListener,
    FractletListener,
    ActionListener,
    ItemListener,
    AdjustmentListener,
    TSPListener
{
  private static final long serialVersionUID = 1L;

  FractletCanvas fCanvas;
  Frame frame;
  public boolean isApplet = true;

  /* The GUI-components: */
  Button figurePropButton, figureDefButton,
      drawButton, stopButton, helpButton;
  Button previewPropButton;
  CheckboxGroup cbgView;
  Checkbox cbPatMain, cbPat1stLevel, cbPat2ndary, cbFigure;
  Checkbox cbUse1stLevel, cbUse2ndary;
  Checkbox cbPreview, cbColoursBasedOnAngle;

  /* And the properties connected to GUI-components: */
  FractletProperties properties;

  // Various top-level windows:
  FigureProps figureProps = null; 
  PreviewProps previewProps = null;
  HelpViewer helpViewer = null;

  public ColourBox colourSelector = null; // FractletCanvas needs this...
  Choice paletteChoice, recurseUntilChoice;
  NumberField recurseField, colourShiftField;
  Label recurseDoneField;
  MinLabel nameField; // Only for applications...
  Choice figureChoice; // Only for applets
  Vector appletFigures = null; // - "" -
  Label textCursor1, textCursor2, textSelection1, textSelection2;

  /* And some related information about them: */
  int currentView = -1, currentPalette = -1;
  String myFilename = null; // The filename we have been saved under (if any)
  String myDir = null; // ... in which directory
  String currentDir = null; // And what is our current directory?

  boolean isDrawing = false;

  public final static String SAVE_FILE_SUFFIX =   ".frc";
  public final static String PNG_FILE_SUFFIX =   ".png";
  public final static String JPEG_FILE_SUFFIX =   ".jpg";
  public final static String DEF_SAVE_FILE =   "figure.frc";
  public final static String DEF_PNG_FILE =   "fractlet.png";
  public final static String DEF_JPEG_FILE =   "fractlet.jpg";
  public final static String SAVE_FILE_FILTER =   "*.frc";
  public final static String SAVE_FILE_TAG = "FRACTLET";
  public final static String DEFAULT_HELP_INDEX = "doc/index.html";

  public final static String FRACTLET_VERSION = "V1.02";
  public final static String DEF_TITLE = "Fractlet "+FRACTLET_VERSION;

  public final static String SAVE_TAG_FIGURE = "FIGURE";

  public final static String SAVE_TAG_PALETTE = "PALETTE";
  public final static String SAVE_TAG_BACKGROUND = "BACKGROUND";
  public final static String SAVE_TAG_PATTERN_MAIN = "MAIN_PATTERN";
  public final static String SAVE_TAG_PATTERN_1ST_LEVEL = "FIRST_LEVEL_PATTERN";
  public final static String SAVE_TAG_PATTERN_2NDARY = "SECONDARY_PATTERN";

  public final static String SAVE_TAG_WINDOW = "WINDOW";

  public final static String CHOICE_TITLE_MAIN =   "Main pattern      ";
  public final static String CHOICE_TITLE_FIGURE = "Fractal figure    ";
  public final static String CHOICE_TITLE_2NDARY = "2ndary pattern   ";
  public final static String CHOICE_TITLE_1ST_LEVEL =    "1st-level pattern";

  public final static String MENU_TITLE_USE_1ST_LEVEL = "Use separate 1st-level pattern";
  public final static String MENU_TITLE_USE_2NDARY = "Use secondary pattern";

  public final static int MODE_MAIN = 0;
  public final static int MODE_1ST_LEVEL = 1;
  public final static int MODE_2NDARY = 2;
  public final static int MODE_RESULT = 3;

  public static Color [] [] palettes = new Color [][] {
	{ Color.red, Color.magenta },
	{ Color.red, Color.magenta, Color.white, Color.magenta },
	{ Color.yellow, Color.green, Color.yellow, Color.red },
	{ Color.white, Color.darkGray, Color.white, Color.darkGray },
	{ Color.red, Color.yellow },
	{ Color.white, new Color(192, 208, 255) },
	{ Color.white, new Color(255, 128, 192), Color.white,
	      new Color(192, 128, 255) },
	{ Color.green.darker(), Color.green.brighter() },
	{ Color.green.darker(), Color.green.brighter(),
	        new Color(255, 144, 0), Color.green.brighter() },
	{ Color.white },
    };
  public static Color [] backgrounds = new Color [] {
      Color.black, Color.black, Color.black, Color.black,
      Color.black, Color.black, Color.black, Color.black,
      Color.black, Color.black
  };

  public static String [] paletteNames = new String [] {
	"Penicillin",
	"Pinky",
	"Joker",
	"Grayscale",
	"Fire",
     "Ice",
	"Cold",
	"Jungle",
	"Tree",
	"Custom",
    };
  public final static int DEFAULT_PALETTE = 4;
  public final static int CUSTOM_PALETTE = palettes.length - 1;

  /* Then the menu-definitions: */

  public final static Object [] menuDefs = new Object [] {
      "menu", "file", "File", null,
          "item", "file.new", "New figure", null,
          "item", "file.load", "(L)Load figure", null,
          "item", "file.save", "(S)Save figure", null,
          "", "", "", null,
          "item", "file.save_png", "Export as PNG", null,
          "menu", "file.save_jpeg", "Export as JPEG...", null,
              "item", "file.save_jpeg.1", "Factor 1 (best compression)", null,
              "item", "file.save_jpeg.10", "       10", null,
              "item", "file.save_jpeg.25", "       25", null,
              "item", "file.save_jpeg.50", "       50", null,
              "item", "file.save_jpeg.75", "       75", null,
              "item", "file.save_jpeg.100", "       100 (best quality)", null,

          "", "", "", null,
          "item", "file.exit", "(X)Exit", null,

      "menu", "edit", "Edit", null,
          "item", "edit.undo", "(U)Undo", null,
          "item", "edit.redo", "(R)Redo", null,
          "", "", "", null,
          "item", "edit.delete", "(D)Delete point(s)", null,
          "item", "edit.select_all", "(A)Select all", null,
          "menu", "edit.copy",  "Copy pattern", null,
          "item", "edit.split",  "Split selected line(s)", null,
          "", "", "", null,
               "item", "edit.copy.m_to_f", "Main -> first", null,
               "item", "edit.copy.m_to_s", "Main -> secondary", null,
               "item", "edit.copy.f_to_m", "First -> main", null,
               "item", "edit.copy.s_to_m", "Secondary -> main", null,
               "item", "edit.copy.x_mf", "Main <-> first", null,
               "item", "edit.copy.x_ms", "Main <-> secondary", null,
               "item", "edit.copy.x_fs", "First <-> secondary", null,
          "item", "edit.center", "(C)Center pattern", null,
          "menu", "edit.fit",  "Fit pattern", null,
               "item", "edit.fit.window", "to window", null,
               "item", "edit.fit.preserve", "preserve aspect ratio", null,
          "menu", "edit.align", "Align pattern", null,
               "item", "edit.align.top", "Top", null,
               "item", "edit.align.bottom", "Bottom", null,
               "item", "edit.align.left", "Left", null,
               "item", "edit.align.right", "Right", null,
          "menu", "edit.align_selected", "Align selected", null,
               "item", "edit.align_selected.top", "Top", null,
               "item", "edit.align_selected.bottom", "Bottom", null,
               "item", "edit.align_selected.left", "Left", null,
               "item", "edit.align_selected.right", "Right", null,
               "item", "edit.align_selected.center_vertical",
                           "Vert. center", null,
               "item", "edit.align_selected.center_horizontal",
                           "Horiz. center", null,
      "menu", "options", "Options", null,
          "cb", "options.double_buffering", "Use double-buffering",
                FractletProperties.FP_DOUBLE_BUFFERING,
          "cb", "options.auto_resize", "Auto-resize",
                FractletProperties.FP_AUTO_RESIZE,
          "cb", "options.center_on_load", "Center on load",
                FractletProperties.FP_CENTER_ON_LOAD,
          "cb", "options.show_point_nrs", "Show point numbers",
                FractletProperties.FP_SHOW_POINT_NRS,
         "cb", "options.show_line_dirs", "Show line directions",
                FractletProperties.FP_SHOW_LINE_DIRS,
         "cb", "options.prevent_multi_draw", "Prevent multiple draws",
                FractletProperties.FP_PREVENT_MULTIDRAW,
         "cb", "options.draw_intermediate", "Draw intermediate levels",
                FractletProperties.FP_DRAW_INTERMEDIATE,
      "menu", "help", "Help", null,
         "item", "help.help", "Browse help files", null,
          "", "", "", null,
         "item", "help.about", "About Fractlet", null,
  };
  public Hashtable menuComponents;


  /* And other misc data: */
  public final static double [] recurseUntilLevels = new double [] {
      0.5, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 16.0
  };
  public final static int DEF_RECURSE_UNTIL_INDEX = 1;

  public final static int DEF_MAX_PREVIEW_TIME_INDEX = 2;

  public final static int WAIT_FRAME = 0;
  public final static int WAIT_CONTAINER = 1;
  public final static int WAIT_CANVAS = 2;
  public final static int WAIT_LEVELS = 3;

  public final static void
  main(String [] args)
  {
    FractalMate f = new FractalMate();
    f.isApplet = false;
    f.init();
    f.start();
  }

  public
  FractalMate()
  {
      properties = new FractletProperties();      
  }

  /* Doh... the layout construction is tedious and error-prone with
   * AWT. The resulting code is horrible. :-/
   */
  @Override
  public void init()
  {
      if (!isApplet) {
	  frame = new Frame(DEF_TITLE);
	  frame.addWindowListener(new WindowAdapter() {
		  public void
		      windowClosing(WindowEvent e)
		  {
		      System.exit(0);
		  }
	      });
      }

      setLayout(new BorderLayout());
      fCanvas = new FractletCanvas(this, properties);
      properties.setRepaintable(fCanvas);
      
      if (!isApplet) {
	  	fCanvas.setSize(512, 512);
      }
      add(fCanvas, "Center");
      
      Container main_panel = new SystemPanel();
      add(main_panel, "North");

      main_panel.setLayout(new GridBagLayout());
      
      // First we'll divide the control area to 4 distinct
      // named panels:
      NamedPanel panel_pattern = new NamedPanel("View");
      NamedPanel panel_figure = new NamedPanel("Figure");
      NamedPanel panel_colour = new NamedPanel("Colour");
      NamedPanel panel_drawing = new NamedPanel("Drawing");

      GridBagConstraints main_gb = new GridBagConstraints();
      main_gb.gridy = 0;
      main_gb.gridx = 0;
      main_gb.gridheight = 2;
      main_gb.fill = GridBagConstraints.BOTH;
      main_gb.anchor = GridBagConstraints.EAST;
      main_gb.weighty = 100.0;
      main_gb.weightx = 20.0;

      // Hmmh. Preview-mode should be outside the box:
      Container panel_pattern_main = new SystemPanel(new GridBagLayout());
      main_panel.add(panel_pattern_main, main_gb);

      GridBagConstraints main_gbc2 = new GridBagConstraints();

      main_gbc2.gridx = 0;
      main_gbc2.gridy = 0;
      main_gbc2.weightx = 100.0;
      main_gbc2.weighty = 500.0;
      main_gbc2.fill = GridBagConstraints.BOTH;
      panel_pattern_main.add(panel_pattern, main_gbc2);
      main_gbc2.gridy++;

      cbPreview = new Checkbox("Preview mode", properties.getBooleanValue(FractletProperties.FP_DRAW_PREVIEW));
      cbPreview.addItemListener(this);
      previewPropButton = new Button("...");
      previewPropButton.addActionListener(this);
      Container dummy_panel = new SystemPanel(new FlowLayout(FlowLayout.LEFT,
							     1, 1));
      dummy_panel.add(cbPreview, main_gbc2);      
      dummy_panel.add(previewPropButton, main_gbc2);      
      panel_pattern_main.add(dummy_panel, main_gbc2);

      main_gb.gridx++;
      main_gb.gridheight = 1;
      main_gb.weightx = 60.0;
      main_panel.add(panel_figure, main_gb);
      main_gb.gridy = 1;
      main_panel.add(panel_colour, main_gb);

      main_gb.gridx++;
      main_gb.gridy = 0;
      main_gb.gridheight = 2;
      main_gb.weightx = 20.0;

      // Actually, 3rd panel doesn't cover the whole area,
      // Let's leave draw & stop buttons out:
      Container panel_drawing2 = new SystemPanel(new GridBagLayout());

      main_panel.add(panel_drawing2, main_gb);

      main_gbc2 = new GridBagConstraints();

      main_gbc2.gridx = 0;
      main_gbc2.gridy = 0;
      main_gbc2.weightx = 100.0;
      main_gbc2.weighty = 0.0;
      main_gbc2.fill = GridBagConstraints.BOTH;
      panel_drawing2.add(panel_drawing, main_gbc2);

      main_gbc2.gridx = 0;
      main_gbc2.gridy = 1;
      main_gbc2.weighty = 100.0;
      main_gbc2.fill = GridBagConstraints.HORIZONTAL;
      main_gbc2.anchor = GridBagConstraints.SOUTH;
      main_gbc2.insets = new Insets(2, 0, 2, 0);

      Container dummy2 = new SystemPanel(new GridBagLayout());
      //FlowLayout(FlowLayout.LEFT, 1,1));
      GridBagConstraints dbc = new GridBagConstraints();
      dbc.gridx = 0;
      dbc.gridy = 0;
      dbc.weightx = 0.0;
      dbc.weighty = 1.0;
      dbc.fill = GridBagConstraints.NONE;
      dbc.anchor = GridBagConstraints.SOUTH;
      dbc.insets = new Insets(0, 2, 0, 2);

      drawButton = new Button("Draw!");
      drawButton.addActionListener(this);
      dummy2.add(drawButton, dbc);

      stopButton = new Button("Stop!");
      stopButton.addActionListener(this);
      stopButton.setEnabled(false);
      stopButton.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      dbc.gridx = 1;
      dummy2.add(stopButton, dbc);

      helpButton = new Button("Help");
      helpButton.addActionListener(this);
      dbc.gridx = 3;
      dummy2.add(helpButton, dbc);

      dbc.gridx = 2;
      dbc.fill = GridBagConstraints.VERTICAL;
      dbc.weightx = 100.0;
      dummy2.add(new Canvas(), dbc);

      panel_drawing2.add(dummy2, main_gbc2);      

      // And then we'll create them, first
      // 'Pattern'-panel:

      panel_pattern.setLayout(new GridBagLayout());
      GridBagConstraints pattern_gbc = new GridBagConstraints();

      cbgView = new CheckboxGroup();
      cbPatMain = new Checkbox("Main pattern", cbgView, true);
      cbPatMain.addItemListener(this);
      cbPatMain.addFocusListener(this);
      cbPat1stLevel = new Checkbox("1st level pattern", cbgView, false);
      cbPat1stLevel.setEnabled(false);
      cbPat1stLevel.addItemListener(this);
      cbPat2ndary = new Checkbox("Secondary pattern", cbgView, false);
      cbPat2ndary.setEnabled(false);
      cbPat2ndary.addItemListener(this);
      cbFigure = new Checkbox("Fractal figure", cbgView, false);
      cbFigure.addItemListener(this);

      pattern_gbc.gridy = 0;
      pattern_gbc.gridx = 0;
      pattern_gbc.anchor = GridBagConstraints.NORTHWEST;
      pattern_gbc.fill = GridBagConstraints.NONE;
      pattern_gbc.weighty = 0.0;
      pattern_gbc.weightx = 0.0;

      panel_pattern.add(cbPatMain, pattern_gbc);
      pattern_gbc.gridy++;
      panel_pattern.add(cbPat1stLevel, pattern_gbc);
      pattern_gbc.gridy++;
      panel_pattern.add(cbPat2ndary, pattern_gbc);
      pattern_gbc.gridy++;
      panel_pattern.add(cbFigure, pattern_gbc);      

      pattern_gbc.gridwidth = 2;

      pattern_gbc.gridwidth = 1;
      // This is required for padding it seems:
      // (as checkboxes don't like to align like I want otherwise)
      pattern_gbc.gridy++;
      pattern_gbc.fill = GridBagConstraints.BOTH;
      pattern_gbc.weighty = 100.0;
      panel_pattern.add(new Canvas(), pattern_gbc);

      pattern_gbc.gridy = 1;
      pattern_gbc.gridx = 1;
      pattern_gbc.weightx = 100.0;
      pattern_gbc.weighty = 0.0;
      pattern_gbc.fill = GridBagConstraints.NONE;

      cbUse1stLevel = new Checkbox();
      cbUse1stLevel.setState(properties.getBooleanValue(FractletProperties.FP_USE_1ST_LEVEL));
      cbUse1stLevel.addItemListener(this);
      cbUse2ndary = new Checkbox();
      cbUse2ndary.setState(properties.getBooleanValue(FractletProperties.FP_USE_2NDARY));
      cbUse2ndary.addItemListener(this);
      pattern_gbc.ipadx = -8;
      panel_pattern.add(cbUse1stLevel, pattern_gbc);      
      pattern_gbc.gridy += 1;
      panel_pattern.add(cbUse2ndary, pattern_gbc);      

      currentView = MODE_MAIN;

      // Then 'Figure'-panel:

      panel_figure.setLayout(new GridBagLayout());
      GridBagConstraints figure_gbc = new GridBagConstraints();

      figure_gbc.gridy = 0;
      figure_gbc.gridx = 0;
      figure_gbc.weighty = 0.0;
      figure_gbc.anchor = GridBagConstraints.WEST;
      figure_gbc.ipadx = 0;

      if (isApplet) {
	  figure_gbc.weightx = 100.0;
	  figure_gbc.fill = GridBagConstraints.HORIZONTAL;
	  figureChoice = new Choice();
	  figureChoice.addItemListener(this);
	  figureChoice.add("Basic");
	// Let's load the default figures:
	 Thread t = new ResourceLoader(this);
	 t.setPriority(Thread.NORM_PRIORITY - 1);
	 t.start();
	 panel_figure.add(figureChoice, figure_gbc);
    } else {
	figure_gbc.weightx = 0.0;
	nameField = new MinLabel((String) properties.getValue(FractletProperties.FP_NAME), "123456789012345");
	/*
	nameField.setColumns(15);
	nameField.addTextListener(new TextListener() {
	    public void textValueChanged(TextEvent e) {
		fCanvas.setFigureName(((TextField) e.getSource()).getText(),
				      false);
	    }
	});*/
	properties.addListener(FractletProperties.FP_NAME, nameField,
			       TSProperty.LISTEN_SYNC_VALUE);
	figure_gbc.fill = GridBagConstraints.HORIZONTAL;
	figure_gbc.anchor = GridBagConstraints.WEST;
	figure_gbc.weightx = 100.0;
	figure_gbc.weighty = 0.0;
	figure_gbc.gridx++;
	panel_figure.add(nameField, figure_gbc);
      }
      figure_gbc.fill = GridBagConstraints.VERTICAL;

      figure_gbc.weightx = 0.0;
      figure_gbc.gridx++;
      
      figurePropButton = new Button("Props");
      figurePropButton.addActionListener(this);
      panel_figure.add(figurePropButton, figure_gbc);

      figure_gbc.gridx++;
      figureDefButton = new Button("Defs");
      figureDefButton.addActionListener(this);
      panel_figure.add(figureDefButton, figure_gbc);


      // Then 'Colour'-panel:

      panel_colour.setLayout(new GridBagLayout());
      GridBagConstraints colour_gbc = new GridBagConstraints();

      colour_gbc.fill = GridBagConstraints.HORIZONTAL;
      colour_gbc.gridy = 0;
      colour_gbc.gridx = 0;
      colour_gbc.anchor = GridBagConstraints.WEST;
      colour_gbc.weighty = 100.0;
      colour_gbc.weightx = 100.0;

      panel_colour.add(new Label("Palette:"), colour_gbc);

      paletteChoice = new Choice();
      for (int i = 0; i < paletteNames.length; i++) {
	  paletteChoice.addItem(paletteNames[i]);
      }
      paletteChoice.addItemListener(this);
      paletteChoice.select(currentPalette = DEFAULT_PALETTE);
      paletteChanged(true); // To initialize the palette...

      colour_gbc.gridx++;
      colour_gbc.fill = GridBagConstraints.NONE;
      panel_colour.add(paletteChoice, colour_gbc);

      // Colours based on angles?
      cbColoursBasedOnAngle = new Checkbox("Colours from line angles",
        properties.getBooleanValue(FractletProperties.FP_COLOURS_BASED_ON_ANGLE));
      properties.addListener(FractletProperties.FP_COLOURS_BASED_ON_ANGLE,
			     cbColoursBasedOnAngle,
			     TSProperty.LISTEN_SYNC_VALUE);
      colour_gbc.gridy += 1;
      colour_gbc.gridx = 0;
      colour_gbc.gridwidth = 2;
      cbColoursBasedOnAngle.addItemListener(this);
      colour_gbc.fill = GridBagConstraints.NONE;
      colour_gbc.anchor = GridBagConstraints.WEST;
      panel_colour.add(cbColoursBasedOnAngle, colour_gbc);

      // Color shifting:
      dummy_panel = new SystemPanel(new FlowLayout(FlowLayout.LEFT, 1, 1));
      dummy_panel.add(new Label("Col.shift:"));

      colourShiftField = new NumberField(properties.getIntValue(FractletProperties.
						     FP_COLOUR_SHIFT), 0, 100);
      // This is just to prevent it from allocating 'too much' space
      // for showing the angle... *grin*
      colourShiftField.setMinimum(FractletProperties.MIN_COLOUR_SHIFT);
      colourShiftField.setMaximum(FractletProperties.MAX_COLOUR_SHIFT);
      colourShiftField.addAdjustmentListener(this);
      dummy_panel.add(colourShiftField);
      dummy_panel.add(new Label("deg/lvl"));

      colour_gbc.gridx = 0;
      colour_gbc.gridwidth = 2;
      colour_gbc.gridy += 1;
      colour_gbc.weighty = 0.0;
      colour_gbc.fill = GridBagConstraints.HORIZONTAL;

      panel_colour.add(dummy_panel, colour_gbc);

    // And finally 'Drawing'-panel:

      panel_drawing.setLayout(new GridBagLayout());
      GridBagConstraints drawing_gbc = new GridBagConstraints();

      drawing_gbc.gridy = 0;
      drawing_gbc.gridx = 0;
      drawing_gbc.weighty = 0.0;
      drawing_gbc.weightx = 100.0;
      drawing_gbc.anchor = GridBagConstraints.WEST;

      dummy_panel = new SystemPanel(new FlowLayout(FlowLayout.LEFT, 1, 1));
      dummy_panel.add(new Label("Steps:"));

      recurseField = new NumberField(properties.getIntValue(
				 FractletProperties.FP_RECURSE_LEVEL),
				     FractletProperties.MIN_RECURSE_LEVEL,
				     FractletProperties.MAX_RECURSE_LEVEL);
      recurseField.addAdjustmentListener(this);
      dummy_panel.add(recurseField);
      drawing_gbc.fill = GridBagConstraints.NONE;
      recurseDoneField = new MinLabel("(- taken)", "(199 taken)");
      dummy_panel.add(recurseDoneField, drawing_gbc);
      panel_drawing.add(dummy_panel, drawing_gbc);
      
      // one more dummy...
      dummy_panel = new SystemPanel(new FlowLayout(FlowLayout.LEFT, 1, 1));
      dummy_panel.add(new Label("Rec. to: "));

      recurseUntilChoice = new Choice();
      for (int i = 0; i < recurseUntilLevels.length; i++) {
	  recurseUntilChoice.add(""+recurseUntilLevels[i]+" pt lines");
      }
      recurseUntilChoice.select(DEF_RECURSE_UNTIL_INDEX);
      recurseUntilChoice.addItemListener(this);
      dummy_panel.add(recurseUntilChoice);

      drawing_gbc.gridy += 1;
      drawing_gbc.fill = GridBagConstraints.HORIZONTAL;

      panel_drawing.add(dummy_panel, drawing_gbc);

    // Then we'll create the status panel:
    Container statusp = new SystemPanel(new GridBagLayout());
    textCursor1 = new Label("Cursor:");
    textCursor2 = new MinLabel("", "999,999 ");
    textSelection1 = new MinLabel("", "Dragging point #99:");
    textSelection2 = new Label("");

    GridBagConstraints stcon = new GridBagConstraints();
    stcon.anchor = GridBagConstraints.WEST;
    stcon.ipadx = 0;
    stcon.ipady = 0;
    stcon.gridy = 0;
    stcon.gridx = 0;
    stcon.weightx = 0.0;
    stcon.fill = GridBagConstraints.NONE;
    stcon.insets = new Insets(1, 2, 2, 1);
    statusp.add(textCursor1, stcon);
    stcon.gridx++;
    stcon.weightx = 0.0;
    statusp.add(textCursor2, stcon);
    stcon.gridx++;
    stcon.weightx = 0.0;
    statusp.add(textSelection1, stcon);
    stcon.gridx++;
    stcon.weightx = 100.0;
    stcon.fill = GridBagConstraints.HORIZONTAL;
    statusp.add(textSelection2, stcon);

    add(statusp, "South");

    if (frame != null) {
	menuComponents = initMenus(frame, menuDefs);

      // Shouldn't be hard-coded I know but:
	setMenuEnabled("edit.undo", false);
	setMenuEnabled("edit.redo", false);

	frame.add(this);
	frame.pack();
	updateFocus();
	frame.setVisible(true);
    }

    // Also, we need to attach few TSProperty - listeners:
    properties.addListener(FractletProperties.FP_RECURSE_LEVEL,
			   this,
			   TSProperty.LISTEN_SYNC_VALUE);
    properties.addListener(FractletProperties.FP_RECURSE_UNTIL,
			   this,
			   TSProperty.LISTEN_SYNC_VALUE);
    properties.addListener(FractletProperties.FP_COLOUR_SHIFT,
			   this,
			   TSProperty.LISTEN_SYNC_VALUE);
    properties.addListener(FractletProperties.FP_DRAW_PREVIEW,
			   this,
			   TSProperty.LISTEN_SYNC_VALUE);
  }

  @Override
  public void start()
  {
    fCanvas.initPatterns();
    fCanvas.repaint();

    fCanvas.initDone();
    updateFocus();

    if (isApplet) {
	Component c;
	while ((c = getParent()) != null) {
	    if (c instanceof Frame) {
		frame = (Frame) c;
		break;
	    }
	}
    }
  }

  // ActionListener

  @Override
  public void actionPerformed(ActionEvent e)
  {
      Component c = (Component) e.getSource();

      if (c == drawButton) {
	  if (!isDrawing) {
	      beginDraw();
	  }
      } else if (c == stopButton) {
	  interruptDraw();
      } else if (c == helpButton) {
	  doMenuAction("help.help", null);
      } else if (c == figureDefButton) {
	  fCanvas.doMenuAction("pattern.defs", null);
      } else if (c == figurePropButton) {
	  if (figureProps == null)
	      figureProps = new FigureProps(frame, properties);
	  figureProps.show();
      } else if (c == previewPropButton) {
	  if (previewProps == null) {
	    previewProps = new PreviewProps(frame, properties, fCanvas);
	    previewProps.init();
	  } else if (!previewProps.isVisible()) {
	     previewProps.init();
	  }
	  previewProps.show();

      } else if (c == colourSelector) {

	  Color bg = colourSelector.getSelectedBg();

	  // Need to reset the previous selection? No, probably not.
	  // Anything may have happened this far, let's just quit. :-)
	  if (bg == null)
	      return;

	  // Now, when ok'ing / applying, we better make sure the
	  // selection is still 'Custom':
	  palettes[CUSTOM_PALETTE] = colourSelector.getSelectedFg();
	  backgrounds[CUSTOM_PALETTE] = bg;
	  fCanvas.paletteChanged(palettes[CUSTOM_PALETTE],
				 backgrounds[CUSTOM_PALETTE]);
	  paletteChoice.select(currentPalette = CUSTOM_PALETTE);
	  fCanvas.repaint();
      }
      updateFocus();
  }

  /**** ItemListener: ****/

  @Override
  public void itemStateChanged(ItemEvent e)
  {
      Component c = (Component) e.getSource();
      int change = e.getStateChange();

      try {

	  if (c == cbUse1stLevel) {
	      set1stLevel((change == ItemEvent.SELECTED), c);
	      return;
	  } else if (c == cbUse2ndary) {
	      set2ndary((change == ItemEvent.SELECTED), c);	  
	      return;
	  } else if (c == cbPreview) {
	      boolean state = (change == ItemEvent.SELECTED);
	      properties.setBooleanValue(FractletProperties.FP_DRAW_PREVIEW,
					 state, c);
	      if (state) {
		  if (!isDrawing)
		      stopButton.setEnabled(true);
	      } else {
		  if (!isDrawing)
		      stopButton.setEnabled(false);
	      }
	  } else if (c == cbColoursBasedOnAngle) {
	      properties.setBooleanValue(FractletProperties.FP_COLOURS_BASED_ON_ANGLE,
				 change == ItemEvent.SELECTED, c);
	  }

	  if (change != ItemEvent.SELECTED)
	      return;

	  if (c == cbPatMain) {
	      setViewTo(MODE_MAIN);
	  } else if (c == cbPat1stLevel) {
	      setViewTo(MODE_1ST_LEVEL);
	  } else if (c == cbPat2ndary) {
	      setViewTo(MODE_2NDARY);
	  } else if (c == cbFigure) {
	      setViewTo(MODE_RESULT);
	  } else if (c == figureChoice) {
	      figureChanged();
	  } else if (c == paletteChoice) {
	      paletteChanged(false);
	      updateFocus();
	  } else if (c == recurseUntilChoice) {
	      int x = recurseUntilChoice.getSelectedIndex();
	      properties.setDoubleValue(FractletProperties.FP_RECURSE_UNTIL,
					recurseUntilLevels[x], c);
	  }

       // Let's try to keep the focus at the canvas so we'll
       // get the keyboard stuff....
      } finally {
	  updateFocus();
      }
  }

  // // AdjustmentListener:
  @Override
  public void adjustmentValueChanged(AdjustmentEvent e)
  {
      Component c = (Component) e.getSource();

      if (c == recurseField) {
	  properties.setIntValue(FractletProperties.FP_RECURSE_LEVEL,
				 e.getValue(), c);
      } else if (c == colourShiftField) {
	  properties.setIntValue(FractletProperties.FP_COLOUR_SHIFT,
				 e.getValue(), c);
      }
  }

  // // TSPListener

  @Override
  public void TSPChanged(String name, Object old_value, Object new_value)
  {
      int i;

      // I _think_ we can use simple 'pointer comparison' here,
      // as constants are always used, not distinct strings.
      // If this turns out to be untrue, we need to use 'equals'
      // instead:
      if (name == FractletProperties.FP_RECURSE_LEVEL) {
	  i = ((Integer) new_value).intValue();
	  recurseField.setValue(i);
      } else if (name == FractletProperties.FP_RECURSE_UNTIL) {
	  double d = ((Double) new_value).doubleValue();
	  // Hmmh. This is not as straight-forward as it should be but:
	  i = TSArray.findClosestIndex(recurseUntilLevels, d);
	  recurseUntilChoice.select(i);
      } else if (name == FractletProperties.FP_DRAW_PREVIEW) {
	  boolean b = ((Boolean) new_value).booleanValue();
	  // This initializes the preview mode limits, if necessary:
	  fCanvas.setPreview(b);
      } else if (name == FractletProperties.FP_COLOUR_SHIFT) {
	  i = ((Integer) new_value).intValue();
	  colourShiftField.setValue(i);
      }
  }

  /******* Other funcs: ***********/
  Integer cursorLock = new Integer(0);
  int [] cursorLevels = new int[WAIT_LEVELS];
  Cursor [] oldCursors = new Cursor[WAIT_LEVELS];

  public void setWaitCursor(int level, int mod)
  {
		Component component = this;

      switch (level) {
      case WAIT_FRAME:
			 if (frame != null) {
				  component = frame;
			 }
			 break;

		case WAIT_CANVAS:
			 component = fCanvas;
      }

      synchronized (cursorLock) {
			 cursorLevels[level] += mod;

			 // Setting the wait cursor?
			 if (mod > 0) {
				  if (oldCursors[level] == null) {
						if ((oldCursors[level] = component.getCursor()) == null) {
							 oldCursors[level] = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
						}
						Cursor c = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
						component.setCursor(c);
				  }
			 } else {
				  if (cursorLevels[level] < 1) {
						if (oldCursors[level] != null) {
							 component.setCursor(oldCursors[level]);
							 oldCursors[level] = null;
						}
				  }
			 }
      }
  }

  public boolean isApplet() { return isApplet; }

  public Hashtable initMenus(Frame f, Object [] defs)
  {
      MenuBar mb = new MenuBar();
      Menu last_menu = null;
      Hashtable comps = new Hashtable();
      for (int i = 0; i < defs.length; i += 4) {
	  String type = (String) defs[i];
	  String id = (String) defs[i+1];
	  String title = (String) defs[i+2];
	  Object opt = defs[i+3];

	  int first_dot = id.indexOf('.');
	  int last_dot = id.lastIndexOf('.');

	  // A separator?
	  if (type == null || type.length() == 0) {
	      last_menu.add(new MenuItem("-"));
	      continue;
	  }

	  if (type.equals("menu")) {
	      Menu m = new Menu(title);
	      comps.put(id, m);
	      // Main-level menu?
	      if (first_dot < 0) {
		  mb.add(m);
	      } else {
		  last_menu =(Menu) comps.get(id.substring(0, last_dot));
		  last_menu.add(m);
	      }
	      // Hard-coded, bit silly:
	      if (id.equals("help"))
		  mb.setHelpMenu(m);
	      continue;
	  }

	  MenuShortcut sc = null;

	  if (title.startsWith("(")) {
	      sc = new MenuShortcut(title.charAt(1));
	      title = title.substring(3);
	  }

	  if (type.equals("item")) {
	      FractletMenuItem m = new FractletMenuItem(title, id, this);
	      if (sc != null)
		  m.setShortcut(sc);
	      comps.put(id, m);
	      last_menu = (Menu) comps.get(id.substring(0, last_dot));
	      last_menu.add(m);
	      continue;
	  }
	  if (type.equals("cb")) {
	      boolean state = false;
	      String id2 = null;
	      if (opt != null) {
		  if (opt instanceof Boolean)
		      state = ((Boolean) opt).booleanValue();
		  else if (opt instanceof String) {
		      id2 = (String) opt;
		  }
	      }
	      FractletCBM m = new FractletCBM(title, id, state, this,
					      properties, id2);
	      if (sc != null)
		  m.setShortcut(sc);
	      comps.put(id, m);
	      last_menu = (Menu) comps.get(id.substring(0, last_dot));
	      last_menu.add(m);
	      continue;
	  }

	  throw (new Error("Error: unknown menu type '"+type+"'."));
      }

      // There are some special menus, unfortunately, that need different
      // linking...
      
      f.setMenuBar(mb);
      return comps;
  }

  public void
  drawingDone()
  {
    isDrawing = false;
    drawButton.setEnabled(true);
    setWaitCursor(WAIT_CANVAS, -1);
    if (!properties.getBooleanValue(FractletProperties.FP_DRAW_PREVIEW)) {
		  stopButton.setEnabled(false);
	 }
  }

  public void
  setViewTo(int v, boolean change_cb)
  {
      setViewTo(v);

      if (change_cb) {
      switch (v) {
      case MODE_MAIN:
	  cbgView.setSelectedCheckbox(cbPatMain);
	  break;
      case MODE_1ST_LEVEL:
	  cbgView.setSelectedCheckbox(cbPat1stLevel);
	  break;
      case MODE_2NDARY:
	  cbgView.setSelectedCheckbox(cbPat2ndary);
	  break;
      case MODE_RESULT:
	  cbgView.setSelectedCheckbox(cbFigure);
	  break;
      }      
      }
  }

  public void
  setViewTo(int v)
  {
      String c;
      boolean m_off = false, m_on = false;
      Checkbox cb;

      switch (v) {
      case MODE_MAIN:

	  m_on = (currentView == MODE_RESULT);
	  c = CHOICE_TITLE_MAIN;
	  cb = cbPatMain;
	  break;

      case MODE_1ST_LEVEL:

	  if (!properties.getBooleanValue(FractletProperties.FP_USE_1ST_LEVEL))
	      return;
	  m_on = (currentView == MODE_RESULT);
	  c = CHOICE_TITLE_1ST_LEVEL;
	  cb = cbPat1stLevel;
	  break;

      case MODE_2NDARY:

	  if (!properties.getBooleanValue(FractletProperties.FP_USE_2NDARY))
	      return;
	  m_on = (currentView == MODE_RESULT);
	  c = CHOICE_TITLE_2NDARY;
	  cb = cbPat2ndary;
	  break;

      case MODE_RESULT:

	  m_off = (currentView != MODE_RESULT);
	  c = CHOICE_TITLE_FIGURE;
	  m_on = false;
	  cb = cbFigure;
	  break;

      default:
	  throw new Error("Unknown view ("+v+") to show.");
      }

      if (m_on || m_off) {
	  setMenuEnabled("edit", m_on);
      }

      currentView = v;
      cbgView.setSelectedCheckbox(cb);
      fCanvas.viewChanged(currentView);
  }

  public void
  paletteChanged(boolean force)
  {
      int sel = paletteChoice.getSelectedIndex();

      if (sel == currentPalette && !force && sel != CUSTOM_PALETTE)
	  return;
      
      int old_sel = currentPalette;

      currentPalette = sel;

      if (sel == CUSTOM_PALETTE) {
	  if (colourSelector == null) {
	      colourSelector = new ColourBox(frame, "Custom palette", false,8);

	      colourSelector.addActionListener(this);
	  }
	  if (!colourSelector.isShowing()) {
	      backgrounds[sel] = backgrounds[old_sel];
	      palettes[sel] = palettes[old_sel];
	      colourSelector.init(backgrounds[old_sel],
				  palettes[old_sel]);
	      colourSelector.show();
	  }
      } else {
	  fCanvas.paletteChanged(palettes[sel], backgrounds[sel]);
      }
      fCanvas.repaint();
  }

  /** These functions can only be called by applets: ***/
  public void
  figureChanged()
  {
      int sel = figureChoice.getSelectedIndex();

      if (sel == 0) {
	    doMenuAction("file.new", null);
      } else {
	  if (appletFigures == null || sel > appletFigures.size())
	      return;
	  try {
	      fCanvas.loadFigure((LoadedDef) appletFigures.elementAt(sel-1));
	  } catch (IOException ie) {
	      // Should we print a window here?
	  }
      }
  }

  public void
  addFigure(LoadedDef defs)
  {
      LoadedDef fig_def = defs.findAssignmentValueFor(FractalMate.SAVE_TAG_FIGURE, false, true);
      
      if (fig_def == null) {
	  return;
      }
      
      LoadedDef auth = fig_def.findAssignmentValueFor(FractletProperties.FP_NAME, false, true);
      if (auth == null || !auth.containsString()) {
	  return;
      }
      String name = auth.getString();

      if (appletFigures == null) {
	  appletFigures = new Vector();
      }
      appletFigures.addElement(defs);
      figureChoice.add(name);
  }

  /*** Then the palette changing: ***/
  public void
  paletteChanged(Color [] cols, Color bg, boolean update_box)
  {
      int i, j;

      // We probably should check out if this is an existing palette?
      for (i = 0; i < (palettes.length - 1); i++) {
	  // (last entry is 'custom', no need to check it...)
	  Color [] pal = palettes[i];
	  if (pal.length != cols.length)
	      continue;
	  for (j = 0; j < pal.length; j++) {
	      Color a = pal[j];
	      Color b = cols[j];

	      if (!a.equals(b))
		  break;
	  }

	  if (j == pal.length)
	      break;
      }

      if (i < (palettes.length - 1)) {
	  paletteChoice.select(i);
	  currentPalette = i;
	  fCanvas.paletteChanged(palettes[i], backgrounds[i]);
      } else {
	  paletteChoice.select(CUSTOM_PALETTE);
	  fCanvas.paletteChanged(cols, bg);
	  currentPalette = CUSTOM_PALETTE;	  
	  palettes[CUSTOM_PALETTE] = cols;
	  backgrounds[CUSTOM_PALETTE] = bg;
      }

      if (update_box && colourSelector != null)
	  colourSelector.init(bg, cols);

  }

  public boolean
  beginDraw()
  {
      // Perhaps we first better warn about possibly extending
      // fractals? (ie. begin->end length smaller than the
      // length of the longest single segment)
      if (fCanvas.extendingFractal()) {
	  MessageBox m = new MessageBox(frame, "Expanding fractal!",
	     new String [] {
		 "",
		 "The fractal seems to expand, ie. the longest line",
		 "segment is longer than the distance between the",
		 "starting and ending points.",
		 "",
		 "Do you want to continue with drawing?",
		 ""
	  }, MessageBox.YES_NO
					
           );
	  
	  m.show();
	  if (!m.okPressed())
	      return false;
      }

      setWaitCursor(WAIT_CANVAS, 1);

      isDrawing = true;
      setViewTo(MODE_RESULT);
      drawButton.setEnabled(false);
      stopButton.setEnabled(true);
      fCanvas.beginDraw(properties.getIntValue(FractletProperties.FP_RECURSE_LEVEL));

      // We won't change cursor back here, but on 'drawingDone()'...

      return true;
  }

  public void
  interruptDraw()
  {
      fCanvas.interruptDraw();
  }

  /*** Implementation of FractletListener: ****/

  @Override
  public void doMenuAction(String id, AWTEvent ev)
  {
      String second;
      int i = id.indexOf('.');
//      Object source = (ev == null) ? null : ev.getSource();

      if (i < 0)
	  second = "";
      else {
	  second = id.substring(i+1);
	  id = id.substring(0, i);
      }

      /****** FILE-menu ****/
      if (id.equals("file")) {
	  FileDialog f;
	  String dir, filename;
	  File file;

	  // Menu-file-new:
	  if (second.equals("new")) {

	      fCanvas.initPatterns();
	      properties.resetAllSaveable(this);
	      set1stLevel(FractletProperties.DEF_USE_1ST_LEVEL, this);
	      set2ndary(FractletProperties.DEF_USE_2NDARY, this);
	      setViewTo(MODE_MAIN, true);
	      paletteChoice.select(currentPalette = DEFAULT_PALETTE);
	      paletteChanged(true);

	  // Menu-file-load:
	  } else if (second.equals("load")) {

	      do {
	      f = new FileDialog(frame, "Load a fractlet figure",
					    FileDialog.LOAD);
	      /*f.setFilenameFilter(SAVE_FILE_FILTER new FilenameFilter() {
		      public boolean accept(File d, String n) {
			  return n.toLowerCase().endsWith(SAVE_FILE_SUFFIX);
		      }
		      }
		      );*/
	      f.setFile(SAVE_FILE_FILTER);
	      if (currentDir != null)
		  f.setDirectory(currentDir);
	      f.show();
	      if ((filename = f.getFile()) == null)
		  break;
	      dir = f.getDirectory();
	      file = new File(dir, filename);
	      if (!file.exists()) {
		  new MessageBox(frame, "Error on load!",
				 new String [] {
				     "",
			      "          File '"+filename+"' does not exist!          ",
				     "",
				 }).show();
		  break;
	      }

	      boolean ok = true;
	      FileReader r = null;
	      
	      if (!file.canRead() || !file.isFile())
		  ok = false;
	      else {
		  try {
		      r = new FileReader(file);
		  } catch (FileNotFoundException e) {
		      ok = false;
		  }
	      }

	      if (!ok) {
		  new MessageBox(frame, "Error on load!",
				 new String [] {
				     "",
			     "          Can't read file '"+filename+"'!          ",
				     "",
				 }).show();
		  break;
	      }

	      // Returns FALSE if we couldn't parse the file!
	      try {
		  if (fCanvas.loadFigure(r)) {
		      myFilename = filename;
		      myDir = dir;
		      currentDir = dir;
		  } else {
		    new MessageBox(frame, "Error on load!",
			     new String [] {
				 "",
				 "File '"+filename+"' does not define a valid fractlet figure.",
				 "",
			     }).show();
		  }
	      } catch (IOException ie) {
		    new MessageBox(frame, "Error on load!",
			     new String [] {
				 "",
				 "  Error when loading a figure from '"+filename+"':  ",
				 ie.getMessage(),
				 ""
			     }).show();
	      }
	      try {
		  r.close();
	      } catch (IOException e) {
		  ; // What can we do?
	      }

	      // Let's select the main view...
	      setViewTo(MODE_MAIN, true);
	      } while (false);

	  // Menu-file-save:
	  } else if (second.equals("save")) {

	      do {
	      f = new FileDialog(frame, "Save the fractlet figure",
					    FileDialog.SAVE);
	      /*f.setFilenameFilter(SAVE_FILE_FILTER /*new FilenameFilter() {
		      public boolean accept(File d, String n) {
			  return n.toLowerCase().endsWith(SAVE_FILE_SUFFIX);
		      }}
		      );*/
	      if (myFilename != null)
		  f.setFile(myFilename);
	      else
		  f.setFile(DEF_SAVE_FILE);
	      if (currentDir != null)
		  f.setDirectory(currentDir);
	      f.show();
	      if ((filename = f.getFile()) == null)
		  break;
	      dir = f.getDirectory();
	      file = new File(dir, filename);
	      // Why does this return FALSE even if we can write to the file???
	      /*
	      if  (!file.canWrite()) {
System.err.println("CAN'T WRITE to '"+dir+"'/'"+filename+"': "+file);
		  new MessageBox(frame, "Error on save!",
				 new String [] {
				     "",
			     "        Can't write to file '"+filename+"'!        ",
				     "",
				 }).show();
		  break;
	      }
	      */

	      /* As is, let's only notify on command-line...*/
	      if (file.exists() && (myFilename == null ||
		  !myFilename.equals(filename))) {
		  MessageBox m = new MessageBox(frame, "Overwrite?",
				 new String [] {
				     "",
			     "    There already exists a figure named '"+filename+"'.   ",
				     "    Do you want to overwrite it?",
				     ""
				 }, MessageBox.YES_NO);
		  m.show();
		  if (!m.okPressed())
		      break;
	      }

	      FileWriter w;
	      try {
		  w = new FileWriter(file);
	      } catch (IOException e) {
		  new MessageBox(frame, "Error on save!",
				 new String [] {
				     "",
			     "        Error when trying to save '"+filename+"':        ",
				     e.toString(),
				     "",
				 }).show();
		  break;
	      }

	      if (fCanvas.saveFigure(w)) {
		  myFilename = filename;
		  myDir = dir;
		  currentDir = dir;
	      }
	      try {
		  w.close();
	      } catch (IOException e) {
		  ; // So... ?
	      }
	      } while (false);

	  // Menu-file-save_png:
	  } else if (second.equals("save_png") ||
		     second.startsWith("save_jpeg")) {
	      
	      boolean png = second.equals("save_png");
	      String type = png ? "PNG" : "JPEG";
	      int qty = 100;

	      // We'll select the quality...
	      if (!png) {
		  try {
		      qty = Integer.parseInt(second.substring(
                          second.lastIndexOf('.')+1));
		  } catch (NumberFormatException ne) {
		      throw new Error("Illegal save-as-jpeg entry '"+second+"'");
		  }
	      }

	      String suffix = png ? PNG_FILE_SUFFIX : JPEG_FILE_SUFFIX;

	      do { // a dummy block:

		  f = new FileDialog(frame, png ? "Save the image as PNG" :
				 "Save the image as JPEG (quality "+qty+"%)",
					    FileDialog.SAVE);
	      /*f.setFilenameFilter(SAVE_FILE_FILTER /*new FilenameFilter() {
		      public boolean accept(File d, String n) {
			  return n.toLowerCase().endsWith(PNG_FILE_SUFFIX);
		      }});*/
	      if (myFilename != null) {
		  String def;
		  i = myFilename.lastIndexOf('.');
		  if (i < 0)
		      def = myFilename + suffix;
		  else {
		      def = myFilename.substring(0, i) + suffix;
		  }
		  f.setFile(def);
	      } else {
		  f.setFile(png ? DEF_PNG_FILE : DEF_JPEG_FILE);
	      }
	      if (currentDir != null)
		  f.setDirectory(currentDir);
	      f.show();
	      if ((filename = f.getFile()) == null)
		  break;
	      dir = f.getDirectory();
	      file = new File(dir, filename);
	      FileOutputStream out;
	      try {
		  out = new FileOutputStream(file);
	      } catch (IOException e) {
		  new MessageBox(frame, "Error on export!",
				 new String [] {
				     "",
	"    Error when trying to export image '"+filename+"'     ",
				     "    as "+(png ? "PNG:" : "JPEG:"),
				     e.toString(),
				     "",
				 }).show();
		  break;
	      }

	      Image im = fCanvas.getImage();

	      if (im == null) {
		  new MessageBox(frame, "Error on export!",
		     new String [] {
			    "",
	        "        No fractal image drawn yet.        ",
				     "",
				 }).show();
		  break;
	      }

	      Object encoder = png ? PNGSaver.getPNGEncoder(im, out) :
		      PNGSaver.getJPEGEncoder(im, out, qty);

	      if (encoder == null) {
		  new MessageBox(frame, "Error on export!",
		     new String [] {
			    "",
	        "   Couldn't export the image; "+(png ? "PNG" : "JPEG")
			    + " encoder not found.",
				     "",
				 }).show();
		  break;
	      }

	      setWaitCursor(WAIT_FRAME, 1);

	      try {
		  if ((png && !PNGSaver.PNGencode(encoder))
		      || (!png && !PNGSaver.JPEGencode(encoder))) {

		      new MessageBox(frame, "Error on export!",
				     new String [] {
					 "",
					 "   "+(png ? "PNG" : "JPEG")+"-encoder was unable to encode the picture.    ",
					 "",
				     }).show();
		  }
	      } catch (Exception e) {
		      new MessageBox(frame, "Error on export!",
				     new String [] {
					 "",
					 "  "+(png ? "PNG" : "JPEG")+"-encoder was unable to encode the picture:  ",
					 e.toString(),
					 "",
				     }).show();
	      }
		  
	      setWaitCursor(WAIT_FRAME, -1);

	      try {
		  out.close();
	      } catch (IOException e) {
		  ; // So... ?
	      }

	      } while (false); // Ends the dummy block
	  // Menu-file-exit:
	  } else if (second.equals("exit")) {
	      System.exit(0);
	  } else {
	      throw new Error("Unknown file-menu '"+second+"'");
	  }

      /****** EDIT-menu ****/
      } else if (id.equals("edit")) {

	  if (second.equals("undo")) {
	      fCanvas.doUndo();
	  } else if (second.equals("redo")) {
	      fCanvas.doRedo();
	  } else if (second.equals("delete")) {
	      fCanvas.deletePoint(-1, null, true);
	  } else if (second.equals("split")) {
	      fCanvas.splitSelectedLine(true);
	  } else if (second.equals("select_all")) {
	      fCanvas.selectAll();
	  } else if (second.equals("center")) {
	      fCanvas.centerPattern();
	  } else if (second.equals("fit.window")) {
	      fCanvas.fitPattern(false);
	  } else if (second.equals("fit.preserve")) {
	      fCanvas.fitPattern(true);
	  } else if (second.startsWith("align.")) {
	      fCanvas.alignPattern(second.substring(6));
	  } else if (second.startsWith("align_selected.")) {
	      fCanvas.alignSelected(second.substring(15));
	  } else if (second.startsWith("copy")) {
	     if (second.equals("copy.m_to_f")) {
		  fCanvas.changePattern(FractletCanvas.PAT_MAIN,
					FractletCanvas.PAT_FIRST, false);
	     } else if (second.equals("copy.m_to_s")) {
		 fCanvas.changePattern(FractletCanvas.PAT_MAIN,
				       FractletCanvas.PAT_SECONDARY, false);
	     } else if (second.equals("copy.f_to_m")) {
		  fCanvas.changePattern(FractletCanvas.PAT_FIRST,
					FractletCanvas.PAT_MAIN, false);
	     } else if (second.equals("copy.s_to_m")) {
		  fCanvas.changePattern(FractletCanvas.PAT_SECONDARY,
					FractletCanvas.PAT_MAIN, false);
	     } else if (second.equals("copy.x_mf")) {
		  fCanvas.changePattern(FractletCanvas.PAT_MAIN,
					FractletCanvas.PAT_FIRST, true);
	     } else if (second.equals("copy.x_ms")) {
		  fCanvas.changePattern(FractletCanvas.PAT_MAIN,
					FractletCanvas.PAT_SECONDARY, true);
	     } else if (second.equals("copy.x_fs")) {
		  fCanvas.changePattern(FractletCanvas.PAT_FIRST,
					FractletCanvas.PAT_SECONDARY, true);
	     } else {
		 throw new Error("Unknown edit-menu '"+second+"'");
	     }
	  } else {
	      throw new Error("Unknown edit-menu '"+second+"'");
	  }

      /****** OPTIONS-menu ****/
      } else if (id.equals("options")) {

	  // At the moment, all options are actually handled directly
	  // by FractletCBM...

      /****** HELP-menu ****/
      } else if (id.equals("help")) {
	  if (second.equals("about")) {
	      new MessageBox(frame, "About Fractlet",
				 new String [] {
"",
"Fractlet "+FRACTLET_VERSION,
"",
"(c) 1999 Tatu Saloranta, tatu.saloranta@iki.fi",
FRACTLET_VERSION+", Last changed 12-Nov-1999, TSa",
"",
"PNG-package by VisualTek, http://www.visualtek.com",
"JPEG-package by James R. Weeks and BioElectroMech,",
"http://www.obrador.com/essentialjpeg/jpeg.htm",
"",
				 }).show();

	      return;
	  } else if (second.equals("help")) {
	      if (helpViewer == null) {
		  setWaitCursor(WAIT_FRAME, 1);

		  helpViewer = new HelpViewer(frame, this,
			  isApplet ? this: null, DEFAULT_HELP_INDEX, -1, -1,
						 false);
		  helpViewer.show();

		  setWaitCursor(WAIT_FRAME, -1);

	      } else {
		  helpViewer.toFront();
	      }
	  } else {
	      throw new Error("Unknown file-menu '"+id+"'");
	  }
      }
  }

  public void helpWindowClosed() {
      helpViewer = null;
  }

  public void
  setMenuState(String id, boolean state)
  {
      if (menuComponents == null)
	  return;

      CheckboxMenuItem cb = (CheckboxMenuItem) menuComponents.get(id);

      if (cb == null) {
	  throw new Error("Unknown menu '"+id+"' to turn "+
			  (state ? "on" : "off")+".");
      }

      cb.setState(state);
  }

  @Override
  public boolean getMenuState(String id)
  {
      if (menuComponents == null)
	  return false;

      CheckboxMenuItem cb = (CheckboxMenuItem) menuComponents.get(id);

      if (cb == null) {
	  throw new Error("Unknown menu '"+id+"' in getMenuState().");
      }
      return cb.getState();
  }

  @Override
  public void setMenuEnabled(String id, boolean state)
  {
      if (menuComponents == null)
	  return;

      MenuItem it = (MenuItem) menuComponents.get(id);

      if (it == null) {
	  throw new Error("Unknown menu '"+id+"' to turn "+
			  (state ? "on" : "off")+".");
      }
      it.setEnabled(state);
  }

  // Not part of the implementation, although could be:

  public void
  setMenuTitle(String id, String title)
  {
      if (menuComponents == null)
	  return;
      MenuItem it = (MenuItem) menuComponents.get(id);
      if (it == null) {
	  throw new Error("Unknown menu '"+id+"'; can't change the title.");
      }

      it.setLabel(title);
  }

  public void
  setMenuTitleAndEnabled(String id, String title, boolean state)
  {
      if (menuComponents == null)
	  return;
      MenuItem it = (MenuItem) menuComponents.get(id);
      if (it == null) {
	  throw new Error("Unknown menu '"+id+"'; can't change the title.");
      }

      it.setLabel(title);
      it.setEnabled(state);
  }

  /*** End of FractletListener-implementation .... */

  public synchronized void
  set1stLevel(boolean state, Object by_whom)
  {
      properties.setBooleanValue(FractletProperties.FP_USE_1ST_LEVEL,
				 state, by_whom);
      if (state) {
	  fCanvas.create1stLevel();
      } else {
	  if (cbgView.getSelectedCheckbox() == cbPat1stLevel)
	      setViewTo(MODE_MAIN, true);
      }
      if (by_whom != cbUse1stLevel)
	  cbUse1stLevel.setState(state);
      cbPat1stLevel.setEnabled(state);
      fCanvas.setMenuEnabled("figure.to_1st_level", state);
      fCanvas.setMenuEnabled("pattern.to_1st_level", state);
  }

  public synchronized void
  set2ndary(boolean state, Object by_whom)
  {
      properties.setBooleanValue(FractletProperties.FP_USE_2NDARY,
				 state, by_whom);
      if (state) {
	  fCanvas.create2ndary();
      } else {
	  if (cbgView.getSelectedCheckbox() == cbPat2ndary) {
	      setViewTo(MODE_MAIN, true);
	  }
      }
      if (by_whom != cbUse2ndary)
	  cbUse2ndary.setState(state);
      cbPat2ndary.setEnabled(state);
      fCanvas.setMenuEnabled("pattern.to_2ndary", state);
      fCanvas.setMenuEnabled("figure.to_2ndary", state);
  }

  public final void setTitle(String s) { setTitle(s); }

  public final void setStatusLine(String crsr, String sel, String sel2)
  {
      if (crsr != null)
	  textCursor2.setText(crsr);
      if (sel != null)
	  textSelection1.setText(sel);
      if (sel2 != null)
	  textSelection2.setText(sel2);
  }

  public final void
  setRecurseDoneLevel(int l)
  {
      recurseDoneField.setText("("+l+" taken)");
  }

  // We'll make sure it's one of the 'legal' values...
  // If not any of them exactly, we'll just select the closest match
  public final void
  setRecurseUntil(double lvl, Object who)
  {
      int i, sel;
      double dist = 100000000.0;
      for (i = sel = 0; i < recurseUntilLevels.length; i++) {
	  double d2 = lvl - recurseUntilLevels[i];
	  d2 = d2 * d2;
	  if (d2 < dist) {
	      sel = i;
	      dist = d2;
	  }
      }
      properties.setDoubleValue(FractletProperties.FP_RECURSE_UNTIL, lvl, who);
      if (who != recurseUntilChoice)
	  recurseUntilChoice.select(sel);
  }

  public final void
  setRecurseUntil(Object who)
  {
      setRecurseUntil(recurseUntilLevels[DEF_RECURSE_UNTIL_INDEX], who);
  }

  public void updateFocus()
  {
      fCanvas.requestFocus();
  }

  // // FocusListener implementation:

  // A kludge, sole purpose to make the damn canvas get the #��&%�%/
  // focus right away, so it can catch the key presses.
  protected boolean focus_init_done = false;

  @Override
  public void focusGained(FocusEvent e)
  {
      if (e.isTemporary())
	  return;
      if (!focus_init_done) {
	  focus_init_done = true;
	  fCanvas.requestFocus();
	  removeFocusListener(this);
      }
  }

  @Override
  public void  focusLost(FocusEvent e) { }

  /** Then various helper functions: **/
  public LoadedDef
  readFigure(Reader r)
      throws IOException
  {
      return fCanvas.readFigure(r, true);
  }     

  public LoadedDef []
  readFigures(Reader r, boolean nice)
  {
      Vector v = new Vector();
      LoadedDef def = null;

      while (true) {
	  try {
//System.err.println("readFigures: about to call readFigure()");
	      def = fCanvas.readFigure(r, false);
	  } catch (Exception e) {
	      break;
	  }
	  if (def == null)
	      break;
	  v.addElement(def);
	  if (nice) {
	      Thread.yield();
	  }
      }

      LoadedDef [] ret = new LoadedDef[v.size()];
      v.copyInto(ret);
      return ret;
  }     
}
