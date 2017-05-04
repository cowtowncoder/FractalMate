/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    TSProperties.java

Description:
    A simple "database", that contains
    properties (TSProperty) indexed by
    property name. These properties are not
    to be directly handled, but instead via
    a TSProperties instance.
    When a property changes, it usually informs
    listeners about the change.
    TSProperties can also save/load property
    values, and can restore properties to
    their default values.

Last changed:
    26-Oct-1999, TSa

Changes:
  26-Oct-1999, TSa:
    Added 'getStringValue()'. It's mostly
    just a very simple convenience-function;
    it can be used to get rid of just one
    simple cast (in addition, it prevents
    cast exceptions).

***************************************/

package com.cowtowncoder.gui;

import java.util.*;

public class TSProperties
{
    protected Hashtable TSProps;

    public
    TSProperties()
    {
	TSProps = new Hashtable();
    }

 /*** Basic functions for creating and accessing the properties: ***/

    public void addProperty(String name, Object def_val, int props)
    {
	TSProperty p = new TSProperty(name, def_val, props);

	TSProps.put(name, p);
    }

    public boolean setValue(String name, Object value, Object changer)
    {
	TSProperty t = (TSProperty) TSProps.get(name);

	if (t == null)
	    return false;

	return t.setValue(value, changer);
    }
 
    public String
    saveVealue(String name)
    {
	TSProperty t = (TSProperty) TSProps.get(name);

	return (t == null) ? null : t.saveValue();
    }
 
    public boolean
    resetValue(String name, Object changer)
    {
	TSProperty t = (TSProperty) TSProps.get(name);

	if (t == null)
	    return false;

	return t.resetValue(changer);
    }

    public Object
    getValue(String name)
    {
	TSProperty t = (TSProperty) TSProps.get(name);

	if (t == null)
	    return null;

	return t.getValue();
    }

    public void
    loadValue(String name, Object saved_value)
    {
	TSProperty t = (TSProperty) TSProps.get(name);
	if (t != null)
	    t.loadValue(saved_value);
    }

    // This function resets all saveable properties to their 
    synchronized public void
    resetAllSaveable(Object changer)
    {
	Enumeration en = TSProps.elements();

	while (en.hasMoreElements()) {
	    TSProperty t = (TSProperty) en.nextElement();
	    if (t.isSaveable()) {
		t.resetValue(changer);
	    }
	}
    }

/*** And derived convenience functions: ***/

    public boolean
    getBooleanValue(String name)
    {
	Object x = getValue(name);

	if (x == null || !(x instanceof Boolean))
	    return false;

	return ((Boolean) x).booleanValue();
    }

    public int
    getIntValue(String name)
    {
	Object x = getValue(name);

	if (x == null || (!(x instanceof Integer) && !(x instanceof Long)))
	    return 0;

	return ((Number) x).intValue();
    }

    public double
    getDoubleValue(String name)
    {
	Object x = getValue(name);

	if (x == null || !(x instanceof Double))
	    return 0.0;

	return ((Double) x).doubleValue();
    }

    public String
    getStringValue(String name)
    {
	Object x = getValue(name);

	// Should we return "" or null?
	if (x == null || !(x instanceof String))
	    return null;

	return (String) x;
    }

    public boolean
    setBooleanValue(String name, boolean state, Object changer)
    {
	return setValue(name, new Boolean(state), changer);
    }

    public boolean
    toggleBooleanValue(String name, Object changer)
    {
	Object x = getValue(name);

	if (x == null || !(x instanceof Boolean))
	    return false;

	return setValue(name, new Boolean(!((Boolean) x).booleanValue()),
			changer);
    }

    public boolean
    setIntValue(String name, int state, Object changer)
    {
	return setValue(name, new Integer(state), changer);
    }

    public boolean
    setDoubleValue(String name, double val, Object changer)
    {
	return setValue(name, new Double(val), changer);
    }

    // setStringValue() is not necessary, as Strings are
    // normal objects... For orthogonality, it might be
    // included though?

    /*** We also need to attach listeners for properties: ***/
    
    public void
    addListener(String name, Object x, int f)
    {
	TSProperty t = (TSProperty) TSProps.get(name);

	if (t == null)
	    return;

	t.addListener(x, f);
    }

/*** Then various other utility functions: ***/

    public String
    toString()
    {
	Enumeration en = TSProps.keys();

	StringBuffer buf = new StringBuffer(TSProps.size() * 40);

	while (en.hasMoreElements()) {
	    String key = (String) en.nextElement();
	    TSProperty value = (TSProperty) TSProps.get(key);
	    buf.append(key);
	    buf.append(" = ");
	    buf.append(value.value.toString());
	    buf.append("\n");
	}

	return buf.toString();
    }
}
