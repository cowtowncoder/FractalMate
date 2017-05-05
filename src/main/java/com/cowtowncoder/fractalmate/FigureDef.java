/**************************************
                                       
Project:
    FractalMate; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FigureDef.java

Description:
    A class (window) that displays the figure
    definition as if saved to a file then
    viewed.

Last changed:
    26-Oct-1999, TSa

Changes:
  26-Oct-1999, TSa:
    Now uses ClosableDialog as the base
    class instead of plain Dialog.

***************************************/

package com.cowtowncoder.fractalmate;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.cowtowncoder.fractalmate.ext.PlatformSpecific;
import com.cowtowncoder.fractalmate.gui.ClosableDialog;

public final class FigureDef
    extends ClosableDialog
    implements ActionListener
{
    Color textColour = PlatformSpecific.getSystemColour(SystemColor.controlText);
    Color backgroundColour = PlatformSpecific.getSystemColour(SystemColor.control);
    Color borderColour = PlatformSpecific.getSystemColour(SystemColor.controlDkShadow);

    Button bOk;
    TextArea textDef;
    String def;

    public
    FigureDef(Frame parent, String title, String fd, int x, int y)
    {
	super(parent, "Figure definition of '"+title+"'", true);
	setForeground(textColour);
	setBackground(backgroundColour);
	setLayout(new BorderLayout());

	def = fd;

	int lines = 1, len = lines;
	int ix = -1, foo;
	int strlen = def.length();

	while (lines < 32 && ix < strlen &&
	       (foo = def.indexOf('\n', ix+1)) >= 0) {
	    lines += 1;
	    if ((foo - ix) > len)
		len = foo - ix;
	    ix = foo;
	}

	// We'll just try to figure out some neat size for the
	// text area, based on nr. of lines & max line length
	// on the first lines...
	if (lines < 8)
	    lines = 8;
	if (len < 20)
	    len = 20;
	else if (len > 128)
	    len = 128;

	textDef = new TextArea(lines, len);
	textDef.setText(def);
	textDef.setEditable(false);

	Panel p = new Panel();
	bOk = new Button("Ok");
	bOk.addActionListener(this);
	bOk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	p.add(bOk);

	add(p, "South");

	// Let's change the order of add's, so Ok-button
	// would get the focus by default...

	add(textDef, "Center");

	// Otherwise, it seems, there's no decent way of forcing
	// certain component to have the focus by default!!!!!
	// (bOk.requestFocus() seems to only work inside overridden
	// setVisible()!!!!)

	// ... then again, doesn't harm trying to:
	bOk.requestFocus();
	pack();

	if (parent != null) {
	    if (x < 0 || y < 0) {
		Dimension scr_s = Toolkit.getDefaultToolkit().getScreenSize();
		Rectangle r = parent.getBounds();
		Dimension d = getSize();
		
		x = r.x + (r.width / 2 - d.width / 2);
		y = r.y + (r.height / 2 - d.height / 2);
		if ((x + d.width) > scr_s.width)
		    x = scr_s.width - d.width;
		if ((y + d.height) > scr_s.height)
		    x = scr_s.height - d.height;
		if (x < 0)
		    x = 0;
		if (y < 0)
		    y = 0;
	    }
	    setLocation(x, y);
	}
    }

    /* Do we need to override this method to draw borders? */
    @Override
    public void paint(Graphics g)
    {
	super.paint(g);
	if (borderColour != null) {
	    Dimension d = getSize();    
	    if (d != null) {
		g.setColor(borderColour);
		g.drawRect(0, 0, d.width - 1, d.height - 1);
		g.drawRect(2, 2, d.width - 5, d.height - 5);
		g.setColor(Color.black);
		g.drawRect(1, 1, d.width - 3, d.height - 3);
	    }
	}
    }

  /*** ActionListener-interface: ***/
    @Override
    public void actionPerformed(ActionEvent e) 
    {
	Component c = (Component) e.getSource();
	
	if (c == bOk) {
	    setVisible(false);
	} else {
	    System.err.println("Warning: Unknown action on FigureDef.");
	}
    }

    // Instead, this seems to work. AWT sucks:
    /*
    public void setVisible(boolean b) {
	if (b) bOk.requestFocus();
	super.setVisible(b);
    }
    */
}

