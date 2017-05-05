/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    NamedPanel.java

Description:
    A GUI utility class that creates container
    that is surrounded by borders, and that has
    a title on the top-left corner of the border,
    much like:

    --NamedPanel1---------------
   |                            |
   |                            |
   |                            |
    ----------------------------

Last changed:
    28-Aug-1999, TSa.

Changes:
    28-Aug-1999, TSa:
      Netscape has problems with lightweight
      containers, and for this reason unfortunatelyx
      we need to extend Panel instead of Container. :-(

***************************************/

package com.cowtowncoder.fractalmate.gui;

import java.awt.*;

import com.cowtowncoder.fractalmate.ext.PlatformSpecific;

public class NamedPanel
    //extends Container
extends Panel
{
    private static final long serialVersionUID = 1L;

    protected String name;
    protected int fontX, fontY, fontBase, nameWidth;
    protected Color foreground, background, bgDark, bgBright;
    protected Color titleColour, titleShadow;
    
    protected Insets myInsets = null;

    public
    NamedPanel(String nm, Color fg, Color bg)
    {
	super();
	name = nm;
	setName(nm);

	foreground = fg;
	background = bg;

	setBackground(bg);
	setForeground(fg);

	bgDark = bg.darker();
	bgBright = bg.brighter();

	titleColour = new Color((background.getRed() + foreground.getRed())/2,
		   (background.getGreen() + foreground.getGreen())/2,
		   (background.getBlue() + foreground.getBlue())/2
				);
	titleShadow = null;
    }

    public
    NamedPanel(String nm)
    {
	/*this(nm, SystemColor.controlText,
	  SystemColor.control);*/
	this(nm, PlatformSpecific.getSystemColour(SystemColor.controlText),
	     PlatformSpecific.getSystemColour(SystemColor.control));
    }

    public void
    setInsets(Insets i)
    {
	myInsets = i;
    }

    // // // Then some functions defined by Component & Container, that

    @Override
    public void setForeground(Color c)
    {
	super.setForeground(c);
	foreground = c;
    }

    @Override
    public void setBackground(Color c)
    {
	super.setBackground(c);
	background = c;
	bgDark = c.darker();
	bgBright = c.brighter();
    }

    @Override
    public void setName(String s)
    {
	fontX = fontY = fontBase = -1;
	name = s;
    }       

    @Override
    public Insets getInsets()
    {
	if (fontX < 0)
	    initSize();
	if (fontX < 0)
	    return new Insets(0, 0, 0, 0);
	//return new Insets(fontY, fontX, fontX, fontY);
	if (myInsets != null)
	    return new Insets(myInsets.top + fontY, myInsets.left,
			      myInsets.bottom, myInsets.right);
	return new Insets(fontY, fontX / 2, fontY / 2, fontX / 2);
    }

    protected void
    initSize()
    {
	Font f = getFont();
	if (f == null)
	    return;
	FontMetrics fm = getFontMetrics(f);
	if (fm == null)
	    return;
	fontX = fm.getMaxAdvance();
	fontY = fm.getHeight();
	fontBase = fm.getAscent();
	nameWidth = fm.stringWidth(name);
    }

    @Override
    public void paint(Graphics g)
    {
	if (fontX < 0)
	    initSize();

	super.paint(g);

	Dimension d = getSize();

	g.setColor(background);

	int ytop, ybottom, xleft, xright;

	if (myInsets == null) {
	    ytop = fontY;
	    ybottom = fontY / 2;
	    xleft = xright = fontX / 2;
	} else {
	    ytop = myInsets.top + fontY;
	    ybottom = myInsets.bottom;
	    xleft = myInsets.left;
	    xright = myInsets.right;
	}

	// First we'll clear the border area:
	g.fillRect(0, 0, d.width, ytop);
	g.fillRect(0, d.height - ybottom, d.width, ybottom);
	g.fillRect(0, 0, xleft, d.height);
	g.fillRect(d.width - xright, 0, xright, d.height);

	// Then draw the surrounding rectangle:
	g.setColor(bgDark);
	g.drawRect(xleft / 2 + 1, ytop / 2 + 1,
		   d.width - ((xleft + xleft) / 2) - 1,
		   d.height - ((ytop + ybottom) / 2) -1);
	g.setColor(bgBright);
	g.drawRect(xleft / 2, ytop / 2,
		   d.width - ((xleft + xleft) / 2) - 1,
		   d.height - ((ytop + ybottom) / 2) -1);

	// And then we can draw the title, first erasing rectangle under
	// the title...

	g.setColor(background);
	g.drawRect(xleft + fontX / 2, ytop / 2, nameWidth, 1);

	if (titleShadow != null) {
	    g.setColor(titleShadow);
	    g.drawString(name, xleft + (fontX / 2) + 1, fontBase+1);
	}
	g.setColor(titleColour);
	g.drawString(name, xleft + (fontX / 2), fontBase);
    }
}
