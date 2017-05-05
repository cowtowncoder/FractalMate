/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FractletMenuItem.java

Description:
    Part of the system with which menus and
    popup menus are dynamically created and
    linked to Fractlet...

Last changed:

Changes:

***************************************/

package com.cowtowncoder.fr8r;

import java.awt.*;
import java.awt.event.*;

public class FractletMenuItem
extends MenuItem
    implements ActionListener
{
    FractletListener parent;
    String id = null;

    public
    FractletMenuItem(String title, String i, FractletListener p)
    {
	super(title);
	id = i;
	parent = p;
	addActionListener(this);
    }

    public void
    actionPerformed(ActionEvent e)
    {
	parent.doMenuAction(id, e);
	    
    }
}
