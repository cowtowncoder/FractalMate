/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    HelpViewer.java

Description:
    A class (window) that displays the help-files;
    a tiny www-browser sort of thing.

Last changed:

Changes:

***************************************/

package com.cowtowncoder.fr8r;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import com.cowtowncoder.fr8r.ext.PlatformSpecific;
import com.cowtowncoder.fr8r.util.HTML;

import java.io.*;

public final class HelpViewer
    extends Dialog
    implements ActionListener, WindowListener

{
    // I prfer browser windows that have bigger height than width...
    public static int DEF_PAGE_WIDTH = 600;
    public static int DEF_PAGE_HEIGHT = 800;

    Color textColour = PlatformSpecific.getSystemColour(SystemColor.controlText);
    Color backgroundColour = PlatformSpecific.getSystemColour(SystemColor.control);
    Color borderColour = PlatformSpecific.getSystemColour(SystemColor.controlDkShadow);

    FractalMate fractlet;
    TextArea textDef;
    String def;
    ScrollPane scrPane;
    HTML document;
    String indexAddr, relIndexAddr;
    
    Button bIndex, bBack, bForward, bReload;
    Choice chLinks;

    Vector backPages = new Vector();
    Vector forwardPages = new Vector();
    Object currentPage = null; // File / URL

    public
    HelpViewer(Frame parent, FractalMate fr, Applet a,
	      String addr, int x, int y, boolean load_first)
    {
	super(parent, "Fractlet help", false);

	indexAddr = addr;
	int i = addr.lastIndexOf(HTML.DEFAULT_PATH_SEPARATOR);
	relIndexAddr = (i < 0) ? addr : addr.substring(i+1);

	Dimension scr_s = Toolkit.getDefaultToolkit().getScreenSize();
	if (scr_s.width < DEF_PAGE_WIDTH)
	    DEF_PAGE_WIDTH = scr_s.width;
	if (scr_s.height < DEF_PAGE_HEIGHT)
	    DEF_PAGE_HEIGHT = scr_s.height;

	fractlet = fr;
	setForeground(textColour);
	setBackground(backgroundColour);
	setLayout(new BorderLayout());
	addWindowListener(this);

	document = new HTML(a, this, new Dimension(DEF_PAGE_WIDTH,
						   DEF_PAGE_HEIGHT));
	document.addActionListener(this);

	scrPane = new FractletScrollPane(document);
	scrPane.setBackground(backgroundColour);
	scrPane.add(document);
	add(scrPane, "Center");

	scrPane.setSize(DEF_PAGE_WIDTH, DEF_PAGE_HEIGHT);

	Panel p = new Panel(new FlowLayout(FlowLayout.LEFT));
	p.setBackground(backgroundColour);
	
	bBack = new Button("< Back");
	bBack.addActionListener(this);
	bBack.setEnabled(false);
	bForward = new Button("Forward >");
	bForward.addActionListener(this);
	bForward.setEnabled(false);
	bReload = new Button("Reload");
	bReload.addActionListener(this);
	bIndex = new Button("Go to index");
	bIndex.addActionListener(this);
	chLinks = new Choice();
	//chLinks.addItemListener(this);

	p.add(bBack);
	p.add(bForward);
	p.add(new Label(" "));
	p.add(bIndex);
	p.add(bReload);
	p.add(new Label(" "));
	p.add(chLinks);
	p.add(new Panel());
	add(p, "North");

	pack();

	if (parent != null) {
	    if (x < 0 || y < 0) {
		Rectangle r = parent.getBounds();
		Dimension d = getSize();
		
		x = r.x + (r.width / 2 - d.width / 2);
		y = r.y + (r.height / 2 - d.height / 2);
		if ((x + d.width) > scr_s.width)
		    x = scr_s.width - d.width;
		if ((y + d.height) > scr_s.height)
		    x = scr_s.height - d.height;
		if (x < 0)
		    x = 0;
		if (y < 0)
		    y = 0;
	    }
	    setLocation(x, y);
	}

	// Need to use 'true' for inform, so that we get the action
	// event, which in turn allows us to define scroll bar settings:
	try {
	    document.addPageToLoad(indexAddr, true, true, scrPane);

	} catch (IOException ie) {
	    System.err.println("Failed to load the index: "+ie);
	}
    }

    public void setTitle(String str) {
	super.setTitle("Fractlet help: "+str);
    }

    /* Do we need to override this method to draw borders? */
	/*public void
    paint(Graphics g)
    {
	super.paint(g);
	if (borderColour != null) {
	    Dimension d = getSize();    
	    if (d != null) {
		g.setColor(borderColour);
		g.drawRect(0, 0, d.width - 1, d.height - 1);
		g.drawRect(2, 2, d.width - 5, d.height - 5);
		g.setColor(Color.black);
		g.drawRect(1, 1, d.width - 3, d.height - 3);
	    }
	}
	}*/

  /*** ActionListener-interface: ***/
    public void
    actionPerformed(ActionEvent e) 
    {
	Object o = e.getSource();
	Object addr;

	try {
	    if (o instanceof Component) {
		Component c = (Component) e.getSource();
	    
		if (c == bReload) {
		    document.addPageToLoad(currentPage, true, false, scrPane);
		} else if (c == bIndex) {
		    document.addPageToLoad(relIndexAddr, false, true, scrPane);
		} else if (c == bBack) {
		    if (backPages.size() < 1)
			return;
		    
		    addr = backPages.lastElement();
		    
		    document.addPageToLoad(addr, false, false, scrPane);
		    
		    backPages.removeElementAt(backPages.size() - 1);
		    // Or should we try to make sure we did actually
		    // succeed?
		    if (backPages.size() < 1)
			bBack.setEnabled(false);
		    try {
			forwardPages.addElement(currentPage);
		    } catch (Exception ee) {
			System.err.println("Warning: Failed to load the page: "+ee);
		    }
		    if (forwardPages.size() == 1)
			bForward.setEnabled(true);
		    
		    currentPage = addr;
		} else if (c == bForward) {
		    if (forwardPages.size() < 1)
			return;
		    addr = forwardPages.lastElement();		
		    try {
			document.addPageToLoad(addr, false, false, scrPane);
		    } catch (Exception ee) {
			System.err.println("Warning: Failed to load the page: "+ee);
		    }
		    
		    forwardPages.removeElementAt(forwardPages.size() - 1);
		    // Or should we try to make sure we did actually
		    // succeed?
		    if (forwardPages.size() < 1)
			bForward.setEnabled(false);
		    backPages.addElement(currentPage);
		    if (backPages.size() == 1)
			bBack.setEnabled(true);		    
		    
		    currentPage = addr;
		}
	    } else {
		// A link chosen & taken succesfully:
		
		addr = e.getSource();
		if (addr == null) {
		    System.err.println("Warning: null-link from HTML.");
		    return;
		}
		
		// Is this the first page loaded?
		if (currentPage == null) {
		    System.err.println("First load: '"+addr+"'");
		    
		    scrPane.doLayout();
		    
		    int w = HTML.defaultFontWidth;
		    int h = HTML.defaultRowHeight;
		    
		    Adjustable aa = scrPane.getHAdjustable();
		    Adjustable ab = scrPane.getVAdjustable();
		    
		    aa.setUnitIncrement(w);
		    ab.setUnitIncrement(h);
		    aa.setBlockIncrement(8 * w);
		    ab.setBlockIncrement(8 * h);
		    
		    document.setSize(document.getPreferredSize().width,
				     document.getPreferredSize().height);
		    currentPage = addr;
		    return;
		}
		
		forwardPages.removeAllElements();
		bForward.setEnabled(false);
		backPages.addElement(currentPage);
		currentPage = addr;
		if (backPages.size() == 1)
		    bBack.setEnabled(true);
		System.err.println("Link taken: '"+addr+"'");
		scrPane.doLayout();
		document.setSize(document.getPreferredSize().width,document.getPreferredSize().height);
	    }
	} catch (IOException ie) {
	    System.err.println("Warning: Failed to load a page: "+ie);
	}
    }
	
  /*** WindowListener-interface: ***/

    public void windowActivated(WindowEvent e)  { }
    public void windowClosed(WindowEvent e)  { }
    public void windowClosing(WindowEvent e)  {
	setVisible(false);
	fractlet.helpWindowClosed();
	dispose();
    }
    public void windowDeactivated(WindowEvent e)  { }
    public void windowDeiconified(WindowEvent e)  { }
    public void windowIconified(WindowEvent e)  { }
    public void windowOpened(WindowEvent e) { }
}

class FractletScrollPane
    extends ScrollPane
{
    HTML document;

    public FractletScrollPane(HTML d) {
	super(ScrollPane.SCROLLBARS_AS_NEEDED);
	document = d;
    }

    public void doLayout() {
	Dimension d = getViewportSize();
	document.layoutMe(d.width, false);
	setBackground(document.getBackground());
	super.doLayout();
    }
}
