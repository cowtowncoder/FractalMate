/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    MinLabel.java

Description:
    A GUI utility class that extends Label.
    The main addition is the possibility to
    define a certain fixed minimum size
    that is independent of the actual text
    label displays.


Last changed:

Changes:

***************************************/

package ts.gui;

import java.awt.*;

public class
MinLabel
extends Label
{
    protected int minX = -1, minY = -1;
    protected String minText = null;

    public
    MinLabel(String text, String mintext)
    {
	super(text);

	minText = mintext;
    }

    public
    MinLabel(String text, int minx, int miny)
	throws AWTException
    {
	super(text);

	setMinimumSize(minx, miny);
    }

    public void
    setMinimumSize(int minx, int miny)
	throws AWTException
    {
	if (minx < 0 || miny < 0)
	    throw new AWTError("Invalid minimum size specification "+minx+", "+miny+" for MinLabel on constructor.");

	minText = null;
	minX = minx;
	minY = miny;
    }

    public void
    setMinimumSize(String mins)
    {
	minText = mins;
	minX = minY = 0;	
    }

    protected void
    initMinSize()
    {
	Font f = getFont();

	if (f == null)
	    return;

	FontMetrics fm = getFontMetrics(f);

	if (fm == null)
	    return;

	minY = fm.getHeight();
	minX = fm.stringWidth(minText);
    }

    public Dimension getMinimumSize()
    {
	if (minX < 0)
	    initMinSize();
	Dimension d = super.getMinimumSize();

	if (d.width >= minX && d.height >= minY)
	    return d;
	return new Dimension((minX >= d.width) ? minX : d.width,
			     (minY >= d.height) ? minY : d.height);
    }

    public Dimension getPreferredSize()
    {
	if (minX < 0)
	    initMinSize();
	if (minX < 0)
	    initMinSize();
	Dimension d = super.getPreferredSize();

	if (d.width >= minX && d.height >= minY)
	    return d;
	return new Dimension((minX >= d.width) ? minX : d.width,
			     (minY >= d.height) ? minY : d.height);
    }

    public Dimension getMaximumSize()
    {
	if (minX < 0)
	    initMinSize();
	if (minX < 0)
	    initMinSize();
	Dimension d = super.getMaximumSize();

	if (d.width >= minX && d.height >= minY)
	    return d;
	return new Dimension((minX >= d.width) ? minX : d.width,
			     (minY >= d.height) ? minY : d.height);
    }
}
