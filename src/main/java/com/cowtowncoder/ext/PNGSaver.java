/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    PNGSaver.java

Description:
    A simple class that interfaces
    VisualTek's JavaPNG-package to various
    applications/applets; current at least Fractlet.
    The idea is that this class can be instantiated
    directly, and when need be, it can dynamically
    load the encoder JavaPNG provides. This way,
    the encoder/decoder may be included or excluded
    from packages without loader getting ape shit...

Last changed:
  12-Jul-99, TSa

Changes:

***************************************/

package ts.ext;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.*;

// Not needed:
//import com.visualtek.png.*;

public class
PNGSaver
{
    public final static Object
    getPNGEncoder(Image im, OutputStream out)
    {
	try {
	    Class c = Class.forName("com.visualtek.png.PNGEncoder");

	    Class c1 = Class.forName("java.awt.Image");
	    Class c2 = Class.forName("java.io.OutputStream");

	    Constructor con = c.getConstructor(new Class [] { c1, c2 });
	    
	    Object o = con.newInstance(new Object[] {
		im, out
	    });
	    return o;

	    /* A few exceptions we can get... and little we can do 'bout
	     * them. Thus:
	     */
	} catch (Exception e) {

	    System.err.println("EXCEPTION: "+e);
	}

	return null;
    }    

    public final static Object
    getJPEGEncoder(Image im, OutputStream out, int quality)
    {
	if (quality < 0 || quality > 100)
	    throw new Error("Error: JPEG-compression quality must be between 0 and 100; "+quality+" is out of the range.");
	try {
	    Class c = Class.forName("jpg.JpegEncoder");

	    Class c1 = Class.forName("java.awt.Image");
	    Class c2 = Integer.TYPE;
	    Class c3 = Class.forName("java.io.OutputStream");

	    Constructor con = c.getConstructor(new Class [] { c1, c2, c3 });
	    
	    Object o = con.newInstance(new Object[] {
		im, new Integer(quality), out
	    });
	    return o;

	    /* A few exceptions we can get... and little we can do 'bout
	     * them. Thus:
	     */
	} catch (Exception e) {

	    System.err.println("EXCEPTION: "+e);
	}

	return null;
    }    

    public final static boolean
    PNGencode(Object o)
	throws Exception
    {
	// Let's assume it indeed is a valid PNGEncoder:
	Method m = o.getClass().getMethod("encode", new Class [] { });
	m.invoke(o, new Object [] { });
	return true;
    }

    public final static boolean
    JPEGencode(Object o)
	throws Exception
    {
	// Let's assume it indeed is a valid PNGEncoder:
	Method m = o.getClass().getMethod("Compress", new Class [] { });
	m.invoke(o, new Object [] { });
	return true;
    }

}



