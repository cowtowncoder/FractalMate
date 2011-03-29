/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FractletListener.java

Description:
    This interface defines how Fractlet
    and menu items can interact...

Last changed:

Changes:

***************************************/

package ts.fractlet;

import java.awt.*;
import java.awt.event.*;

public interface
FractletListener
{
    public void doMenuAction(String id, AWTEvent e);
    public void setMenuState(String id, boolean state);
    public boolean getMenuState(String id);
    public void setMenuEnabled(String id, boolean state);
}
