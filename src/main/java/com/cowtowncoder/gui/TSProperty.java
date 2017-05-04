/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    TSProperty.java

Description:
    A simple container which is used to
    store a single property of the application.
    These containers are usually stored
    in TSProperties - instance; you can think
    of TSProperty as a (database) tuple, and
    TSProperties a very simple database, that
    is also able to inform objects when the
    property is changed.

Last changed:
    -

Changes:

***************************************/

package com.cowtowncoder.gui;

import java.awt.*;

import java.net.URLEncoder;

import com.cowtowncoder.io.LoadedDef;

class TSPropertyListElement
{
    public TSPropertyListElement next = null;
    public int flags = 0;
    public Object me = null;

    public
    TSPropertyListElement(Object p, int f, TSPropertyListElement n)
    {
	flags = f;
	me = p;
	next = n;
    }
}

public class
TSProperty
{
    public int properties;
    public Object value, defaultValue;
    public String name;

    TSPropertyListElement listeners = null;

    public final static int P_SAVEABLE = 0x0001;

    public final static int LISTEN_SYNC_VALUE = 0x0001;
    public final static int LISTEN_ENABLE_WHEN_TRUE = 0x0002;
    public final static int LISTEN_DISABLE_WHEN_TRUE = 0x0004;
    public final static int LISTEN_ALWAYS = 0x0008;
    public final static int LISTEN_REPAINT = 0x0010;

    // These properties are set by TSProperty, not passed by
    // the listener:
    public final static int LISTENER_TSPL = 0x0100;
    public final static int LISTENER_TEXT = 0x0200;
    public final static int LISTENER_LABEL = 0x0400;
    public final static int LISTENER_CB = 0x0800;
    public final static int LISTENER_CB_MENU = 0x1000;

    public
    TSProperty(String n, Object def_val, int p)
    {
	name = n;
	value = defaultValue = def_val;
	properties = p;
    }

    // // // Simple get- & set-functions and alike:

    // Returns 'true' if setting succeeded:
    public boolean setValue(Object new_val, Object changer)
    {
	Object old = value;

	value = new_val;

	// If there are listeners, we need to inform them;
	// but only if
	// a) new value is different from the old value, or
	// b) we have 'P_INFORM_ALWAYS' set

	if (listeners == null)
	    return true;

	boolean changed;
	boolean on_off_type = (new_val != null && new_val instanceof Boolean);
	boolean on_off_state = on_off_type && ((Boolean) new_val).booleanValue();

        if (value == null && old == null)
	    changed = false;
	else if (value == null || old == null)
	    changed = true;
	else changed = !(value.equals(old));

	TSPropertyListElement el = listeners;

	for (; el != null; el = el.next) {
	    int flags = el.flags;
	    Object listener = el.me;

	    // We won't inform the listener if it was the one that
	    // changed the value:
	    if (listener == changer)
		continue;

	    if (changed || (flags & LISTEN_ALWAYS) != 0) {

		if ((flags & LISTEN_REPAINT) != 0) {
		    ((Component) listener).repaint();
		}

		// Now we need some ugly code to see how to inform
		// the listener:
		if ((flags & LISTEN_SYNC_VALUE) != 0) {
		    if ((flags & LISTENER_TSPL) != 0)
		       ((TSPListener) listener).TSPChanged(name, old, new_val);
		    else if (on_off_type) {
			if ((flags & LISTENER_CB) != 0)
			    ((Checkbox) listener).setState(on_off_state);
			else if ((flags & LISTENER_CB_MENU) != 0)
			    ((CheckboxMenuItem) listener).setState(on_off_state);
		    } else if ((flags & LISTENER_LABEL) != 0) {
			((Label) listener).setText(new_val.toString());
		    } else if ((flags & LISTENER_TEXT) != 0) {
			((TextComponent) listener).setText(new_val.toString());
		    } else {
			System.err.println("Warning: listener "+listener+" synced to value of "+name+" [flags "+Integer.toHexString(flags)+"], new value of type "+new_val.getClass()+": don't know how to inform.");
		    }
		}

		// LISTEN_ENABLE_xxx is only applicable to properties
		// and components that have on/off state, ie. Boolean()
		// properties and Checkboxes & CheckboxMenuItems.
		if (on_off_type &&
		    (flags & (LISTEN_ENABLE_WHEN_TRUE |
			      LISTEN_DISABLE_WHEN_TRUE)) != 0) {
		    boolean enable = on_off_state;

		    if ((flags & LISTEN_DISABLE_WHEN_TRUE) != 0)
			enable = !enable;

		    if ((flags & LISTENER_CB) != 0)
			((Checkbox) listener).setEnabled(enable);
		    else if ((flags & LISTENER_CB_MENU) != 0)
			((CheckboxMenuItem) listener).setEnabled(enable);
		}
	    }
	}

	return true;
    }

    public Object
    getValue()
    {
	return value;
    }

    public boolean
    isSaveable()
    {
	return (properties & P_SAVEABLE) != 0;
    }

    // // // Functions for adding/removing & accessing listeners:

    public void addListener(Object x, int f)
    {
	// Should we check for duplicate listener entries?
	// Well, we'll do it: 
	if (listeners != null)
	    removeListener(x);

	// These are mutually exclusive:
	if (x instanceof Checkbox)
	    f |= LISTENER_CB;
	else if (x instanceof CheckboxMenuItem)
	    f |= LISTENER_CB_MENU;
	else if (x instanceof Label)
	    f |= LISTENER_LABEL;
	else if (x instanceof TextComponent)
	    f |= LISTENER_TEXT;

	// And this can be set in addition to previous ones:
	if (x instanceof TSPListener)
	    f |= LISTENER_TSPL;

	listeners = new TSPropertyListElement(x, f, listeners);
    }

    public boolean removeListener(Object x)
    {
	TSPropertyListElement el = listeners, last = null;

	while (el != null) {
	    if (el.me == x)
		break;
	    last = el;
	    el = el.next;
	}
	if (el == null)
	    return false;

	if (last == null)
	    listeners = el.next;
	else
	    last.next = el.next;
	return true;
    }

    /*** Then the various functions to trigger actions ***/

    /*** And functions for loading/saving/restoring property values: ***/

    public boolean resetValue(Object changer)
    {
	return setValue(defaultValue, changer);
    }

    // Note that the saved_value may either be a valid content in itself,
    // _or_ a presentation of the actual value in some suitable format
    // (in practice, usually a string representing a non-string value,
    // such as number or boolean value), that is the reason why we need
    // some heuristics here. In addition, Strings are saved in URL-encoded
    // form, so decoding is needed:
    public void loadValue(Object saved_value)
    {
	// Yuck...
	if (saved_value instanceof LoadedDef) {
	    saved_value = ((LoadedDef) saved_value).getNativeContents();
	}

      do { // A dummy loop, so we can use 'break':

	if (saved_value == null || defaultValue == null ||
	    defaultValue.getClass().isAssignableFrom(saved_value.getClass())){

	    // Strings are URL-encoded on save, so we need to decode 'em:
	    if (saved_value instanceof String) {
		int ptr = 0, index;
		String new_s = null;
		String orig_s = ((String) saved_value).replace('+', ' ');
		
		while ((index = orig_s.indexOf('%', ptr)) >= 0) {
		    if (new_s == null)
			new_s = orig_s.substring(0, index);
		    else
			new_s += orig_s.substring(ptr, index);
		    // Only faulty strings can cause this but:
		    if ((index + 3) > orig_s.length())
			break;
		    String tmp = orig_s.substring(index+1, index+3).toLowerCase();
		    try {
			new_s += (char) (Integer.parseInt(tmp, 16));
		    } catch (NumberFormatException nfe) {
			; // Fine. Let's just skip it...
		    }
		    ptr = index + 3;
		}
		if (new_s == null) // No encoding?
		    saved_value = orig_s;
		else
		    saved_value = new_s + orig_s.substring(ptr);
	    }
	    setValue(saved_value, null);
	    return;
	}

	// Ok. So let's see...
	if (saved_value instanceof String) {
	    String s = (String) saved_value;

	    // Booleans are quite easy; 'true' and 't' (case insensitive) are
	    // accepted as true, 'false' and 'f' as false; otherwise we'll
	    // use the default value. Could of course throw an exception
	    // as well?
	    if (defaultValue instanceof Boolean) {
		s = s.toLowerCase();
		
		if (s.equals("true") || s.equals("t"))
		    setValue(new Boolean(true), null);
		else if (s.equals("false") || s.equals("f") || s.length() == 0)
		    setValue(new Boolean(false), null);
		else setValue(defaultValue, null);
		return;
	    }
	    
	    try {
		if (defaultValue instanceof Double) {
		    setValue(new Double(s), null);
		} else if (defaultValue instanceof Integer) {
		    setValue(new Integer(s), null);
		} else if (defaultValue instanceof Long) {
		    setValue(new Long(s), null);
		} else if (defaultValue instanceof Float) {
		    setValue(new Float(s), null);
		}
		return;
	    } catch (NumberFormatException ne) {
		setValue(defaultValue, null);
	    }
	} else if (saved_value instanceof Double) {
	    Double d = (Double) saved_value;
	    if (defaultValue instanceof Integer) {
		setValue(new Integer(d.intValue()), null);
	    } else if (defaultValue instanceof Float) {
		setValue(new Float(d.floatValue()), null);
	    } else if (defaultValue instanceof Long) {
		setValue(new Long(d.longValue()), null);
	    } else break;
	    return;
	}
      } while (false);

      throw new IllegalArgumentException("TSProperty.loadValue(): property '"
+name+"' doesn't know how to load from a '"+saved_value.getClass()+"' value "
+"(def value is of type "+defaultValue.getClass()+").\n");
    }

    public String saveValue()
    {
	if (defaultValue instanceof String)
	    return "\"" + URLEncoder.encode((String) value)+ "\"";
	if (defaultValue instanceof Boolean) {
	    return (value != null && ((Boolean) value).booleanValue())
		? "true" : "false";
	}
	return value.toString();
    }
}
