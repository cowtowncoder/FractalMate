/******************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    TSPListener.java

Description:
    The interface that defines the way various
    objects can indicate they want to be informed
    about the changes to a property value.

Last changed:
    -

Changes:

*******************************************/

package com.cowtowncoder.gui;

public interface TSPListener
{
    public void TSPChanged(String name, Object old_value, Object new_value);
}
