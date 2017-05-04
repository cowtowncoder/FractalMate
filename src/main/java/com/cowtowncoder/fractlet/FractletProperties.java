/*************************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FractletProperties.java

Description:
    This class extends TSProperties class, and
    encapsulates all properties of the fractals
    used by Fractlet.

Last changed:

Changes:

*************************************************/

package com.cowtowncoder.fractlet;

import java.io.*;
import java.util.*;
import java.awt.*;

import com.cowtowncoder.gui.TSProperties;
import com.cowtowncoder.gui.TSProperty;

import com.cowtowncoder.io.LoadedDef;

public class FractletProperties
    extends TSProperties
{
    // Names for saveable variables:

    public final static String FP_NAME = "NAME";
    public final static String FP_AUTHOR = "AUTHOR";
    public final static String FP_COMMENT = "COMMENT";
    public final static String FP_RECURSE_LEVEL = "RECURSE";
    public final static String FP_RECURSE_UNTIL = "REC_UNTIL";
    public final static String FP_COLOUR_SHIFT = "COLOUR_SHIFT";
    public final static String FP_COLOURS_BASED_ON_ANGLE = "COLOURS_BASED_ON_ANGLE";
    public final static String FP_DRAW_INTERMEDIATE = "DRAW_INTERMEDIATE";
    public final static String FP_PREVENT_MULTIDRAW = "PREVENT_MULTIDRAW";

    // Default states & limits for saveable properties:
    public final static int MIN_RECURSE_LEVEL = 0; 
    public final static int MAX_RECURSE_LEVEL = 999;
    public final static int MIN_COLOUR_SHIFT = -360;
    public final static int MAX_COLOUR_SHIFT = 360;

    // Names for non-saveable variables:

    public final static String FP_AUTO_RESIZE = "AUTO_RESIZE";
    public final static String FP_CENTER_ON_LOAD = "CENTER_ON_LOAD";
    public final static String FP_SHOW_POINT_NRS = "SHOW_POINT_NRS";
    public final static String FP_SHOW_LINE_DIRS = "SHOW_LINE_DIRS";
    public final static String FP_DOUBLE_BUFFERING = "DOUBLE_BUFFERING";
    public final static String FP_USE_1ST_LEVEL = "USE_1ST_LEVEL";
    public final static String FP_USE_2NDARY = "USE_2NDARY";

    public final static String FP_DRAW_PREVIEW = "DRAW_PREVIEW";
    public final static String FP_PREVIEW_MAX_REC = "PREVIEW_MAX_REC";
    public final static String FP_PREVIEW_MIN_LINE = "PREVIEW_MIN_LINE";
    public final static String FP_PREVIEW_MAX_TIME = "PREVIEW_MAX_TIME";
    
    // Default states etc for non-saveable properties:
    public final static boolean DEF_USE_1ST_LEVEL = false;
    public final static boolean DEF_USE_2NDARY = false;
    
    // Default lengths for certain field sizes... Should they be here
    // or somewhere else?
    public final static int DEF_LEN_NAME = 30;
    public final static int DEF_LEN_AUTHOR = 30;
    public final static int DEF_LEN_COMMENT = 4 * 30;

    /* Who do we repaint, if need be? */
    Component repaintable = null;

    public
    FractletProperties()
    {
	// Saveable properties:
	int f =  TSProperty.P_SAVEABLE;

	addProperty(FP_NAME, "unnamed", f);
	addProperty(FP_AUTHOR, "unknown", f);
	addProperty(FP_COMMENT, "-", f);
	addProperty(FP_RECURSE_LEVEL, new Integer(99), f);
	addProperty(FP_RECURSE_UNTIL, new Double(1.0), f);
	addProperty(FP_COLOUR_SHIFT, new Integer(0), f);
	addProperty(FP_COLOURS_BASED_ON_ANGLE, new Boolean(true), f);
	addProperty(FP_DRAW_INTERMEDIATE,new Boolean(false),f);
	addProperty(FP_PREVENT_MULTIDRAW, new Boolean(true), f);

	// Non-saveable properties:
	addProperty(FP_AUTO_RESIZE, new Boolean(false), 0);
	addProperty(FP_CENTER_ON_LOAD, new Boolean(true), 0);
	addProperty(FP_SHOW_POINT_NRS, new Boolean(true), 0);
	addProperty(FP_SHOW_LINE_DIRS, new Boolean(true), 0);
		    
	addProperty(FP_DOUBLE_BUFFERING, new Boolean(true), 0);
	addProperty(FP_USE_1ST_LEVEL, new Boolean(false), 0);
	addProperty(FP_USE_2NDARY, new Boolean(false), 0);
	addProperty(FP_DRAW_PREVIEW, new Boolean(false), 0);

	addProperty(FP_PREVIEW_MAX_REC, new Integer(5), 0);
	addProperty(FP_PREVIEW_MIN_LINE, new Double(2.0), 0);
	addProperty(FP_PREVIEW_MAX_TIME, new Double(1.0), 0);
    }

    /*** Saving & loading: ***/

    public void
    saveTo(PrintWriter out)
    {
	Enumeration en = TSProps.keys();

	while (en.hasMoreElements()) {
	    String key = (String) en.nextElement();
	    TSProperty value = (TSProperty) TSProps.get(key);
	    if ((value.properties & TSProperty.P_SAVEABLE) == 0)
		continue;
	    out.print("\t");
	    out.print(key);
	    out.print(" = ");
	    out.print(value.saveValue());
	    out.println(";");
	}
    }

    public boolean
    loadFrom(LoadedDef data)
    {
	if (!data.containsList()) {
	    return false;
	}
	Enumeration en = TSProps.keys();

	while (en.hasMoreElements()) {
	    String key = (String) en.nextElement();
	    TSProperty value = (TSProperty) TSProps.get(key);
	    if ((value.properties & TSProperty.P_SAVEABLE) == 0) {
		continue;
	    }
	    LoadedDef d = data.findAssignmentValueFor(key, false, false);
	    if (d != null) {
		value.loadValue(d);
	    }
	    else System.err.println("Warning: property '"+key+"' not found from save file");
	}	
	return true;
    }

    /*** And some misc functions: ***/

    public void
    setRepaintable(Component c)
    {
	if (repaintable == c)
	    return;

	repaintable = c;
	// A change in certain properties also means we need to do
	// a repaint():

	addListener(FP_SHOW_POINT_NRS, c, TSProperty.LISTEN_REPAINT);
	addListener(FP_SHOW_LINE_DIRS, c, TSProperty.LISTEN_REPAINT);

	addListener(FP_DRAW_PREVIEW, c, TSProperty.LISTEN_REPAINT);
	addListener(FP_PREVIEW_MAX_REC, c, TSProperty.LISTEN_REPAINT);
	addListener(FP_PREVIEW_MIN_LINE, c, TSProperty.LISTEN_REPAINT);
	addListener(FP_PREVIEW_MAX_TIME, c, TSProperty.LISTEN_REPAINT);

	addListener(FP_DRAW_INTERMEDIATE, c, TSProperty.LISTEN_REPAINT);
	addListener(FP_COLOUR_SHIFT, c, TSProperty.LISTEN_REPAINT);
	addListener(FP_COLOURS_BASED_ON_ANGLE, c, TSProperty.LISTEN_REPAINT);
    }
}
