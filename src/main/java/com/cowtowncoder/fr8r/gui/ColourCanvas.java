/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    ColourCanvas.java

Description:
    A simple GUI component which extends
    normal AWT canvas, and paints itself
    in the given colour. Used by
    ts.gui.ColourBox, for example.

Last changed:
  19-Jul-99, TSa

Changes:

***************************************/

package com.cowtowncoder.fr8r.gui;

import java.awt.*;

// Let it be final for now, better optimizings possible...
final class
ColourCanvas
extends Canvas
{
    Color border, border2;
    boolean selected = false;

    Color background;
    float [] hsb;
    float value; // hsb[2] is the same, at least should be...

    /*** Constructors: ***/
    
    public
    ColourCanvas(Color bg)
    {
	this(bg, Color.black);
    }

    public
    ColourCanvas(Color bg, Color b)
    {
	background = bg;
	border = b;
	if (border == null)
	    border2 = null;
	else 
	    border2 = new Color(255 ^ border.getRed(),
			    255 ^ border.getGreen(), 255 ^ border.getBlue());
	hsb = Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), null);
	value = hsb[2];
    }

    /*** Simple set/get - functions: ****/

    // By default, let's accept focus.
    public boolean
    isFocusTraversable()
    {
	return true;
    }


    public void
    setSelected(boolean x)
    {
	selected = x;
	repaint();
    }

    public void
    setColour(Color c, boolean use_value)
    {
	hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
	if (use_value)
	    value = hsb[2];
	else
	    hsb[2] = value;
	background = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
	repaint();
    }

    public void
    setValue(int val)
    {
	float v = (float) val / (float) 255;

	hsb[2] = value = v;
	background = Color.getHSBColor(hsb[0], hsb[1], value);
	repaint();
    }

    public int []
    getColourComponents()
    {
	int [] x = new int[4];
	int rgb = Color.HSBtoRGB(hsb[0], hsb[1], value);

	x[0] = (rgb >> 16) & 255;
	x[1] = (rgb >> 8) & 255;
	x[2] = rgb & 255;
	x[3] = (int) (255.99 * value);

	return x;
    }

    public Color
    getColour()
    {
	return background;
    }

    public float []
    getHSBColour()
    {
	return hsb;
    }

    /*** And then the funcs that define the look: ****/

    public void update(Graphics g) { paint(g); }

    public void
    paint(Graphics g)
    {
	Dimension d = getSize();

	if (border != null) {
	    g.setColor(border);
	    g.drawRect(0, 0, d.width - 1, d.height - 1);
	}

	if (selected) {
	    if (border != null) {
		g.setColor(border2);
		g.drawRect(1, 1, d.width - 3, d.height - 3);
		g.setColor(border);
		g.drawRect(2, 2, d.width - 5, d.height - 5);
	    }
	    g.setColor(background);
	    g.fillRect(3, 3, d.width - 6, d.height - 6);
	} else {
	    g.setColor(background);
	    g.fillRect(1, 1, d.width - 2, d.height - 2);
	}
    }
}

