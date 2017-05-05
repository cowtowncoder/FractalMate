/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FractletCBM.java

Description:
    Part of the system with which menus and
    popup menus are dynamically created and
    linked to Fractlet...

Last changed:
    05-Oct-1999, TSa

Changes:
  05-Oct-1999, TSa:
    Now makes use of TSProperties...

***************************************/

package com.cowtowncoder.fractalmate;

import java.awt.*;
import java.awt.event.*;

import com.cowtowncoder.fractalmate.gui.TSProperties;
import com.cowtowncoder.fractalmate.gui.TSProperty;

public class FractletCBM
    extends CheckboxMenuItem
    implements ItemListener
{
    FractletListener parent;
    String id;
    TSProperties properties;
    String propertyId;

    public FractletCBM(String title, String i, boolean state, FractletListener p,
		TSProperties tsp, String pid)
    {
	super(title, (pid == null) ? state : tsp.getBooleanValue(pid));

	id = i;
	parent = p;
	properties = tsp;
	propertyId = pid;
	addItemListener(this);

	if (pid != null) {
	    properties.addListener(pid, this, TSProperty.LISTEN_SYNC_VALUE);
	}
    }

    @Override
    public void itemStateChanged(ItemEvent e)
    {
	parent.doMenuAction(id, e);

	// We also need to update the property we are representing
	// (if any):
	if (propertyId != null) {
	    properties.setValue(propertyId, new Boolean(e.getStateChange() ==
							ItemEvent.SELECTED),
				this);
	}
    }
}
