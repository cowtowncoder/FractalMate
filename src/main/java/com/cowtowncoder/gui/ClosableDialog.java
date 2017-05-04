/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications I've made.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
     ClosableDialog.java

Description:
     A simple utility class that takes care
     of closing the dialog when the user
     presses the platform-specific close
     widget (or menu item).

Last changed:

Changes:

***************************************/

package com.cowtowncoder.gui;

import java.awt.*;
import java.awt.event.*;

public class
ClosableDialog
    extends Dialog
    implements WindowListener
{
    public
    ClosableDialog(Frame parent, String title, boolean modal)
    {
	super(parent, title, modal);

	addWindowListener(this);
    }

    /*** WindowListener - implementation: ***/
    
    // By default we'll just hide the window... Overriding
    // classes are free to do whatever they please.
    public void windowClosing(WindowEvent e)
    {
	setVisible(false);
    }

    // And then dummy implementations for other required
    // functions:
    public void windowOpened(WindowEvent e) { }
    public void windowClosed(WindowEvent e) { }

    public void windowIconified(WindowEvent e) { }
    public void windowDeiconified(WindowEvent e) { }

    public void windowActivated(WindowEvent e) { }
    public void windowDeactivated(WindowEvent e) { }
}
