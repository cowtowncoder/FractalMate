/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    LoadedDef.java
    (and some static non-public implementation
    classes, with names 'LoadedXXXDef'

Description:
    Class that encapsulates the structure
    that has earlier on been loaded by
    Loader (from a file, network etc).
    Instances of LoadedDef actually form
    a hierarchical structure; each instance
    encapsulates either:

    - A list, (list of values)
    - An assignment, (key + value)
    - A function, (name, arg list)
    - A label (label name)
    - A 'simple value:
        o Numeric (Double)
	o String
	o Boolean

    The type of the contents may be examined
    with 'containsXxxx()'-functions like
    'containsList()', 'containsDouble()' etc.

Last changed:
    12-Nov-1999, TSa

Changes:
  12-Nov-1999, TSa:
    Decided to use an actual object hierarchy,
    instead of a polymorphic base class. Much
    cleaner this way, and saves memory for each
    allocated LoadedDef instance. On the other
    hand, results in a few new classes, and
    doesn't completely solve the 'bloated base class
    problem'.

***************************************/

package com.cowtowncoder.fractalmate.io;

import java.util.*;

/****** First the main base class; the only visible public class: ******/

public class
LoadedDef
{
    private static LoadedDef nullDef = null;

    /* A semi-singleton pattern; a single instance is used for marking NULL-values.
     * However, so that we can derive classes, we do need to allow protected
     * overriding...
     */
    protected LoadedDef() { }

    // Factory methods. First a 'generic' one for 'simple' values:
    public static LoadedDef createValueDef(Object o) {
	// This casting-stuff is messy, but...
	if (o == null) {
	    if (nullDef == null) {
		nullDef = new LoadedDef();
	    }
	    return nullDef;
	}
	if (o instanceof LoadedDef)
	    return (LoadedDef) o;
	if (o instanceof String)
	    return new LoadedStringDef((String) o);
	if (o instanceof Double)
	    return new LoadedDoubleDef(((Double) o).doubleValue());
	if (o instanceof Boolean) { // Uses 2 'doubletons' (???? only 2 instances ever exists that is)
	    return LoadedBooleanDef.createLoadedBooleanDef(((Boolean) o).booleanValue());
	}
	if (o instanceof Vector)
	    return new LoadedListDef((Vector) o);

	// And else... ? Well, let's default to the null instance:
	return createValueDef(null);
    }

    // Then more specific factory funcs:
    public static LoadedDef createAssignmentDef(String k, LoadedDef val) {
	return new LoadedAssignmentDef(k, val);}
    public static LoadedDef createFunctionDef(String n, LoadedDef args) {
	return new LoadedFunctionDef(n, args); }
    public static LoadedDef createLabelDef(String l) { return new LoadedLabelDef(l); }
    public static LoadedDef createListDef(Vector v) { return new LoadedListDef(v);  }

    /** Simple functions for determining the type: **/
    public boolean isEmpty() { return this == nullDef; }
    public boolean containsAssignment() { return false; }
    public boolean containsBoolean() { return false; }
    public boolean containsDouble() { return false; }
    public boolean containsFunction() { return false; }
    public boolean containsLabel() { return false; }
    public boolean containsList() { return false; }
    public boolean containsString() { return false; }

    /** And accessor functions: **/
    public String getAssignmentKey() { return null;}
    public LoadedDef getAssignmentValue() { return null; }

    public boolean getBoolean() { return false; }

    public double getDouble() { return 0.0; }

    public String getFunctionName() { return null; }
    public Enumeration getFunctionArgs() { return null; }
    public String getLabelName() { return null; }

    public Enumeration getListContents() { return null;}
    public int containedListSize() { return 0; }

    public String getString() { return null; }

    // This is the 'generic' accessor; not a clean one but necessary sometimes:
    public Object getNativeContents() { return null; }

    public LoadedDef findAssignmentValueFor(String key, boolean strict_case, boolean recurse) {
	return null;
    }
}

class LoadedAssignmentDef extends LoadedDef {
    String key;
    LoadedDef value;
    
    public LoadedAssignmentDef(String k, LoadedDef v) {
	key = k;
	value = v;
    }

    @Override
    public boolean containsAssignment() { return true; }

    @Override
    public String getAssignmentKey() { return key; }
    @Override
    public LoadedDef getAssignmentValue() { return value; }

    @Override
    public Object getNativeContents() {
	Hashtable h = new Hashtable();
	h.put(key, value);
	return h;
    }
}

class LoadedBooleanDef extends LoadedDef {
    static LoadedBooleanDef T = null, F = null; // 'Doubleton'-pattern

    private LoadedBooleanDef() { } // ... no outside instantiation, thus.
    public static LoadedBooleanDef createLoadedBooleanDef(boolean b) {
	if (T == null) {
	    T = new LoadedBooleanDef();
	    F = new LoadedBooleanDef();
	}
	return b ? T : F;
    }
    @Override
    public boolean containsBoolean() { return true; }
    @Override
    public boolean getBoolean() { return this == T; }
    @Override
    public Object getNativeContents() { return new Boolean(this == T); }

}
class LoadedDoubleDef extends LoadedDef {
    double value;

    public LoadedDoubleDef(double d) {
	value = d;
    }

    @Override
    public boolean containsDouble() { return true; }
    @Override
    public double getDouble() { return value; }
    @Override
    public Object getNativeContents() { return new Double(value); }
}

class LoadedFunctionDef extends LoadedDef {
    String name;
    LoadedDef args;

    public LoadedFunctionDef(String n, LoadedDef a) {
	name = n;
	args = a;
    }

    @Override
    public boolean containsFunction() { return true; }
    @Override
    public String getFunctionName() { return name; }
    @Override
    public Enumeration getFunctionArgs() { return args.getListContents(); }
    @Override
    public Object getNativeContents() {
	Hashtable h = new Hashtable();
	h.put(name, args);
	return h;
    }
}

class LoadedLabelDef extends LoadedDef {
    String label;

    public LoadedLabelDef(String n) {
	label = n;
    }

    @Override
    public boolean containsLabel() { return true; }
    @Override
    public String getLabelName() { return label; }
    @Override
    public Object getNativeContents() { return label; }
}

class LoadedListDef extends LoadedDef {
    Vector list;
    
    public LoadedListDef(Vector v) {
	list = v;
    }

    @Override
    public boolean containsList() { return true; }
    @Override
    public Enumeration getListContents() { return list.elements(); }
    @Override
    public Object getNativeContents() { return list.elements(); }
    @Override
    public int containedListSize() { return list.size(); }

    @Override
    public LoadedDef findAssignmentValueFor(String key, boolean strict_case, boolean recurse)
    {
	Enumeration en = list.elements();
	while (en.hasMoreElements()) {
	    LoadedDef d = (LoadedDef) en.nextElement();
	    
	    if (!d.containsAssignment()) {
		continue;
	    }
	    if (strict_case) {
		if (key.equals(d.getAssignmentKey())) {
		    return d.getAssignmentValue();
		}
	    } else {
		if (key.equalsIgnoreCase(d.getAssignmentKey())) {
		    return d.getAssignmentValue();
		}
	    }
	}

	if (recurse) {
	    en = getListContents();
	    while (en.hasMoreElements()) {
		LoadedDef d = (LoadedDef) en.nextElement();
		if (d.containsList() && (d = d.findAssignmentValueFor(key, strict_case, recurse)) != null) {
		    return d;
		}
	    }
	}
	return null;
    }
}

class LoadedStringDef extends LoadedDef {
    String string;

    public LoadedStringDef(String s) {
	string = s;
    }

    @Override
    public boolean containsString() { return true; }
    @Override
    public String getString() { return string; }
    @Override
    public Object getNativeContents() { return string; }
}

