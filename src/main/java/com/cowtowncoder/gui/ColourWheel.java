/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    ColourWheel.java

Description:
    A GUI component that paints a HSB
    colour wheel in the specified (default)
    size.
    Used by ts.gui.ColourBox, for example.

Last changed:
  19-Jul-99, TSa

Changes:

***************************************/

package ts.gui;

import java.awt.*;
import java.awt.event.*;

import java.util.*;

final class
ColourWheel
extends Canvas
    implements Runnable, MouseListener, MouseMotionListener
{
    Color background, border;
    Graphics wheelG = null;
    Image wheelI = null;
    int width, height, insetX, insetY;
    ColourBox parent;

    int deltaX, deltaY;

    public
    ColourWheel(Color bg, Color b, int x, int y, int ins_x, int ins_y,
		ColourBox p)
    {
	setSize(x + ins_x, y + ins_y);
	background = bg;
	border = b;
	width = x;
	height = y;
	insetX = ins_x;
	insetY = ins_y;
	parent = p;

	deltaX = deltaY = 0;

	addMouseListener(this);
	addMouseMotionListener(this);
    }

    public void
    setColour(Color c)
    {
      float [] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
      // Need to add 1 PI as hsb's angle is 180 degrees 'early':
      double angle = Math.PI * 2.0 * hsb[0] + Math.PI;

      deltaX = (int) ((double) width * hsb[1] / 2.0 * Math.cos(angle));
      deltaY = (int) ((double) height * hsb[1] / 2.0 * Math.sin(angle));

      repaint();
    }

    public void update(Graphics g) { paint(g); }

    public void
    paint(Graphics g)
    {
	if (wheelI == null) {
	    wheelI = createImage(width, height);
	    if (wheelI == null)
		return;
	    wheelG = wheelI.getGraphics();
	    if (wheelG == null) {
		wheelI = null;
		return;
	    }
	    
	    wheelG.setColor(background);
	    wheelG.fillRect(0, 0, width, height);
	    
	    // Let's first draw a simplified color wheel first:
	    for (int i = 5; i < 365; i += 10) {
		float h = ((float) (i - 5)) / 360.0f;
		wheelG.setColor(Color.getHSBColor(h, 1.0f, 1.0f));
		wheelG.fillArc(0, 0, width, height, 180 - i, -11);
		wheelG.setColor(Color.getHSBColor(h, 0.5f, 1.0f));
		wheelG.fillArc(width / 4, height / 4,
			       width / 2, height / 2, 180 - i, -11);
	    }

	    new Thread(this).start();
	}

	Dimension d = getSize();

	// Let's only clear things if need be:
	int x = d.width - width - 2;
	int y = d.height - height - 2;
	if (x > 0) {
	    g.setColor(background);
	    g.fillRect(1, 1, x / 2, d.height - 2);
	    g.fillRect(x / 2 + width, 1, (x - x / 2), d.height);
	}

	if (y > 0) {
	    g.setColor(background);
	    g.fillRect(1, 1, d.width, y / 2);
	    g.fillRect(1, y / 2 + height, d.width, (y - y / 2));
	}

	g.drawImage(wheelI, x / 2, y / 2, this);

	g.setColor(border);
	g.drawRect(0, 0, d.width - 1, d.height - 1);

	x = d.width / 2 + deltaX;
	y = d.height / 2 + deltaY;

	if (x < 0) x = 0;
	else if (x >= d.width) x = d.width - 1;

	if (y < 0) y = 0;
	else if (y >= d.height) y = d.height - 1;

	g.setColor(Color.black);
	g.drawLine(x - 2, y - 2, x + 2, y + 2);
	g.drawLine(x - 2, y + 2, x + 2, y - 2);
    }

    public void
    run()
    {	
	int cx = width / 2;
	int cy = height / 2;
	
	double dcx = (double) cx;
	double dcy = (double) cy;
	
	double px, py, dist, twopi = 2.0 * Math.PI;
	Color c;

	for (int i = width / 2 + 1; --i >= 0; ) {
	    px = ((double) (i - cx)) / dcx;
	    if (i % 16 == 15) {
		paint(getGraphics());
	    }
	    for (int j = 0; j < height; j++) {
		py = ((double) (j - cy)) / dcy;
		
		if ((dist = px * px + py * py) > 1.0)
		    continue;
		
		c = Color.getHSBColor((float)((Math.atan2(py, px)/twopi) + 0.5)
					    ,(float) Math.sqrt(dist),
					    (float) 1.0);
		wheelG.setColor(c);
		wheelG.drawLine(i, j, i, j);
		c = Color.getHSBColor((float)((Math.atan2(py,-px)/twopi) + 0.5)
					    ,(float) Math.sqrt(dist),
					    1.0f);
		wheelG.setColor(c);
		wheelG.drawLine(width - i, j, width - i, j);
	    }
	}

	wheelG.setColor(border);
	wheelG.drawArc(0, 0, width-1, height-1, 0, 360);
	repaint();
    }

    void
    mouseAt(int x, int y)
    {
	Dimension d = getSize();
	int dx = x - (d.width / 2);
	int dy = y - (d.height / 2);
	
	double fx = (double) dx / (double) (width / 2);
	double fy = (double) dy / (double) (height / 2);
	double length = fx * fx + fy * fy;
	
	if (length > 1.0) {

	    length = Math.sqrt(length);
	    fx /= length;
	    fy /= length;

	    dx = (int) ((double) dx / length);
	    dy = (int) ((double) dy / length);

	    length = 1.0;
	}

	if (dx == deltaX && dy == deltaY) {
	    return;
	}

	deltaX = dx;
	deltaY = dy;

	// Hue needs to be from 0.0 to 1.0:
	double hue = (Math.atan2(-fy, -fx) / (2.0 * Math.PI)); // + 0.5;

	parent.setSelectedColour(hue, Math.sqrt(length));
	repaint();
    }

    /*** MouseListener-interface ***/
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }

    public void mousePressed(MouseEvent e) {
	mouseAt(e.getX(), e.getY());
    }

    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) { }

    /*** MouseMotionListener-interface: ***/

    public void
    mouseDragged(MouseEvent e)
    {
	mouseAt(e.getX(), e.getY());
    }

    public void
    mouseMoved(MouseEvent e)
    {
    }

}

