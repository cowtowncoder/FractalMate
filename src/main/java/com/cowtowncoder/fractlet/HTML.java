/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    HTML.java

Description:
    A module that contains various classes
    related to reading in and displaying
    HTML-documents.

Last changed:
    09-Nov-1999, TSa

Changes:

  22-Aug-1999, TSa:
    Now uses locking to prevent multiple
    access. Also, while loading, the links
    (of the previous page) are not active...
  26-Aug-1999, TSa:
    Now handles references as URLs/Files
    instead of Strings
  09-Nov-1999, TSa:
    Now takes care of the platform-dependant
    path separator chars, when run as an
    application.

***************************************/

package ts.fractlet;

import java.applet.Applet;
import java.awt.event.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class HTML
    extends
	/* Hmmh. Component is the light-weight version, but unfortunately
	 * it doesn't work 100% well (doesn't clear the background on
	 * Windows etc). Thus, we'll still use Canvas.
	 */
	Canvas //Component
    implements
        MouseListener, MouseMotionListener, Runnable
{
    public final static int DEFAULT_LEFT_MARGIN = 8;
    public final static int DEFAULT_RIGHT_MARGIN = 8;
    public final static int DEFAULT_TOP_MARGIN = 2;
    public final static int DEFAULT_BOTTOM_MARGIN = 2;
    public final static int DEFAULT_LIST_INDENT = 3;
    public final static double DEFAULT_LIST_MARKER_DEINDENT = 1.5;

    public final static int DEFAULT_CELL_SPACING = 2;
    public final static int DEFAULT_CELL_PADDING = 5;
    public final static int DEFAULT_ROW_SPAN = 1;
    public final static int DEFAULT_COL_SPAN = 1;

    public final static int DEFAULT_BR_CLEAR = 0; // Ie. none
    public final static int DEFAULT_CAPTION_HALIGN = HTMLProps.CENTER;
    public final static int DEFAULT_TABLE_VALIGN = HTMLProps.MIDDLE;
    public final static int DEFAULT_TABLE_HEADER_HALIGN = HTMLProps.CENTER;

    public final static Color DEFAULT_BACKGROUND_COLOUR = Color.lightGray;
    public final static Color TEXT_COLOUR_DEFAULT = Color.black;
    public final static Color LINK_COLOUR_DEFAULT = Color.blue;

    public final static char DEFAULT_PATH_SEPARATOR = '/';

    // Just some of the more common special chars....
    public final static String specChars =
	"/lt=</gt=>/amp=&/quot=\"/copy=(c)/auml=ä/ouml=ö/Auml=Ä/Ouml=Ö/";

    // Tags other than these will simply be discarded:
    // (except for various text effect tags, that are
    // handled separately)
    public final static String knownTags =
	"/body/head/html/title/link"
	+"/br/hr/img/p/font/basefont/center"
	+"/h1/h2/h3/h4/h5/h6"
	+"/table/tr/td/th/caption"
	+"/ul/ol/li"
	+"/img/a/base/blockquote/pre/";

    // These are removed after the effects are applied:
    public final static String textTags =
	"/address=a/a=L/abbr=A/acronym=A/b=b/big=B/cite=C/center=c/dfn=d/em=e/i=i/kbd=k"
	+"/code=o/q=q/strong=s/samp=S/tt=t/u=u/var=v"
	// (thus, these tags are simply removed, as they add no effects):
	+"/font=/a=/span=/div=/"
	// Table-related dummy tags:
	+"/col/colgroup/thead/tfoot/tbody/"
	;
    // Then other tags that can add flags (but that are not removed)
    public final static String effectTags =
	"/h1=b/h2=b/h3=b/h4=b/h5=b/h6=b/h7=b/pre=p/th=b/";
    
    // Tags that need (and can not) be closed:
    public final static String [] startOnlyTags = new String [] {
	"br", "hr", "img", "p",
    };

    /*** Class variable(s): ****/
    public static Hashtable pageCache = null;
    public static Hashtable imageCache = null;

    /*** Instance variables: ***/

    // These are required by image-loading:
    Applet applet;
    HelpViewer helpViewer;

    // Our input stream:
    PushbackReader reader;
    
    // Current properties inside the document:
    String textEffects = null;

    // URL-address of this document / Directory that contains
    // the document
    URL url = null;
    String dir = null;

    // Head & body of the document:
    HTMLElement body = null, head = null;

    MediaTracker tracker = null;

    // Font information:
    static Font defaultFont;
    static FontMetrics defaultFontMetrics;
    static int defaultRowHeight,  defaultFontWidth;
      // The height of a line of text printed on def font
      // And the width of the widest char in def char set

    // To be able to catch the clicks on links, we need to
    // have a list of link elements, as well as locking
    HTMLString [] linkStrings = null;
    HTMLString chosenLink = null;
    Cursor oldCursor = null;
    Cursor myOldCursor = null;

    // Page-loading now done asynchronously, so:
    Thread loaderThread = null;
    Integer pageLock = new Integer(0);

    boolean pageLoading = false; // Protected by pageLock
    Integer layoutLock = new Integer(0);
    Object pageToLoad = null;
    boolean pageToLoadForce = false;
    boolean pageToLoadInform = false;
    Container pageToLoadContainer = null;

    boolean backgroundDefd = false;

    // Let's send action events to those interested...
    ActionListener actionListener = null;

    // Cursor should be changed when loading the
    // page, and also when the pointer is above a link:

    boolean isOverLink = false;

    /* Various platforms have different path-separators;
	 * the default used by Fractlet is '/', which is used
	 * in HTML as well as in UNIX-world; '\' is used in
	 * Dos/Windows, and ':' in MacOS. So, we sometimes
	 * need to do path-mangling:
	 */
	char localPathSeparator;

    /****** Constructor.... *************/

    public
    HTML(Applet a, HelpViewer v, Dimension initialSize)
    {
	prefSize = initialSize;
	applet = a;
	helpViewer = v;
	addMouseListener(this);
	addMouseMotionListener(this);

	if (a != null) {
	    url = a.getCodeBase();
	    dir = null;
	    localPathSeparator = DEFAULT_PATH_SEPARATOR;
	} else {
	    url = null;
	    dir = ".";
	    /* In applications, we may need to do path mangling. Note,
	     * though, that if some platform has a multi-char separator
	     * (don't know any? How about VMS?), this doesn't work
	     * correctly...
	     */
	    localPathSeparator = File.separatorChar;
	}
    }

    /***** And the thread that loads page(s) in.... ****/
    public void run()
    {
	Object page;
	Container cont;
	boolean force, inform;
	while (true) {
	    synchronized (pageLock) {
		while (pageToLoad == null) {
		    try {
				pageLock.wait();
		    } catch (InterruptedException ie) { }
		}
		page = pageToLoad;
		cont = pageToLoadContainer;
		force = pageToLoadForce;
		inform = pageToLoadInform;
		pageToLoad = null;
	    }
	
long time = System.currentTimeMillis();

	    try {
		if (readIn(page, force)) {
		    if (cont != null) {
			cont.doLayout();
			setSize(prefSize.width, prefSize.height);
		    }
		    if (inform)
			processActionEvent(page, page.toString());
		}
	    } catch (IOException ie) {
		System.err.println("Invalid link '"+page+"': "+ie);
	    }
	
time = System.currentTimeMillis() - time;
System.err.println("Loading took "+time+" ms.");
	}
    }
    
    // Addr may be either:
    //
    // - String -> relative reference; usually when loading the
    //   first page. Has to be resolved...
    // - URL/File -> usually as a result of Back/Forward button
    //   being pressed
    public boolean addPageToLoad(Object addr, boolean force,
				 boolean inform, Container cont)
	throws IOException
    {
	synchronized (pageLock) {
	    if (pageLoading)
		return false;

	    if (addr instanceof String) {
		
		addr = getRefFor((String) addr);
/*
File foo =  (File) addr;
System.err.println("Canon="+foo.getCanonicalPath());
System.err.println("Abs="+foo.getAbsolutePath());
System.err.println("Props="+System.getProperties());
*/

		System.err.println("DEBUG: getRefFor -> '"+addr+"'");
	    }

	    pageToLoad = addr;
	    pageToLoadForce = force;
	    pageToLoadInform = inform;
	    pageToLoadContainer = cont;
	    if (loaderThread == null) {
		loaderThread = new Thread(this);
		loaderThread.start();
	    }
	    pageLock.notify();
	}
	return true;
    }

    public Object getRefFor(String ref)
	throws IOException
    {
	int i;
	boolean abs = false;

	System.err.println("GetRefFor '"+ref+"'");

	// Well, perhaps we should use the anchor for something but...
	if ((i = ref.indexOf('#')) >= 0) {
	    ref = ref.substring(0, i);
	}

	boolean is_url = (ref.indexOf(':') >= 0);

	if (ref.charAt(0) == DEFAULT_PATH_SEPARATOR) {
	    abs = true;
	}
	if (localPathSeparator != DEFAULT_PATH_SEPARATOR) {
	    ref = ref.replace(DEFAULT_PATH_SEPARATOR, localPathSeparator);
	}

	if (is_url)
	    return new URL(ref);
	if (applet != null)
	    return new URL(url, ref);
	if (dir != null)
	    return new File(dir, ref);
	return new File(ref);
    }

    public InputStream getInputStreamFor(Object ref)
	throws IOException
    {
	if (ref instanceof File)
	    return new FileInputStream((File) ref);

	if (ref instanceof URL)
	    return ((URL) ref).openStream();


	String s = (String) ref;

	if (applet != null || s.indexOf(':') >= 0) {
	    return new URL(s).openStream();
	}
	if (localPathSeparator != DEFAULT_PATH_SEPARATOR) {
	    s = s.replace(DEFAULT_PATH_SEPARATOR, localPathSeparator);
	}

	return new FileInputStream(s);
    }

    public String getFilenameFor(String file) {
	return dir + file;
    }

    // For debugging:
    public void printMe() {
	System.err.println("Head=");
	printTree(head, 0, true);
	System.err.println("Body=");
	printTree(body, 0, true);
	System.err.println();
	System.err.println("Title = '"+getTitle()+"'");
	System.err.println("Min width: "+getMinimumWidth());
    }

    /**** Simple set-/get-functions: ****/

    public int getMinimumWidth() {

	int w = 0, i;

	if (pageLoading || body == null)
	    return prefSize.width;

	Enumeration en = body.contents.elements();
	while (en.hasMoreElements()) {
	    HTMLPrimitive p = (HTMLPrimitive) en.nextElement();
	    i = p.getMinimumWidth();
	    if (i > w) {
		w = i;
	    }
	}
	// And also some padding:
	w += DEFAULT_LEFT_MARGIN + DEFAULT_RIGHT_MARGIN;
	return w;
    }

    public String getTitle() {
	HTMLElement h;
	if (head == null || (h = head.findElement("title")) == null)
	    return "<Untitled>";
	
	return h.getAsString();
    }

    public String setEffect(String fx, char p, boolean state) {

	// A kludge to by-pass 'empty sets':
	if (p == '/')
	    return fx;

	if (fx == null) {
	    if (!state)
		return null;
	    fx = String.valueOf(p);
	} else {
	    if (!state) {
		int i = fx.indexOf(p);
		if (i >= 0) {
		    if (fx.length() == 1)
			fx = null;
		    else fx = fx.substring(0, i) + fx.substring(i+1);
		}
	    } else if (fx.indexOf(p) < 0) {
		fx += String.valueOf(p);
	    }
	}
	return fx;
    }

    public boolean allowTrimming(String s) {
	if (s == null)
	    return true;
	return s.indexOf('p') < 0;
    }

    /**** Support for generating ActionEvents: *****/
    public void processActionEvent(Object src, String link) {
	if (actionListener != null) {
	    actionListener.actionPerformed(new
		ActionEvent(src, ActionEvent.ACTION_PERFORMED, link));
	}
    }
    public  void addActionListener(ActionListener l) {
	actionListener = AWTEventMulticaster.add(actionListener, l);
    }
    public  void removeActionListener(ActionListener l) {
	actionListener = AWTEventMulticaster.remove(actionListener, l);
    }

    /**** Some small utility-funcs: ****/

    public boolean contains(String [] a, String s) {
	for (int i = a.length; --i >= 0; ) {
	    if (a[i].equals(s))
		return true;
	}
	return false;
    }

    /**** Parsing functions: ****/

    // Returns true if the loading succeeded, false (or exception)
    // otherwise...
    private boolean
    readIn(Object addr, boolean force)
      throws IOException
    {
	int i;

	boolean ok = false;
	synchronized (pageLock) {
	    if (pageLoading)
		return false;
	    pageLoading = true;
	}

	changeCursor(isOverLink, true);

	try {
	    if (doReadIn(addr, force))
		ok = true;

	    // Finally is used, in case an exception is caught
	} finally {
	    if (ok) {
		if (prefSize.width > 0) {
		    layoutMe(prefSize.width, true);
		}
		repaint();
	    }
	    synchronized (pageLock) {
		pageLoading = false;
	    }
	    changeCursor(isOverLink, false);
	}

	return ok;
    }

    private boolean
    doReadIn(Object addr, boolean force)
      throws IOException
    {
	String base;
	int i;
	boolean done = false;

	if (addr instanceof URL) {
	    url = (URL) addr;
	    dir = null;
	} else if (addr instanceof File) {
	    url = null;
	    dir = ((File) addr).getParent();
	} else {
	    System.err.println("Warning: address not instanceof File or URL.");
	    return false;
	}

	// Then we'll check the cache:
	if (pageCache != null && !force) {
	    Object [] pages = (Object []) pageCache.get(addr);
	    if (pages != null) {
		head = (HTMLElement) pages[0];
		body = (HTMLElement) pages[1];
		linkStrings = (HTMLString []) pages[2];
		done = true;
	    }
	}

	if (!done) {
	    reader = new PushbackReader(new InputStreamReader(
			       getInputStreamFor(addr)));

	    Vector v = new Vector();
	    Object o;
	
	    while ((o = readNextElement(reader)) != null)
		v.addElement(o);

	    Vector v2 = new Vector();
	    Vector links = new Vector();

System.err.print(" makeSyntaxTree ");
long time = System.currentTimeMillis();
	    
            HTMLProps props = new HTMLProps();
	    makeSyntaxTree(v, v2, 0, null, props, null, links);

time = System.currentTimeMillis() - time;
System.err.println(" ("+(force ? "forced" : "normal")+") took "+time+" ms.");
	    
	    linkStrings = new HTMLString[links.size()];
	    links.copyInto(linkStrings);
	    
	    reader.close();

	// Now we better find the HEAD & BODY...

	    HTMLElement v_html = removeHTMLElement(v2, "html", true);
	
	    if (v_html == null)
		v = v2;
	    else v = v_html.contents;
	    
	    head = removeHTMLElement(v, "head", true);
	    body = removeHTMLElement(v, "body", true);
	    if (body == null) {
		body = new HTMLElement(this, "body", false, null);
		body.contents = v;
	    }

	    // And we also better remove possible leading & trailing
	    // empty strings:
	    body.removeEmpty(true, true);

	} // if (!done) ...	    

	if (body.properties != null && body.properties.background != null) {
	    backgroundDefd = true;
	    setBackground(body.properties.background);
	} else {
	    backgroundDefd = false;
	    setBackground(HTML.DEFAULT_BACKGROUND_COLOUR);
	}

	// Then we'll fetch the default font, which is required
	// for calculating certain layout values:

	if (defaultFont == null) {

	    defaultFont = new Font("SansSerif", Font.PLAIN,
		    HTMLString.fontSizes[HTMLProps.FONT_SIZE_DEFAULT-1]);
	    defaultFontMetrics = getFontMetrics(defaultFont);
	    defaultRowHeight = defaultFontMetrics.getHeight();
	    defaultFontWidth = defaultFontMetrics.getMaxAdvance();
	}
	    
	// Now let's wait for the images to load:
	if (tracker != null) {
	    System.err.println("DEBUG: Loading images...");
	    try {
		tracker.waitForID(0);
	    } catch (InterruptedException ie) {
		System.err.println("Warning: Interrupted image-loading, "+ie);
	    }
	    System.err.println("DEBUG: Done...");
	    tracker = null;
	}

	if (pageCache == null)
	    pageCache = new Hashtable();
	pageCache.put(addr, new Object [] { head, body, linkStrings });

	return true;
    }

    public static HTMLElement removeHTMLElement(Vector v, String tag,
						boolean recurse)
    {
        if (v == null)
	    return null;
	Enumeration en = v.elements();
	while (en.hasMoreElements()) {
	    Object x = en.nextElement();
	    if (!(x instanceof HTMLElement))
		continue;
	    HTMLElement h = (HTMLElement) x;
	    if (!h.end && h.name.equals(tag)) {
		v.removeElement(x);
		return h;
	    }
	    HTMLElement tmp;
	    if (recurse && (tmp = removeHTMLElement(h.contents, tag, true))
		!= null)
		return tmp;
	}
	return null;
    }

    static void
    printTree(HTMLElement h, int indent, boolean rec)
    {
	int t;
	for (t = 0; t < indent; t++) System.err.print("  ");
	System.err.println(h);
	Vector v = h.contents;
	if (v != null) {
	    Enumeration en = v.elements();
	    while (en.hasMoreElements()) {
		Object x = en.nextElement();
		if (x instanceof HTMLString) {
		    for (t = 0; t < indent; t++) System.err.print("  ");
		    System.err.println(x);
		} else {
		    if (rec)
			printTree((HTMLElement) x, indent+1, true);
		    else
			System.err.println(x);
		}
	    }
	}
	for (t = 0; t < indent; t++) System.err.print("  ");
	System.err.println("</"+h.name+">");
    }

    public int
    makeSyntaxTree(Vector from, Vector to, int ptr, HTMLElement parent,
		   HTMLProps props, String fx, Vector links)
    {
	String fx2;
	int i;
	boolean list_item = false, table_row = false,
	    table_item = false, table_header = false;
	String end = (parent == null) ? "" : parent.name;

	if (end != null) {
	    list_item = end.equals("li");
	    table_row = end.equals("tr");
	    table_header = (end.equals("th"));
	    table_item = table_header || (end.equals("td"));
	}

	for (; ptr < from.size(); ) {
	    HTMLPrimitive x = (HTMLPrimitive) from.elementAt(ptr);
	    ptr += 1;
	    if (x instanceof HTMLString) {
		HTMLString str = ((HTMLString) x);
		str.linkParent(parent);
		str.setEffects(fx);
		str.setProperties(props, helpViewer);
		if (allowTrimming(fx)) {
		    str.replaceLinefeeds();
		    str.trimDoubleSpaces();
		} else {
		    str.trimPreformatted();
		    x = new HTMLPrefString(str);
		}

		if (str.text.length() < 1) {
		    //System.err.println("<Empty string removed>");
		} else {
		    int len = str.text.length();
		    to.addElement(x);
		    if (str.link)
			links.addElement(str);
		    continue;
		}
	    }

	    HTMLElement t = (HTMLElement) x;
	    String tag = t.name;
	    
	    // A matching tag ends the level:
	    if (t.end && tag.equals(end))
		return ptr;

	    t.linkParent(parent);

	    // Sometimes also a new opening tag ends the level:
	    if ((list_item && tag.equals("li"))
		|| (table_item && (tag.equals("td") || tag.equals("th")
				|| tag.equals("tr")))
		|| (table_row && tag.equals("tr"))
		)
		return ptr - 1;

	    // Otherwise, we'll either open up a new level
	    // (an opening delimeter), or end the current level
	    // (closing delimeter)

	    if (t.end) {
		// We'll eat the ending tag, if matches the enclosing
		// opening tag. Actually, the only case we do NOT
		// eat is if the enclosing tag actually exists somewhere
		// in the 'call stack'.

		if (end != null && !tag.equals(end)) {
		    // Is there a matching opening delimeter somewhere
		    // (ie. a parent's parent etc)
		    HTMLElement h2 = parent;
		    while (h2 != null) {
			if (h2.name.equals(end))
			    break;
		    }
		    // If not, we'll eat the erroneous closing tag:
		    if (h2 != null)
			ptr -= 1;
		}

		return ptr;
	    }

	    // For other tags, we'll inherit the properties, parse
	    // new ones and add them to properties:
	    HTMLProps nprops = props.inherit();
	    nprops.add(t.parameters, url, tag);
	    t.setProperties(nprops);

	    // There are also certain tags that don't enclose
	    // anything, such as <P>, <BR>, <HR> and <IMG>:
	    if (contains(startOnlyTags, tag)) {
		if (!t.end) {
		    to.addElement(t);
		    if (tag.equals("img")) {
			if (tracker == null)
			    tracker = new MediaTracker(helpViewer);
			t.loadMe(applet, tracker, 0);
		    }
		}
		continue;
	    }

	    // Also, certain tags that indeed do enclose a block
	    // in HTML need not to do so here. We still need to
	    // use the property inheritance though, so:

	    if ((i = textTags.indexOf("/"+tag+"=")) >= 0) {
		fx2 = setEffect(fx, textTags.charAt(i+2+tag.length()),
				       true);
		ptr = makeSyntaxTree(from, to, ptr, t, nprops, fx2, links);
	    } else {
		if ((i = effectTags.indexOf("/"+tag+"=")) >= 0) {
		    fx2 = setEffect(fx, effectTags.charAt(
				       i + 2 + tag.length()), true);
		} else fx2 = fx;
		// If we open a new level, we need to do bit extra work
		Vector v2 = new Vector();
		
		ptr = makeSyntaxTree(from, v2, ptr, t, nprops, fx2, links);
		if (v2.size() == 0)
		    v2 = null;
		t.contents = v2;
		to.addElement(t);

		// Tables and lists need some extra initializations, once their
		// contents have been parsed... Thus we have:
		t.initBlock();
	    }
	}
	return ptr;
    }

    public final Object
    readNextElement(PushbackReader r)
      throws IOException
    {
    int i;
    char c;
    //int ws_count;

    main_read_loop:

    while (true) { // 'main_read_loop'

    	//ws_count = skipWS(r);
         if ((i = r.read()) == -1)
	     return null;

	 c = (char) i;

	 // This is a normal text-sequence?

	 if (c != '<') {
	     // If so, we'll simply read it all the way until the
	     // doc ends, or a tag begins. It does NOT yet remove
	     // the white space, as <PRE> etc may require 'em...

	     StringBuffer s = new StringBuffer(80);
	     //if (ws_count > 0)
	     //   s.append(' ');
	     s.append(c);
	     while (true) {
		 i = r.read();
		 if (i == -1)
		     break;
		 c = (char) i;
		 if (c == '<') {
		     r.unread(i);
		     break;
		 }
		 if (c == '&')
		     readSpecialChar(r, s);
		 else s.append(c);
	     }

	     return new HTMLString(this, s.toString());
	 }

	 // No, either a tag, or a comment or such:

	 // Well, let's give mercy; if the doc ends in the middle
	 // of the tag...
	 if ((i = r.read()) == -1)
	     return null;
	 c = (char) i;

	 if (c == '!') { // A comment?
	 // A comment or a tag we really do not care for much...
    comment_check:
	 do {
	     // Actually... we need to make sure it's a comment, first:
	     i = r.read();
	     if (i == -1)
		 return null;
	     c = (char) i;

	     if (c != '-') {
		 r.unread(c);
		 break comment_check;
	     }

	     i = r.read();
	     if (i == -1)
		 return null;
	     c = (char) i;

	     if (c != '-') {
		 r.unread('-');
		 r.unread(c);
		 break comment_check;
	     }

	     // Well, it is, indeed, a comment. Means that we need to loop
	     // to find its end, marked by -- followed by >; but these can
	     // be separated by a linefeed. :-/

	 comment_read_loop:
	     while (true) {
		 i = r.read();
		 if (i == -1)
		     return null;
		 if (i != (int) '-')
		     continue comment_read_loop;
		 i = r.read();
		 if (i == -1)
		     return null;
		 if (i != (int) '-')
		     continue comment_read_loop;
	
		 // Now possible white space
		 skipWS(r);
		 i = r.read();
		 if (i == -1)
		     return null;
		 if (i == (int) '>')
		     break;
	     }
		 
	     // After a comment, a new element begins, thus:
	     continue main_read_loop;
	 } while (false);
	 }
	
	 // Nope, we do have a real tag, which should now be read:
	 boolean end = false;

	 if (i == (int) '/') {
	     end = true;
	 } else r.unread(i);
    
	 // First we'll fetch the tag name:
	 String title = readId(r);

	 // And then perhaps some args:
	 Hashtable params = new Hashtable();
	 while (true) {
	     skipWS(r);

	     // We need to make sure tag doesn't end yet:
	     i = r.read();
	     if (i == -1 || i == (int) '>')
		 break;

	     r.unread(i);
	     String key = readId(r);

	     boolean ass = false;
	     while (isWS(i = r.read()) || i == '=') {
		 if (i == '=')
		     ass = true;
	     }

	     r.unread(i);

	     String value;
	     if (ass)
		 value = readValue(r);
	     else value = "true";

	     params.put(key, value);
	 }

	 title = title.toLowerCase();

	 // This is only for recognizing 'familiar' tags...
	 if ((i = textTags.indexOf("/"+title+"=")) >= 0) {
	     /*textEffects = setEffect(textEffects, textTags.charAt(
	       i + 2 + title.length()), !end);*/
	 } else {

	     // Otherwise, we'll check if we actually even do
	     // recognize the tag:
	     if (knownTags.indexOf("/"+title+"/") < 0) {
		 System.err.println("DEBUG: Unknown tag <"+title+">");	     
		 continue;
	     }
	 }
	 if (!end) {
	     if (title.equals("table"))
		 return new HTMLTable(this, title, params);
	     if (title.equals("ul"))
		 return new HTMLList(this, title, params, false);
	     if (title.equals("ol"))
		 return new HTMLList(this, title, params, true);
	 }
	 return new HTMLElement(this, title, end, params);
    }

    }

    public static final int
    skipWS(PushbackReader r)
    throws IOException
    {
	int i, count = 0;

	while ((i = r.read()) != -1) {
	    if (!isWS(i))
	        break;
	    count += 1;
	}
	if (i != -1)
	    r.unread(i);
	return count;
    }

    public static final void
    readSpecialChar(PushbackReader r, StringBuffer s)
	throws IOException
    {
	int i;
	StringBuffer spec = new StringBuffer();
	char c;

	while ((i = r.read()) != -1) {
	    if (i == ';') {
		String spec_s = spec.toString();
		if (spec_s.length() < 1)
		    s.append('?');
		else if (spec_s.length() > 1 && spec_s.charAt(0) == '#') {
		    spec_s = spec_s.substring(1);
		    try {
			if ((c = spec_s.charAt(0)) == 'x' || c == 'X')
			    i = Integer.parseInt(spec_s.substring(1), 16);
			else
			    i = Integer.parseInt(spec_s);
			s.append((char) i);
		    } catch (NumberFormatException ne) {
			s.append('?');
		    }
		} else if ((i = specChars.indexOf("/"+spec+"=")) < 0)
		    s.append('?');
		else s.append(specChars.substring(i+3+spec_s.length(),
				  specChars.indexOf('/', i+3)));
		return;
	    }
	    spec.append((char) i);
	}

    }

    public static final String
    readId(PushbackReader r)
    throws IOException
    {
	int i;
	StringBuffer s = new StringBuffer();

	while ((i = r.read()) != -1 && i != (int) '>' && isAlnum(i)) {
	     s.append((char) i);
	 }

	if (i != -1)
	    r.unread(i);
	return s.toString();
    }

    public static final String
    readValue(PushbackReader r)
    throws IOException
    {
	int i;
	StringBuffer s = new StringBuffer();

	i = r.read();

	if (i == -1)
	    return "";

	if (i == (int) '>') {
	    r.unread(i);
	    return "";
	}

	int delim = i;
	boolean  delimited = true;

	if (i != '\'' && i != '"') {
	    delimited = false;
	    r.unread(i);
	}

	while ((i = r.read()) != -1) {
	    if (delimited) {
		if (i == delim)
		    break;
	    } else if (isWS(i) || i == '>')
		break;

	    if (i == '&')
		readSpecialChar(r, s);
	    else s.append((char) i);
	}

	if (i == (int) '>')
	    r.unread(i);
	return s.toString();
    }

    public static final boolean
    isWS(int i)
    {
	char c = (char) i;
	return (c == ' ' || c == '\r' || c == '\t' || c == '\n');
    }

    public static final boolean
    isWS(char c)
    {
	return (c == ' ' || c == '\r' || c == '\t' || c == '\n');
    }

    public static final boolean
    isAlnum(int i)
    {
	char c = (char) i;
	return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
	    || (c >= '0' && c <= '9') || c == '_';
    }

    /**** Then layout-related functions: ****/

    Dimension prefSize;

    public Dimension getPreferredSize() {
	return prefSize;
    }

    public void layoutMe(int pref_width, boolean force)
    {
	if (!force) {
	    if (pageLoading || body == null || pref_width == prefSize.width) {
		System.err.println(" [Layout skipped]");
		return;
	    }
	}
long time = System.currentTimeMillis();

	synchronized (layoutLock) {

	    int min = getMinimumWidth();

	    if (min > pref_width)
		pref_width = min;

	    HTMLLayoutProps lprops = new HTMLLayoutProps();

	    lprops.x = DEFAULT_LEFT_MARGIN;
	    lprops.y = DEFAULT_TOP_MARGIN;
	    lprops.rights = null;
	    lprops.lefts = null;
	    lprops.base_left = DEFAULT_LEFT_MARGIN;
	    lprops.base_right = pref_width - DEFAULT_RIGHT_MARGIN;
	    lprops.right = lprops.base_right;
	    lprops.defRowHeight = defaultRowHeight;
	    lprops.defFontWidth = defaultFontWidth;
	    
	    body.location.x = 0;
	    body.location.y = 0;
	    body.location.width = pref_width;
	    
	    body.invalidateLayout();
	    body.layoutMe(lprops);
	    
	    prefSize.width = body.location.width;
	    prefSize.height = body.location.height;

	// Now we probably need to do a linefeed, and also check out
	// if any of the floating items extends below the bottom
	// (which we can figure out from the margin settings):

	// .....
	}

time = System.currentTimeMillis() - time;
System.err.println(" ("+(force ? "forced" : "normal")+") took "+time+" ms.");
    }

    public void update(Graphics g) { paint(g); }

    public void paint(Graphics g) {

	if (pageLoading) {
	    g.setColor(getBackground());
	    Dimension d = getSize();
	    g.fillRect(0, 0, d.width, d.height);
	    return;
	}

	//long time = System.currentTimeMillis();

	// Let's only clear the background if we have to...
	if (body != null) {
	    if (backgroundDefd) {
		HTMLPaintProps pp = new HTMLPaintProps();
		Rectangle clip = g.getClipBounds();
		pp.bg = getBackground();
		g.setColor(pp.bg);
		g.fillRect(clip.x, clip.y, clip.width, clip.height);
		pp.y = 0;
		pp.top = clip.y;
		pp.bottom = clip.y + clip.height - 1;
		pp.width = prefSize.width;
		synchronized (layoutLock) {
		    body.paintMe(g, pp);
		}
	    } else {
		synchronized (layoutLock) {
		    body.paintMe(g, null);
		}
	    }
	}
	//time = System.currentTimeMillis() - time;
	//System.err.println("Paint took "+time+" ms.");
    }

    /*** MouseListener & MouseMotionListener ****/

    public void mouseClicked(MouseEvent ev) {

	if (chosenLink == null)
	    return;

	if (pageLoading)
	    return;

	// Now we need to locate the href:
	HTMLElement e = chosenLink.parent;
	while (e != null) {
	    if (e.properties.href == null)
		e = e.parent;
	    else {
		try {
		    addPageToLoad(getRefFor(e.properties.href), false, true,
			      (Container) getParent());
		} catch (Exception e2) {
		    System.err.println("Warning: failed to load the page: "+e2);
		}
		return;
	    }
	}
    } 
    
    public void mouseEntered(MouseEvent e) {
    }
    
    public void mouseExited(MouseEvent e) {
    }
    
    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }
    
    public void mouseDragged(MouseEvent e) {
    }
    
    public void mouseMoved(MouseEvent e) {

	if (pageLoading) {
	    if (isOverLink) {
		isOverLink = false;
		chosenLink = null;
		changeCursor(isOverLink, pageLoading);
	    }
	    return;
	}

	if (linkStrings == null)
	    return;

	// We need to check whether we are over a link:
	int x = e.getX(), y = e.getY();
	HTMLString str = null;
	for (int i = linkStrings.length; --i >= 0; ) {
	    if (linkStrings[i].contains(x, y)) {
		str = linkStrings[i];
		break;
	    }
	}

	chosenLink = str;
	if (str == null) {
	    if (isOverLink) {
		isOverLink = false;
		changeCursor(isOverLink, pageLoading);
	    }
	} else {
	    if (!isOverLink) {
		isOverLink = true;
		changeCursor(isOverLink, pageLoading);
	    }
	}
    }

    public void changeCursor(boolean over_link, boolean loading) {

	Component c = getParent();
	Cursor crsr;

	if (loading || over_link) {
	    if (oldCursor == null) {
		if ((oldCursor = c.getCursor()) == null) {
		    oldCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
		}
	    }
	    if (myOldCursor == null) {
		if ((myOldCursor = getCursor()) == null) {
		    myOldCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
		}
	    }
	    
	    if (loading) {
		crsr = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
	    } else {
		crsr = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
	    }
	    setCursor(crsr);
	    c.setCursor(crsr);
	} else {
	    if (oldCursor != null) {
		c.setCursor(oldCursor);
		oldCursor = null;
	    }
	    if (myOldCursor != null) {
		setCursor(myOldCursor);
		myOldCursor = null;
	    }
	}
    }    
}

class HTMLSize
{
    public final static int TYPE_NONE = 0;
    public final static int TYPE_ABS = 1;
    public final static int TYPE_RELATIVE = 2;
    public final static int TYPE_PERCENTAGE = 3;
    public final static int TYPE_PROPORTIONAL = 4;

    public int type = TYPE_NONE;
    public int value = 0;

    public HTMLSize(int t, int v)
    {
	if (t < TYPE_NONE || t > TYPE_PROPORTIONAL)
	    throw new Error("Error: Invalid size type "+t);
	type = t;
	value = v;
    }

    public static int calcSize(int avail_size, HTMLSize size) {
	if (size == null)
	    return avail_size;
	if (size.type == TYPE_PERCENTAGE)
	    return (avail_size * size.value) / 100;
	// To calculate proportional size, we'd need to know
	// the other parts; and relative size is not used
	// in conjunction with available size...
	return avail_size;
    }

    public String toString() {
	String s = String.valueOf(value);

	if (type == TYPE_PERCENTAGE)
	    s += "%";
	else if (type == TYPE_PROPORTIONAL)
	    s += " pcs";
	else s += "pixels";
	return s;
    }
}

class HTMLProps
    implements Cloneable
{
    public final static int UNKNOWN = -1;
    public final static int NONE = 0;
    public final static int TOP = 1;
    public final static int BOTTOM = 2;
    public final static int MIDDLE = 3;

    public final static int LEFT = 4;
    public final static int RIGHT = 8;
    public final static int CENTER = 12;

    public final static int ALL_DIRS = 15;

    public final static int RULE_UNKNOWN = -1;
    public final static int RULE_NONE = 0;
    public final static int RULE_GROUPS = 1;
    public final static int RULE_ROWS = 2;
    public final static int RULE_COLS = 4;
    public final static int RULE_ALL = 7;

    public final static int STYLE_UNKNOWN = -1;
    public final static int STYLE_DISC = 0;
    public final static int STYLE_CIRCLE = 1;
    public final static int STYLE_SQUARE = 2;
    public final static int STYLE_ARABIC = 0;
    public final static int STYLE_ALPHA_LOWER = 1;
    public final static int STYLE_ALPHA_UPPER = 2;
    public final static int STYLE_ROMAN_LOWER = 3;
    public final static int STYLE_ROMAN_UPPER = 4;

    public final static int FONT_SIZE_DEFAULT = 3;
    public final static int FONT_SIZE_MIN = 1;
    public final static int FONT_SIZE_MAX = 7;

    public String id = null; // My id, if any
    public String href = null; // If this is a link, where does it lead to?
    public String src = null; // For Images, where is it to be loeaded from?

    public int vAlign = UNKNOWN; // If we are aligned, vertical align
    public int hAlign = UNKNOWN; // - "" - horiz. align
    public Color background = null; // Colour overrides, if any
    public Color foreground = null;
    public Color linkColour = null;
    public Color visitedLinkColour = null;
    public HTMLSize width = null, height = null;
    public int clear = UNKNOWN; // Only used for <BR> but..

    // Font information:
    public HTMLSize fontSize = null;
    public String fontNames = null;
    public int ownFontSize = FONT_SIZE_DEFAULT;

    // For tables & images:
    public HTMLSize borderSize = null;

    // For table definitions:
    public int tableFrame = UNKNOWN;
    public int tableRules = RULE_UNKNOWN;
    public HTMLSize cellPadding = null;
    public HTMLSize cellSpacing = null;

    // For table cells:
    public int rowSpan = HTML.DEFAULT_ROW_SPAN;
    public int colSpan = HTML.DEFAULT_COL_SPAN;
    public boolean noWrap = false; // to prevent wrapping

    // List-related, Style (non-Style Sheet) & starting symbol
    public int listStyle = STYLE_UNKNOWN;
    public HTMLSize listStart = null;
    public int listDepth = -1;
    public boolean listOrdered = false;

    public String toString() {
	String s = "";

	if (id != null)
	    s += " id="+id;
	if (href != null)
	    s += " href="+href;
	if (src != null)
	    s += " src="+src;
	if (vAlign != UNKNOWN)
	    s += " valign="+vAlign;
	if (hAlign != UNKNOWN)
	    s += " halign="+hAlign;
	if (width != null)
	    s += " width="+width;
	if (height != null)
	    s += " height="+height;
	if (fontNames != null)
	    s += " fonts="+fontNames;
	if (fontSize != null)
	    s += " font-size="+fontSize;
	return s;
    }

    public HTMLProps inherit() {
	HTMLProps h = new HTMLProps();
	h.vAlign = vAlign;
	h.hAlign = hAlign;
	h.background = background;
	h.foreground = foreground;
	h.linkColour = linkColour;
	h.visitedLinkColour = h.visitedLinkColour;
	h.tableRules = tableRules;
	h.href = href;
	h.fontNames = fontNames;
	h.fontSize = fontSize;
	h.listDepth = listDepth;
	h.listOrdered = listOrdered;
	return h;
    }

    public void setProperty(String prop, String orig_value,
			    URL url, String tag)
    {
	prop = prop.toLowerCase();
	String value = orig_value.toLowerCase();
	if (prop.equals("id")) {
	    id = value;

	    // URL-links etc:
	} else if (prop.equals("href")) {
	    /*try {
		href = new URL(url, value);
	    } catch (MalformedURLException me) {
		System.err.println("Erroneous URL: "+value);
		}*/
	    href = value;
	} else 	if (prop.equals("src")) {
	    /*try {
		src = new URL(url, value);
	    } catch (MalformedURLException me) {
		System.err.println("Erroneous URL: "+value);
		}*/
	    src = value;

	    // Alignment:
	} else if (prop.equals("valign")) {
	    vAlign = parseAlignment(value);
	} else if (prop.equals("halign") || prop.equals("align")) {
	    hAlign = parseAlignment(value);

	    // Colors:
	} else if (prop.equals("bgcolor")) {
	    background = parseColour(value);
	} else if (prop.equals("color") || prop.equals("text")) {
	    foreground = parseColour(value);
	} else if (prop.equals("link")) {
	    linkColour = parseColour(value);
	} else if (prop.equals("vlink")) {
	    visitedLinkColour = parseColour(value);

	    // Fonts:
	} else if (prop.equals("face")) {
	    fontNames = value;
	    // Actually, also for <HR>, thus
	} else if (prop.equals("size")) {
	    if (tag.equals("hr"))
		width = parseSize(value);
	    else if (tag.equals("font") || tag.equals("fontbase")) {
	        fontSize = parseSize(value, fontSize);

	    } else System.err.println("Warning: 'size'-attribute for tag '"+tag+"', not applicable.");
	    // Generic properties:
	} else if (prop.equals("width")) {
	    width = parseSize(value);
	} else if (prop.equals("height")) {
	    height = parseSize(value);

	    // For <BR>
	} else 	if (prop.equals("clear")) {
	    clear = parseAlignment(value);

	    // Table-related:
	} else if (prop.equals("colspan")) {
	    colSpan = parseInt(value, 1);
	} else if (prop.equals("rowspan")) {
	    rowSpan = parseInt(value, 1);
	} else 	if (prop.equals("nowrap")) {
	    noWrap = true;
	} else if (prop.equals("frame")) {
	    tableFrame = parseFrameType(value);
	} else if (prop.equals("rules")) {
	    tableRules = parseRuleType(value);
	    // (border applicable to Images too, though)
	} else if (prop.equals("border")) {
	    borderSize = parseSize(value);
	} else if (prop.equals("cellspacing")) {
	    cellSpacing = parseSize(value);
	} else if (prop.equals("cellpadding")) {
	    cellPadding = parseSize(value);

	    // List-related:
	} else if (prop.equals("start")) {
	    listStart = parseSize(value);
	} else if (prop.equals("type")) {
	    if (orig_value.equals("disc"))
		listStyle = STYLE_DISC;
	    else if (orig_value.equals("circle"))
		listStyle = STYLE_CIRCLE;
	    else if (orig_value.equals("square"))
		listStyle = STYLE_SQUARE;
	    else if (orig_value.equals("1"))
		listStyle = STYLE_ARABIC;
	    else if (orig_value.equals("a"))
		listStyle = STYLE_ALPHA_LOWER;
	    else if (orig_value.equals("A"))
		listStyle = STYLE_ALPHA_UPPER;
	    else if (orig_value.equals("i"))
		listStyle = STYLE_ROMAN_LOWER;
	    else if (orig_value.equals("I"))
		listStyle = STYLE_ROMAN_UPPER;
	}
    }

    public void setBackground(String s) { background = parseColour(s); }
    public void setForeground(String s) { foreground = parseColour(s); }
    public void setLinkColour(String s) { linkColour = parseColour(s); }

    public static int parseFrameType(String s) {
	s = s.toLowerCase();
	if (s.equals("void") || s.equals("none"))
	    return NONE;
	if (s.equals("above"))
	    return TOP;
	if (s.equals("below"))
	    return BOTTOM;
	if (s.equals("lhs"))
	    return LEFT;
	if (s.equals("rhs"))
	    return RIGHT;
	if (s.equals("hsides"))
	    return TOP | BOTTOM;
	if (s.equals("vsides"))
	    return LEFT | RIGHT;
	if (s.equals("box") || s.equals("border"))
	    return TOP | BOTTOM | LEFT | RIGHT;
	return UNKNOWN;
    }

    public static int parseRuleType(String s) {
	s = s.toLowerCase();
	if (s.equals("none"))
	    return RULE_NONE;
	if (s.equals("groups"))
	    return RULE_GROUPS;
	if (s.equals("rows"))
	    return RULE_ROWS;
	if (s.equals("cols"))
	    return RULE_COLS;
	if (s.equals("all"))
	    return RULE_ALL;
	return RULE_UNKNOWN;
    }

    public static int parseAlignment(String s) {
	int align = NONE;
	if (s != null) {
	    s = s.toLowerCase();
	    if (s.equals("top"))
		align = TOP;
	    else if (s.equals("middle"))
		align = MIDDLE;
	    else if (s.equals("bottom"))
		align = BOTTOM;
	    else if (s.equals("left"))
		align = LEFT;
	    else if (s.equals("center"))
		align = CENTER;
	    else if (s.equals("right"))
		align = RIGHT;
	    else if (s.equals("all")) // Only valid for <BR clear=all> but...
		align = RIGHT | LEFT;
	}
	return align;
    }

    public static int parseInt(String s, int def) {
	try {
	    return Integer.parseInt(s.trim());
	} catch (NumberFormatException ne) {
	}
	return def;
    }

    // At the moment only used when determining font size...
    public static HTMLSize parseSize(String s, HTMLSize old)
    {
	if (old == null)
	    return parseSize(s);
	HTMLSize h = parseSize(s);
	if (old.type == HTMLSize.TYPE_RELATIVE
	    && h.type == HTMLSize.TYPE_RELATIVE)
	    h.value += old.value;
	return h;
    }

    public static HTMLSize parseSize(String s)
    {
	boolean negate = false;

	if (s == null || s.length() == 0)
	    return null;
	s = s.toLowerCase().trim();
	int type, value;
	if (s.endsWith("%")) {
	    s = s.substring(0, s.length() - 1).trim();
	    type = HTMLSize.TYPE_PERCENTAGE;
	} else if (s.endsWith("*")) {
	    s = s.substring(0, s.length() - 1).trim();
	    type = HTMLSize.TYPE_PROPORTIONAL;
	} else if (s.charAt(0) == '+') {
	    type = HTMLSize.TYPE_RELATIVE;
	    s = s.substring(1);
	} else if (s.charAt(0) == '-') {
	    type = HTMLSize.TYPE_RELATIVE;
	    s = s.substring(1);
	    negate = true;
	} else type = HTMLSize.TYPE_ABS;

	try {
	    value = Integer.parseInt(s);
	    if (negate)
		value = -value;
	} catch (NumberFormatException e) {
	    return null;
	}
	return new HTMLSize(type, value);
    }

    public Color parseColour(String s)
    {
	if (s == null || s.length() == 0)
	    return null;
	s = s.toLowerCase();
	if (s.equals("black"))
	    return Color.black;
	else if (s.equals("silver"))
	    return Color.lightGray;
	else if (s.equals("gray"))
	    return Color.gray;
	else if (s.equals("white"))
	    return Color.white;
	else if (s.equals("maroon"))
	    return new Color(128, 0, 0);
	else if (s.equals("red"))
	    return Color.red;
	else if (s.equals("purple"))
	    return new Color(128, 0, 128);
	else if (s.startsWith("fu")) // Fuchsia
	    return new Color(255, 0, 255);
	else if (s.equals("green"))
	    return new Color(0, 128, 0);
	else if (s.equals("lime"))
	    return new Color(0, 255, 0);
	else if (s.equals("olive"))
	    return new Color(128, 128, 0);
	else if (s.equals("yellow"))
	    return Color.yellow;
	else if (s.equals("navy"))
	    return new Color(0, 0, 128);
	else if (s.equals("blue"))
	    return Color.blue;
	else if (s.equals("teal"))
	    return new Color(0, 128, 128);
	else if (s.equals("aqua"))
	    return Color.cyan;
	else if (s.charAt(0) == '#') {
	    try {
		int i = Integer.parseInt(s.substring(1), 16);
		return new Color((i >> 16) & 255, (i >> 8) & 255,
				 i & 255);
	    } catch (NumberFormatException ne) {
	    }
	}
	// What should be the appropriate 'error' colour? Pink? 8-)
	return Color.black;
    }

    public void add(Hashtable p, URL u, String tag) {

	// Hmm. Certain elements have different default
	// values, and they should even override the
	// inheritted values:
	if (tag.equals("caption")) {
	    hAlign = HTML.DEFAULT_CAPTION_HALIGN;
	} else if (tag.equals("td")) {
	    vAlign = HTML.DEFAULT_TABLE_VALIGN;
	} else if (tag.equals("th")) {
	    vAlign = HTML.DEFAULT_TABLE_VALIGN;
	    hAlign = HTML.DEFAULT_TABLE_HEADER_HALIGN;
	}

	if (p != null) {
	    Enumeration en = p.keys();
	    while (en.hasMoreElements()) {
		String key = (String) en.nextElement();
		setProperty(key, (String) p.get(key), u, tag);
	    }
	}

	// Intrinsic (base) font size has to be deduced from the tag:
	if (tag.equals("h1"))
	    ownFontSize = 7;
	else if (tag.equals("h2"))
	    ownFontSize = 6;
	else if (tag.equals("h3"))
	    ownFontSize = 5;
	else if (tag.equals("h4"))
	    ownFontSize = 4;
	else if (tag.equals("h5"))
	    ownFontSize = 3;
	else if (tag.equals("h6"))
	    ownFontSize = 3;
	else if (tag.equals("h7"))
	    ownFontSize = 3;
	else if (fontSize != null) {
	    if (fontSize.type == HTMLSize.TYPE_ABS)
		ownFontSize = fontSize.value;
	}

	// There are other 'special' tags that add various
	// properties, which can't be handled with text
	// effects:
	if (tag.equals("center"))
	    hAlign = CENTER;
	else if (tag.equals("ul")) {
	    listOrdered = false;
	    listDepth += 1;
	} else if (tag.equals("ol")) {
	    listOrdered = true;
	    listDepth += 1;
	}

	// Some other tags also directly add flags etc:

	if (fontSize != null) {
	    if (fontSize.type == HTMLSize.TYPE_RELATIVE)
		ownFontSize += fontSize.value;
	}

	if (ownFontSize < 1)
	    ownFontSize = 1;
	else if (ownFontSize > 7)
	    ownFontSize = 7;
    }
}

/**** Elements that form the HTML-document: *******/

/* HTMLPrimitive is the (abstract) base class of both HTMLString
 * (a simple text-only element that has related attributes & properties)
 * and HTMLElement (other more structural elements, list, tables,
 * containers for strings).
 */
abstract class HTMLPrimitive
{
    public final static int LAYOUT_FULL = 1;
    public final static int LAYOUT_PARTIAL = 2;
    public final static int LAYOUT_NONE = 3;

    public final static int ELEM_UNKNOWN = -1;

    // Main structural entities:
    public final static int ELEM_HEAD = 0;
    public final static int ELEM_BODY = 1;

    // Generic upper-level entities:
    public final static int ELEM_HEADER = 10;
    public final static int ELEM_IMG = 11;
    public final static int ELEM_P = 12;
    public final static int ELEM_PRE = 13;
    public final static int ELEM_BLOCKQUOTE = 14;
    public final static int ELEM_A = 15;

    // Separators:
    public final static int ELEM_BR = 20;
    public final static int ELEM_HR = 21;

    // Table-related
    public final static int ELEM_TABLE = 30;
    public final static int ELEM_TABLE_CAPTION = 31;
    public final static int ELEM_TABLE_ROW = 32;
    public final static int ELEM_TABLE_HEADER = 33;
    public final static int ELEM_TABLE_ELEMENT = 34;

    // List-related
    public final static int ELEM_LIST_ORD = 40;
    public final static int ELEM_LIST_UNORD = 41;
    public final static int ELEM_LIST_ITEM_ORD = 42;
    public final static int ELEM_LIST_ITEM_UNORD = 43;

    // Um, these are reserved for HTMLStrings:
    public final static int ELEM_STRING = 100;

    public HTML document;

    /*int x = -1, y = -1;
      int currWidth = -1, currHeight = -1;*/
    int minWidth = -1, maxWidth = -1;
    public Rectangle location = new Rectangle(); // Bounding box; not always
        // fully used (for example, wrapped text segments)
    public HTMLElement parent = null;
    public int hAlign = HTMLProps.UNKNOWN;
    public int type = ELEM_UNKNOWN;

    // To be overridden/defined by class extending us...
    public abstract void invalidateLayout();
    public abstract int getMinimumWidth();
    public abstract int getMaximumWidth();
    public int getHeight() { return 0; }
    public abstract int getRowHeight();
    public abstract boolean inlineable();
    public abstract boolean floating();
    public abstract int separatorWidth(int x, HTMLPrimitive prev);
    public abstract int separatorHeight(int x);
    public abstract void linkParent(HTMLElement p);

    public abstract int fitMe(HTMLLayoutProps lprops,int indent,boolean force);
    public void layoutMe(HTMLLayoutProps lprops) {
	;
    }
    public abstract void asyncLayoutMe(HTMLLayoutProps lprops);

    public abstract void paintMe(Graphics g, HTMLPaintProps pp);

    public HTMLPrimitive(HTML h) {
	document = h;
    }

    public void moveMe(int dx, int dy) {
	location.x += dx;
	location.y += dy;
    }

    public void moveLast(int dx, int dy) {
	;
    }
    public static final boolean isWS(char c) {
	return (c == ' ' || c == '\r' || c == '\t' || c == '\n');
    }
}

/* HTMLLayoutProps is a structure that contains 'layout-environment'
 * passed to various functions:
 */
class HTMLLayoutProps
    implements Cloneable
{
    int x, y;  // Current x & y - position (upper left corner)
    int rowHeight; // Height of the current layout line
    int [] lefts, rights; // Left & right marginals, if floating
      // items have changed them; pairs of coordinates, first is the
      // position of the marginal (in abs. pixel coordinates), and
      // the second the y-coordinate until which the margin lasts.
    int right; // The current right margin for this row
    int base_left, base_right; // The default margins to use
    int defRowHeight, defFontWidth;
    boolean trimLeadingWS = false;

    public Object clone() {
	try {
	    return super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
    }
}

/* HTMLElement holds most 'higher-level' HTML-constructs: */

class HTMLElement
    extends HTMLPrimitive
{
    public String name;
    public boolean end = false;
    public Hashtable parameters = null;
    Vector contents = null;
    public HTMLProps properties;
    public Image image = null; // Mostly for images, perhaps later on
    // also as the background tiling image...
    public int vAlign = HTMLProps.UNKNOWN;

    // List-attributes:
    public int listItemNr = 0, listDepth = 0;

    public HTMLElement(HTML doc, String n, boolean e, Hashtable h)
    {
	super(doc);
	name = n.toLowerCase();
	end = e;
	parameters = h;

	if (name.equals("hr"))
	    type = ELEM_HR;
	else if (name.equals("br"))
	    type = ELEM_BR;
	else if (name.equals("img"))
	    type = ELEM_IMG;
	else if (name.equals("pre"))
	    type = ELEM_PRE;
	else if (name.equals("blockquote"))
	    type = ELEM_BLOCKQUOTE;
	else if (name.equals("p"))
	    type = ELEM_P;
	else if (name.equals("a"))
	    type = ELEM_A;
	else if (name.startsWith("h"))
	    type = ELEM_HEADER;

	else if (name.equals("table"))
	    type = ELEM_TABLE;
	else if (name.equals("caption"))
	    type = ELEM_TABLE_ROW;
	else if (name.equals("th"))
	    type = ELEM_TABLE_HEADER;
	else if (name.equals("tr"))
	    type = ELEM_TABLE_ROW;
	else if (name.equals("td"))
	    type = ELEM_TABLE_ELEMENT;

	else if (name.equals("ol"))
	    type = ELEM_LIST_ORD;
	else if (name.equals("ul"))
	    type = ELEM_LIST_UNORD;
	else if (name.equals("li")) 
	    type = ELEM_LIST_ITEM_ORD;
                // We'll change it later to 'unord' if need be...
	else if (name.equals("body"))
	    type = ELEM_BODY;
	else if (name.equals("head"))
	    type = ELEM_HEAD;
    }
    
    public void linkParent(HTMLElement p) {
	parent = p;
	if (type == ELEM_LIST_ITEM_ORD) {
	    HTMLElement h = this;
	    while ((h = h.parent) != null) {
		if (h instanceof HTMLList) {
		    if (h.type == ELEM_LIST_ORD) {
			type = ELEM_LIST_ITEM_ORD;
		    } else {
			type = ELEM_LIST_ITEM_UNORD;
		    }
		    break;
		}
	    }
	}
    }

    public void initBlock() {
	if (type == ELEM_LIST_ITEM_ORD || type == ELEM_LIST_ITEM_UNORD) {
	    removeEmpty(true, true);
	}
    }

    public void setProperties(HTMLProps p) {
	properties = p;

	if (p != null) {
	    if (p.hAlign != HTMLProps.UNKNOWN)
		hAlign = p.hAlign;
	    if (p.vAlign != HTMLProps.UNKNOWN)
		vAlign = p.vAlign;
	    if (type == ELEM_LIST_ITEM_ORD) {
		listDepth = p.listDepth % 5;
	    } else if (type == ELEM_LIST_ITEM_UNORD) {
		listDepth = p.listDepth % 3;
	    }
	    if (type == ELEM_BR) {
		hAlign = p.clear;
	    }
	}
    }

    public String toString()
    {
	String s = "<"+(end ? "/" : "")+name+">";
	if (properties != null)
	    s += " ("+properties+")";
	return s;
    }

    public HTMLElement findElement(String s) {
	if (name.equals(s))
	    return this;
	if (contents == null)
	    return null;
	Enumeration en = contents.elements();
	while (en.hasMoreElements()) {
	    Object x = en.nextElement();
	    if (!(x instanceof HTMLElement))
		continue;
	    HTMLElement h = ((HTMLElement) x).findElement(s);
	    if (h != null)
		return h;
	}
	return null;
    }

    public String getAsString() {
	if (contents == null)
	    return "";
	String s = "";

	Enumeration en = contents.elements();
	while (en.hasMoreElements()) {
	    Object x = en.nextElement();
	    if (x instanceof HTMLElement)
		s += ((HTMLElement) x).getAsString();
	    else if (x instanceof HTMLString)
		s += ((HTMLString) x).text;
	}
	return s;
    }

    public void loadMe(Applet a, MediaTracker t, int id) {
System.err.println("LOAD IMAGE, props="+properties+", src="+properties.src);
	if (properties.src == null)
	    return;
	try {
	    if (a == null) {
		String file = document.getFilenameFor(properties.src);
		System.err.println("LOAD IMAGE, non-applet '"+file+"'");
		image = Toolkit.getDefaultToolkit().getImage(file);

	    } else {
		System.err.println("LOAD IMAGE, applet '"+properties.src+"'");
		try {
		    image = a.getImage(new URL(document.url, properties.src));
		} catch  (MalformedURLException e) {
		    image = null;
		}
	    }
	    t.addImage(image, id);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.err.println("Error when trying to load image "+(properties.src)+": "+e+"/"+e.getMessage());
	    throw new Error("Error, quitting.");
	}
    }

    /*** HTMLPrimitive-implementation: ****/

    public void invalidateLayout() {
	if (contents == null)
	    return;
	Enumeration en = contents.elements();
	while (en.hasMoreElements()) {
	    HTMLPrimitive p = (HTMLPrimitive) en.nextElement();
	    p.invalidateLayout();
	}
    }

    public void moveMe(int dx, int dy) {
	super.moveMe(dx, dy); // Moves the element itself...
	if (contents == null)
	    return;
	Enumeration en = contents.elements();
	while (en.hasMoreElements()) {
	    ((HTMLPrimitive) en.nextElement()).moveMe(dx, dy);
	}
    }

    public void moveLast(int dx, int dy) { moveMe(dx, dy); }

    public int separatorWidth(int x, HTMLPrimitive prev) {
	if (prev == null)
	    return 0;
	// Images etc are always separated by half a (default) char...
	return x / 2;
    }
    public int separatorHeight(int x) {
	return x / 2;
    }

    public boolean inlineable() {
	// Images are inlineable if they are not floating (aligned to
	// left or right)
	if (type == ELEM_IMG) {
	    if (properties == null)
		return true;
	    int al = properties.hAlign;
	    if (al == HTMLProps.LEFT || al == HTMLProps.RIGHT)
		return false;
	    return true;
	}

	// Most HTML-elements are not inlineable, meaning that
	// a linefeed is required before and after locating
	// them to the page...
	return false;
    }

    public void asyncLayoutMe(HTMLLayoutProps lprops) {
	// async layout for tables is in HTMLTable...
	if (type != ELEM_IMG)
	    throw new Error("Error: asyncLayoutMe() for non-Image/Table element '"+this+"' ("+type+")");


	// (why maximum?)
	int w = getMaximumWidth();
	int h = getHeight();

	location.width = w;
	location.height = h;

	w += lprops.defFontWidth / 2;
	h += lprops.defRowHeight / 2;

	int orig_x = lprops.x;
	int orig_y = lprops.y;

	if (!findMarginRoomFor(lprops, w)) {
	    System.err.println("Warning: asyncLayoutMe() can't make element '"+this+"' fit, no matter what; width="+w+", only "+(lprops.right - lprops.x)+" available.");
	    // Let's not quit though, just warn
	}

	location.y = lprops.y + lprops.defRowHeight / 4;

	if (properties.hAlign == HTMLProps.LEFT) {
	    location.x = lprops.x;
	    lprops.lefts = addMargin(lprops.lefts, location.y + h,
				     location.x + w, true);
	} else {
	    location.x = lprops.right - w;
	    lprops.rights = addMargin(lprops.rights, location.y + h,
				     lprops.right - w, false);
	}

	location.x += lprops.defFontWidth / 4;

	lprops.x = orig_x;
	lprops.y = orig_y;

	updateMargins(lprops);
    }

    public final void applyBr(boolean do_lf, HTMLLayoutProps lprops) {
	if (do_lf)
	    lprops.y += HTML.defaultRowHeight;

	// It may also be used to move 'cursor' below floating
	// elements:
	if (hAlign == HTMLProps.UNKNOWN)
	    hAlign = HTML.DEFAULT_BR_CLEAR;
	
	if ((hAlign & HTMLProps.LEFT) > 0 && lprops.lefts != null) {
	    int new_y = lprops.lefts[lprops.lefts.length - 2];
	    if (new_y > lprops.y)
		lprops.y = new_y;
	}
	if ((hAlign & HTMLProps.RIGHT) > 0 && lprops.rights != null) {
	    int new_y = lprops.rights[lprops.rights.length - 2];
	    if (new_y > lprops.y)
		lprops.y = new_y;
	}
	updateMargins(lprops);
    }

    public int fitMe(HTMLLayoutProps p, int indent, boolean force)
    {
	if (type != ELEM_IMG)
	    throw new Error("Error: inlinedLayoutMe() for non-Image element '"
			    +this+"' ("+type+")");

	int w = getMaximumWidth();
	int h = getHeight();
	
	if (!force && w > (p.right - p.x - indent))
	    return LAYOUT_NONE;
	
        location.height = h;
	location.width = w;
	location.x = p.x + indent;
	location.y = p.y - h;

	p.x += w + indent;

	return LAYOUT_FULL;
    }

    // Images and tables float, if they are aligned to the left or
    // right:
    public boolean floating() {
	if (type == ELEM_IMG || type == ELEM_TABLE) {
	    if (properties != null) {
		int al = properties.hAlign;
		if (al == HTMLProps.LEFT || al == HTMLProps.RIGHT) {
		    return true;
		}
	    }
	}
	return false;
    }

    public int getRowHeight() {
	if (type != ELEM_IMG)
	    return 1;
	return getHeight();
    }

    public void paintMe(Graphics g, HTMLPaintProps pp) {
	int x = location.x;
	int y = location.y;

	if (properties == null || properties.foreground == null)
	    g.setColor(HTML.TEXT_COLOUR_DEFAULT);
	else g.setColor(properties.foreground);

	switch (type) {

	case ELEM_IMG:

	    //if (pp != null) {}
	    if (properties.borderSize != null) {
		int w = location.width - 1;
		int h = location.height - 1;
		for (int i = properties.borderSize.value; --i >= 0; ) {
		    g.drawRect(x, y, w, h);
		    w -= 2;
		    h -= 2;
		    x++;
		    y++;
		}
	    }
	    g.drawImage(image, x, y, document);
	    return;

	case ELEM_BR:
	case ELEM_P:
	    return;

	case ELEM_HR:

	    //if (pp != null) {}

	    Color c = null;
	    if (properties != null) {
		if (properties.foreground != null)
		    c = properties.foreground;
		if (properties.background != null)
		    c = properties.background;
	    }
	    if (c == null)
		c = HTML.DEFAULT_BACKGROUND_COLOUR;
	    y += (location.height / 2);
	    g.setColor(c.darker());
	    g.drawLine(x, y, x + location.width, y);
	    g.setColor(c.brighter());
	    g.drawLine(x, y+1, x + location.width, y+1);
	    return;

	case ELEM_LIST_ITEM_ORD:

	    g.drawString(""+listItemNr+".", x, y + location.height);
	    break;

	case ELEM_LIST_ITEM_UNORD:

	    g.setFont(HTML.defaultFont);
	    int i = location.width / 2;
	    if ((location.height / 2) < i)
		i = location.height / 2;
	    switch (listDepth % 3) {
	    case 0:
		g.fillOval(x + (location.width - i) / 2,
			   y + (location.height - i) * 2 / 3,
			   i, i);
		break;
	    case 1:
		g.drawOval(x + (location.width - i) / 2,
			   y + (location.height - i) * 2 / 3,
			   i, i);
		break;
	    default:
		g.drawRect(x + (location.width - i) / 2,
			   y + (location.height - i) * 2 / 3,
			   i, i);
		break;
	    }
	    break;
	    
	case ELEM_TABLE_ELEMENT:
	case ELEM_TABLE_HEADER:

	    /*g.setColor(Color.blue);
	      g.drawRect(x, y, location.width - 1, location.height - 1);*/
	    /*System.err.print("TD/TH: "+x+","+y+" +"+location.width
	      +" +"+location.height+" -> ");*/
	    /*HTMLString foop = ((HTMLString)contents.elementAt(0));
	    if (foop != null && foop.locations != null)
	    System.err.println(foop.text + " ("+foop.locations[0].x+","+foop.locations[0].y+")");
	    else System.err.println();*/

	default:
	}

	// By default we render the contents (too):

	if (contents == null)
	    return;
	Enumeration en = contents.elements();
	while (en.hasMoreElements()) {
	    ((HTMLPrimitive) en.nextElement()).paintMe(g, pp);
	}
    }

    public int getHeight() {
	if (type == ELEM_IMG) {
	    if (image == null)
		return 0;
	    int i = image.getHeight(document);
	    if (i < 0)
		i = 0;
	    if (properties != null && properties.borderSize != null)
		i += 2 * properties.borderSize.value;
	    return i;
	}

	// Need a way to calculate tables' sizes too but:
	return 0;
    }

    public static int getMinimumWidth(Vector v) {
	if (v == null)
	    return 0;

	int min = 0;
	Enumeration en = v.elements();
	while (en.hasMoreElements()) {
	    HTMLPrimitive p = (HTMLPrimitive) en.nextElement();
	    int x = p.getMinimumWidth();
	    if (x > min)
		min = x;
	}
	return min;
    }

    public int getMinimumWidth() {
	Enumeration en;
	int i;

	if (minWidth < 0) {

	    // How to calculate the minimum depends:
	    switch (type) {

		// For images the size calculation is rather easy:
	    case ELEM_IMG:
		// Not yet loaded? Not much we can do, yet..
		if (image == null)
		    break;
		minWidth = image.getWidth(document);
		if (minWidth < 0)
		    break;
		if (properties != null && properties.borderSize != null) {
		    minWidth += 2 * properties.borderSize.value;
		}
		break;

		// The (minimum) width of tables has to be recursively
		// calculated, based on the minimum widths of the
		// rows & columns etc:

	    default:

		// Absolute sizing is possible, too:
		if (properties != null && properties.width != null
		    && properties.width.type == HTMLSize.TYPE_ABS)
		    return properties.width.value;

		minWidth = getMinimumWidth(contents);
		break;
	    }
	}

	if (minWidth < 0)
	    return 0;
	return minWidth;
    }

    public int getWidth(int pos) { return getMaximumWidth(); }

    public static int getMaximumWidth(Vector v) {
	if (v == null)
	    return 0;

	int max = 0;
	Enumeration en = v.elements();

	while (en.hasMoreElements()) {
	    HTMLPrimitive p = (HTMLPrimitive) en.nextElement();
	    int x = p.getMaximumWidth();
	    if (x > max)
		max = x;

	    // Floating elements can 'start' an inlineable line,
	    // thus they do contribute to maximum, but only if
	    // they appear before the inlineable elements...
	    if (!p.floating() || !p.inlineable())
		continue;

	    int w = x;
	    while (en.hasMoreElements()) {
		HTMLPrimitive p2 = (HTMLPrimitive) en.nextElement();
		int w2 = p2.getMaximumWidth();
		if (p2.floating()) {
		    if (w2 > max)
			max = w2;
		    continue;
		}
		if (!p2.inlineable()) {
		    if (w2 > max)
			max = w2;
		    break;
		}
		w += w2;
	    }
	    if (w > max)
		max = w;
	}

	return max;
    }

    public int getMaximumWidth() {
	Enumeration en;
	int i, indent = 0;

	if (maxWidth < 0) {

	    // How to calculate the minimum depends:
	    switch (type) {

		// For images the size calculation is rather easy:
	    case ELEM_IMG:
		// Not yet loaded? Not much we can do, yet..
		if (image == null)
		    break;
		maxWidth = image.getWidth(document);
		if (maxWidth < 0)
		    break;
		if (properties != null && properties.borderSize != null) {
		    maxWidth += 2 * properties.borderSize.value;
		}
		break;
		
		// The minimum width of a list has to be recursively
		// calculated; it's indentation width plus the maximum
		// of maximums of the list items

	    default:

		// Absolute sizing is possible, too:
		if (properties != null && properties.width != null
		    && properties.width.type == HTMLSize.TYPE_ABS)
		    return properties.width.value;

		maxWidth = getMaximumWidth(contents);
		break;
	    }

	    if (maxWidth >= 0)
		maxWidth += indent;
	}

	if (maxWidth < 0)
	    return 0;
	return maxWidth;
    }

    public void layoutMe(HTMLLayoutProps lprops) {

	/* First we'll have specific layout strategies for various
	 * components; if none match, we also have a generic 'container
	 * layout' routine we can use.
	 */
	boolean item_first_line = false;
	int list_item_nr = -1;

	/*
	if (type == ELEM_LIST_ITEM_ORD || type == ELEM_LIST_ITEM_UNORD) {
	    String str = "x";
	    if (contents != null && contents.size() > 0) {
		Object poop = contents.elementAt(0);
		if (poop instanceof HTMLString) {
		    str = ((HTMLString) poop).text;
		    if (str.length() > 8)
			str = str.substring(0, 7);
		}
	    }
	    System.err.println(" <ITEM "+properties.listDepth+":'"+str+"'>");
	}
	*/

	if (type == ELEM_HR) {
	    location.height = lprops.defRowHeight;

	    location.x = lprops.x;
	    location.y = lprops.y;

	    lprops.y += location.height;
	    // We should consider relative sizes etc too:
	    int w = HTMLSize.calcSize(lprops.right - location.x,
				      (properties == null) ? null :
				      properties.width);
	    location.width = w;
	    location.x += ((lprops.right - location.x) - w) / 2;
	    return;
	}

	if (type == ELEM_BR) {
	    applyBr(true, lprops);
	    return;
	}

	if (type == ELEM_P) {
	    lprops.y += lprops.defRowHeight;
	    return;
	}

	// Then tags that do use the default, but also need some
	// initializations:
	if (type == ELEM_HEADER) {
	    lprops.y += lprops.defRowHeight;
	    updateMargins(lprops);
	} else if (type == ELEM_LIST_ITEM_ORD || type == ELEM_LIST_ITEM_UNORD){
	    item_first_line = true;
	    location.x = lprops.base_left - ((int) HTML.DEFAULT_LIST_MARKER_DEINDENT *
		lprops.defFontWidth);
	    location.y = lprops.y;
	    location.width = lprops.defFontWidth;
	    location.height = lprops.defRowHeight;
	}

	/* And then the generic version: */

	int start = 0, max = 0;
	Vector floating = new Vector();

	if (contents == null)
	    return;
        HTMLPrimitive [] stuff = new HTMLPrimitive[contents.size()];
	contents.copyInto(stuff);

    generic_loop:
	while (start < stuff.length) {
	    HTMLPrimitive h = stuff[start];

	    // Basically we have 3 kinds of elements to lay out:
	    // a) Non-inlined non-floating elements; separators,
	    //    pre-formatted text blocks, lists, non-aligned
	    //    tables.
	    // b) Floating elements; images and tables that have
	    //    been aligned on left or right side.
	    // c) Inlined elements; text segments and inlined
	    //    (non-aligned) images.

	    /* Case b: */
	    if (h.floating()) { // Floating elements

		// These elements need to be handled more or less
		// asynchronously; but not yet now...
		start++;
		floating.addElement(h);

	    /* Case c: */
	    } else if (h.inlineable()) { // Inlined elements

		int i = start, ret;
		int separ_w = 0;
		HTMLPrimitive prev = null;
		boolean partial = false;

		lprops.rowHeight = 0;

	    inline_loop:

		for (; i < stuff.length; i++) {
		    h = stuff[i];

		    if (h.floating()) {
			floating.addElement(h);
			continue;
		    }

		    if (!h.inlineable())
			break inline_loop;

		    separ_w = h.separatorWidth(lprops.defFontWidth, prev);

		    lprops.trimLeadingWS = (prev == null);
		    ret = h.fitMe(lprops, separ_w, prev == null);

		    if (ret == LAYOUT_NONE)
			break inline_loop;

		    max = h.getRowHeight()
			+ h.separatorHeight(lprops.defRowHeight);
		    if (max > lprops.rowHeight)
			lprops.rowHeight = max;

		    switch (ret) {
			
		    case LAYOUT_PARTIAL:
			partial = true;
			break inline_loop;

		    case LAYOUT_FULL:
		    }

		    prev = h;
		}

		// We may need to align the text to left/right/center
		// (well, 'justify' may be available in future too?)
		// All the elements should have the same centering,
		// but let's use the alignment of the first element:
		int al = stuff[start].hAlign;

		separ_w = lprops.right - lprops.x;
		if (al == HTMLProps.RIGHT) {
		    ;
		} else if (al == HTMLProps.CENTER) {
		    separ_w /= 2;
		} else {
		    separ_w = 0;
		}

		// Now we need to move the components, depending on
		// alignment and row height:
		max = i + (partial ? 1 : 0);
		for (int j = start; j < max; j++) {
		    h = stuff[j];
		    if (h.floating())
			continue;
		    h.moveLast(separ_w, lprops.rowHeight);
		}

		// Now we can locate the actual marker, as we know the
		// line height:
		if (item_first_line) {
		    item_first_line = false;
		    location.y = lprops.y + lprops.rowHeight -
			location.height;
		}
		/*
		System.err.print(" <BR "+(max - start)+(partial?"-":"")
				 +">");
		*/

		lprops.y += lprops.rowHeight;
		start = i;

		// Finally, a trailing <BR> may be eaten (and has to,
		// actually, to prevent extraneous linefeeds)
		if (!partial && start < stuff.length) {
		    if (stuff[start].type == HTMLPrimitive.ELEM_BR) {
			// ... but if 'clear'-attribute is used, we
			// still need to apply it:
			applyBr(false, lprops);
			start++;
		    }
		}
		
	    /* Case b: */
	    } else { // Non-floating, non-inlined elements:
		h.layoutMe(lprops);		
		start++;
	    }

	    updateMargins(lprops);

	    // Now we need to add floating stuff, if any seen so far:
	    if (floating.size() > 0) {
		Enumeration en = floating.elements();
		while (en.hasMoreElements()) {
		    h = (HTMLPrimitive) en.nextElement();
		    h.asyncLayoutMe(lprops);
		}
		floating.removeAllElements();
	    }

	    // Well, now we need to move to the next line.
	    // Y-coordinate has already been modified by the
	    // instances, but the X-coordinate handling should
	    // be identical:
	    updateMargins(lprops);
	}

	if (type == ELEM_HEADER) {
	    lprops.y += lprops.defRowHeight;
	    updateMargins(lprops);
	}

	// Finally, BODY-element needs to know its height:
	if (type == ELEM_BODY) {
	    location.height = lprops.y - location.y
		+ HTML.DEFAULT_BOTTOM_MARGIN;
	}
    }

    public static void updateMargins(HTMLLayoutProps lprops) {
	int i;
	int [] arr;

	lprops.x = lprops.base_left;
	lprops.right = lprops.base_right;

	// First we'll clear up obsolete margins:
	lprops.lefts = removeMarginsUpTo(lprops.lefts, lprops.y);
	lprops.rights = removeMarginsUpTo(lprops.rights, lprops.y);

	if (lprops.lefts != null)
	    lprops.x = lprops.lefts[1];
	if (lprops.rights != null)
	    lprops.right = lprops.rights[1];

    }

    public static int [] addMargin(int [] arr, int y, int x, boolean left) {
	if (arr == null)
	    return new int [] { y, x };
	int i = 0;
	for (; i  < arr.length; i += 2) {
	    if (y < arr[i])
		break;
	    if ((left && (x < arr[i+1])) || (!left && (x > arr[i+1])))
		continue;
	    arr[i+1] = x;
	}
	// If the last element needs no modification, we'll
	// return...
	if (i < arr.length && ((left && x < arr[i+1]) ||
			       (!left && x > arr[i+1])))
	  return arr;

	int [] arr2 = new int [arr.length + 2];
	for (int j = 0; j < arr.length; j += 2) {
	    if (j < i) {
		arr2[j] = arr[j];
		arr2[j+1] = arr[j+1];
	    } else {
		arr2[j+2] = arr[j];
		arr2[j+3] = arr[j+1];
	    }
	}
	arr2[i] = y;
	arr2[i+1] = x;
	return arr2;
    }

    public static boolean findMarginRoomFor(HTMLLayoutProps lprops, int w) {
	
	while (w > (lprops.right - lprops.x)) {
	    if (lprops.lefts == null && lprops.rights == null) {
		return false;
	    }
	    // Let's jump to next location that makes the margin grow:
	    boolean left = false;
	    if (lprops.lefts == null)
		lprops.y = lprops.rights[0];
	    else if (lprops.rights == null)
		    lprops.y = lprops.lefts[0];
	    else {
		if (lprops.lefts[0] > lprops.rights[0])
		    lprops.y = lprops.rights[0];
		else
		    lprops.y = lprops.lefts[0];
	    }
	    updateMargins(lprops);
	}
	return true;
    }
    
    public static int [] removeMarginsUpTo(int [] arr, int y) {
	int i;
	
	if (arr == null)
	    return null;

	
	for (i = 0; i < arr.length; i += 2) {
	    if (arr[i] > y)
		break;
	}

	if (i == arr.length)
	     return null;
	if (i == 0)
	     return arr;

	int [] arr2 = new int[arr.length - i];
	System.arraycopy(arr, i, arr2, 0, arr2.length);
	return arr2;
    }

    public void removeEmpty(boolean start, boolean end) {

	if (contents == null)
	    return;

	Object x;
	int len = contents.size();

	// First the leading empty strings:
	if (start) {
	  while (len > 0) {
	    x = contents.firstElement();
	    if (!(x instanceof HTMLString))
		break;
	    HTMLString s = (HTMLString) x;
	    if (s.text.trim().length() > 0)
		break;
	    contents.removeElementAt(0);
	    len--;
	  }
	}

	// Then the trailing empty strings:
	if (end) {
	  while (len > 0) {
	    x = contents.lastElement();
	    if (!(x instanceof HTMLString))
		break;
	    HTMLString s = (HTMLString) x;
	    if (s.text.trim().length() > 0)
		break;
	    contents.removeElementAt(--len);
	  }
	}

	if (len == 0)
	    contents = null;
    }

}

/*** And then HTMLString, that encapsulates the actual
 *   text segments:
 */

class HTMLString
    extends HTMLPrimitive
{
    public String text, textLeft;
    public String effects = null;

    public boolean bold = false;
    public boolean italics = false;
    public boolean underlined = false;
    public boolean monospaced = false;
    public boolean noWrap = false;
    public boolean link = false;
    public int vAlign = HTMLProps.UNKNOWN;

    public Font myFont = null;
    public FontMetrics myFontMetrics = null;
    public Color textColour = HTML.TEXT_COLOUR_DEFAULT;

    // And the split/wrapped text:
    public String [] parts = null;
    public Rectangle [] locations = null;

    public static Hashtable seenFonts = new Hashtable();
    public final static int [] fontSizes = new int [] {
	8, 10, 12, 14, 16, 19, 24
    };

    public HTMLString(HTML h, String s)
    {
	super(h);
	text = s;
	type = HTMLPrimitive.ELEM_STRING;
    }

    public void linkParent(HTMLElement p) { parent = p; }

    public void copyInto(HTMLString s) {
	s.effects = effects;
	s.bold = bold;
	s.italics  = italics;
	s.underlined = underlined;
	s.monospaced = monospaced;
	s.noWrap = true;
	s.link = link;
	s.vAlign = vAlign;
	s.myFont = myFont;
	s.myFontMetrics = myFontMetrics;
	s.textColour = textColour;
    }

    public void setEffects(String p)
    {
	effects = p;

	if (effects != null) {
	    // Bold / strong -> bold font
	    if (contains(p, "bs"))
		bold = true;

	    // Address / Emphasis / Italics -> italics font
	    if (contains(p, "aei"))
		italics = true;
	    
	    // Underlined / links -> underlined font
	    if (contains(p, "uL"))
		underlined = true;

	    // dfn, kbd, pre, tt, var -> monospaced ("teletype") font
	    if (contains(p, "dkptv"))
		monospaced = true;

	    // pre (preformatted text); linewrapping not allowed
	    // (or actually; linewrapping is already defined by
	    // explicit linefeeds):
	    if (contains(p, "p")) {
		noWrap = true;
	    }

	    if (contains(p, "L"))
		link = true;
	}
    }

    public boolean contains(String a, String b)
    {
	if (a.length() > b.length()) {
	    String c = a;
	    a = b;
	    b = c;
	}
	for (int i = a.length(); --i >= 0; ) {
	    if (b.indexOf(a.charAt(i)) >= 0)
		return true;
	}
	return false;
    }

    // Returns true if the given point resides inside (bounding box of)
    // this string:
    public boolean contains(int x, int y) {
	if (locations == null)
	    return false;
	for (int i = locations.length; --i >= 0; ) {
	    Rectangle r = locations[i];
	    if (y > r.y)
		return false;
	    if (y <= (r.y - r.height))
		 continue;
	    if (x >= r.x && x < (r.x + r.width)) {
		return true;
	    }
	}
	return false;
    }

    public String
	toString()
    {
	return "(String, props="+effects+", font="+myFont+", minwidth="+getMinimumWidth()+") '"+text+"'";
    }

    public void trimDoubleSpaces() {
	int i = 0;
	
	while ((i = text.indexOf("  ", i)) >= 0) {
	    text = text.substring(0, i) + text.substring(i+1);
	}
    }

    public void replaceLinefeeds() {
	if (text.indexOf('\n') >= 0)
	    text = text.replace('\n', ' ');
	if (text.indexOf('\r') >= 0)
	    text = text.replace('\r', ' ');
	if (text.indexOf('\t') >= 0)
	    text = text.replace('\t', ' ');
    }

    // Hmmh. We probably need to replace tabs now with spaces,
    // to align them correctly (drawString() probably won't do that)
    // Also, in case we have \r's, they need to be removed too.
    public void trimPreformatted() {
	int i;
	while ((i = text.indexOf('\r')) >= 0) {
	    if (i < (text.length() - 1))
		text = text.substring(0, i) + text.substring(i+1);
	    else
		text = text.substring(0, i);
	}
    }

    public void setProperties(HTMLProps p, Component c) {

	textColour = (p == null) ? HTML.TEXT_COLOUR_DEFAULT : p.foreground;

	if (p != null)
	    hAlign = p.hAlign;

	int style = Font.PLAIN;

	if (bold)
	    style += Font.BOLD;
	if (italics)
	    style += Font.ITALIC;

	String name;
	// Monospaced or
	if (monospaced)
	    name = "Monospaced";
	else {
	    if (p == null || p.fontNames == null)
		name = "SansSerif";
	    else {
		// We probably should scan through the list but...
		name = p.fontNames;
		int i = name.indexOf(',');
		if (i >= 0)
		    name = name.substring(i);
	    }
	}

	int size = (p == null) ? HTMLProps.FONT_SIZE_DEFAULT : p.ownFontSize ;

	myFont = new Font(name, style, fontSizes[size-1]);

	if (seenFonts.contains(myFont))
	    myFontMetrics = (FontMetrics) seenFonts.get(myFont);
	else {
	    myFontMetrics = c.getFontMetrics(myFont);
	    seenFonts.put(myFont, myFontMetrics);
	}

	    // And links have link-text colour
	if (effects != null && contains(effects, "L")) {
	    if (p != null && p.linkColour != null)
		textColour = p.linkColour;
	    else textColour = HTML.LINK_COLOUR_DEFAULT;
	}

	getMinimumWidth();
    }

    /*** HTMLPrimitive - implementation / overrides: ****/

    public void invalidateLayout() {
	parts = null;
	locations = null;
	textLeft = text;
    }

    // Let's separate strings by 1 char... at least if both are
    // strings:
    public int separatorWidth(int x, HTMLPrimitive prev) {
	if (prev == null)
	    return 0;
	if (prev instanceof HTMLString)
	    return 0; // Or x??
	return x / 2;
    }

    public void moveMe(int dx, int dy) {
	super.moveMe(dx, dy);
	for (int i = locations.length; --i >= 0; ) {
	    locations[i].x += dx;
	    locations[i].y += dy;
	}
    }

    public void moveLast(int dx, int dy) {
	if (parts != null) {
	    locations[locations.length - 1].x += dx;
	    locations[locations.length - 1].y += dy;
	}
    }

    // And no additional padding between text lines:
    public int separatorHeight(int x) {
	return 0;
    }

    // Texts are inlineable, without exceptions?
    public boolean inlineable() {
	return true;
    }

    // And they never 'float', as a consequence
    public boolean floating() {
	return false;
    }

    public int getRowHeight() {
	return myFontMetrics.getHeight();
    }

    public int getMaximumWidth() {

	// Maximum width is either:
	// a) The width of the text (for non pre-formatted stuff)
	// b) The width of the longest individual line, for
	//    preformatted text
	if (maxWidth < 0) {
	    if (text == null) {
		maxWidth = 0;
	    } else {
		maxWidth = myFontMetrics.stringWidth(text);
	    }
	}

	return maxWidth;
    }

    public int getMinimumWidth() {

	if (minWidth < 0) {
	    if (text == null)
		minWidth = 0;
	    else {

	    // The minimum size is the maximum of word length, ie.
	    // space the longest word occupies (as words can not
	    // be split over multiple lines).
	    // We can make it bit faster if we assume the font
	    // is monospaced. So, for now, we'll do it, not matter
	    // whether the font is monospaced or not. *grin*

	    // Actually, with 'noWrap', this gets trickier but..

		if (noWrap) {
		    minWidth = getMaximumWidth();
		} else {
		    int max_len = 0;
		    int pos = 0, len = text.length(), pos2 = 0;

		check_loop:
		    while (pos < len) {
			char c;
			
			if ((c = text.charAt(pos++)) == ' ' || c == '\n') {
			    continue check_loop;
			}
			
			// Now a new word begins, thus:
			pos2 = pos - 1;
			
			while (pos < len && ((c = text.charAt(pos)) != ' ') &&
			       c != '\n') {
			    pos++;
			}
			
			int plen = myFontMetrics.stringWidth(text.substring(pos2,
									    pos));
			if (plen > max_len)
			    max_len = plen;
		    }
		    
		    minWidth = max_len;
		}
	    }
	}
	return minWidth;
    }

    // No such method needed here really:
    public void asyncLayoutMe(HTMLLayoutProps lprops)
    {
	throw new Error("Error: asyncLayoutMe() on a HTMLString.");
    }

    public int fitMe(HTMLLayoutProps p, int indent, boolean force)
    {	
	int end = (textLeft == null) ? 0 : textLeft.length();
	int begin = 0;
	String s;

	if (p.trimLeadingWS) {
	    // Can NOT use trim, because it removes trailing WS also...
	    //textLeft = textLeft.trim();
	    while (begin < end && isWS(textLeft.charAt(begin)))
		begin++;
	    if (begin > 0) {
		textLeft = textLeft.substring(begin);
		end = textLeft.length();
	    }
	    begin = 0;
	}

	if (end < 1) {
	    textLeft = null;
	    return LAYOUT_FULL;
	}
	
	int my_w = myFontMetrics.stringWidth(textLeft);
	int w = p.right - p.x + indent;

	// Fits completely?
	if (my_w <= w) {
	    s = textLeft;
	    textLeft = null;

	    // If not, perhaps partially?
	} else {

	    s = null;
	    char c;
	    int i = begin, x;
	    int end_pos = -1;
	    
	    // First we'll skip some WS again:

	    while (i < end && isWS(textLeft.charAt(i)))
		i++;

	    while (i < end) {
	      while (i < end && !isWS(textLeft.charAt(i)))
		  i++;
	      x = myFontMetrics.stringWidth(textLeft.substring(begin, i));
	      if (x > w) {
		  if (force) {
		      force = false;
		  } else {
		      break;
		  }
		  break;
	      }
	      
	      my_w = x;

	      while (i < end && isWS(textLeft.charAt(i)))
		  i++;

	      end_pos = i;
	    }

	    if (end_pos < 0) {
		return LAYOUT_NONE;
	    }

	    if (end_pos >= end) {
		s = textLeft;
		textLeft = null;
	    } else {
		s = textLeft.substring(begin, end_pos);
		textLeft = textLeft.substring(end_pos);
	    }
	}

	p.x += indent;

	int h = getRowHeight();
	Rectangle r = new Rectangle(p.x, p.y, my_w, h);

	p.x += my_w;
	    
	if (parts == null) {
	    parts = new String[1];
	    locations = new Rectangle[1];
	    location.x = r.x;
	    location.y = r.y;
	    location.height = getRowHeight();
	} else {
	    String [] new_p = new String[parts.length + 1];
	    Rectangle [] new_l = new Rectangle[locations.length + 1];
	    System.arraycopy(parts, 0, new_p, 0, parts.length);
	    System.arraycopy(locations, 0, new_l, 0, locations.length);
	    parts = new_p;
	    locations = new_l;
	}

	parts[parts.length-1] = s;
	locations[locations.length-1] = r;

	return (textLeft == null) ? LAYOUT_FULL : LAYOUT_PARTIAL;
    }

    public void paintMe(Graphics g, HTMLPaintProps pp) {

	if (parts == null || locations == null) {
	    return;
	}

	//if (pp != null) {
	//    Rectangle r = locations[locations.length - 1];
	//    if (clearUntil(g, pp, r.y + r.height))
	//}

	//for (int i = 0; i < parts.length; i++) {
	for (int i = parts.length; --i >= 0; ) {
	    if (myFont != null) {
		g.setFont(myFont);
	    }
	    if (textColour == null) {
		g.setColor(HTML.TEXT_COLOUR_DEFAULT);
	    } else {
		g.setColor(textColour);
	    }

	    if (locations[i] == null) {
		continue;
	    }
	    int x = locations[i].x;
	    int y = locations[i].y;
	    
	    g.drawString(parts[i], x, y);

	    if (underlined) {
		g.drawLine(x, y+1, x + locations[i].width, y+1);
	    }
	}
    }
}

final class HTMLPrefString
    extends HTMLString
{
    int index = 0;

    public HTMLPrefString(HTMLString s) {
	super(s.document, s.text);

	s.copyInto(this);

	int i = 0, j = 0, tlen = text.length();
	Vector v = new Vector();
	while (true) {
	    
	    i = text.indexOf('\n', j);
	    if (i < 0) {
		v.addElement(text.substring(j));
		break;
	    }
	    v.addElement(text.substring(j, i));
	    j = i + 1;
	    if (j >= tlen) {
		v.addElement("");
		break;
	    }
	}
	parts = new String[v.size()];
	locations = new Rectangle[parts.length];
	v.copyInto(parts);
    }

    public int getMaximumWidth() {
	if (maxWidth < 0) {
	    if (parts == null)
		maxWidth = -1;
	    else {
		maxWidth = 0;
		for (int i = 0; i < parts.length; i++) {
		    int x = myFontMetrics.stringWidth(parts[i]);
		    if (x > maxWidth)
			maxWidth = x;
		}
	    }
	}
	if (maxWidth < 0)
	    return 0;
	return maxWidth;
    }

    public int getMinimumWidth() {
	return getMaximumWidth();
    }

    public void invalidateLayout() { index = 0; }

    public int fitMe(HTMLLayoutProps p, int indent, boolean force) {
	if (index >= parts.length) {
	    return LAYOUT_FULL;
	}
	int my_w = myFontMetrics.stringWidth(parts[index]);
	int w = p.right - p.x + indent;

	if (!force && my_w > w)
	    return LAYOUT_NONE;

	locations[index] = new Rectangle(p.x, p.y, my_w, getRowHeight());
	index += 1;
	return (index >= parts.length) ? LAYOUT_FULL : LAYOUT_PARTIAL;
    }

    public void moveLast(int dx, int dy) {
	if (parts != null && index > 0 && index <= parts.length) {
	    locations[index-1].x += dx;
	    locations[index-1].y += dy;
	}
    }
}

final class HTMLTable extends HTMLElement
{
    int tableColNr, tableRowNr;
    int cellSpacing, cellPadding;
    HTMLElement [] tableRows; // Instances of table rows
    HTMLElement [][] tableCells; // Instances of table rows
    int [][] colSpans; // Contains cell widths as cells, -1 for spanned
    int [][] rowSpans; // - "" - heights - "" -
    HTMLElement caption = null;
    int [] minColWidths, maxColWidths;
    int [] colWidths, rowHeights;
    int borderWidth = 0, borderType = 0;
    int tableRules = 0;

    public HTMLTable(HTML h, String title, Hashtable params)
    {
	super(h, title, false, params); // false -> not the ending tag
    }

    // This will be called once the table contents have been read in;
    // now we can create the internal structure(s):
    public void initBlock()
    {
	if (properties == null)
	    properties = new HTMLProps();
	if (properties.cellSpacing != null) {
	    // Should we care about percentage values?
	    cellSpacing = properties.cellSpacing.value;
	} else cellSpacing = HTML.DEFAULT_CELL_SPACING;
	if (properties.cellPadding != null) {
	    // Should we care about percentage values?
	    cellPadding = properties.cellPadding.value;
	} else cellPadding = HTML.DEFAULT_CELL_PADDING;

	if (properties.borderSize == null) {
	    borderWidth = 0;
	    borderType = 0;
	    tableRules = 0;
	} else {
	    borderWidth = properties.borderSize.value;
	    if (properties.tableFrame != HTMLProps.UNKNOWN) {
		borderType = properties.tableFrame;
	    } else {
		borderType = HTMLProps.ALL_DIRS;
	    }
	    tableRules = HTMLProps.RULE_ALL;
	}

	if (properties.tableRules != HTMLProps.RULE_UNKNOWN)
	    tableRules = properties.tableRules;

	// We shouldn't recurse down the structure, at least not inside
	// other tables. However, to make things easier, let's not allow
	// structures to be nested at all (not sure if HTML even allows
	// such nesting in any case?)
	
	caption = HTML.removeHTMLElement(contents, "caption", false);

	if (caption != null) {
	    if (caption.hAlign == HTMLProps.UNKNOWN)
		caption.hAlign = HTML.DEFAULT_CAPTION_HALIGN;
	}
	
	HTMLElement row;
	Vector v = new Vector();

	// Then we need to find out the number of rows in the table
	// (as it can be done without considering cell spanning etc):
	while ((row = HTML.removeHTMLElement(contents, "tr", false)) != null)
	    v.addElement(row);

	tableRowNr = v.size();
	tableRows = new HTMLElement[tableRowNr];
	v.copyInto(tableRows);

	// And then we can deduce the number of columns as well, and
	// also mark the cells that are 'span-offs', ie. cells that
	// are occupied by table items that span multiple cells.
	Hashtable tmp_h = new Hashtable();

	int cols, i;
	for (i = 0; i < tableRowNr; i++) {
	    row = tableRows[i];
	    if (row.contents == null)
		continue;
	    cols = 0;
	    Enumeration en = row.contents.elements();
	    while (en.hasMoreElements()) {
		// If cells span from the previous row:
		while ((tmp_h.get(""+cols+","+i)) != null) {
		    cols++;
		}
		HTMLPrimitive h2 = (HTMLPrimitive) en.nextElement();
		if (!(h2 instanceof HTMLElement))
		    continue;
		HTMLElement h3 = (HTMLElement) h2;
		if (h3.end || (h3.type != ELEM_TABLE_HEADER &&
			       h3.type != ELEM_TABLE_ELEMENT))
		    continue;
		int x = (h3.properties == null) ? HTML.DEFAULT_COL_SPAN :
		      h3.properties.colSpan;
		int y = (h3.properties == null) ? HTML.DEFAULT_ROW_SPAN :
		      h3.properties.rowSpan;
		String orig = ""+cols+","+i;
		for (; --x >= 0; cols++) {
		    for (int z = 0; z < y; z++) {
			//if (x == 0 && z == 0)
			//    continue;
			tmp_h.put(""+cols+","+(i + z), orig);
		    }
		}
		tmp_h.put(orig, h3);
	    }
	    if (cols > tableColNr)
		tableColNr = cols;
	}
	int j;

	// We can now make the actual arrays based on the info gathered:
	tableCells = new HTMLElement[tableColNr][];
	colSpans = new int[tableColNr][];
	rowSpans = new int[tableColNr][];
	for (i = 0; i < tableColNr; i++) {
	    tableCells[i] = new HTMLElement[tableRowNr];
	    colSpans[i] = new int[tableRowNr];
	    rowSpans[i] = new int[tableRowNr];
	    for (j = 0; j < tableRowNr; j++) {
		Object o = tmp_h.get(""+i+","+j);
		if (o instanceof HTMLElement) {
		    tableCells[i][j] = (HTMLElement) o;
		    HTMLProps hp = ((HTMLElement) o).properties;
		    rowSpans[i][j] = (hp == null) ? HTML.DEFAULT_ROW_SPAN :
			hp.rowSpan;
		    colSpans[i][j] = (hp == null) ? HTML.DEFAULT_COL_SPAN :
			hp.colSpan;
		} else if (o instanceof String) {
		    tableCells[i][j] = (HTMLElement) tmp_h.get(o);
		    colSpans[i][j] = rowSpans[i][j] = -1;
		} else {
		}
	    }
	}

	minColWidths = new int[tableColNr];
	maxColWidths = new int[tableColNr];
        colWidths = new int[tableColNr];

        rowHeights = new int[tableRowNr];
    }

    private int getBaseWidth() {
	return 2 * borderWidth + (tableColNr + 1) * cellSpacing
	    + tableColNr * 2 * cellPadding;
    }

    public int getMinimumWidth() {

	// To get the minimum width, we need to count in:
	//
	// - Border-width x 2 (left & right border), if borders
	//   are on
	// - Cell-spacing, columns + 1 times
	// - Cell-padding, 2 * columns
	// - Sum of column-width minimums; for each column it's
	//   the maximum of minimums...

	if (minWidth < 0) {
	    calcMinWidths();
	    minWidth = getBaseWidth();
	    for (int i = 0; i < tableColNr; i++)
		minWidth += minColWidths[i];


	    // The caption width may exceed this...
	    if (caption != null) {
		int cw = caption.getMinimumWidth();
		if (cw > minWidth)
		    minWidth = cw;
	    }
	}

	return minWidth;
    }

    public int getMaximumWidth() {
	if (maxWidth < 0) {
	    calcMaxWidths();
	    maxWidth = getBaseWidth();
	    for (int i = 0; i < tableColNr; i++)
		maxWidth += maxColWidths[i];

	    // The caption width may exceed this...
	    if (caption != null) {
		int cw = caption.getMaximumWidth();
		if (cw > maxWidth)
		    maxWidth = cw;
	    }
	}
	return maxWidth;
    }

    private void calcMinWidths() {
	for (int col = 0; col < tableColNr; col++) {
	    minColWidths[col] = 0;
	    for (int row = 0; row < tableRowNr; row++) {
		HTMLElement cell = tableCells[col][row];
		int w = cell.getMinimumWidth();
		if (cell.properties != null)
		    w /= cell.properties.colSpan;
		if (w > minColWidths[col])
		    minColWidths[col] = w;
	    }
	}
    }

    private void calcMaxWidths() {
	for (int col = 0; col < tableColNr; col++) {
	    maxColWidths[col] = 0;
	    for (int row = 0; row < tableRowNr; row++) {
		HTMLElement cell = tableCells[col][row];
		int w = cell.getMaximumWidth();
		if (cell.properties != null)
		    w /= cell.properties.colSpan;
		if (w > maxColWidths[col])
		    maxColWidths[col] = w;
	    }
	}
    }

    private int calcWidths(int w, boolean abs) {
	int diff, i, share;

	int max = getMaximumWidth();
	// If we have enough room it's trivial:
	if (max <= w) {
	    diff = w - max;
	    // Extra space evenly distributed... Not the only way,
	    // but the simplest:
	    share = diff / tableColNr;
	    for (int col = 0; col < tableColNr; col++) {
		colWidths[col] = maxColWidths[col];
		if (abs && share > 0)
		    colWidths[col] += share;
	    }
	    return abs ? w : max;
	}

	// Now we assume that we are between the min and max. Thus,
	// we need to give each column some value between their
	// min and max as well. However, if some column's max is
	// equal or below their 'fair share' (width / nr_of_cols),
	// let's give it what it needs, and only 'punish' greedier
	// cells. :-)

	int min = getMinimumWidth();
	share = w / tableColNr;

	for (int col = 0; col < tableColNr; col++) {
	    if (maxColWidths[col] <= share) {
		colWidths[col] = maxColWidths[col];
	    } else {
		colWidths[col] = minColWidths[col];
	    }
	    w -= colWidths[col];
	    max -= colWidths[col];
	}

	int left = w;
	for (int col = 0; col < tableColNr; col++) {
	    if (colWidths[col] < maxColWidths[col])
		continue;
	    min = (maxColWidths[col] - minColWidths[col])* w / max;
	    colWidths[col] += min;
	    left -= min;
	}

	// And the last column gets what's left:
	if (left > 0)
	    colWidths[tableColNr - 1] += left;
	return w;
    }

    public void layoutMe(HTMLLayoutProps lprops) {
	int avail_w = lprops.right - lprops.x;
	boolean abs = false;

	if (properties != null) {
	    avail_w = HTMLSize.calcSize(avail_w, properties.width);
	    if (properties.width != null && properties.width.type
		== HTMLSize.TYPE_ABS)
		abs = true;
	}

	int min = getMinimumWidth();

	if (min > avail_w)
	    avail_w = min;

	location.width = calcWidths(avail_w, abs);
	location.x = lprops.x;
	location.y = lprops.y;

	// We need a new instance...
	HTMLLayoutProps lprops2 = (HTMLLayoutProps) lprops.clone();

	int curr_y = lprops.y + borderWidth;


	if (caption != null) {
	// Hmmh. In case caption is centered (often is), we need
	// to set the margins, based on the actual width the table
	// will get:
	    lprops2.x += borderWidth + cellSpacing;
	    lprops2.base_right = lprops2.right = lprops2.x + location.width;
	    caption.layoutMe(lprops2);
	    caption.location.height = lprops2.y - curr_y;
	    lprops2.y += 2 * cellSpacing + borderWidth;
	    curr_y = lprops2.y;
	}
	    
	for (int row = 0; row < tableRowNr; row++) {
	    int max_y = 0;
	    int curr_x = lprops.x + borderWidth;
	    curr_y += cellPadding + cellSpacing;
	    for (int col = 0; col < tableColNr; col++) {
		curr_x += cellPadding + cellSpacing;
		lprops2.y = curr_y;
		HTMLElement cell = tableCells[col][row];
		// Can't use rowSpans[] here, as we need the 'real'
		// row span, even if it's a "span-off":
		int rs = (cell.properties == null) ? HTML.DEFAULT_ROW_SPAN :
		      cell.properties.rowSpan;
		// We shouldn't apply layout multiple times to
		// spanned cells...
		if (colSpans[col][row] < 0) {
		    // ... they do effect the row heights, though:
		    if ((cell.location.height / rs) > max_y)
			max_y = cell.location.height / rs;
		    continue;
		}
		int cs = colSpans[col][row];
		cell.location.x = lprops2.x = lprops2.base_left = curr_x;
		cell.location.y = lprops2.y = curr_y;
		int w = 0;
		for (int t = cs; --t >= 0; ) {
		    w += colWidths[col + t];
		    if (t > 0)
			w += 2 * cellPadding + cellSpacing;
		}
		
		lprops2.base_right = lprops2.right = lprops2.x + w;
		int prev_x = lprops2.x;

		cell.layoutMe(lprops2);
		cell.location.height = lprops2.y - curr_y;
		if (cell.location.height > max_y)
		    max_y = cell.location.height;

		cell.location.width = w;
		curr_x += w + cellPadding;
	    }

	    rowHeights[row] = max_y;
	    curr_y += max_y + cellPadding;
	}
	curr_y += cellSpacing;
	location.height = curr_y - lprops.y;

	lprops.y = curr_y;

	// Now that we know the cell sizes we can handle the alignment:
	for (int row = 0; row < tableRowNr; row++) {
	    for (int col = 0; col < tableColNr; col++) {
		if (colSpans[col][row] < 0)
		    continue;
		int w = 0, h = 0;

		for (int t = colSpans[col][row]; --t >= 0; ) {
		    w += colWidths[col + t];
		    if (t > 0)
			w += 2 * cellPadding + cellSpacing;
		}
		for (int t = rowSpans[col][row]; --t >= 0; ) {
		    h += rowHeights[row + t];
		    if (t > 0)
			w += 2 * cellPadding + cellSpacing;
		}

		HTMLElement cell = tableCells[col][row];
		int dx = w - cell.location.width;
		int dy = h - cell.location.height;

		if (cell.hAlign == HTMLProps.RIGHT)
		    ;
		else if (cell.hAlign == HTMLProps.CENTER)
		    dx /= 2;
		else dx = 0;

		if (cell.vAlign == HTMLProps.BOTTOM)
		    ;
		else if (cell.vAlign == HTMLProps.MIDDLE)
		    dy /= 2;
		else dy = 0;

		if (dx != 0 || dy != 0) {
		    cell.moveMe(dx, dy);
		}
	    }
	}
    }

    public void asyncLayoutMe(HTMLLayoutProps lprops) {

	int orig_x = lprops.x;
	int orig_y = lprops.y;

	int pad_x = lprops.defFontWidth / 2;
	int pad_y = lprops.defRowHeight / 2;

	// First we need to find out the minimum width in which
	// the table could be fit in...
	int w = getMinimumWidth() + pad_x;

	if (!findMarginRoomFor(lprops, w)) {
	    System.err.println("Warning: asyncLayoutMe() can't make table element '"+this+"' fit, no matter what; width="+w+", only "+(lprops.right - lprops.x)+" available.");
	}

	int top_x = lprops.x;
	int top_y = lprops.y;
	int top_right = lprops.right;

	// Let's use some padding too:
	lprops.y += pad_y / 2;
	lprops.x += pad_x / 2;

	// Then we need to layout the table; the actual width used
	// can only then be seen:
	layoutMe(lprops);

	if (properties.hAlign == HTMLProps.LEFT) {
	    lprops.lefts = addMargin(lprops.lefts, lprops.y + pad_y / 2,
				     top_x + pad_x + location.width, true);
	} else {
	    int dx = location.x;
	    location.x = top_right - location.width - pad_x / 2;
	    dx = location.x - dx;
	    lprops.rights = addMargin(lprops.rights, lprops.y + pad_y / 2,
				     location.x - pad_x / 2, false);
	    moveMe(dx, 0);
	}

	lprops.x = orig_x;
	lprops.y = orig_y;

	updateMargins(lprops);
    }

    public void paintMe(Graphics g, HTMLPaintProps pp) {
	int x = location.x;
	int y = location.y;

	int w = location.width;
	int h = location.height;

	if (caption != null) {
	    caption.paintMe(g, pp);
	    y += caption.location.height + 2 * cellSpacing;
	    h -= caption.location.height + 2 * cellSpacing;
	}

	Color c, dark, bright;
	if (properties == null || properties.foreground == null)
	    c = HTML.DEFAULT_BACKGROUND_COLOUR;
	else c = properties.foreground;

	dark = c.darker();
	bright = c.brighter();

	// First we'll draw the border:
	if (borderWidth > 0) {
	    if (borderType > 0) {
		int curr_x = x, curr_y = y;
		int curr_x2 = x + w - 1, curr_y2 = y + h - 1;
		for (int i = 0; i < borderWidth; i++) {
		    g.setColor(bright);
		    if ((borderType & HTMLProps.TOP) != 0)
			g.drawLine(curr_x, curr_y, curr_x2, curr_y);
		    if ((borderType & HTMLProps.LEFT) != 0)
			g.drawLine(curr_x, curr_y, curr_x, curr_y2);

		    g.setColor(dark);
		    if ((borderType & HTMLProps.BOTTOM) != 0)
			g.drawLine(curr_x, curr_y2, curr_x2, curr_y2);
		    if ((borderType & HTMLProps.RIGHT) != 0)
			g.drawLine(curr_x2, curr_y, curr_x2, curr_y2);
		    curr_x++;
		    curr_y++;
		    curr_x2--;
		    curr_y2--;
		}
	    }
	}

	int curr_y = y + borderWidth;
	boolean draw_b = (tableRules > 0);
	int i;

	for (int row = 0; row < tableRowNr; row++) {
	    int curr_x = location.x + borderWidth;
	    curr_y += cellSpacing;

	    for (int col = 0; col < tableColNr; col++) {
		curr_x += cellSpacing;
		if (colSpans[col][row] < 0)
		    continue;

		tableCells[col][row].paintMe(g, pp);
		int curr_w = 0, curr_h = 0;

		for (i = 0; i < colSpans[col][row]; i++) {
		    curr_w += colWidths[col + i] + 2 * cellPadding;
		    if (i > 0) curr_w += cellSpacing;
		}
		for (i = 0; i < rowSpans[col][row]; i++) {
		    curr_h += rowHeights[row + i] + 2 * cellPadding;
		    if (i > 0) curr_h += cellSpacing;
		}

	    // Then the cell separators...
		if (draw_b) {
		    g.setColor(dark);
		    // Border on left:
		    if ((tableRules & HTMLProps.RULE_COLS) != 0)
		      g.drawLine(curr_x, curr_y, curr_x, curr_y + curr_h-1);
		    // Border above
		    if ((tableRules & HTMLProps.RULE_ROWS) != 0)
		       g.drawLine(curr_x, curr_y, curr_x + curr_w-1, curr_y);

		    g.setColor(bright);
		    // Border on right:
		    if ((tableRules & HTMLProps.RULE_COLS) != 0)
			g.drawLine(curr_x + curr_w-1, curr_y, curr_x + curr_w-1, curr_y + curr_h-1);
		    // Border bottom
		    if ((tableRules & HTMLProps.RULE_ROWS) != 0)
			g.drawLine(curr_x, curr_y + curr_h-1, curr_x + curr_w-1, curr_y + curr_h-1);
		}
		curr_x += curr_w;
	    }
	    curr_y += rowHeights[row] + 2 * cellPadding;
	}

    }
}

final class HTMLList extends HTMLElement
{
    // Contents contains stuff outside the items:
    HTMLElement [] listItems;

    boolean ordered;

    // The list type & depth could be stored here, as well...

    public HTMLList(HTML h, String title, Hashtable params, boolean ord)
    {
	super(h, title, false, params); // false -> not the ending tag

	ordered = ord;
    }

    // This will be called once the table contents have been read in;
    // now we can create the internal structure(s):
    public void initBlock()
    {
	removeEmpty(true, true);

	if (properties == null)
	    properties = new HTMLProps();

	// Let's remove the list items from the contents:
	Vector v = new Vector();

	HTMLElement h;

	int nr = 1;
	while (contents != null &&
	    (h = HTML.removeHTMLElement(contents, "li", false)) != null) {
	    v.addElement(h);
	    h.listItemNr = nr;
	    nr += 1;
	}

	listItems = new  HTMLElement[v.size()];
	v.copyInto(listItems);
    }

    /**** Some overridden methods.... ****/
    public int getMinimumWidth() {

	if (minWidth < 0) {

	    // This will get the min width of the 'caption' of the list:
	    minWidth = getMinimumWidth(contents);

	    for (int x = 0; x < listItems.length; x++) {
		int w = listItems[x].getMinimumWidth();
		if (w > minWidth)
		    minWidth = w;
	    }
	    minWidth += HTML.DEFAULT_LIST_INDENT * HTML.defaultFontWidth;
	}

	return minWidth;
    }

    public int getMaximumWidth() {

	if (maxWidth < 0) {

	    // This will get the min width of the 'caption' of the list:
	    maxWidth = getMaximumWidth(contents);

	    for (int x = 0; x < listItems.length; x++) {
		int w = listItems[x].getMaximumWidth();
		if (w > maxWidth)
		    maxWidth = w;
	    }
	    maxWidth += (HTML.DEFAULT_LIST_INDENT * HTML.defaultFontWidth);
	}

	return maxWidth;
    }

    public void layoutMe(HTMLLayoutProps lprops) {
	lprops.base_left += HTML.DEFAULT_LIST_INDENT * lprops.defFontWidth;
	updateMargins(lprops);

	// The contents - part should be easy to layout by the default
	// system, so:
	super.layoutMe(lprops);

	// Then we'll lay the items out:
	for (int i = 0; i < listItems.length; i++) {
	    HTMLElement item =  listItems[i];
	    item.layoutMe(lprops);
	    
	    // An empty item?
	    if (item.contents == null || item.contents.size() < 1) {
		// If so, we probably need to do the linefeed manually:
		lprops.y += HTML.defaultRowHeight;
		updateMargins(lprops);
	    }
	}

	lprops.base_left -= HTML.DEFAULT_LIST_INDENT * lprops.defFontWidth;
	updateMargins(lprops);
    }

    public void paintMe(Graphics g, HTMLPaintProps pp) {
	super.paintMe(g, pp);
	for (int i = 0; i < listItems.length; i++) {
	    listItems[i].paintMe(g, pp);
	}
    }

    public void invalidateLayout() {
	super.invalidateLayout();
	for (int i = 0; i < listItems.length; i++)
	    listItems[i].invalidateLayout();
    }

}

// Simple container struct used when painting the document:
class HTMLPaintProps
{
    Color bg = null;
    int y = 0;
    int top, bottom;
    int width = 0;
}
