/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    SystemPanel.java

Description:
    A trivial extension of the standard
    Container-class... Simply sets the background
    and foreground to default 'control'-colours
    using the system settings, except for those
    platforms that have insanely ugly defaults
    (such as Netscape Communicator, especially
    on Linux).

Last changed:

Changes:

***************************************/

package com.cowtowncoder.gui;

import com.cowtowncoder.ext.PlatformSpecific;

import java.awt.*;

public class SystemPanel
    extends Panel
{
    public
    SystemPanel(LayoutManager l)
    {
	setLayout(l);
	setForeground(PlatformSpecific.getSystemColour(SystemColor.controlText));
	setBackground(PlatformSpecific.getSystemColour(SystemColor.control));
    }

    public
    SystemPanel()
    {
	this(new FlowLayout());
    }
}
