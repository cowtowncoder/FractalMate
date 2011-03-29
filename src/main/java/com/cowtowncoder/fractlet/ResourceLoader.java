/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    Loader.java

Description:
    This class handles reading in of the
    figure definitions. The actual parsing
    is not done here; this should mostly
    be 'format-independent' module.

Last changed:
    16-Jul-99, TSa

Changes:

16-Jul-99, TSa:
    Great. Netscape (4.6) at least throws a
    SecurityException if we attempt to
    load any resource(s) whatsoever. So,
    we seem to have to load stuff through
    http as the alternative. :-/
30-Jul-99, TSa:
    Even worse. NS Communicator, on WinX
    has a buggy JIT, which among other things:

    a) Threw a (incorrect) verifier error
       about a perfectly valid initialization
       on drawFractal2() (on FractletCanvas)...
       I could code my way around it by making
       unnecessary extra initializations but
       this should not be necessary
    b) Its JIT died on ResourceLoader
       (actually, Loader.loadContents()).
       Thus, it seems we need to explicitly
       turn the crappy JIT off during the
       loading process.... And this can NOT
       BE DONE FROM APPLETS!!! Thanks a bunch
       Symantec and NS. :-p

***************************************/

package ts.fractlet;

import ts.ext.PlatformSpecific;

import ts.io.LoadedDef;

import java.io.*;
import java.net.*;
import java.util.*;

public class
ResourceLoader
    extends Thread
{
    Fractlet parent;

    public
    ResourceLoader(Fractlet p)
    {
	parent = p;
    }

    public String
    getDefName(int i)
    {
	if (i < 0)
	    return "figures/defX.frc";
	return "figures/def" + i + ".frc";
    }

    public void
    run()
    {
	int i = 0;
	Class c = getClass();
	LoadedDef defs;

	if (PlatformSpecific.getVendor() == PlatformSpecific.VENDOR_NETSCAPE
	    && PlatformSpecific.getGenericPlatform() == PlatformSpecific.GEN_PLATFORM_WINDOWS) {
	    System.err.println("Warning: Netscape on Windows -- due to a JIT bug, this thread possibly (probably?) dies, and the default figures are NOT loaded. Thanks a bunch Symantec!");
	}

	for (i = 0; true; i++) {
	  String rname = getDefName(i);
System.err.println("Trying to load resource '"+rname+"'");
		
	    InputStream in;
	    try {

		in = c.getResourceAsStream(rname);
	    } catch (Exception e) {
System.err.println("ResourceLoader got exception when loading resource: "+e);
                break;
	    }

	    if (in == null)
		break;

	    try {
		defs = parent.readFigure(new InputStreamReader(in));
	    } catch (IOException e) {
System.err.println("DEBUG: failure on image-loading: "+e);
		continue;
	    }

System.err.println("Loaded '"+rname+"'");
	    parent.addFigure(defs);
	}

	// If we managed to load at least one default figure, we'll consider
	// the loading to be succesful, and return:
	if (i > 0)
	    return;
	
	// If not, we'll try another access method. Seems to be needed
	// at least with browsers that are fascist about resource loading. :-/
	
	String path = parent.getDocumentBase().toExternalForm();
	if ((i = path.lastIndexOf('/')) < 0) {
	    System.err.println("Couldn't get a valid document path; got '"
		    +path+"'.");
	    return;
	}

	path = path.substring(0, i+1) + "/";

	for (i = -1; true; i++) {
	    String rname = getDefName(i);

System.err.println("Trying to load '"+rname+"' via URL.");
	    
	    try {
		URL u = new URL(path + rname);
		InputStreamReader in = new InputStreamReader(u.openStream());
	    // First we try to load a 'combo'-file:
	        if (i < 0) {
		    LoadedDef [] d = parent.readFigures(in, true);
		    for (int j = 0; j < d.length; j++) {
			parent.addFigure(d[j]);
			Thread.yield();
		    }
		} else {
		    defs = parent.readFigure(in);
		    parent.addFigure(defs);
		}
System.err.println("Loaded '"+path+rname+"'");
	    } catch (Exception e) {
		System.err.println("ResourceLoader got exception when accessing via URL: "+e);

		// Let's only quit if we have tried both the combo-file
		// and first two 'normal' files...
		if (i > 0)
		    break;
	    }
	    
	}
    }
}
