/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    Loader.java

Description:
    This class handles reading in of the
    figure definitions. The actual parsing
    is not done here; this should mostly
    be 'format-independent' module.

    As a design pattern component, Loader
    is quite close to being a Builder
    (or possibly, having a factory
    method, depending on your POV).

Last changed:
    11-Nov-1999, TSa

Changes:
  11-Nov-1999, TSa:
    About to rewrite this completely, to be
    more versatile as well as cleaner code
    in general. ;-)

***************************************/

package com.cowtowncoder.io;

import java.io.*;
import java.util.*;

public class Loader
{
    public final static int F_REQUIRE_MATCHING_DELIMS = 0x0001;
    public final static int F_STRICT_DELIMS = 0x0001;

    Reader in;
    StreamTokenizer tokenizer;
    int flags;

    // Initialization is rather simple:
    public
    Loader(Reader r, int f)
    {
      in = r;
      flags = f;
      tokenizer = new StreamTokenizer(r);

      tokenizer.slashSlashComments(true);
      tokenizer.slashStarComments(true);
      tokenizer.eolIsSignificant(false);
      tokenizer.parseNumbers();
      tokenizer.lowerCaseMode(false);
      tokenizer.commentChar('#');
      tokenizer.quoteChar('"');
      tokenizer.quoteChar('\'');
      tokenizer.wordChars('_', '_');
      // The closing tag is prepended by a '/', so:
      tokenizer.ordinaryChar('/');
    }

    public LoadedDef loadNextTaggedDef(String tag, Hashtable args, boolean do_close)
            throws IOException
    {
        loadTag(tag, args);
        return loadContents(tag, do_close);
    }

    /*
    /**********************************************************************
    /* First part of the functionality is for reading in
    /* definitions enclosed by HTML-tags:
    /**********************************************************************
     */

    /**
     * loadTag() searches for next (opening) tag 'tag', and
     * fills the given hash table with its possible arguments
     * (for example, {@code <TAG arg1=value> } would contain one key-value - pair)
     */
    public void loadTag(String tag, Hashtable args)
        throws IOException
    {
      Vector seq = loadNextTagSequence(tokenizer, tag);

      // If begin-tag isn't found, we fail:
      if (seq == null) {
	  throw new IOException("Begin tag (<"+tag+"> not found.");
      }

      Enumeration en = seq.elements();
      // We'll skip the tag itself:
      if (en.hasMoreElements()) {
	  en.nextElement();
      }
      parseTagArgs(en, args);
    }

  Vector
  loadNextTagSequence(StreamTokenizer t, String tag)
      throws IOException
  {
      Vector result;
      int i;

      tag = tag.toLowerCase();

      while (true) {
	  int type = t.nextToken();
	  
	  // If we ran to the EOF, it's not a valid file:
	  if (type == StreamTokenizer.TT_EOF)
	      return null;
	  
	  if (type != '<')
	      continue;

	  result = new Vector();

	  int count = 0;
	  while (true) {
	      type = t.nextToken();
	      
	      if (type == StreamTokenizer.TT_EOF)
		  return null;

	      if (type == '>')
		  break;

	      // A sanity check, let's not die on a runaway HTML-tag,
	      // ie. we'll only read up to 10000 args...
	      if (count++ < 10000)
		  result.addElement(makeLoadElement(t, type));
	  }

	  // Now we'll check whether it was the correct tag; if not,
	  // we'll do the timewarp again...

	  if (result.size() < 1)
	      continue;

	  Object x = result.elementAt(0);

	  if (!(x instanceof String) ||
	      !((String) result.elementAt(0)).toLowerCase().equals(tag)) {
	      continue;
	  }

	  break;
      }

      return result;
  }

  // Makes a hashtable out of list of args in HTMLs tag-args style
  void
  parseTagArgs(Enumeration en, Hashtable args)
  {
      Object a, b = null;
      while (en.hasMoreElements()) {
	  if (b != null) {
	      a = b;
	      b = null;
	  } else {
	      a = en.nextElement();
	  }

	  if (!(a instanceof String)) {
	      continue;
	  }

	  String key = ((String) a).toLowerCase();

	  // Unless the key is followed by '=', we'll simply put
	  // an empty string as its value. Likewise if it was the
	  // last element in the array
	  if (!en.hasMoreElements() || !((b = en.nextElement()) instanceof Character)
	      || ((Character) b).charValue() != '=') {
	      args.put(key, "");
	      continue;
	  }
          args.put(key, (en.hasMoreElements()) ? en.nextElement() : "");
      }
  }

  Object
  makeLoadElement(StreamTokenizer t, int type)
  {
      switch (type) {

      case StreamTokenizer.TT_NUMBER:
	  return new Double(t.nval);

      case StreamTokenizer.TT_WORD:

	  if (t.sval.equalsIgnoreCase("true"))
	      return new Boolean(true);
	  if (t.sval.equalsIgnoreCase("false"))
	      return new Boolean(false);
	  return t.sval;

      case '"':

	  // We need to decode the URL-encoded string:
	  int ptr, index;
	  String orig_s, new_s = null;

	  orig_s = t.sval.replace('+', ' ');

	  ptr = 0;

	  while ((index = orig_s.indexOf('%', ptr)) >= 0) {
	      if (new_s == null)
		  new_s = orig_s.substring(0, index);
	      else
		  new_s += orig_s.substring(ptr, index);

	      // Only faulty strings can cause this but:
	      if ((index + 3) > orig_s.length()) {
		  break;
	      }

	      String tmp = orig_s.substring(index+1, index+3).toLowerCase();

	      try {
		  
		  new_s += (char) (Integer.parseInt(tmp, 16));
	      } catch (NumberFormatException nfe) {
		  ; // Fine. Let's just skip it...
	      }

	      ptr = index + 3;
	  }

	  if (new_s == null) // No encoding?
	      new_s = orig_s;
	  else
	      new_s += orig_s.substring(ptr);

	  return new_s;
      }
      return new Character((char) type);
  }

  Vector
  loadUntilTagEnd(StreamTokenizer t, String tag)
      throws IOException
  {
      Vector result = new Vector();

      tag = tag.toLowerCase();

      while (true) {
	  int type = t.nextToken();
	  
	  // If we ran to the EOF, it's not a valid file:
	  if (type == StreamTokenizer.TT_EOF)
	      return null;
	  
	  if (type != '<') {
	      result.addElement(makeLoadElement(t, type));
	      continue;
	  }

	  if ((type = t.nextToken()) == StreamTokenizer.TT_EOF) {
	      return null;
	  }

	  if (type != '/') {
	      result.addElement(makeLoadElement(t, type));
	      continue;
	  }

	  if ((type = t.nextToken()) == StreamTokenizer.TT_EOF)
	      return null;

	  if (type != StreamTokenizer.TT_WORD ||
	      !t.sval.toLowerCase().equals(tag)) {
	      result.addElement(makeLoadElement(t, type));
	      continue;
	  }

	  while (true) {
	      if ((type = t.nextToken()) == StreamTokenizer.TT_EOF)
		  return null;
	      if (type == '>')
		  break;
	  }
	  break;
      }

      return result;
  }


 /******************************************************

  And the second part of the functionality is the actual
  parsing, ie. creating the structure represented by the
  loaded data.

 ******************************************************/   
  
  protected final static String delims = "()[]{}";
  private final static boolean openingDelimeter(char c) {
      int i = delims.indexOf(c);
      return (i >= 0) && ((i & 1) == 0);
  }
  private final static boolean closingDelimeter(char c) {
      int i = delims.indexOf(c);
      return (i >= 0) && ((i & 1) == 1);
  }
  private final static char matchingDelimeter(char c) {
      int i = delims.indexOf(c);
      return (i >= 0) ? delims.charAt(i ^ 1) : '\u0000';
  }

  /** loadContents does the actual work: it reads the (structured)
   *  definitions up to the closing tag. It does not understand recursive
   *  tags, but it's rather liberal about syntax of the contents...
   */
    LoadedDef
    loadContents(String tag, boolean close)
      throws IOException
    {
	LoadedDef def = null;
	IOException x = null;
	Vector seq = loadUntilTagEnd(tokenizer, tag);

	if (seq == null) {
	    x = new IOException("End tag (</"+tag+"> not found.");
	} else {
	    try {
		def = parseList(seq.elements(), ' ', 0);
	    } catch (IOException ie) {
		x = ie;
	    }
	}

	if (close) {
	    try { in.close(); } catch (IOException ie) { }
	}

	in = null;
	tokenizer = null;

	if (x != null) {
	    throw x;
	}

	return def;
    }

    LoadedDef
    parseList(Enumeration tokens, char end_delim, int level)
	throws IOException
    {
	IOException x = null;
	Object o;
	Vector result = new Vector();
	Object curr = null;
	String curr_id = null;

	// Next we'll parse the file structure; first we'll
	// read the stuff until the end tag:
	    
    parse_loop:
	while (tokens.hasMoreElements()) {
	    
	    o = tokens.nextElement();
	    
	    if (o instanceof Character) {
		char c = ((Character) o).charValue();
		if (openingDelimeter(c)) {
		    // We either have a function ('foo(args, ...)':
		    if (curr != null) {
			if (!(curr instanceof String)) {
			    throw new IOException("A list following a non-string token (or list)");
			}
			LoadedDef args = parseList(tokens, matchingDelimeter(c), level + 1);
			curr = LoadedDef.createAssignmentDef((String) curr, args);
		    // or a plain list '(a, b, ...)'
		    } else {
			curr = parseList(tokens, matchingDelimeter(c), level + 1);
		    }
		    continue parse_loop;
		}		    
		
		if (closingDelimeter(c)) {
		    if ((flags & F_REQUIRE_MATCHING_DELIMS) != 0
			&& (matchingDelimeter(c) != c)) {
			throw new IOException("Delimeter mismatch: opening delimeter '"+matchingDelimeter(end_delim)+"', closing delimeter '"+c+"'");
		    }
		    
		    if (level < 1 && (flags & F_STRICT_DELIMS) != 0) {
			throw new IOException("Extra closing delimeter ('"+c+"') encountered");
		    }
		    break parse_loop;
		}
		
		// A delimeter?
		if (c == ';' || c == ',') {
		    if (curr_id == null) {
			result.addElement(LoadedDef.createValueDef(curr));
		    } else {
			result.addElement(LoadedDef.createAssignmentDef(curr_id,
					  LoadedDef.createValueDef(curr)));
			curr_id = null;
		    }
		    curr = null;
		    
	        // A label?
		} else if (c == ':') {
		    if (curr_id != null) {
			throw new IOException("Value part of an assignemt is a label");
		    }
		    if (!(curr instanceof String)) {
			throw new IOException("Label not a string");
		    }
		    result.addElement(LoadedDef.createLabelDef((String) curr));
		    curr = null;
		    
		// '=' denotes an assignment:
		} else if (c == '=') {
		    
		    if (curr == null) {
			curr = "";
		    } else {
			if (!(curr instanceof String)) {
			    throw new IOException("Left side of an assignment not a string");
			}
			curr_id = (String) curr;
			curr = null;
		    }
		    /* By default, though, it's just a separate char, and
		     * will be converted to a string:
		     */
		} else {
		    if (curr == null) {
			curr = String.valueOf(c);
			} else {
			    if (!(curr instanceof String)) {
				throw new IOException("A character token preceded by a non-string token");
			    }
			    curr = (String) curr + " " + String.valueOf(c);
			}
		}
		
		continue parse_loop;
		
		// Well, by default we'll simply consider it to be an id...
	    } else if (o instanceof String) { // if (o instanceof char) ...
		if (curr == null) {
		    curr = o;
		} else {
		    if (!(curr instanceof String)) {
			throw new IOException("Syntax error: a string preceded by a non-string token");
		    }
		    curr = (String) curr + " " + (String) o;
		}
	    } else { // else if (o instanceof String) ...
		if (curr != null) {
		    throw new IOException("Syntax error: a non-string token used after a string token, outside an assigment");
		}
		curr = o;
	    }
	} // while (token.hasMoreElements())

	// If we are inside a list, we may want to do strict checking:
	if (curr_id != null || curr != null) {
	    if (level > 0 && (flags & F_STRICT_DELIMS) != 0) {
		throw new IOException("Missing delimeter ('"+end_delim+"') at the end of the definition");
	    }
	    if (curr_id == null) {
		result.addElement(LoadedDef.createValueDef(curr));
	    } else {
		// Strictly speaking, shouldn't really be legal ('foobar =' at the end of file)
		result.addElement(LoadedDef.createAssignmentDef(curr_id, LoadedDef.createValueDef(null)));
	    }
	}
	
	return LoadedDef.createListDef(result);
    }

  public void
  close()
  {
      if (in != null) {
	  try {
	      in.close();
	  } catch (IOException ie) {
	      // What can we do? =)
	  }
	  in = null;
	  tokenizer = null;
      }
  }


}
