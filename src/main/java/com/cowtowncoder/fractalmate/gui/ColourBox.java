/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    ColourBox.java

Description:
    A GUI utility class that implements
    a colour chooser window. With it
    the user may specify a palette of
    colours. Originally created for the
    Fractlet - project.

Last changed:
  22-Aug-1999, TSa

Changes:

  22-Aug-1999, TSa:
    Now sends ActionEvents, to indicate
    that either 'Ok', 'Cancel' or 'Apply'
    has been pressed.

***************************************/

package com.cowtowncoder.fractalmate.gui;

import java.awt.*;
import java.awt.event.*;

import java.util.*;

import com.cowtowncoder.fractalmate.ext.PlatformSpecific;

public final class ColourBox extends Dialog
    implements ActionListener, AdjustmentListener,
     MouseListener, MouseMotionListener, FocusListener,
	       WindowListener
{
    private static final long serialVersionUID = 1L;

    protected Button bOk, bCancel, bApply;
    protected Color foreground, background, border;

    protected Label rValue, gValue, bValue, vValue;

  // Colour definitions:
  ColourWheel wheel;
  Scrollbar scrR, scrB, scrG, scrV;
  ColourCanvas canvBg;
  ColourCanvas [] canvFg;
  Checkbox [] fgInUse;
  Color origBg;
  Color [] origFg;

  // Selection(s):
  ColourCanvas selectedColour = null;
  int coloursInUse = 0;
  Color [] finalFg;
  Color finalBg;
  
  // And we need this too:
  Panel selectionPanel;

  // And to generate ActionEvents:
  ActionListener actionListener = null;

  public
  ColourBox(Frame parent, String title, boolean modal, int cols)
  {
      this(parent, title, modal, cols,
	   PlatformSpecific.getSystemColour(SystemColor.controlText),
	   PlatformSpecific.getSystemColour(SystemColor.control),
	   PlatformSpecific.getSystemColour(SystemColor.controlDkShadow)
	   /*SystemColor.controlText,
	   SystemColor.control,
	   SystemColor.controlDkShadow*/
      );
  }

  public void
  init(Color bg, Color [] fg)
  {
    int i;

    origBg = bg;
    origFg = fg;
    coloursInUse = origFg.length;

    for (i = coloursInUse; --i >= 0; ) {
	if (i >= coloursInUse) {
	    fgInUse[i].setState(false);
	    canvFg[i].setColour(Color.white, false);
	} else {
	    fgInUse[i].setState(true);
	    canvFg[i].setColour(origFg[i], true);
	}
    }
    canvBg.setSelected(false);
    canvBg.setColour(bg, true);

    selectCanvas(canvFg[0]);
    selectedColour = canvFg[0];

    repaint();
  }

  @Override
  public void show()
  {
      canvFg[0].requestFocus();
      pack();
      super.show();
  }

  public
  ColourBox(Frame parent, String title, boolean modal, int cols,
	 Color f, Color b, Color br)
  {
    super(parent, title, modal);

    addWindowListener(this);

    foreground = f;
    background = b;
    border = br;

    setBackground(b);
    setForeground(f);
    
    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridheight = 1;
   
    // First we'll put the colour-wheel on the top-left corner:
    wheel = new ColourWheel(background, border, 256, 256, 10, 10,
					this);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 50.0;
    gbc.weighty = 50.0;
    gbc.gridwidth = 1;
    // Ugh. An ugly hack to by-pass linux window manager probs:
    gbc.insets = new Insets(20, 4, 4, 4);
    add(wheel, gbc);

    // Then the RGB+V - selection panel:

    Panel rgbvPanel = new Panel();
    rgbvPanel.setLayout(new GridBagLayout());

    gbc.insets = new Insets(10, 4, 4, 4);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.weightx = 25.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 1;
    Label l = new Label("R");
    rgbvPanel.add(l, gbc);
    l = new Label("G");
    gbc.gridx = 1;
    rgbvPanel.add(l, gbc);
    l = new Label("B");
    gbc.gridx = 2;
    rgbvPanel.add(l, gbc);
    l = new Label("V");
    gbc.gridx = 3;
    rgbvPanel.add(l, gbc);

    gbc.insets = new Insets(4, 4, 4, 10);
    gbc.gridy = 2;
    rValue = new Label("255", Label.CENTER);
    gValue = new Label("255", Label.CENTER);
    bValue = new Label("255", Label.CENTER);
    vValue = new Label("255", Label.CENTER);
    gbc.gridx = 0;
    rgbvPanel.add(rValue, gbc);
    gbc.gridx += 1;
    rgbvPanel.add(gValue, gbc);
    gbc.gridx += 1;
    rgbvPanel.add(bValue, gbc);
    gbc.gridx += 1;
    rgbvPanel.add(vValue, gbc);

    gbc.insets = new Insets(4, 4, 4, 4);
    gbc.gridy = 1;
    gbc.weighty = 99.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.gridx = 0;
    scrR = new Scrollbar(Scrollbar.VERTICAL, 0, 1, 0, 255);
    scrR.addAdjustmentListener(this);
    rgbvPanel.add(scrR, gbc);
    gbc.gridx = 1;
    scrG = new Scrollbar(Scrollbar.VERTICAL, 0, 1, 0, 255);
    scrG.addAdjustmentListener(this);
    rgbvPanel.add(scrG, gbc);
    gbc.gridx = 2;
    scrB = new Scrollbar(Scrollbar.VERTICAL, 0, 1, 0, 255);
    scrB.addAdjustmentListener(this);
    rgbvPanel.add(scrB, gbc);
    gbc.gridx = 3;
    scrV = new Scrollbar(Scrollbar.VERTICAL, 0, 1, 0, 255);
    scrV.addAdjustmentListener(this);
    rgbvPanel.add(scrV, gbc);    

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 50.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 1;

    add(rgbvPanel, gbc);

    // Then the selection/control panel on the bottom

    gbc.insets = new Insets(0, 4, 4, 4);

    selectionPanel = new Panel();

    selectionPanel.setLayout(new GridBagLayout());

    GridBagConstraints gbc2 = new GridBagConstraints();
    gbc2.anchor = GridBagConstraints.CENTER;
    gbc2.fill = GridBagConstraints.BOTH;
    gbc2.weightx = 10.0;
    gbc2.weighty = 10.0;
    gbc2.gridwidth = 1;
    gbc2.gridheight = 1;
    gbc2.ipadx = 0;
    gbc2.ipady = 0;
    gbc2.insets = new Insets(2, 2, 2, 8);

    Label bgl = new Label("Bg");
    gbc2.gridx = 0;
    gbc2.gridy = 0;
    selectionPanel.add(bgl, gbc2);

    canvBg = new ColourCanvas(Color.white);
    canvBg.addFocusListener(this);
    canvBg.addMouseListener(this);
    canvBg.addMouseMotionListener(this);
    canvBg.setSize(32, 32);
    gbc2.gridx = 0;
    gbc2.gridy = 1;
    selectionPanel.add(canvBg, gbc2);

    gbc2.insets = new Insets(2, 2, 2, 2);

    canvFg = new ColourCanvas[cols];
    fgInUse = new Checkbox[cols];

    for (int i = 0; i < cols; i++) {
	gbc2.gridx++;

	fgInUse[i] = new Checkbox("" + (i+1), false);
	gbc2.gridy = 0;
	selectionPanel.add(fgInUse[i], gbc2);

	canvFg[i] = new ColourCanvas(Color.white);
	canvFg[i].addFocusListener(this);
	canvFg[i].addMouseListener(this);
	canvFg[i].addMouseMotionListener(this);
	canvFg[i].setSize(32, 32);
	gbc2.gridy = 1;
	selectionPanel.add(canvFg[i], gbc2);

    }

    gbc.gridy = 2;
    gbc.gridx = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 50.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(selectionPanel, gbc);

    // And finally the OK/CANCEL/APPLY buttons:

    Panel buttonPanel = new Panel();

    // Bottom inset 12, otherwise browsers fail to display buttons...
    // Probably should
    if (PlatformSpecific.addPaddingToBottom())
      gbc.insets = new Insets(0, 4, 16, 4);
    buttonPanel.setLayout(new GridLayout(1, 2));

    bOk = new Button("Ok");
    bOk.addActionListener(this);
    bOk.setActionCommand("ok");
    bCancel = new Button("Cancel");
    bCancel.addActionListener(this);
    bCancel.setActionCommand("cancel");
    bApply = new Button("Apply");
    bApply.addActionListener(this);
    bApply.setActionCommand("apply");
    //bOk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    //bCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    buttonPanel.add(bApply);
    buttonPanel.add(new Label(" "));
    buttonPanel.add(bOk);
    buttonPanel.add(bCancel);

    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 50.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
  
    add(buttonPanel, gbc);

    pack();

    if (parent != null) {
      Dimension scr_s = Toolkit.getDefaultToolkit().getScreenSize();
      Rectangle r = parent.getBounds();
      Dimension d = getSize();

      int x = r.x + (r.width / 2 - d.width / 2);
      int y = r.y + (r.height / 2 - d.height / 2);
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

    //show();
  }

    // WindowListener:
  @Override
  public void windowActivated(WindowEvent e)  { }
  @Override
  public void windowClosed(WindowEvent e)  { }
  @Override
  public void windowClosing(WindowEvent e)  {
      actionPerformed(new ActionEvent(bCancel, ActionEvent.ACTION_PERFORMED,
              bCancel.getActionCommand()));
  }
  @Override
  public void windowDeactivated(WindowEvent e)  { }
  @Override
  public void windowDeiconified(WindowEvent e)  { }
  @Override
  public void windowIconified(WindowEvent e)  { }
  @Override
  public void windowOpened(WindowEvent e) { }

    /**** Support for generating ActionEvents: *****/
    public void processActionEvent(Object src, String cmd) {
	if (actionListener != null) {
	    actionListener.actionPerformed(new
		ActionEvent(src, ActionEvent.ACTION_PERFORMED, cmd));
	}
    }
    public  void addActionListener(ActionListener l) {
	actionListener = AWTEventMulticaster.add(actionListener, l);
    }
    public  void removeActionListener(ActionListener l) {
	actionListener = AWTEventMulticaster.remove(actionListener, l);
    }

    /**** Then the funcs that inform about selections: *****/

    public Color getSelectedBg() { return finalBg; }
    public Color [] getSelectedFg() { return finalFg; }

  // ActionListener-interface:
  @Override
  public void actionPerformed(ActionEvent e) 
  {
    Component c = (Component) e.getSource();

    // When the user OKs the (possible) changes, we need to
    // copy the colour definitions to a vector first...
    if (c == bOk || c == bApply) {
	finalFg = null;
	finalBg = null;
	Vector v = new Vector();
	for (int i = 0; i < fgInUse.length; i++) {
	    if (fgInUse[i].getState()) {
		v.addElement(canvFg[i].getColour());
	    }
	}
	if (v.size() > 0) {
	    finalBg = canvBg.getColour();
	    finalFg = new Color[v.size()];
	    v.copyInto(finalFg);
	}
	if (c == bOk) {
	    setVisible(false);
	}
    } else if (c == bCancel) {
	finalFg = null;
	finalBg = null;
	setVisible(false);
    } else {
	System.err.println("Warning: Unknown action on ColourBox.");
    }

    // Now we better send the action event; our action listeners
    // then need to do appropriate actions based on the type of
    // the action (ok, cancel, apply)
    processActionEvent(this, ((Button) c).getActionCommand());    
  }

  /**** Interaction with the colour wheel: ****/
  public void
  setSelectedColour(double hue, double saturation)
  {
      if (selectedColour == null)
	  return;

      Color c = Color.getHSBColor((float) hue, (float) saturation, (float) 1.0);
      selectedColour.setColour(c, false);
      int [] comps = selectedColour.getColourComponents();
      scrR.setValue(255 - comps[0]);
      rValue.setText("" + comps[0]);
      scrG.setValue(255 - comps[1]);
      gValue.setText("" + comps[1]);
      scrB.setValue(255 - comps[2]);
      bValue.setText("" + comps[2]);
      scrV.setValue(255 - comps[3]);
      vValue.setText("" + comps[3]);
      selectedColour.repaint();
  }

  /*** Something else ***/

  public void
  updateFocus()
  {
    bOk.requestFocus();
  }

  /* We need to override this method to draw borders: */
  @Override
  public void paint(Graphics g)
  {
    super.paint(g);
    if (border != null) {
      Dimension d = getSize();    
      // should never be null but...
      if (d != null) {
	g.setColor(border);
	g.drawRect(0, 0, d.width - 1, d.height - 1);
	g.drawRect(2, 2, d.width - 5, d.height - 5);
	g.setColor(Color.black);
	g.drawRect(1, 1, d.width - 3, d.height - 3);
      }
    }
  }

  /* And this as well (why?): */
  private Insets iBorders = new Insets(6, 8, 4, 8);

  @Override
  public Insets getInsets()
  {
    return iBorders;
  }

  @Override
  public void update(Graphics g)
  {
    paint(g);
  }

    /**** FocusListener - interface: ****/

  // We're just interested in the colour canvas selections...

  void
  selectCanvas(ColourCanvas c)
  {
      if (selectedColour != null) {
	  selectedColour.setSelected(false);
      }
      selectedColour = c;
      selectedColour.setSelected(true);

      // We also need to inform ColourWheel:

      Color col = c.getColour();
      int [] comps = selectedColour.getColourComponents();

      wheel.setColour(col);

      scrR.setValue(255 - comps[0]);
      scrG.setValue(255 - comps[1]);
      scrB.setValue(255 - comps[2]);
      scrV.setValue(255 - comps[3]);

      rValue.setText(""+comps[0]);
      gValue.setText(""+comps[1]);
      bValue.setText(""+comps[2]);
      vValue.setText(""+comps[3]);

  }

  @Override
  public void focusGained(FocusEvent e)
  {
	if (e.isTemporary())
	    return;

	Component c = (Component) e.getSource();

	if (c instanceof ColourCanvas)
	    selectCanvas((ColourCanvas) c);
    }
    
  @Override
  public void focusLost(FocusEvent e) { }

  // AdjustmentListener-interface

  @Override
  public void adjustmentValueChanged(AdjustmentEvent e)
  {
    Component c = (Component) e.getSource();

    if (!(c instanceof Scrollbar))
	return;
    
    if (c == scrV) {
	selectedColour.setValue(255 - scrV.getValue());
    } else {
	int r = 255 - scrR.getValue();
	int g = 255 - scrG.getValue();
	int b = 255 - scrB.getValue();
	Color col = new Color(r, g, b);
	wheel.setColour(col);
	selectedColour.setColour(col, true);
    }

    int [] rgb = selectedColour.getColourComponents();

    // To prevent possible feedback effect, let's only update
    // other components (ie. not the one that caused this event):
    if (c == scrV) {
	scrR.setValue(255 - rgb[0]);
	scrG.setValue(255 - rgb[1]);
	scrB.setValue(255 - rgb[2]);

    } else {
	scrV.setValue(255 - rgb[3]);
    }
    rValue.setText(""+rgb[0]);
    gValue.setText(""+rgb[1]);
    bValue.setText(""+rgb[2]);
    vValue.setText(""+rgb[3]);
  }

  /*** MouseListener-interface: ***/

  @Override
  public void mouseEntered(MouseEvent e) {}
  @Override
  public void mouseExited(MouseEvent e) { }
  @Override
  public void mousePressed(MouseEvent e) { }

  @Override
  public void mouseReleased(MouseEvent e)
  {
      Component c = (Component) e.getSource();
	
      if (!(c instanceof ColourCanvas) || !dragging)
	  return;

      if (dragging) {
	  int x = e.getX();
	  int y = e.getY();

	  Point p = dragFrom.getLocation();
	  x += p.x;
	  y += p.y;

	  Component c2 = dragFrom.getParent().getComponentAt(x, y);
	  dragging = false;
	  setCursor(dragCursor);
	  if (c2 != null && c2 instanceof ColourCanvas) {
	      ColourCanvas c3 = (ColourCanvas) c2;
	      if (c3 != dragFrom) {
		  if (dragXchange) {
		      Color a = dragFrom.getColour();
		      dragFrom.setColour(c3.getColour(), true);
		      c3.setColour(a, true);
		  } else {
		      c3.setColour(dragFrom.getColour(), true);
		  }
	      }
	      selectCanvas(c3);
	  }
      }
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
	Component c = (Component) e.getSource();
	if (c instanceof ColourCanvas) {
	    selectCanvas((ColourCanvas) c);
	}
  }

  /*** MouseMotionListener-interface: ***/

  boolean dragging = false, dragXchange = false;
  Cursor dragCursor;
  ColourCanvas dragFrom;

  @Override
  public void mouseDragged(MouseEvent e)
  {
//      int x = e.getX();
//      int y = e.getY();

	Component c = (Component) e.getSource();

	if (!(c instanceof ColourCanvas)) {
	    return;
	}

	if (!dragging) {
	    dragging = true;
	    dragXchange = ((e.getModifiers() & (InputEvent.SHIFT_MASK))!= 0);
	    dragFrom = (ColourCanvas) c;
	    dragCursor = getCursor();
	    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}
  }

  @Override
  public void mouseMoved(MouseEvent e) { }
}
