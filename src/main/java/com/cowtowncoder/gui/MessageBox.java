/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    ColourBox.java

Description:
    A simple class that displays various
    information to the user. Usually modal,
    and has to be dismissed by pressing
    OK-button (or possibly Cancel, if
    one is available...)

Last changed:
  19-Jul-1999, TSa

Changes:

***************************************/
				       
package com.cowtowncoder.gui;

import com.cowtowncoder.ext.PlatformSpecific;

import java.awt.*;
import java.awt.event.*;

public final class MessageBox
extends Dialog
{
    public final static int OK = 1;
    public final static int OK_CANCEL = 2;
    public final static int YES_NO = 3;

  private Label lText;
  private Label lText2;
  private Button bOk, bCancel;
  private Color fg, bg, border;
  private Insets iBorders = new Insets(6, 8, 4, 8);

  public
  MessageBox(Frame parent, String title, String [] msg)
  {
      this(parent, title, msg, OK);
  }

  public
  MessageBox(Frame parent, String title, String [] msg, int type)
  {
      this(parent, title, msg,
	   PlatformSpecific.getSystemColour(SystemColor.controlText),
	   PlatformSpecific.getSystemColour(SystemColor.control),
	   PlatformSpecific.getSystemColour(SystemColor.controlDkShadow),
	   type);
  }

  public
  MessageBox(Frame parent, String title, String [] msg,
	 Color f, Color b, Color br)
  {
      this(parent, title, msg, f, b, br, OK);
  }

  public
  MessageBox(Frame parent, String title, String [] msg,
	 Color f, Color b, Color br, int type)
  {
    super(parent, title, true);

    fg = f;
    bg = b;
    border = br;

    setBackground(bg);
    setForeground(fg);
    
    setLayout(new GridBagLayout());
    GridBagConstraints gbc;

    for (int i = 0; i < msg.length; i++) {
      Label l = new Label(msg[i]);
      l.setBackground(bg);
      l.setForeground(fg);
      gbc = new GridBagConstraints();
      gbc.gridwidth = 2;
      gbc.gridx = 0;
      gbc.gridy = i;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.weightx = 100.0;
      gbc.weighty = 1.0;
      add(l, gbc);
    }

    if (type == YES_NO)
	bOk = new Button("Yes");
    else
	bOk = new Button("Ok");
    bOk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
	  okPressed = true;
	setVisible(false);
      }
    });
    bOk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridx = 0;
    gbc.gridy = msg.length;
    gbc.weighty = 50.0;

    // Browsers seem to need this:
    if (PlatformSpecific.addPaddingToBottom())
	gbc.insets = new Insets(0, 4, 16, 4);

    if (type != OK) {
	if (type == YES_NO)
	    bCancel = new Button("No");
	else
	    bCancel = new Button("Cancel");
	bCancel.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    okPressed = false;
		    setVisible(false);
		}
	    });
	
	bCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

	gbc.gridwidth = 1;
	gbc.weightx = 50.0;

	gbc.gridx = 0;
	gbc.anchor = GridBagConstraints.SOUTHEAST;
	add(bOk, gbc);

	gbc.gridx = 1;
	gbc.anchor = GridBagConstraints.SOUTHWEST;
	add(bCancel, gbc);
    } else {
	gbc.weightx = 100.0;
	gbc.gridwidth = 2;
	gbc.anchor = GridBagConstraints.SOUTH;
	add(bOk, gbc);
    }

    pack();

    //      setSize(200, 100);
    /*    Dimension scr_s = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension my_size = getSize();

    setLocation(scr_s.width / 2 - my_size.width / 2,
		scr_s.height / 2 - my_size.height / 2);
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    */

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

    updateFocus();
    //show();
  }

  boolean okPressed = false;

  public boolean okPressed() { return okPressed; }

  public void
  updateFocus()
  {
    bOk.requestFocus();
  }

  /* We need to override this method to draw borders: */
  public void
  paint(Graphics g)
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

  /* And this as well: */
  public Insets
  getInsets()
  {
    return iBorders;
  }

  public void
  update(Graphics g)
  {
    paint(g);
  }

  public void
  setTexts(String a, String b)
  {
    if (a == null)
      lText.setText("");
    else lText.setText(a);
    
    if (b == null)
      lText2.setText("");
    else lText2.setText(b);
    
    repaint();
  }

  // This function can be used if the window is not modal...
  public void
  waitForOk(String title)
  {
    bOk.setLabel(title);
    repaint();
    while (true) {
      try { Thread.sleep(100); } catch (InterruptedException e) {}
    }
  }

  public void
  hideButton()
  {
    bOk.setVisible(false);
  }
}
