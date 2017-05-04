/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    PreviewProps.java

Description:
    A class (window) that allows for changing
    the properties of the preview mode.
    Non-modal, so that it can co-exist
    with the main window...

Last changed:

  22-Aug-1999, TSa:
    Now the user can also limit the time
    taken by drawing the preview figure.x

Changes:

***************************************/

package com.cowtowncoder.fractlet;

import java.awt.*;
import java.awt.event.*;

import com.cowtowncoder.ext.PlatformSpecific;
import com.cowtowncoder.gui.NamedPanel;
import com.cowtowncoder.gui.NumberField;
import com.cowtowncoder.util.TSArray;

public final class PreviewProps
    extends Dialog
    implements ActionListener, AdjustmentListener, ItemListener
{
    Color textColour = PlatformSpecific.getSystemColour(SystemColor.controlText);
    Color backgroundColour = PlatformSpecific.getSystemColour(SystemColor.control);
    Color borderColour = PlatformSpecific.getSystemColour(SystemColor.controlDkShadow);

    Button bOk, bCancel;
    NumberField adjMaxRec;
    Choice chMinLine;
    Choice chMaxTime;

    int initialMaxRec;
    double initialMinLine, initialMaxTime;
    FractletProperties properties;
    Component repaint; // Who to repaint when values change...

    public final static double [] previewTimeLimits = new double [] {
	0.25, 0.5, 1.0, 2.0, 5.0, 10.0
    };
    public final static double [] previewLineLimits = new double [] {
	0.5, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 16.0
    };

    public
    PreviewProps(Frame f, FractletProperties props, Component rep)
    {
	super(f, "Preview mode settings", false);
	setForeground(textColour);
	setBackground(backgroundColour);
	properties = props;
	repaint = rep;

	NamedPanel np = new NamedPanel("Settings");
	np.setLayout(new GridBagLayout());
	add(np,"Center");

	GridBagConstraints gbc = new GridBagConstraints();

	//gbc.anchor = GridBagConstraints.WEST;
	gbc.anchor = GridBagConstraints.NORTHWEST;
	gbc.weightx = 0.1;
	gbc.weighty = 0.1;
	gbc.fill = GridBagConstraints.NONE;
	gbc.gridx = 0;
	gbc.gridy = 0;

	Panel foop = new Panel(new FlowLayout(FlowLayout.LEFT, 5, 0));
	foop.add(new Label("Stop at "));
	int mr = properties.getIntValue(FractletProperties.FP_PREVIEW_MAX_REC);
	adjMaxRec = new NumberField(mr,
				    FractletProperties.MIN_RECURSE_LEVEL,
				    FractletProperties.MAX_RECURSE_LEVEL);
	foop.add(adjMaxRec);
	//setMaxRec(mr);
	foop.add(new Label("levels"));
	np.add(foop, gbc);
	gbc.gridy++;

        foop = new Panel(new FlowLayout(FlowLayout.LEFT, 5, 0));
	foop.add(new Label("Stop at "));
	chMinLine = new Choice();	
	for (int i = 0; i < previewLineLimits.length; i++) {
	    chMinLine.add(""+previewLineLimits[i]+" pt lines");
	}
	foop.add(chMinLine);
	setMinLine(properties.getDoubleValue(FractletProperties.FP_PREVIEW_MIN_LINE));
	np.add(foop, gbc);
	gbc.gridy++;

        foop = new Panel(new FlowLayout(FlowLayout.LEFT, 2, 0));
	foop.add(new Label("Use at most "));
	chMaxTime = new Choice();	
	for (int i = 0; i < previewTimeLimits.length; i++) {
	    chMaxTime.add(""+previewTimeLimits[i]+" sec");
	}
	foop.add(chMaxTime);
	foop.add(new Label("/ level"));
	setMaxTime(properties.getDoubleValue(FractletProperties.FP_PREVIEW_MAX_TIME));
	np.add(foop, gbc);
	gbc.gridy++;

        foop = new Panel();
	bOk = new Button("Ok");
	bOk.addActionListener(this);
	bCancel = new Button("Cancel");
	bCancel.addActionListener(this);
	bOk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	bCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	foop.add(bOk);
	foop.add(bCancel);

	gbc.weighty = 100.0;
	gbc.anchor = GridBagConstraints.SOUTH;
	gbc.gridwidth = 2;
	gbc.fill = GridBagConstraints.NONE;

	np.add(foop, gbc);

	pack();

	if (f != null) {
	    Dimension scr_s = Toolkit.getDefaultToolkit().getScreenSize();
	    Rectangle r = f.getBounds();
	    Dimension d = getSize();
	    
	    int x = r.x + r.width - d.width;
	    int y = r.y;
	    if ((x + d.width) > scr_s.width)
		x = scr_s.width - d.width;
	    if ((y + d.height) > scr_s.height)
		x = scr_s.height - d.height;
	    if (x < 0)
		x = 0;
	    if (y < 0)
		y = 0;
	    setLocation(x, y);
	}

	chMinLine.addItemListener(this);
	adjMaxRec.addAdjustmentListener(this);
	chMaxTime.addItemListener(this);
    }

    public void
    init()
    {
	initialMaxRec = adjMaxRec.getValue();
	initialMinLine = previewLineLimits[chMinLine.getSelectedIndex()];
	initialMaxTime = previewTimeLimits[chMaxTime.getSelectedIndex()];
    }

    /* We need to override this method to draw borders: */
    public void
    paint(Graphics g)
    {
	super.paint(g);
	if (borderColour != null) {
	    Dimension d = getSize();    
	    // should never be null but...
	    if (d != null) {
		g.setColor(borderColour);
		g.drawRect(0, 0, d.width - 1, d.height - 1);
		g.drawRect(2, 2, d.width - 5, d.height - 5);
		g.setColor(Color.black);
		g.drawRect(1, 1, d.width - 3, d.height - 3);
	    }
	}
    }

    public void
    setMinLine(double v)
    {
      chMinLine.select(TSArray.findClosestIndex(previewLineLimits, v));
    }

    public void
    setMaxTime(double v)
    {
      chMaxTime.select(TSArray.findClosestIndex(previewTimeLimits, v));
    }

    public void
    setMaxRec(int x)
    {
	adjMaxRec.setValue(x);
    }

  /*** ActionListener-interface: ***/
    boolean okPressed = false;
    public boolean okPressed() { return okPressed; }

    public void
    actionPerformed(ActionEvent e) 
    {
	Component c = (Component) e.getSource();
	
	if (c == bOk) {
	    okPressed = true;
	    setVisible(false);
	} else if (c == bCancel) {
	    okPressed = false;
	    // We need to restore the initial values...
	    properties.setIntValue(FractletProperties.FP_PREVIEW_MAX_REC,
				   initialMaxRec, this);
	    properties.setDoubleValue(FractletProperties.FP_PREVIEW_MIN_LINE,
				   initialMinLine, this);
	    properties.setDoubleValue(FractletProperties.FP_PREVIEW_MAX_TIME,
				   initialMaxTime, this);
	    setMaxRec(initialMaxRec);
	    setMinLine(initialMinLine);
	    setMaxTime(initialMaxTime);
	    setVisible(false);
	} else {
	    System.err.println("Warning: Unknown action on PreviewProps.");
	}
    }

    private final void updateValues() {
	properties.setIntValue(FractletProperties.FP_PREVIEW_MAX_REC,
			       adjMaxRec.getValue(), this);
	properties.setDoubleValue(FractletProperties.FP_PREVIEW_MIN_LINE,
	   previewLineLimits[chMinLine.getSelectedIndex()], this);
	properties.setDoubleValue(FractletProperties.FP_PREVIEW_MAX_TIME,
	  previewTimeLimits[chMaxTime.getSelectedIndex()], this);
	if (properties.getBooleanValue(FractletProperties.FP_DRAW_PREVIEW))
	    repaint.repaint();
    }

  /*** ItemListener-interface: ***/
    public void itemStateChanged(ItemEvent e) { updateValues(); }

  /*** AdjustmentListener-interface: ***/
    public void adjustmentValueChanged(AdjustmentEvent e) { updateValues(); }
}
