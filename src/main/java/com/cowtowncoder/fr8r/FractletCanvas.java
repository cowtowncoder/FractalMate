/**********************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FractletCanvas.java

Description:
    Implements the canvas that displays
    both the patterns and the resulting
    fractal figure, as well as actually
    takes care of the fractal generation
    process itself.

Last changed:
    12-Nov-1999, TSa

Changes:

   01-Aug-1999, TSa:
     Added Undo/Redo - functionality
   22-Aug-1999, TSa:
     Finally implemented the time-limit
     for preview-mode.
   13-Sep-1999, TSa:
     Added highlighting used when the mouse
     pointer is moved above a line or point...
   29-Sep-1999, TSa:
     Small optimizations to calculations, resulting
     in ~5% speedup
   05-Oct-1999, TSa:
     Now uses TSProperties for storing various
     properties.
   12-Nov-1999, TSa:
     Loader - subsystem rewritten. More OO, better
     reusability and so forth.

***********************************************/

package com.cowtowncoder.fr8r;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

import com.cowtowncoder.fr8r.io.LoadedDef;
import com.cowtowncoder.fr8r.io.Loader;

public final class FractletCanvas
extends
    Canvas
implements
    Runnable,
    MouseListener, MouseMotionListener, KeyListener,
    FractletListener
{
    public final static int INSET_X = 2;
    public final static int INSET_Y = 2;
    // Actually, palette size is nr_of_colours * DEF_PALETTE_SIZE...
    // Furthermore, currently the size can't be changed from 256. :-/
    public final static int DEF_PALETTE_SIZE = 256;

    public final static int NODE_RADIUS = 4;
    public final static int NODE_OUTER_RADIUS = 6;
    public final static int SELECT_RANGE = NODE_RADIUS + 1;
    public final static float SELECT_RANGE_F = (float) SELECT_RANGE;

    // firstPattern is optional; if defined, is used for the first level;
    // mainPattern is otherwise used on the first level. mainPattern is
    // the main replacement pattern used when iterating; optionally a
    // secondary pattern may be used for every other level (could
    // of course define N levels that would cyclically be used, if
    // that seems useful)
    // normMainPattern is the normalized version of the main pattern
    // (ie. its end-to-end length is 1.0, and its aligned along X-axis)

    public final static int PAT_MAIN = 0;
    public final static int PAT_FIRST = 1;
    public final static int PAT_SECONDARY = 2;
    public final static int PAT_AMOUNT = 3;

    FractalSegment [][] patterns = new FractalSegment[PAT_AMOUNT][];

    Color [] fractalPalette; // Just few colours, from which fractalColours are calced:
    Color [] fractalColours; // 360 colours (or such)
    int recurseLevel = 0; // Max. recurse level; passed by Fractlet
                         // on beginDraw...
    int recurseHigh = 0; // What's the highest actual rec. level encountered
    Color DEF_BG_COLOUR = Color.black;

    Color currBgColour = DEF_BG_COLOUR;
    Color defFgColour = Color.gray;
    Color defBoldColour = Color.white;
    Color defDimColour = Color.darkGray;
    Color defHilightColour = Color.green.brighter();
    public final static Color DEF_POINT_NR_COLOUR = Color.red.brighter();

    double [] mainF = new double [] {

  // The default wireframe pattern with which to replace
    0.1, 0.9,
    0.38, 0.9,
    0.5, 0.6,
    0.62, 0.9,
    0.9, 0.9,
  };
    
  // Off-screen gfx buffer...
  Graphics fractalG = null;
  Image fractalI;
  int fractalX, fractalY; // Size of the image
  int view = FractalMate.MODE_MAIN;
  Image doubleI; // For double-buffered draws
  Graphics doubleG; // - "" -
  int doubleIwidth, doubleIheight;

  int windowX, windowY; // Drawable size of the window currently

  // And other components:
  FractalMate master; // Parent...
  FractletProperties properties; // Contains variours properties. :-)
  FractalSegment [] selectedSegments = null;
  int selectedSegmentNr = -1;
  int overSegmentNr = -1, overPointNr = -1;
  int selectedPatternNr = -1;

  private Thread drawing = null;
  private PreviewLimit previewLimit = null;
  private int previewStep = 1; // Used with previewLimit
  private boolean interrupted = false;

  final static long DEF_UPDATE_DELAY = 100;
  //final static long DEF_UPDATE_DELAY = 200;

  /*** Data structures for Undo/Redo - operations: ***/
  Undo undo = null, redo = null;

  public final static Object [] popupDefs = new Object [] {
      "menu", "pattern", "Pattern", null,
          "item", "pattern.to_main", "-> main pattern", null,
          "item", "pattern.to_1st_level", "-> 1st level pattern", null,
          "item", "pattern.to_2ndary", "-> secondary pattern", null,
          "item", "pattern.to_figure", "-> fractal figure", null,
           "", "", "", null,
          "item", "pattern.undo", "Undo", null,
          "item", "pattern.redo", "Redo", null,
          "item", "pattern.select_all", "Select all", null,
           "", "", "", null,
          "item", "pattern.center", "Center pattern", null,
          "menu", "pattern.fit",  "Fit pattern", null,
               "item", "pattern.fit.window", "to window", null,
               "item", "pattern.fit.preserve", "preserve aspect ratio", null,
          "menu", "pattern.align", "Align pattern", null,
               "item", "pattern.align.top", "Top", null,
               "item", "pattern.align.bottom", "Bottom", null,
               "item", "pattern.align.left", "Left", null,
               "item", "pattern.align.right", "Right", null,
          "menu", "pattern.align_selected", "Align selected", null,
           "", "", "", null,
              "item", "pattern.align_selected.top", "Top", null,
               "item", "pattern.align_selected.bottom", "Bottom", null,
               "item", "pattern.align_selected.left", "Left", null,
               "item", "pattern.align_selected.right", "Right", null,
               "item", "pattern.align_selected.center_vertical",
                           "Vert. center", null,
               "item", "pattern.align_selected.center_horizontal",
                           "Horiz. center", null,
          "cb", "pattern.double_buffering", "Use double-buffering",
                FractletProperties.FP_DOUBLE_BUFFERING,
          "cb", "pattern.auto_resize", "Auto-resize",
                FractletProperties.FP_AUTO_RESIZE,
          "cb", "pattern.show_point_nrs", "Show point numbers",
                FractletProperties.FP_SHOW_POINT_NRS,
          "cb", "pattern.show_line_dirs", "Show line directions",
               FractletProperties.FP_SHOW_LINE_DIRS,
          "cb", "pattern.prevent_multi_draw", "Prevent multiple draws",
                FractletProperties.FP_PREVENT_MULTIDRAW,
           "", "", "", null,
          "item", "pattern.about", "About Fractlet", null,      
 
       "menu", "figure", "Figure", null,
          "item", "figure.to_main", "-> main pattern", null,
          "item", "figure.to_1st_level", "-> 1st level pattern", null,
          "item", "figure.to_2ndary", "-> secondary pattern", null,
           "", "", "", null,
          "item", "figure.about", "About Fractlet", null,      

       "menu", "line", "Line", null,
          "cb", "line.invis", "Invisible", new Boolean(false),
          "cb", "line.upside_down", "Upside down", new Boolean(false),
          "cb", "line.mirrored", "Mirrored", new Boolean(false),
          "cb", "line.double_sided", "Double-sided", new Boolean(false),
          "cb", "line.final", "Final", new Boolean(false),
           "", "", "", null,
          "item", "line.split", "Split line", null,

       "menu", "point", "Point", null,
          "item", "point.delete", "Delete point", null,
  };
  public Hashtable popupComponents;

  // These are needed so we might get the key events...
  public boolean isFocusTraversable() { return true; } 
  /*public void addNotify() {
      super.addNotify();
      master.updateFocus();
      }*/ // Doesn't help a bit... :-/

  public
  FractletCanvas(FractalMate t, FractletProperties p)
  {
    master = t;
    properties = p;

    addMouseListener(this);
    addMouseMotionListener(this);

    addComponentListener(new ComponentAdapter() {
	public void componentResized(ComponentEvent e) {
	    if (resizePattern(false)) {
		repaint();
	    }
	}
    });

    addKeyListener(this);

    popupComponents = initPopups(popupDefs);

    // There are certain 'hard-coded' initializations, though...
    setMenuEnabled("pattern.to_1st_level", FractletProperties.DEF_USE_1ST_LEVEL);
    setMenuEnabled("figure.to_1st_level", FractletProperties.DEF_USE_1ST_LEVEL);
    setMenuEnabled("pattern.to_2ndary", FractletProperties.DEF_USE_2NDARY);
    setMenuEnabled("figure.to_2ndary", FractletProperties.DEF_USE_2NDARY);

    setMenuEnabled("pattern.undo", false);
    setMenuEnabled("pattern.redo", false);
  }

  public Hashtable
  initPopups(Object [] defs)
  {
      Menu last_menu = null;
      Hashtable comps = new Hashtable();
      for (int i = 0; i < defs.length; i += 4) {
	  String type = (String) defs[i];
	  String id = (String) defs[i+1];
	  String title = (String) defs[i+2];
	  Object opt = defs[i+3];

	  int first_dot = id.indexOf('.');
	  int last_dot = id.lastIndexOf('.');

	  // A separator?
	  if (type == null || type.length() == 0) {
	      last_menu.add(new MenuItem("-"));
	      continue;
	  }

	  if (type.equals("menu")) {
	      Menu m;
	      // Main-level menu?
	      if (first_dot < 0) {
		  PopupMenu p = new PopupMenu(title);
		  m = p;
		  add(p);
	      } else {
		  m = new Menu(title);
		  last_menu =(Menu) comps.get(id.substring(0, last_dot));
		  last_menu.add(m);
	      }
	      comps.put(id, m);
	      continue;
	  }

	  if (type.equals("item")) {
	      FractletMenuItem m = new FractletMenuItem(title, id, this);
	      comps.put(id, m);
	      last_menu = (Menu) comps.get(id.substring(0, last_dot));
	      last_menu.add(m);
	      continue;
	  }

	  if (type.equals("cb")) {
	      boolean state = false;
	      String id2 = null;
	      if (opt != null) {
		  if (opt instanceof Boolean)
		      state = ((Boolean) opt).booleanValue();
		  else if (opt instanceof String) {
		      id2 = (String) opt;
		  }
	      }
	      FractletCBM m = new FractletCBM(title, id, state, this,
					      properties, id2);
	      comps.put(id, m);
	      last_menu = (Menu) comps.get(id.substring(0, last_dot));
	      last_menu.add(m);
	      continue;
	  }

	  throw new Error("Error: unknown (popup) menu type '"+type+"'.");
      }

      return comps;
  }

  // This function is called when we're sure to be shown (well, if
  // anything can be guaranteed on Java... :-) )
  public void
  initDone()
  {

      Dimension d = getDrawableSize();

      windowX = d.width;
      windowY = d.height;
 
      // Also, certain parent menus may be disabled at this point:
      checkSelections(null);
  }

  /***** KeyListener - interface: *****/

  public void keyTyped(KeyEvent e) {
  }
  public void keyPressed(KeyEvent e) {
      int code = e.getKeyCode();
      int mod = e.getModifiers();      
      
      /* + and - keys zoom in/out ('scaling') */
      if (code == KeyEvent.VK_ADD || code == KeyEvent.VK_SUBTRACT) {
	  keyboardScaling(code, mod);
	  return;
      }
      
      /* and * and / keys rotate the pattern ('rotation') */
      if (code == KeyEvent.VK_MULTIPLY ||
	  code == KeyEvent.VK_SLASH ||
	  code == KeyEvent.VK_DIVIDE) {
	  keyboardRotation(code, mod);
	  return;
      }
      
      /* Arrow keys move the pattern ('translation')
       * (as well as numbers from number pad)
       */
      if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT
	  || code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN
	  || (code >= KeyEvent.VK_NUMPAD1 && code <= KeyEvent.VK_NUMPAD9)
	  || (code >= KeyEvent.VK_PAGE_UP && code <= KeyEvent.VK_HOME)) {
	  keyboardTranslation(code, mod);
	  return;
      }
      
  }
  public void keyReleased(KeyEvent e) {
  }

  /**** Simple get-/set-functions: ****/

  // This is called by 'TSPChanged()', from Fractlet:
  public void
  setPreview(boolean state)
  {
      if (state && previewLimit == null)
	  previewLimit = new PreviewLimit(this);
      repaint();
  }

  /**** Funcs for handling patterns: ****/

  // This shouldn't be called before we've been displayed;
  // otherwise the patterns can not be scaled correctly:
  public void
  initPatterns()
  {
      synchronized (patterns) {
	  patterns[PAT_MAIN] = makePattern(mainF, false);

	  Dimension d = getSize();
	  
	  patterns[PAT_MAIN] = translatePattern(scalePattern(patterns[PAT_MAIN],
            (float) (d.width - 2 * NODE_RADIUS - 2 * INSET_X),
	    (float) (d.height - 2 * NODE_RADIUS - 2 * INSET_Y)),
	     NODE_RADIUS, NODE_RADIUS);
	  patterns[PAT_FIRST] = null;
	  patterns[PAT_SECONDARY] = null;
      }
  }

  public static FractalSegment []
  translatePattern(FractalSegment [] f1, float dx, float dy)
  {
      if (f1 == null)
          return null;

      int i;
      int len = f1.length;

      FractalSegment [] fs = new FractalSegment[len];

      for (i = len; --i >= 0; ) {
	  fs[i] = (FractalSegment) (f1[i].clone());	  
	  fs[i].x += dx;
	  fs[i].y += dy;
      }
      return fs;
  }

  public static FractalSegment []
  translatePattern(FractalSegment [] f1, double dx, double dy)
  {
      return translatePattern(f1, (float) dx, (float) dy);
  }

  public static FractalSegment []
  clonePattern(FractalSegment [] f1)
  {
      int i;
      int len = f1.length;

      FractalSegment [] fs = new FractalSegment[len];

      for (i = len; --i >= 0; ) {
	  fs[i] = (FractalSegment) (f1[i].clone());	  
      }

      return fs;
  }

  public static void
  copyPattern(FractalSegment [] f1, FractalSegment [] f2)
  {
      for (int i = f1.length; --i >= 0; ) {
	  f2[i].copyFrom(f1[i]);
      }
  }

  public static FractalSegment []
  normalizePattern(FractalSegment [] f1)
  {
      int i;
      int len = f1.length;

      FractalSegment [] fs = clonePattern(f1);

      // When normalizing, we'll first need to move the starting point
      // to origo:

      for (i = len; --i >= 0; ) {
	  fs[i].x -= fs[0].x;
	  fs[i].y -= fs[0].y;
      }

      // And then we'll make the end point to be at 1.0 distance from
      // the start point (ie. start->end line length 1.0)
      float a = fs[len-1].x;
      float b = fs[len-1].y;
      float length = (float) Math.sqrt((a * a) + (b * b));

      for (i = 0; i < len; i++) {
	  fs[i].x /= length;
	  fs[i].y /= length;
      }
      
      // And finally we'll rotate the pattern along X-axis:
      // (ie. start at origo, end at (1, 0), 1 unit from origo to
      // the right)
      // However; if we need to rotate more than +/- 90 degrees,
      // we first better mirror the image to make the rotation be
      // under +/-90 degrees:

      a = fs[len-1].x;
      b = -fs[len-1].y;
      float c = -b;
      float d = a;

      for (i = 0; i < len; i++) {
	  FractalSegment s2 = fs[i];
	  float x = s2.x;
	  float y = s2.y;
	  s2.x = x * a + y * c;
	  s2.y = x * b + y * d;
      }
      return fs;
  }

  // This function builds a pattern from an array of segment end points,
  // optionally normalizing the pattern (ie. length -> 1.0, aligned along
  // X-axis; start point at 0, 0 and thus end point at 1, 0)
  public static FractalSegment []
  makePattern(double [] points, boolean normalize)
  {
    FractalSegment [] s = new FractalSegment[points.length / 2];
    FractalSegment s2;
    int len = points.length, i;

    for (i = 0; i < s.length; i++) {
       s[i] = s2 = new FractalSegment();
       s2.x = (float) points[i * 2];
       s2.y = (float) points[i * 2 + 1];
    }

    if (normalize)
       return normalizePattern(s);

    return s;
  }

  public static FractalSegment []
  scalePattern(FractalSegment [] orig, float x, float y)
  {
      FractalSegment [] p = new FractalSegment[orig.length];

      for (int i = orig.length; --i >= 0; ) {
	  p[i] = (FractalSegment) (orig[i].clone());
	  p[i].x *= x;
	  p[i].y *= y;
      }

      return p;
  }

  public static FractalSegment []
  scalePattern(FractalSegment [] orig, double x, double y,
	       float orig_x, float orig_y)
  {
      FractalSegment [] p = new FractalSegment[orig.length];

      for (int i = orig.length; --i >= 0; ) {
	  p[i] = (FractalSegment) (orig[i].clone());
	  p[i].x -= orig_x;
	  p[i].y -= orig_y;
	  p[i].x *= x;
	  p[i].y *= y;
	  p[i].x += orig_x;
	  p[i].y += orig_y;
      }

      return p;
  }

  public static FractalSegment []
  scalePattern(FractalSegment [] orig, double x, double y) {
      return scalePattern(orig, (float) x, (float) y);
  }

  public static FractalSegment []
  rotatePattern(FractalSegment [] orig, double angle,
		double orig_x, double orig_y)
  {
      FractalSegment [] p = new FractalSegment[orig.length];

      double co = Math.cos(angle);
      double si = Math.sin(angle);

      float o_x = (float) orig_x;
      float o_y = (float) orig_y;

      for (int i = orig.length; --i >= 0; ) {
	  p[i] = (FractalSegment) (orig[i].clone());
	  p[i].x -= o_x;
	  p[i].y -= o_y;
	  float new_x = (float) (p[i].x * co - p[i].y * si);
	  float new_y = (float) (p[i].x * si + p[i].y * co);
	  p[i].x = new_x + o_x;
	  p[i].y = new_y + o_y;
      }

      return p;
  }

  /*** Colours manipulation function(s): ***/

  /* How much trigonometric vs. linear changes things is an open
   * question... :-/
   */
  public static Color []
  generateColours(Color [] orig, boolean trigonometric)
  {
      int col = orig.length;
      Color [] new_cols = new Color[DEF_PALETTE_SIZE * col];
      for (int c = 0; c < col; c++) {
          Color c2 = orig[c];
          Color c1 = orig[(c + 1) % col];
	  for (int i = 0; i < DEF_PALETTE_SIZE; i++) {
	      if (trigonometric) {
		  double x = Math.cos(Math.PI / 2.0 * (double) i / (double) (DEF_PALETTE_SIZE - 1));
		  double y = Math.sin(Math.PI / 2.0 * (double) i / (double) (DEF_PALETTE_SIZE - 1));
		  x *= x;
		  y *= y;
		  int r = (int) (y * c1.getRed() + x * c2.getRed());
		  int g = (int) (y * c1.getGreen() + x * c2.getGreen());
		  int b = (int) (y * c1.getBlue() + x * c2.getBlue());
		  new_cols[c * 256 + i] = new Color(r > 255 ? 255 : r,
						    g > 255 ? 255 : g,
						    b > 255 ? 255 : b);
	      } else {
		  int compl = 255 - i;
		  new_cols[c * 256 + i] = new Color(
			   ((i * c1.getRed() + compl * c2.getRed()) / 256),
			   ((i * c1.getGreen() + compl * c2.getGreen()) / 256),
			   ((i * c1.getBlue() + compl * c2.getBlue()) / 256)
			   );
	      }
	  }
      }
      return new_cols;
  }

  /*** And the entry point for drawing the fractal image: ***/

  public void
  beginDraw(int rec)
  {
    // We should perhaps use locking here....
    if (drawing != null)
      return;

    // This is just to make sure that the preview limit check 
    // won't accidentally interrupt the drawing:
    previewStep += 1;

    recurseLevel = rec;
    recurseHigh = 1;

    // First we need to create the off-screen gfx buffer:
    Dimension d = getSize();
    fractalX = d.width - 2 * INSET_X;
    fractalY = d.height - 2 * INSET_Y;

    // If this isn't the first gfx context we've allocated, let's
    // dispose the context related to the old Image:
    if (fractalI != null) {
	fractalG.dispose();
    }

    fractalI = createImage(fractalX, fractalY);
    if (fractalI == null) {
      System.err.println("Error: Couldn't create the off-screen image!");
      System.exit(1);
    }
    fractalG = fractalI.getGraphics();
    if (fractalG == null) {
      System.err.println("Error: Couldn't get the graphics context of "
			 +"the off-screen image!");
      System.exit(1);
    }

    fractalG.setColor(currBgColour);
    fractalG.fillRect(0, 0, fractalX, fractalY);

    fractalG.setColor(defFgColour);

    // ... and off we go:
    drawing = new Thread(this);
    drawing.setPriority(Thread.NORM_PRIORITY - 1);
    drawing.start();
  }

  public void interruptDraw() {
    interrupted = true;
  }

  public void interruptPreview(int step) {
      if (step < previewStep) {
	  return;
      }
      interrupted = true;
  }

  public void
  run()
  {
      //Cursor old_crsr = getCursor();
      //setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));


    // And off we go; 
    long start = System.currentTimeMillis();

    FractletUpdater up = new FractletUpdater(this, DEF_UPDATE_DELAY);
    new Thread(up).start();

    double min_len = properties.getDoubleValue(FractletProperties.
					       FP_RECURSE_UNTIL);
    drawFractal(properties.getIntValue(FractletProperties.FP_RECURSE_LEVEL),
		min_len * min_len,
		fractalG,
		fractalX, fractalY);

    up.drawingDone();
    
    //setCursor(old_crsr);

    long total = System.currentTimeMillis() - start;

    System.err.println("Took "+(total / 1000)+"."+(total / 100 % 10) +" s.");

    repaint();

    drawing = null;
    master.drawingDone();
  }

  /*** The actual drawing functionality begins about here: ***/

  private final void
  drawFractal(int max_level, double rec_limit, Graphics g, int w, int h)
  {
    int secondary_offset = 0;

    FractalSegment [][] pat = new FractalSegment[3][];

    boolean use1stLevel, use2ndary;

    synchronized (patterns) {
	use1stLevel = properties.getBooleanValue(FractletProperties.
						     FP_USE_1ST_LEVEL);
	use2ndary = properties.getBooleanValue(FractletProperties.
						   FP_USE_2NDARY);
	pat[PAT_MAIN] = normalizePattern(patterns[PAT_MAIN]);
	
	if (use2ndary) { 
	    pat[PAT_SECONDARY] =  normalizePattern(patterns[PAT_SECONDARY]);
	    if (use1stLevel)
		secondary_offset = 1;
	    else
		secondary_offset = 0;
	} else pat[PAT_SECONDARY] = null;

	if (use1stLevel)
	    pat[PAT_FIRST] = clonePattern(patterns[PAT_FIRST]);
	else
	    pat[PAT_FIRST] = clonePattern(patterns[PAT_MAIN]);
    }

    drawFractal2(pat, max_level, rec_limit, g, secondary_offset, w, h,
		 use2ndary);
  }

  private final void
  drawFractal2(FractalSegment [][] init_patterns,
	       int max_levels, double rec_limit,
	       Graphics g, int secondary_offset,
	       int width, int height, boolean use2ndary)
  {
      int level = 0;
      float x1, x2, y1, y2, dx, dy, len;
      int a, b, c, d, j;
      float fx = (float) width;
      float fy = (float) height;
      boolean cols_from_angle = properties.getBooleanValue(FractletProperties.FP_COLOURS_BASED_ON_ANGLE);
      boolean no_multi = properties.getBooleanValue(FractletProperties.FP_PREVENT_MULTIDRAW);

      a = properties.getIntValue(FractletProperties.FP_COLOUR_SHIFT);
      while (a < 0) a+= 360;
      a = a % 360;
      double colour_shift = (double) a / 360.0;

      // Data structs that simulate recursion:
      FractalSegment [][] dParts = new FractalSegment[max_levels][];
      int [] dIndices = new int[max_levels];
      FractalSegment [][] dDoubled = new FractalSegment[max_levels][];
      int index = -1;
      double angle = 0.0;
      Color [] colours = fractalColours;

      // Internal structs used during the drawing:
      FractalSegment [] parts = init_patterns[PAT_FIRST];      
      FractalSegment [] new_parts = null;
      FractalSegment [] new_parts2 = null;
      FractalSegment repl, part2;

      // And some object pools, to prevent unnecessary object
      FractalSegment [][] cache = new FractalSegment[max_levels][];
      FractalSegment [][] cache2;

      int [] drawnPixels = new int[(width * height) / 32 + 1];

      // We only need to allocate the secondary object pool if
      // we have any doubled line segments...
      cache2 = null;
  foop:
      for (a = init_patterns.length; --a >= 0; ) {
	  if (init_patterns[a] == null)
	      continue;
	  new_parts = init_patterns[a];
	  for (b = new_parts.length - 1; --b >= 0; ) {
	      if (new_parts[b].doubleSided) {
		  break foop;
	      }
	  }
      }
      // This is silly. If it wasn't for NS Communicator's broken
      // bytecode verifier, we wouldn't need to allocate the
      // array in the latter case:
      if (a >= 0)
	  cache2 = new FractalSegment[max_levels][];
      else
	  cache2 = new FractalSegment[1][];

    interrupted = false;
    
  draw_loop:
      while (true) {

	  boolean last_level = (level >= max_levels) || interrupted;

      pattern_loop:
	  while (++index < (parts.length - 1)) {
	      FractalSegment part = parts[index];
	      if (part.invis)
		  continue pattern_loop; // Let's not draw invis segments.

	      x1 = part.x;
	      y1 = part.y;
	      x2 = parts[index+1].x;
	      y2 = parts[index+1].y;

	      if ((x1 < 0.0 && x2 < 0.0) // Nor lines outside the window
		  || (y1 < 0.0 && y2 < 0.0)
		  || (x1 > fx && x2 > fx)
		  || (y1 > fy && y2 > fy)
		  ) continue pattern_loop;

	      // If the start and end points are the same, no  need
	      // to iterate. However, we better draw the dot then:
	      dx = x2 - x1;
	      dy = y2 - y1;
	      len = (dx * dx + dy * dy);

	      if (last_level || len < rec_limit || part.Final) {
		  a = (int) x1;
		  b = (int) y1;

	  // Just a small 'sanity check'; small 'lines' are to be
	  // interpreted as dots no matter how rounding is done
	  // (cast to int is a truncation; no matter how short the
	  // line, it is possible for the x or y-coordinates to
	  // end up different...)
	  // Values such 0.25 (0.5 squared), 0.5 (ie. distance
	  // between edges of a cube with 0.5 pt edges) and
	  // 1.0 (end-to-end length 1.0) are possible; higher
	  // number means faster drawing too.
	  // With 1.0 the latter check is unnecessary, as well.

		  //if (len < (float) 0.25 || (a == c && b == d)) {
		  if (len < (float) 1.0) {
		      if (a < 0 || a >= width || b < 0 || b >= height)
			  continue pattern_loop;
		      if (no_multi) {
			  int ix = width * b + a;
			  // Also, let's only draw points once:
			  if ((drawnPixels[ix / 32] & (1 << (ix & 31))) != 0)
			      continue pattern_loop;
			  drawnPixels[ix / 32] |= (1 << (ix & 31));
		      }
		      // Inlined 'calcColour()':
		      angle = (double) level * colour_shift;
		      if (cols_from_angle)
			  angle += (Math.atan2(dy, dx) / Math.PI + 1.0) / 2.0;
		      g.setColor(colours[((int) (angle * (double)
				     colours.length)) % colours.length]);
		      g.drawLine(a, b, a, b);
		  } else {
		      c = (int) x2;
		      d = (int) y2;
		      // Inlined 'calcColour()':
		      angle = (double) level * colour_shift;
		      if (cols_from_angle)
			  angle += (Math.atan2(dy, dx) / Math.PI + 1.0) / 2.0;
		      g.setColor(colours[((int) (angle * (double)
				     colours.length)) % colours.length]);
		      g.drawLine(a, b, c, d);
		  }
		  continue pattern_loop;
	      }

	      // Otherwise, we'll do iterative replacement:

	      // When mirroring, we'll simply swap start & end points, ie.
	      // mirroring respect to the segment itself (180 degree rotation)
	      if (part.mirrored) {
		  float tmp = x1;
		  x1 = x2;
		  x2 = tmp;
		  tmp = y1;
		  y1 = y2;
		  y2 = tmp;
	      }

	      /* If we want to draw intermediate levels: */

	      if (properties.getBooleanValue(FractletProperties.FP_DRAW_INTERMEDIATE)) {
		  // Inlined 'calcColour()':
		  angle = (double) level * colour_shift;
		  if (cols_from_angle)
		      angle += (Math.atan2(dy, dx) / Math.PI + 1.0) / 2.0;
		  g.setColor(colours[((int) (angle * (double)
			         colours.length)) % colours.length]);
		  g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
	      }

	      // Otherwise, we need to replace segment with the pattern
	      // (recursively):
	      FractalSegment [] pattern = init_patterns[PAT_MAIN];

	      if (init_patterns[PAT_SECONDARY] != null &&
		  (level % 2)==secondary_offset)
		  pattern = init_patterns[PAT_SECONDARY];		
	      
	      if ((new_parts = cache[level]) == null) {
		  new_parts = new FractalSegment[pattern.length];
		  for (a = pattern.length; --a >= 0; ) {
		      new_parts[a] = new FractalSegment();
		  }
	      }
	      
	      part2 = new_parts[j = pattern.length - 1];
	      repl = pattern[j];
	      part2.x = x2;
	      part2.y = y2;
	      part2.upsideDown = part.upsideDown ^ repl.upsideDown;
	      part2.mirrored = repl.mirrored;
	      part2.invis = repl.invis;
	      part2.doubleSided = repl.doubleSided;
	      part2.Final = repl.Final;
	      
	      // The distance vector; need not be normalized or anything
	      x2 -= x1;
	      y2 -= y1;
	      
	      // But we also need its normal:
	      float x3 = -y2;
	      float y3 = x2;
	      
	      // When turning segment 'upside-down', we'll just need to rotate
		// the normal 180 degrees:
	      if (part.upsideDown) {
		  y3 = -x2;
		  x3 = y2;
	      }
	      
	      while (--j > 0) {
		  part2 = new_parts[j];
		  repl = pattern[j];
		  part2.x = x1 + repl.x * x2 + repl.y * x3;
		  part2.y = y1 + repl.x * y2 + repl.y * y3;
		  part2.upsideDown = part.upsideDown ^ repl.upsideDown;
		  part2.mirrored = repl.mirrored;
		  part2.invis = repl.invis;
		  part2.doubleSided = repl.doubleSided;
	      }
	      
	      part2 = new_parts[0];
	      repl = pattern[0];
	      part2.x = x1;
	      part2.y = y1;
	      part2.upsideDown = part.upsideDown ^ repl.upsideDown;
	      part2.mirrored = repl.mirrored;
	      part2.invis = repl.invis;
	      part2.doubleSided = repl.doubleSided;
	      part2.Final = repl.Final;
	 
	      /*
	      for (j = pattern.length; --j >= 0; ) {
		  (part2 = new_parts[j]).mirrored = (repl = pattern[j]).mirrored;
		  part2.x = x1 + repl.x * x2b + repl.y * x3;
		  part2.y = y1 + repl.x * y2b + repl.y * y3;
		  part2.upsideDown = part.upsideDown ^ repl.upsideDown;
		  part2.invis = repl.invis;
		  part2.doubleSided = repl.doubleSided;
		  part2.Final = repl.Final;
	      }
	      */
	      if (part.doubleSided) {
		  //if ((new_parts2 = cache2[level]) == null) {
		  new_parts2 = new FractalSegment[pattern.length];
		  for (a = pattern.length; --a >= 0; ) {
		      new_parts2[a] = new FractalSegment();
		  }

		  // We'll do simple upsideDown'ing:
		  y3 = -x2;
		  x3 = y2;
		  for (j = 0; j < pattern.length; j++) {
		      repl = pattern[j];
		      part2 = new_parts2[j];
		      part2.x = x1 + repl.x * x2 + repl.y * x3;
		      part2.y = y1 + repl.x * y2 + repl.y * y3;
		      part2.upsideDown = !(part.upsideDown ^ repl.upsideDown);
		      part2.mirrored = repl.mirrored;
		      part2.invis = repl.invis;
		      part2.doubleSided = repl.doubleSided;
		      part2.Final = repl.Final;
		  }
	      } else new_parts2 = null;
	      
	      if (level >= recurseHigh) {
		  recurseHigh = level + 1;
		  master.setRecurseDoneLevel(recurseHigh);
	      }

	      // Instead of recursion, let's simulate it by
	      // iteration... Hopefully faster this way:
	      dParts[level] = parts;
	      dIndices[level] = index;
	      dDoubled[level] = new_parts2;
	      level += 1;

	      parts = new_parts;
	      index = -1;

	      // Note that the double-sided lines are handled
	      // in a bit kludgy way, later on...
	      continue draw_loop;
	  }
	  if  ((level -= 1) < 0)
	      break;

	  if (dDoubled[level] != null) {
	      cache2[level] = parts;
	      parts = dDoubled[level];
	      dDoubled[level] = null;
	      level += 1;
	      index = -1;
	      // Stuff is already in stack, so we can just loop again:
	      continue draw_loop;
	  }
	  cache[level] = parts;
	  index = dIndices[level];
	  parts = dParts[level];
      }
  }

	  /*
  public final Color
  calcColour(float dx, float dy, int shift)
  {
      double angle = (Math.atan2(dy, dx) / Math.PI + 1.0) / 2.0;
      //double angle = 0.0;

      angle += (double) shift * colourShiftFactor;
      int len = fractalColours.length;
      return fractalColours[((int) (angle * (double) len)) % len];
  }

  public final Color
  calcColour(int shift)
  {
      double angle = (double) shift * colourShiftFactor;
      int len = fractalColours.length;
      return fractalColours[((int) (angle * (double) len)) % len];
  }
	  */
    
  public void update(Graphics g) { paint(g); }

  // This is a simple kludge to prevent excessive repaint()s
  // when dragging points...
  boolean repaintPending = false;    

  public void
  paint(Graphics g)
  {
      repaintPending = false;    

      Dimension d = getSize();
      int w = d.width;
      int h = d.height;

      g.setColor(currBgColour);
      for (int i = 0; i < INSET_X; i++) {
	  g.drawRect(i, i, w - 1 - 2 * i, h - 1 - 2 * i);
      }

      w -= 2 * INSET_X;
      h -= 2 * INSET_X;

      if (properties.getBooleanValue(FractletProperties.FP_DOUBLE_BUFFERING)) {
	  if (doubleI == null || w != doubleIwidth || h != doubleIheight) {
	      if (doubleI != null) {
		  doubleI = null;
		  doubleG.dispose();
	      }
	      doubleIwidth = w;
	      doubleIheight = h;
	      doubleI = createImage(w, h);
	      doubleG = doubleI.getGraphics();
	  }
	  paintMe(doubleG, w, h, 0, 0);

	  g.drawImage(doubleI, INSET_X, INSET_Y, this);

      } else {
	  if (doubleI != null) {
	      doubleI = null;
	      doubleG.dispose();
	      doubleG = null;
	  }
	  paintMe(g, w, h, INSET_X, INSET_Y);
      }
  }

  private FractalSegment [] tmpFs = new FractalSegment[0];

  void
  paintMe(Graphics g, int w, int h, int xoff, int yoff)
  {
      switch (view) {
	    
      case FractalMate.MODE_MAIN:
      case FractalMate.MODE_1ST_LEVEL:
      case FractalMate.MODE_2NDARY:

	  synchronized (patterns) {
	      // This is done just to shorten the locking time necessary...
	      FractalSegment [] fs = patterns[view];
	      if (tmpFs.length != fs.length) {
		  tmpFs = new FractalSegment[fs.length];
		  for (int i = fs.length; --i >= 0; ) {
		      tmpFs[i] = new FractalSegment();
		  }
	      }
	      copyPattern(fs, tmpFs);
	  }

	  g.setColor(currBgColour);    
	  g.fillRect(xoff, yoff, w, h);
	  if (properties.getBooleanValue(FractletProperties.FP_DRAW_PREVIEW)) {
	      double mt = 1000.0 * properties.getDoubleValue(FractletProperties.FP_PREVIEW_MAX_TIME);
	      previewLimit.enableLimit((long) mt, previewStep);
	      double ml = properties.
		  getDoubleValue(FractletProperties.FP_PREVIEW_MIN_LINE);
	      drawFractal(
		 properties.getIntValue(FractletProperties.FP_PREVIEW_MAX_REC),
		 ml * ml, g, w, h);
	      previewStep += 1;
	  }
	  drawPattern(g, tmpFs, w, h, xoff, yoff);

	  break;

      case FractalMate.MODE_RESULT:

	  if (fractalI == null) {
	      
	      g.setColor(currBgColour);    
	      g.fillRect(xoff, yoff, w, h);
	      return;
	  }
	  
	  g.setColor(currBgColour);
	  if (w > fractalX)
	      g.fillRect(fractalX + xoff, yoff, w - fractalX, h);
	  if (h > fractalY)
	      g.fillRect(xoff, fractalY + yoff, w, h - fractalY);

	  g.drawImage(fractalI, xoff, yoff, this);
	  break;

      default:

	  g.setColor(Color.red);    
	  g.fillRect(xoff, yoff, w, h);
	  break;
      }
  }

  // Actually, we perhaps should not cache the font info,
  // in case the font changes. But... It's not all that
  // crucial here:
  private int fontWidth = -1, fontHeight = -1, fontAscent = -1;

  int [] arrowX = new int[4];
  int [] arrowY = new int[4];

  void
  drawPattern(Graphics g, FractalSegment [] fs, int w, int h,
	      int xoff, int yoff)
  {
      // Shouldn't happen but:
      if (fs == null)
	  return;

      double angle = 0.0, old_angle = 0.0, dir;
      
      // First we'll draw the point numbers, so that they won't
      // mask any relevant information...
      if (properties.getBooleanValue(FractletProperties.FP_SHOW_POINT_NRS)) {
	  if (fontWidth < 0) {
	      FontMetrics fm;
	      if (getFont()!=null && (fm = getFontMetrics(getFont()))!= null){
		  fontWidth = fm.getMaxAdvance();
		  fontAscent = fm.getAscent();
		  fontHeight = fontAscent + fm.getDescent();
	      }
	  }

	  g.setColor(DEF_POINT_NR_COLOUR);
	  
	  for (int i = 0; i < fs.length; i++) {
	      FractalSegment s = fs[i];

	      if (i < (fs.length - 1)) {
		  angle = Math.atan2(fs[i+1].y - s.y, fs[i+1].x - s.x);
		  if (i > 0) {
		      dir = angle - old_angle;
		      if (dir < 0)
			  dir = -dir;
		      if (dir < Math.PI)
		        dir = (angle + old_angle) / 2.0 + Math.PI;
		      else
		        dir = (angle + old_angle) / 2.0;
		  } else dir = angle + Math.PI;
		  if (angle > 0)
		      old_angle = angle - Math.PI;
		  else
		      old_angle = angle + Math.PI;
	      } else {
		  dir = old_angle + Math.PI;
	      }
	      g.drawString(""+(i+1),
			   xoff + (int) (s.x + Math.cos(dir)
					    * (fontWidth + NODE_RADIUS))
			   - fontWidth / 3,
			   yoff + fontAscent / 2 + (int) (s.y + Math.sin(dir)
					    * (fontHeight + NODE_RADIUS))
			   );
	  }
      }

      g.setColor(defFgColour);    
      FractalSegment old_s = null;

      float x, y, x2, y2;
      int ix, iy, i;
      
      // Then the actual pattern, points & segments:

      for (i = 0, x2 = y2 = (float) 0.0; i < fs.length; i++) {

	  FractalSegment s = fs[i];
	  x = s.x + xoff;
	  y = s.y + yoff;
	  ix = (int) x;
	  iy = (int) y;

	  boolean hilite = (overSegmentNr == (i - 1));

	  // First the connecting segment:
	  if (i > 0) {
	      if (hilite)
		  g.setColor(defHilightColour);
	      else if (s.selected && old_s.selected) {
		  if (old_s.invis) {
		      g.setColor(defFgColour);
		  } else {
		      g.setColor(defBoldColour);
		  }
	      } else if (old_s.invis) {
		  g.setColor(defDimColour);
	      } else {
		  g.setColor(defFgColour);
	      }
	      // Invis segments are dashed lines:
	      if (old_s.invis) {
		  float len = (float) Math.sqrt((x2 - x) * (x2 - x)
						+ (y2 - y) * (y2 - y));
		  int steps = (int) (len / 6.0);

		  // Let's try to smallen the segment length if
		  // the total length is small...
		  if  (steps < 10)
		      steps += steps / 2;

		  // Let's always use odd number of steps, thus
		  // beginning and ending with a solid segment:
		  steps = steps | 1;

		  float dx = (x2 - x) / (float) steps;
		  float dy = (y2 - y) / (float) steps;

		  // And then we'll just draw the line:
		  for (int j = 0; j < steps; j += 2) {
		      float cx = x + (float) j * dx;
		      float cy = y + (float) j * dy;
		      if (j < (steps -1))
			  //g.drawLine((int) cx, (int) cy,  (int) (cx + dx), (int) (cy + dy));
			  g.drawLine((int) cx, (int) cy,
				     (int) (cx + dx),
				     (int) (cy + dy));
		      else 
			  g.drawLine((int) cx, (int) cy, 
				     (int) x2, (int) y2);
		  }
		  
	      // Other, visible segments show the direction
	      // arrow... (if enabled)
	      } else {
		  g.drawLine(ix, iy, (int) x2, (int) y2);
		  if (properties.getBooleanValue(FractletProperties.FP_SHOW_LINE_DIRS)) {
		      float dx, dy;
		      int points = 3;

		      if (old_s.Final) {
			  points = 4;
			  arrowX[0] = -6;
			  arrowY[0] = 6;
			  arrowX[1] = -6;
			  arrowY[1] = -6;
			  arrowX[2] = 6;
			  arrowY[2] = -6;
			  arrowX[3] = 6;
			  arrowY[3] = 6;
		      } else {

		      // Let's make single-sided slightly bigger
		      // in y-direction:
			  if (old_s.doubleSided) {
			      arrowX[0] = -8;
			      arrowY[0] = 6;
			      arrowX[1] = -8;
			      arrowY[1] = -6;
			      arrowX[2] = 8;
			      arrowY[2] = 0;
			  } else {
			      arrowX[0] = -8;
			      arrowY[0] = 8;
			      arrowX[1] = -8;
			      arrowY[1] = 0;
			      arrowX[2] = 8;
			      arrowY[2] = 0;
			  }
		      }

		      if (old_s.mirrored) {
			  dx = x2 - x;
			  dy = y2 - y;
		      } else {
			  dx = x - x2;
			  dy = y - y2;
		      }

		      float length = (float) Math.sqrt(dx * dx + dy * dy);
		      dx /= length;
		      dy /= length;

		      float dx2, dy2;

		      if (old_s.upsideDown) {
			  dx2 = -dy;
			  dy2 = dx;
		      } else {
			  dx2 = dy;
			  dy2 = -dx;
		      }
		      int ix2 = (int) ((x + x2) / 2.0);
		      int iy2 = (int) ((y + y2) / 2.0);
		      for (int j = 0; j < points; j++) {
			  int tmp = ix2 + (int) Math.round(arrowX[j] * dx
						+ arrowY[j] * dx2
						);
			  arrowY[j] = iy2 + (int) Math.round(arrowX[j] * dy
						+ arrowY[j] * dy2
						);
			  arrowX[j] = tmp;
		      }
		      if (points > 3) {
			  g.drawPolygon(arrowX, arrowY, points);
		      } else 
			  g.fillPolygon(arrowX, arrowY, points);
   
		  }
	      }
	      g.setColor(defFgColour);

	  }
	  // Then we'll draw the point itself:
	  hilite = (overPointNr == i);
	  if (hilite)
	      g.setColor(defHilightColour);
	  
	  g.fillOval(ix - NODE_RADIUS, iy - NODE_RADIUS,
		     2 * NODE_RADIUS, 2 * NODE_RADIUS);
	  g.drawLine(ix, iy, ix, iy);
	  
	  if (s.selected) {
	      if (!hilite)
		  g.setColor(defBoldColour);
	      g.drawOval(ix-NODE_OUTER_RADIUS, iy-NODE_OUTER_RADIUS,
			 2 * NODE_OUTER_RADIUS, 2 * NODE_OUTER_RADIUS);
	      if (!hilite)
		  g.setColor(defFgColour);
	  }
	  
	  if (hilite)
	      g.setColor(defFgColour);
	  
	  x2 = x;
	  y2 = y;
	  old_s = s;
      }

      if (rubberbanding) {
	  g.setColor(defBoldColour);
	  if (rubber.width < 0) {
	      if (rubber.height < 0) {
		  g.drawRect(rubber.x + rubber.width + xoff,
			     rubber.y + rubber.height + yoff,
			     -rubber.width, -rubber.height);
	      } else {
		  g.drawRect(rubber.x + rubber.width + xoff,
			     rubber.y + yoff,
			     -rubber.width, rubber.height);
	      }
	  } else {
	      if (rubber.height < 0) {
		  g.drawRect(rubber.x + xoff,
			     rubber.y + rubber.height + yoff,
			     rubber.width, -rubber.height);
	      } else {
		  g.drawRect(rubber.x + xoff, rubber.y + yoff,
			     rubber.width, rubber.height);
	      }
	  }
      }

      /*
      Graphics g2 = g.create(INSET_X, INSET_Y, w - 2 * INSET_X, h - 2 * INSET_Y);

      g2.setColor(Color.yellow);
      if (fs.length > 3)
	  drawBezier(g2, 5, fs);
      */
  }
  
  /*** Bezier-curves are not yet used... Perhaps in version 2.0? ***/
  /*
  public final void
  drawBezier(Graphics g, int steps, FractalSegment [] fs)
  {
      int i, j, size;

      for (i = 0, j = 4; i < steps; i++) {
	  j += j;
      }

      float [] x = new float[j];
      float [] y = new float[j];      

      for (i = 0; i < 4; i++) {
	  x[j - 4 + i] = fs[i].x;
	  y[j - 4 + i] = fs[i].y;
      }

      float x_b, y_b, x_e, y_e;
      float two = (float) 2.0;
      for (size = 8; size <= x.length; size *= 2) {
	  for (i = x.length - size; i < x.length; i += 8) {

	      j = (i + x.length) / 2;
	      x_b = x[j];
	      y_b = y[j];
	      x_e = x[j+3];
	      y_e = y[j+3];

	      float x_1 = (x_b + x[j+1]) / two;
	      float y_1 = (y_b + y[j+1]) / two;
	      float x_2 = (x[j+1] + x[j+2]) / two;
	      float y_2 = (y[j+1]+ y[j+2]) / two;
	      float x_3 = (x[j+2] + x_e) / two;
	      float y_3 = (y[j+2] + y_e) / two;
	      float x_12 = (x_1 + x_2) / two;
	      float y_12 = (y_1 + y_2) / two;
	      float x_23 = (x_2 + x_3) / two;
	      float y_23 = (y_2 + y_3) / two;
	  
	      float x_123 = (x_12 + x_23) / two;
	      float y_123 = (y_12 + y_23) / two;
      
	      x[i] = x_b;
	      y[i] = y_b;
	      x[i+1] = x_1;
	      y[i+1] = y_1;
	      x[i+2] = x_12;
	      y[i+2] = y_12;
	      x[i+3] = x_123;
	      y[i+3] = y_123;
	      x[i+4] = x_123;
	      y[i+4] = y_123;
	      x[i+5] = x_23;
	      y[i+5] = y_23;
	      x[i+6] = x_3;
	      y[i+6] = y_3;
	      x[i+7] = x_e;
	      y[i+7] = y_e;
	  }
      }

      g.setColor(Color.green);

      int x1 = (int) x[0];
      int y1 = (int) y[0];
      int x2, y2;
      double len = 0.0;

      // len->'real' distance... Calculated as the sum of partial
      // segments:

      for (i = 1; i < x.length; i++) {
	  x2 = (int) x[i];
	  y2 = (int) y[i];
	  //g.setColor(cols[i & 3]);
	  g.drawLine(x1, y1, x2, y2);
	  x1 = x2;
	  y1 = y2;

	  len += Math.sqrt((x[i] - x[i-1]) * (x[i] - x[i-1]) +
			   (y[i] - y[i-1]) * (y[i] - y[i-1]));
      }

      double min = dist(x[0], y[0], x[x.length-1], y[y.length-1]);
      double max = maxDist(fs[0].x, fs[0].y,
		     fs[1].x, fs[1].y,
		     fs[2].x, fs[2].y,
		     fs[3].x, fs[3].y);
      double simple = bezDist(fs[0].x, fs[0].y,
		     fs[1].x, fs[1].y,
		     fs[2].x, fs[2].y,
		     fs[3].x, fs[3].y);

      double compl1 = complDist(x, y, 2);
      double compl2 = complDist(x, y, 4);
      double compl3 = complDist(x, y, 8);
  }

  double complDist(float [] x, float [] y, int steps)
  {
      double res = 0.0;
      int piece = x.length / steps;
      for (int i = 0; i < steps; i++) {
	  int first = i * piece;
	  int last = (i + 1) * piece - 1;

	  res += bezDist(x[first], y[first],
			 x[first + piece / 3], y[first + piece / 3],
			 x[first + 2 * piece / 3], y[first + 2 * piece / 3],
			 x[last], y[last]);
	  res += dist(x[first], y[first], x[last], y[last]);
	  
      }

      return res / 2.0;
  }

  double maxDist(float x1, float y1,
		 float x2, float y2,
		 float x3, float y3,
		 float x4, float y4)
  {
      return Math.sqrt(
			     (x2 - x1) * (x2 - x1) +
			     (y2 - y1) * (y2 - y1)
			     )+
	  + Math.sqrt(
			     (x3 - x2) * (x3 - x2) +
			     (y3 - y2) * (y3 - y2)
			     )+
	  + Math.sqrt(
			     (x4 - x3) * (x4 - x3) +
			     (y4 - y3) * (y4 - y3)
			     )
	  ;
  }

  double dist(float x1, float y1,
	      float x2, float y2)
  {
      return Math.sqrt(
		       (x2 - x1) * (x2 - x1) +
		       (y2 - y1) * (y2 - y1)
		       );
  }

  double bezDist(float x1, float y1,
		 float x2, float y2,
		 float x3, float y3,
		 float x4, float y4)
  {
      double max = maxDist(x1, y1, x2, y2, x3, y3, x4, y4);
      double min = dist(x1, y1, x4, y4);
      return (min + max) / 2.0;
  }
*/

  // Called by Fractlet (parent), when the view is changed:
  public void
  viewChanged(int v)
  {
      view = v;
      repaint();
  }

  // Called by Fractlet (parent), when the colour palette is changed:
  public void
  paletteChanged(Color [] p, Color bg)
  {
      currBgColour = bg;
      fractalPalette = p;
      fractalColours = generateColours(p, false);
  }

  FractalSegment []
  getCurrentPattern()
  {
      switch (view) {

      case FractalMate.MODE_MAIN:

          return patterns[PAT_MAIN];

      case FractalMate.MODE_1ST_LEVEL:

          return patterns[PAT_FIRST];

      case FractalMate.MODE_2NDARY:

          return patterns[PAT_SECONDARY];

      default:
      }
      return null;
  }

  /*boolean
  replacePattern(int ix, FractalSegment [] fs)
  {
      if (ix < 0 || ix >= patterns.length)
	  return false;
      patterns[ix] = fs;
      if (ix == view)
	  selectedSegments = fs;
      return true;
      }*/

  boolean
  replacePattern(FractalSegment [] fs_orig, FractalSegment [] fs_new)
  {
      for (int i = 0; i < patterns.length; i++) {
	  if (patterns[i] == fs_orig) {
	      if (selectedSegments == fs_orig)
		  selectedSegments = fs_new;
	      patterns[i] = fs_new;
	      return true;
	  }
      }
      return false;
  }

  boolean
  replaceCurrentPattern(FractalSegment [] fs)
  {
      synchronized (patterns) {

	  switch (view) {
	      
	  case FractalMate.MODE_MAIN:
	      
	      patterns[PAT_MAIN] = fs;
	      break;

	  case FractalMate.MODE_1ST_LEVEL:

	      patterns[PAT_FIRST] = fs;
	      break;
	      
	  case FractalMate.MODE_2NDARY:
	      
	      patterns[PAT_SECONDARY] = fs;
	      break;
	      
	  default:
	      return false;
	  }
      
	  selectedSegments = fs;

      }
      return true;
  }

  void
  changePattern(int from, int to, boolean xchg)
  {
      if (from == to || from < 0 || to < 0 || from > PAT_AMOUNT
	  || to > PAT_AMOUNT)
	  return;

      synchronized (patterns) {

	  int ix = (from << Undo.SHIFT_SRC) | (to << Undo.SHIFT_DST);
	  if (xchg)
	      createUndo(Undo.OPER_EXCHANGE, ix);
	  else
	      createUndo(Undo.OPER_COPY, ix, patterns[to]);
	  
	  if (xchg) {
	      FractalSegment [] tmp = patterns[to];
	      patterns[to] = patterns[from];
	      patterns[from] = tmp;
	  } else {
	      patterns[to] = clonePattern(patterns[from]);
	  }
      }
      repaint();
  }

  
  /*** Transformations: ***/

  float []
  getBounds(FractalSegment [] fs, boolean only_sel)
  {
      boolean none = true;
      float x1, y1, x2, y2;
      
      x1 = x2 = y1 = y2 = (float) 0.0;

      for (int i = 0; i < fs.length; i++) {
	  FractalSegment f = fs[i];

	  if (only_sel && !fs[i].selected)
	      continue;

	  float x = (float) f.x;
	  float y = (float) f.y;

	  if (none) {
	      x1 = x2 = x;
	      y1 = y2 = y;
	      none = false;
	  } else {
	      if (x < x1)
		  x1 = x;
	      if (x > x2)
		  x2 = x;

	      if (y < y1)
		  y1 = y;
	      else if (y > y2)
		  y2 = y;
	  }
	    
      }

      return new float [] { x1, y1, x2, y2 };
  }

  public Dimension
  getDrawableSize()
  {
      Dimension d = getSize();

      return new Dimension(d.width - 2 * INSET_X, d.height - 2 * INSET_Y);
  }

  public void
  centerPattern()
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();

	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_CENTER, -1, fs);

	  float [] bounds = getBounds(fs, false);
	  Dimension d = getDrawableSize();
	  
	  float dx = ((float) d.width - (bounds[0] + bounds[2])) / (float) 2.0;
	  float dy = ((float) d.height - (bounds[1] + bounds[3])) / (float) 2.0;
	  
	  for (int i = 0; i < fs.length; i++) {
	      fs[i].x += dx;
	      fs[i].y += dy;
	  }
      }

      repaint();
  }

  public void
  alignPattern(String dir)
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  
	  // Should we disable the option when not applicable?
	  // (ie. when watching the output etc)
	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_ALIGN, -1, fs);
	  
	  float [] bounds = getBounds(fs, false);
	  
	  if (dir.equals("top")) {
	      fs = translatePattern(fs, (float) 0.0, -bounds[1]);
	  } else if (dir.equals("left")) {
	      fs = translatePattern(fs, -bounds[0], (float) 0.0);
	  } else if (dir.equals("bottom")) {
	      Dimension d = getDrawableSize();
	      fs = translatePattern(fs, (float) 0.0, d.height - bounds[3]);
	  } else if (dir.equals("right")) {
	      Dimension e = getDrawableSize();
	      fs = translatePattern(fs, e.width - bounds[2], (float) 0.0);
	  } else throw new Error("Error in alignPattern: unknown direction '"+dir+"'.");
	  
	  replaceCurrentPattern(fs);

      }

      repaint();
  }

  public void
  alignSelected(String dir)
  {
      synchronized (patterns) {
	  boolean x = true;
	  boolean min = true;
	  FractalSegment [] fs = getCurrentPattern();
	  float value;
	  
	  // Should we disable the option when not applicable?
	  // (ie. when watching the output or none selected?)
	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_ALIGN, -2, fs);
	  
	  float [] bounds = getBounds(fs, true);

	  if (dir.equals("right") || dir.equals("bottom"))
	      min = false;	  
	  if (dir.equals("top") || dir.equals("center_vertical")
	      || dir.equals("bottom"))
	      x = false;

	  if (dir.startsWith("center"))
	      value = (bounds[x ? 0 : 1] + bounds[x ? 2 : 3]) / (float) 2.0;
	  else
	      value = bounds[min ? (x ? 0 : 1) : (x ? 2 : 3)];

	  for (int i = fs.length; --i >= 0; ) {
	      if (!fs[i].selected)
		  continue;
	      if (x)
		  fs[i].x = value;
	      else fs[i].y = value;
	  }
      }

      repaint();
  }

  public void
  fitPattern(boolean preserve_aspect)
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();

	  // Should we disable the option when not applicable?
	  // (ie. when watching the output etc)
	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_FIT, (preserve_aspect ? -1 : -2), fs);

	  float [] bounds = getBounds(fs, false);
	  Dimension d = getDrawableSize();
	  
	  float f_x = ((float) d.width) / (bounds[2] - bounds[0] + 1);
	  float f_y = ((float) d.height) / (bounds[3] - bounds[1] + 1);
	  float dx = -bounds[0];
	  float dy = -bounds[1];
	  
	  if (preserve_aspect) {
	      if (f_x > f_y)
		  f_x = f_y;
	      else f_y = f_x;
	  }
	  
	  fs = translatePattern(fs, dx, dy);
	  fs = scalePattern(fs, f_x, f_y);
	  
	  if (preserve_aspect) {
	      // Let's do centering on the axis that's not fully maximized...
	      bounds = getBounds(fs, false);
	      dx = (d.width - (bounds[0] + bounds[2])) / (float) 2.0;
	      dy = (d.height - (bounds[1] + bounds[3])) / (float) 2.0;
	      fs = translatePattern(fs, dx, dy);
	  }
	  replaceCurrentPattern(fs);
      }

      repaint();
  }

  public void
  selectAll()
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();

	  for (int i = 0; i < fs.length; i++) {
	      fs[i].selected = true;
	  }
	  checkSelections(fs);
	  repaint();
      }
  }

  public void
  insertPoint(int nr, FractalSegment [] fs, FractalSegment p)
  {
      FractalSegment [] fs2 = new FractalSegment[fs.length + 1];

      for (int i = 0, j = 0; i < fs2.length; i++) {
	  if (i == nr) {
	      fs2[i] = p;
	  } else {
	      fs2[i] = fs[j++];
	  }
      }

      replacePattern(fs, fs2);
  }

  public void
  deletePoint(int nr, FractalSegment [] fs, boolean make_undo)
  {
      synchronized (patterns) {
	  int i, j;
	  if (fs == null) {
	      fs = getCurrentPattern();
	      if (fs == null)
		  return; // Shouldn't happen...
	  }

	  int amount = fs.length;

	  if (nr < 0) {
	      for (i = fs.length; --i >= 0; ) {
		  if (fs[i].selected)
		      amount--;
	      }
	      if (make_undo)
		  createUndo(Undo.OPER_DELETE_POINT, -1, fs);
	  } else {
	      amount--;
	      if (make_undo)
		  createUndo(Undo.OPER_DELETE_POINT, nr, fs[nr]);
	  }

	  FractalSegment [] new_fs = new FractalSegment[amount];

	  for (i = j = 0; i < fs.length; i++) {
	      if (i == nr || (nr < 0 && fs[i].selected)) {
		  // We better de-select them, just so that they
		  // won't cause probs with UNDOs:
		  fs[i].selected = false;
		  continue;
	      }
	      new_fs[j++] = fs[i];
	  }

	  replacePattern(fs, new_fs);
	  repaint();
      }
  }

  // Calling this function results in splitting _all_
  // selected lines...
  public int
  splitSelectedLine(boolean make_undo) 
  {
      int done = 0;
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  if (make_undo)
	      createUndo(Undo.OPER_SPLIT_LINE, -1, fs);
	  for (int i = fs.length - 1; --i >= 0; ) {
	      if (fs[i].selected && fs[i+1].selected) {
		  FractalSegment [] fs2 = splitLine(i, fs, false);
		  if (fs2 != null) {
		      done++;
		      fs = fs2;
		  }
	      }
	  }
	  replaceCurrentPattern(fs);
      }
      if (done > 0)
	  repaint();
      return done;
  }

  public FractalSegment []
  splitLine(int nr, FractalSegment [] fs, boolean make_undo)
  {
      int i;
      // Latter check is to make sure that it's not the
      // last point that was selected (should not happen but...)
      if (nr < 0 || nr >= (fs.length + 1))
	  return null;

      if (make_undo)
	  createUndo(Undo.OPER_SPLIT_LINE, nr);

      FractalSegment a = fs[nr];
      FractalSegment b = fs[nr+1];
	      
      float x = (a.x + b.x) / (float) 2.0;
      float y = (a.y + b.y) / (float) 2.0;
      FractalSegment [] new_fs = new FractalSegment[fs.length+1];
      
      for (i = 0; i <= nr; i++) {
	  new_fs[i] = fs[i];
      }
      for (; i < fs.length; i++) {
	  new_fs[i+1] = fs[i];
      }
      
      FractalSegment s = (FractalSegment) a.clone();
      s.x = x;
      s.y = y;
      new_fs[nr+1] = s;

      return new_fs;
  }

  // Returns true if we did resize the current pattern. If so, the
  // canvas needs to be redrawn.
  public boolean
  resizePattern(boolean force)
  {      
      Dimension d = getDrawableSize();

      int new_x = d.width;
      int new_y = d.height;

      if (new_x == windowX && new_y == windowY)
	  return false;

      boolean ret = false;

      synchronized (patterns) {
	  if (force || properties.getBooleanValue(FractletProperties.FP_AUTO_RESIZE)) {

	  // We'll need to scale _all_ patterns:
	  	  patterns[PAT_MAIN] = scalePattern(patterns[PAT_MAIN],
				     (float) new_x / (float) windowX,
				     (float) new_y / (float) windowY
				     );
	  if (patterns[PAT_FIRST] != null) 
	      patterns[PAT_FIRST] = scalePattern(patterns[PAT_FIRST],
				     (float) new_x / (float) windowX,
				     (float) new_y / (float) windowY
				     );
	  if (patterns[PAT_SECONDARY] != null) 
	      patterns[PAT_SECONDARY] = scalePattern(patterns[PAT_SECONDARY],
				     (float) new_x / (float) windowX,
				     (float) new_y / (float) windowY
				     );
	  ret = true;
	  }
      }
      windowX = new_x;
      windowY = new_y;
      return ret;
  }

  // Copies main pattern to 1st level pattern, unless 1st level has
  // already been defined:
  public void
  create1stLevel()
  {
      if (patterns[PAT_FIRST] != null)
	  return;

      synchronized (patterns) {
	  patterns[PAT_FIRST] = translatePattern(patterns[PAT_MAIN], 0, 0);
      } 
  }

  // Copies main pattern to 2ndary pattern, unless 2ndary pattern has
  // already been defined:
  public void
  create2ndary()
  {
      if (patterns[PAT_SECONDARY] != null)
	  return;

      synchronized (patterns) {
	  patterns[PAT_SECONDARY] = translatePattern(patterns[PAT_MAIN], 0, 0);
      }
  }

  /*** The core of the UI: *******/

  int
  nrOfSelectedPoints()
  {
      int nr = 0;

      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  
	  if (fs == null)
	      return 0;
	  
	  for (int i = fs.length; --i >= 0; ) {
	      if (fs[i].selected)
		  nr++;
	  }
      }
      return nr;
  }

  // Returns the (first) end-point of the pattern that is near the
  // given point:
  // (no need to sync; patterns ought to be sync'ed earlier)
  int
  selectedPoint(FractalSegment [] fs, int x, int y, int first)
  {
      if (fs == null)
	  return -1;

      for (int i = first; i < fs.length; i++) {
	  FractalSegment s = fs[i];
	  
	  int a = (int) s.x - x;
	  int b = (int) s.y - y;
	      
	  // Selected this point?
	  if (((a * a) + (b * b)) <= (4 * SELECT_RANGE * SELECT_RANGE))
	      return i;
      }
      return -1;
  }

  // Returns the (first) line segment (index of its first point)
  // that goes near the given point
  int
  selectedLine(FractalSegment [] fs, int x, int y, int first)
  {
      if (fs == null)
	  return -1;

      double unk_x, unk_y;

      double mx = (double) x;
      double my = (double) y;

      double dx, dy, len;

      for (int i = first; i < (fs.length - 1); i++) {
	  FractalSegment s = fs[i];

	  double x1 = s.x;
	  double y1 = s.y;
	  dx = fs[i+1].x - x1;
	  dy = fs[i+1].y - y1;
	  len = Math.sqrt((dx * dx) + (dy * dy));
	  dx /= len;
	  dy /= len;
	  double dx2 = -dy;
	  double dy2 = dx;
	  double new_x = mx - x1;
	  double new_y = my - y1;

	  unk_x = ((dy2 * new_x) - (dx2 * new_y));
	  unk_y = -((dx * new_y) - (dy * new_x));

	  if (unk_y < (double) 0.0)
	      unk_y = -unk_y;

	  if (unk_x < (double) 0.0)
	      unk_x = -unk_x;
	  else {
	      unk_x -= len;
	      if (unk_x < (double) 0.0)
		  unk_x = (double) 0.0;
	  }
	  if (unk_x < (SELECT_RANGE_F / 2.0) && unk_y < SELECT_RANGE_F) {
	      return i;
	  }
      }
      return -1;
  }

  private final boolean
  clearSelections(FractalSegment [] fs)
  {
      boolean changed = false;
      for (int i = 0; i < fs.length; i++) {
	  if (fs[i].selected) {
	      fs[i].selected = false;
	      changed = true;
	  }
      }
      return changed;
  }

  // This function should enable/disable those menus that operate
  // on selected points/lines:
  private final void
  checkSelections(FractalSegment [] fs)
  {
      if (fs == null) {
	  if ((fs = getCurrentPattern()) == null)
	      return;
      }

      boolean line = false;
      int nr = 0, lines = 0;
      for (int i = fs.length; --i >= 0; ) {
	  if (fs[i].selected) {
	      if (i > 0 && fs[i-1].selected)
		  lines++;
	      nr++;
	  }
      }

      // No selections?
      if (nr == 0) {
	  master.setMenuEnabled("edit.delete", false);
	  master.setMenuEnabled("edit.align_selected", false);
	  setMenuEnabled("pattern.align_selected", false);
      } else {
	  master.setMenuEnabled("edit.align_selected", true);
	  master.setMenuEnabled("edit.delete", (fs.length > (nr + 1)));
	  setMenuEnabled("pattern.align_selected", true);
      }
      if (lines == 0) {
	  master.setMenuEnabled("edit.split", false);
      } else {
	  master.setMenuEnabled("edit.split", true);
      }
  }
  
  int lastMouseX = -100, lastMouseY = -100;

  // Returns whether a repaint() is needed; repaint is necessary
  // if the high-lighting is changed...
  public boolean
  checkMousePosition(MouseEvent e, boolean off, FractalSegment [] fs,
		     int sel_line, int sel_point)
  { 
      boolean changed = false;

      if (off) {
	  lastMouseX = lastMouseY = -100;
	  master.setStatusLine("-", null, null);
	  if (overSegmentNr >= 0 || overPointNr >= 0)
	      changed = true;
	  overSegmentNr = overPointNr = -1;
	  return changed;
      }

      int x = e.getX() - INSET_X;
      int y = e.getY() - INSET_Y;

      if (x == lastMouseX && y == lastMouseY)
	  return false;
      
      lastMouseX = x;
      lastMouseY = y;

      if (sel_line < 0 && sel_point < -1)
	  sel_point = selectedPoint(fs, x, y, 0);
      if (sel_line < -1 && sel_point < 0)
	  sel_line = selectedLine(fs, x, y, 0);
     
      master.setStatusLine(""+x+","+y, null, null);

      if (sel_line != overSegmentNr || sel_point != overPointNr) {
	  changed = true;
	  overSegmentNr = sel_line;
	  overPointNr = sel_point;
      }

      return changed;
  }

  private final String
  getAngle(FractalSegment [] fs, int i)
  {
      int ia, ia2;
      
      double angle = -Math.atan2(fs[i-1].y - fs[i].y,
			  fs[i-1].x - fs[i].x);
      double angle2 = -Math.atan2(fs[i+1].y - fs[i].y,
				  fs[i+1].x - fs[i].x);
      // Let's put em to 0 - 360 degrees range:
      if (angle < 0.0)
	  angle += (2.0 * Math.PI);
      if (angle2 < 0.0)
	  angle2 += (2.0 * Math.PI);
      if (angle < angle2)
	  angle += (2.0 * Math.PI);
      angle = angle - angle2;
      ia = (int) (1800.0 * angle / Math.PI);
      if (ia < 0)
	  ia2 = (-ia) % 10;
      else
	  ia2 = ia % 10;
      return ""+(ia/10)+"."+ia2;
  }

  private final void
  checkSelectionString()
  {
  }

  // This function should update the status line to
  // indicate the status of the dragged point/line:
  public void
  checkDrag(FractalSegment [] fs, int mx, int my)
  {
      double angle;
      int ia, ia2;
      if (draggedLine >= 0) {
	  int i = draggedLine;
	  angle = -Math.atan2(fs[i+1].y - fs[i].y,
				    fs[i+1].x - fs[i].x);
	  ia = (int) (1800.0 * angle / Math.PI);
	  if (ia < 0)
	      ia2 = (-ia) % 10;
	  else
	      ia2 = ia % 10;
	  double len = Math.sqrt((fs[i+1].y - fs[i].y) * (fs[i+1].y - fs[i].y)
		      	 +(fs[i+1].x - fs[i].x) * (fs[i+1].x - fs[i].x));
	  master.setStatusLine(null, null,
	  "("+((int)fs[draggedLine].x)+","+((int)fs[draggedLine].y)+")->("
	  +((int)fs[draggedLine+1].x)+","+((int)fs[draggedLine+1].y)+"), "
	  +((int) len)+" pt, "+(ia / 10)+"."+ia2+" deg"
			       );
      } else if (draggedPoint >= 0) {
	  int i = draggedPoint;
	  if (i == 0)
	      i++;
	  double len = Math.sqrt((fs[i-1].y - fs[i].y) * (fs[i-1].y - fs[i].y)
			   +(fs[i-1].x - fs[i].x) * (fs[i-1].x - fs[i].x));

	  if (fs.length < 3) {
	    master.setStatusLine(null, null,
	"("+((int)fs[draggedPoint].x)+", "+((int)fs[draggedPoint].y)+"), "
			       +((int) len)+" pt.");
	  } else {
	    if (i == (fs.length - 1))
	      i--;
	    master.setStatusLine(null, null,
	"("+((int)fs[draggedPoint].x)+", "+((int)fs[draggedPoint].y)+"), "
			       +((int) len)+" pt, "
			       +getAngle(fs, i)+" deg");
	  }
      }
  }

  /***** MouseListener: *****/

  // Depending on whether Shift-key was used, we'll either
  // select just the newly chosen point(s) (clicking at a
  // segment selects the end points), or add the point(s)
  // to the previous selections.
  public void
  mouseClicked(MouseEvent e)
  {
      boolean changed = false;

      int mx = e.getX() - INSET_X;
      int my = e.getY() - INSET_Y;

      synchronized (patterns) {

	  FractalSegment [] fs = getCurrentPattern();
	  
	  requestFocus();
	  
	  if (fs == null)
	      return;
	  
	  if (checkPopup(e, fs))
	      return;
	  
	  int seg = selectedPoint(fs, mx, my, 0);
	  
	  if (!e.isShiftDown()) {
	      if (clearSelections(fs))
		  changed = true;
	  }
	  
	  if (seg >= 0) {
	      FractalSegment s = fs[seg];
	      s.selected = !s.selected;
	      changed = true;
	      checkMousePosition(e, false, fs, seg, -1);
	  } else if  ((seg = selectedLine(fs, mx, my, 0)) >= 0) {
	      fs[seg].selected ^= true;
	      fs[seg+1].selected ^= true;
	      changed = true;
	      checkMousePosition(e, false, fs, -1, seg);
	  } else {
	      changed = checkMousePosition(e, false, fs, -1, -1);
	  }

	  if (changed)
	      checkSelections(fs);
      }	  

      if (changed)
	  repaint();
  }

  public void
  mouseEntered(MouseEvent e)
  {
      boolean changed = false;
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  changed = checkMousePosition(e, false, fs, -2, -2);
      }
      if (changed)
	  repaint();
  }

  public void
  mouseExited(MouseEvent e)
  {
      boolean changed = false;
      if (overSegmentNr >= 0 || overPointNr >= 0) {
	  overSegmentNr = overPointNr = -1;
	  changed = true;
      }
      checkMousePosition(e, true, null, -1, -1);

      if (changed)
	  repaint();
  }

  public void
  mousePressed(MouseEvent e)
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  checkPopup(e, fs);
	  if (checkMousePosition(e, true, fs, -1, -1))
	      repaint();
      }
  }

  public void
  mouseReleased(MouseEvent e)
  {
      // Do we need to call checkMousePosition() here? We probably
      // need not, as the mouse shouldn't have been moved after the
      // previous mouse event?

      boolean r = false;
      draggedLine = draggedPoint = 0;

      do {
	  synchronized (patterns) {
	      FractalSegment [] fs = getCurrentPattern();
	      
	      if (checkPopup(e, fs)) {
		  r = true;
		  break;
	      }
	      
	      r = dragging || rubberbanding;
	      // We need to select the point(s):
	      
	      if (rubberbanding && fs != null) {
		  int x1 = rubber.x;
		  int y1 = rubber.y;
		  int x2 = x1 + rubber.width;
		  int y2 = y1 + rubber.height;
		  if (x2 < x1) {
		      int foo = x2;
		      x2 = x1;
		      x1 = foo;
		  }
		  if (y2 < y1) {
		      int foo = y2;
		      y2 = y1;
		      y1 = foo;
		  }
		  x1 -= SELECT_RANGE;
		  y1 -= SELECT_RANGE;
		  x2 += SELECT_RANGE;
		  y2 += SELECT_RANGE;
		  
		  if (!e.isShiftDown()) 
		      clearSelections(fs);
		  for (int i = 0; i < fs.length; i++) {
		      int x = (int) fs[i].x;
		      int y = (int) fs[i].y;
		      if (x < x1 || x > x2 || y < y1 || y > y2) {
			  continue;
		      }
		      fs[i].selected = true;
		  }
		  checkSelections(fs);
	      }
	      master.setStatusLine(null, "", "");
	  }
      } while (false);
	  
      dragging = false;
      rubberbanding = false;
      dragRotate = false;

      if (r)
	  repaint();
  }

  /***** MouseMotionListener: *****/

  boolean dragging = false;
  boolean rubberbanding = false;
  Rectangle rubber = new Rectangle();
  int dragX, dragY; // The position after the last drag-event
  int draggedPoint = -1, draggedLine = -1;
  int selectedPoint = -1, selectedLine = -1;
  float dragRotateCX, dragRotateCY;
  double dragAngle;
  boolean dragRotate = false, dragRotateOK = false;

  public void
  mouseDragged(MouseEvent e)
  {
      int mx = e.getX() - INSET_X;
      int my = e.getY() - INSET_Y;

      synchronized (patterns) {

	  FractalSegment [] fs = getCurrentPattern();
	  
	  if (fs == null)
	      return;

	  // -1 to indicate "we are not over any line or point"...
	  // Just so we won't have high-light during the dragging.
	  // (or should we?)

	  checkMousePosition(e, false, fs, -1, -1);
	  
	  if (!dragging) {

	      dragX = mx;
	      dragY = my;

	      // Rotating?
	      dragRotate = ((e.getModifiers() & InputEvent.CTRL_MASK) != 0);

	      if (dragRotate) {
		  // Now we need to store Undo-info:
		  createUndo(Undo.OPER_ROTATE, -2, fs);

		  // Need to calculate the middle-point of the selected
		  // patterns, if any...
		  dragRotateCX = (float) 0.0;
		  dragRotateCY = dragRotateCX;
		  int nr = 0;

		  for (int i = fs.length; --i >= 0; ) {
		      if (fs[i].selected) {
			  dragRotateCX += fs[i].x;
			  dragRotateCY += fs[i].y;
			  nr++;
		      }
		  }
		  // If no points selected, no rotation possible:
		  if (nr == 0) {
		      dragRotateOK = false;
		      return;
		  }
		  dragRotateCX /= (float) nr;
		  dragRotateCY /= (float) nr;
		  dragRotateOK = true;
		  dragAngle = -Math.atan2(mx - dragRotateCX,
					  my - dragRotateCY);
		  if (dragAngle < 0.0)
			  dragAngle += (2.0 * Math.PI);
	      } else { // Normal drag or rubberbanding:
		  
		  boolean line = false;
		  int seg = selectedPoint(fs, mx, my, 0);
		  
		  if (seg < 0) {
		      line = true;
		      draggedPoint = -1;
		      draggedLine = seg = selectedLine(fs, mx, my, 0);
		  } else {
		      draggedLine = -1;
		      draggedPoint = seg;
		  }
		  
		  // If we begin dragging and no point is at the selected
		  // point, we'll do rubberbanding:
		  if (seg < 0) {
		      rubberbanding = true;
		      rubber.x = mx;
		      rubber.y = my;
		      rubber.width = 0;
		      rubber.height = 0;
		      master.setStatusLine(null, "Rubber-banding ", "-");
		  } else {
		      // Otherwise we'll drag the selected point(s) / line(s):
		      rubberbanding = false;
		      
		      // Should we actually care about previous selections...?
		      // Probably yes, if we are dragging a point already selected?

		      // An extra check: if a line was selected, but we
		      // are now only to drag one of the end points (not
		      // the whole line), let's clear previous selections:
		      if (!e.isShiftDown()) {
			  if (!fs[seg].selected ||
			      (nrOfSelectedPoints() == 2 &&
			       (((seg + 1) < fs.length && fs[seg+1].selected)
				|| (seg > 0 && fs[seg-1].selected)))) {
			      clearSelections(fs);
			  }
		      }
		      fs[seg].selected = true;
		      if (line)
			  fs[seg+1].selected = true;
		      checkSelections(fs);

		      // Now we better store the Undo-info. Depending on
		      // whether we're moving just one point or more:
		      int i = fs.length;
		      for (; --i >= 0; )
			  if (fs[i].selected && i != seg)
			      break;

		      if (i < 0)
			  createUndo(Undo.OPER_DRAG_POINT, seg, fs[seg]);
		      else
			  createUndo(Undo.OPER_DRAG_POINT, -1, fs);

		      master.setStatusLine(null, "Dragging "+
					   ((draggedLine >= 0) ? "line #" :
					    "point #") + seg + ": ",
					   null);
		  }
	      }
	      dragging = true;
	      checkDrag(fs, mx, my);
	      repaint();
	      return;
	  }

	  // And then onto the actual dragging-business:
	  int dx = mx - dragX;
	  int dy = my - dragY;
	  
	  dragX = mx;
	  dragY = my;
	  
	  if (dx == 0 && dy == 0) // Shouldn't happen..?
	      return;
	  
	  if (rubberbanding) {
	      rubber.width += dx;
	      rubber.height += dy;
	  } else {	      
	      // Depending on whether we're dragging or rotating:
	      if (dragRotate) {	 
		  if (!dragRotateOK)
		      return;

		  double dragAngle2 = -Math.atan2(mx - dragRotateCX,
					      my - dragRotateCY);
		  if (dragAngle2 < 0.0)
		      dragAngle2 += (2.0 * Math.PI);

		  replaceCurrentPattern(rotatePattern(fs,
			 (dragAngle2 - dragAngle), dragRotateCX,
			 dragRotateCY));
		  dragAngle = dragAngle2;
	      } else { // if (dragRotate)...
		  for (int i = 0; i < fs.length; i++) {
		      FractalSegment s = fs[i];
		      
		      if (s.selected) {
			  s.x += dx;
			  s.y += dy;
		      }
		  }
	      }
	  }
	  checkDrag(fs, mx, my); // Will print status message(s)
      }

      if (!repaintPending) {
	  repaintPending = true;
	  repaint();
      }
  }

  public void
  mouseMoved(MouseEvent e)
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  if (checkMousePosition(e, false, fs, -2, -2))
	      repaint();
      }
  }

  /*** And other UI-related funcs: ***/
  private int popupX = 0, popupY = 0;

  public boolean
  checkPopup(MouseEvent e, FractalSegment [] fs)
  {
      boolean line = false;

      if (!e.isPopupTrigger())
	  return false;

      popupX = e.getX();
      popupY = e.getY();

      int mx = popupX - INSET_X;
      int my = popupY - INSET_Y;

      // fs == null -> the current view is not a pattern, thus:
      if (fs == null) {
	  getPopupMenu("figure").show(this, popupX, popupY);
	  return true;
      }

      int sel = selectedPoint(fs, mx, my, 0);

      if (sel < 0) {
	  sel = selectedLine(fs, mx, my, 0);
	  line = true;
      }

      // Popup-menu is only useful for segments...
      // (could have a different menu for points, and a default
      // 'empty' popup?)
      if (sel < 0) {
	  getPopupMenu("pattern").show(this, e.getX(), e.getY());
	  return true;
      }

      selectedSegmentNr = sel;
      selectedSegments = fs;
      selectedPatternNr = view;

      FractalSegment s = fs[sel];

      if (line) {
	  setMenuState("line.invis", s.invis);
	  setMenuState("line.mirrored", s.mirrored);
	  setMenuState("line.upside_down", s.upsideDown);
	  setMenuState("line.double_sided", s.doubleSided);
	  setMenuState("line.final", s.Final);
	  getPopupMenu("line").show(this, e.getX(), e.getY());
      } else {
	  // We are not to delete the last point...
	  setMenuEnabled("point.delete", fs.length >= 3);
	  getPopupMenu("point").show(this, e.getX(), e.getY());
      }
      return true;
  }

  /***** Import/export (load/save): ******/

  public LoadedDef
  readFigure(Reader r, boolean close)
      throws IOException
  {
      Loader l = new Loader(r, 0);
      Hashtable args = new Hashtable();

      LoadedDef def = l.loadNextTaggedDef(FractalMate.SAVE_FILE_TAG, args, close);

      Object ver = args.get("version");
      double version = -1.0;

      // Here we'd check the version compatibility...
      if (ver != null) {
	  if (!(ver instanceof String)) {
//System.err.println("Loading fractal image, version weird: '"+ver+"'");
	  } else {
	      String ve = (String) ver;
//System.err.println("Loading fractal image, saved by Fractlet version "+ve);
	  }
      } else {
//System.err.println("Loading fractal image, saved by Fractlet version: <unknown>");
      }

      return def;
  }

  private Color
  loadColour(LoadedDef col_list)
  {
      if (col_list == null || !col_list.containsList() || col_list.containedListSize() < 3) {
	  return null;
      }
      Enumeration en = col_list.getListContents();

      return new Color(
		       (int) ((LoadedDef) en.nextElement()).getDouble(),
		       (int) ((LoadedDef) en.nextElement()).getDouble(),
		       (int) ((LoadedDef) en.nextElement()).getDouble()
		       );
  }

  public boolean loadFigure(Reader r) throws IOException { return loadFigure(readFigure(r, true)); }

  public boolean
  loadFigure(LoadedDef l_defs)
      throws IOException
  {
      int i;
      String s;
      boolean state;
      LoadedDef fig_def, def, o2;

      /* And then we'll build the data structures: */

      synchronized (patterns) {

	  // Should we actually use the recurse-option, just in case?
	  fig_def = l_defs.findAssignmentValueFor(FractalMate.SAVE_TAG_FIGURE, false, true);

	  if (fig_def == null || !properties.loadFrom(fig_def)) {
	      throw new IOException("No pattern definitions found from the savefile.");
	  }
	  
	  if ((currBgColour = loadColour(fig_def.findAssignmentValueFor(FractalMate.SAVE_TAG_BACKGROUND,
								    false, false))) == null) {
	      currBgColour = DEF_BG_COLOUR;
	  }

	  if ((def = fig_def.findAssignmentValueFor(FractalMate.SAVE_TAG_PALETTE, false, false)) != null
	      && def.containsList()) {
	      Enumeration en = def.getListContents();
	      Vector p = new Vector();

	      while (en.hasMoreElements()) {
		  if (!def.containsList()) {
		      continue;
		  }
		  Color c = loadColour((LoadedDef) en.nextElement());
		  if (c != null) {
		      p.addElement(c);
		  }
	      }
	      if (p.size() > 0) {
		  Color [] cols = new Color[p.size()];
		  p.copyInto(cols);
		  master.paletteChanged(cols, currBgColour, true);

		  // Let's also inform the ColourBox, if it is visible?
	      }
	  }

	  def = fig_def.findAssignmentValueFor(FractalMate.SAVE_TAG_PATTERN_MAIN, false, true);
	  if (def != null) {
	      FractalSegment [] tmp = constructPattern(def);
	      if (tmp == null) {
		  return false;
	      }
	      patterns[PAT_MAIN] = tmp;
	  } else {
	      return false;
	  }
	  
	  boolean on_off = false;
	  
	  patterns[PAT_SECONDARY] = null;
	  def = fig_def.findAssignmentValueFor(FractalMate.SAVE_TAG_PATTERN_2NDARY, false, true);
	  if (def != null) {
	      FractalSegment [] tmp = constructPattern(def);
	      if (tmp != null) {
		  patterns[PAT_SECONDARY] = tmp;
		  on_off = true;
	      }
	  }
	  master.set2ndary(on_off, null);
	  
	  on_off = false;
	  patterns[PAT_FIRST] = null;
	  def = fig_def.findAssignmentValueFor(FractalMate.SAVE_TAG_PATTERN_1ST_LEVEL, false, true);
	  if (def != null) {
	      FractalSegment [] tmp = constructPattern(def);
	      if (tmp != null) {
		  patterns[PAT_FIRST] = tmp;
		  on_off = true;
	      }
	  }
	  master.set1stLevel(on_off, null);

	  if (properties.getBooleanValue(FractletProperties.FP_CENTER_ON_LOAD))
	  {
	      def = fig_def.findAssignmentValueFor(FractalMate.SAVE_TAG_WINDOW, false, false);
	      if (def.containsList() && def.containedListSize() >= 2) {
		  Enumeration en = def.getListContents();
		  int w = (int) ((LoadedDef) en.nextElement()).getDouble();
		  int h = (int) ((LoadedDef) en.nextElement()).getDouble();

		  // Centering is done by modifying the coordinates
		  // by the difference in window sizes:
		  Dimension d = getDrawableSize();

		  int dx = (d.width - w) / 2;
		  int dy = (d.height - h) / 2;
		  
		  for (i = 0; i < PAT_AMOUNT; i++) {
		      if (patterns[i] != null)
			  patterns[i] = translatePattern(patterns[i],
							 (float)dx,(float)dy);
		  }
	      }
	  }

	  master.setViewTo(FractalMate.MODE_MAIN);
      }

      repaint();
      return true;
  }

  FractalSegment []
  constructPattern(LoadedDef def)
  {
      Vector points = new Vector();   

      if (!def.containsList()) {
	  return null;
      }

      Enumeration en = def.getListContents();
      int s;

      while (en.hasMoreElements()) {
	  LoadedDef pt = (LoadedDef) en.nextElement();

	  if (!pt.containsList() || (s = pt.containedListSize()) < 3) {
	      continue;
	  }

	  Enumeration en2 = pt.getListContents();
	  FractalSegment f = new FractalSegment();

	  f.x = (float) ((LoadedDef) en2.nextElement()).getDouble();
	  f.y = (float) ((LoadedDef) en2.nextElement()).getDouble();
	  // Z-coordinate not used yet...

	  if (s > 3) {
	      en2.nextElement();
	      pt = (LoadedDef) en2.nextElement();
	      if (pt.containsString()) {
		  String props = pt.getString().toLowerCase();
		  if (props.indexOf('i') >= 0)
		      f.invis = true;
		  if (props.indexOf('u') >= 0)
		      f.upsideDown = true;
		  if (props.indexOf('m') >= 0)
		      f.mirrored = true;
		  if (props.indexOf('d') >= 0)
		      f.doubleSided = true;
		  if (props.indexOf('f') >= 0)
		      f.Final = true;
	      }
	  }

	  points.addElement(f);
      }

      FractalSegment [] fs = new FractalSegment[points.size()];
      points.copyInto(fs);

      return fs;
  }

  public boolean
  saveFigure(Writer w)
  {
      PrintWriter out = new PrintWriter(w);
      int i;
      
      // First we'll write the tag that identifies this as being
      // a figure produced by fractlet:

      out.print("<" + FractalMate.SAVE_FILE_TAG + " VERSION="
		+ FractalMate.FRACTLET_VERSION + ">");

      out.println();
      out.println("# Fractlet figure definition, created with Fractlet "
		+ FractalMate.FRACTLET_VERSION);
      out.println();

      out.println(FractalMate.SAVE_TAG_FIGURE+" = (");

      // First we'll dump the saveable properties:
      properties.saveTo(out);

      Dimension d = getDrawableSize();
      out.print("\t" + FractalMate.SAVE_TAG_WINDOW + " = (");
      out.print(d.width);
      out.print(", ");
      out.print(d.height);
      out.println(");");

      // Then the colour palette we're using:
      out.print("\t"+FractalMate.SAVE_TAG_BACKGROUND + " = (");
      out.print(currBgColour.getRed());
      out.print(",");
      out.print(currBgColour.getGreen());
      out.print(",");
      out.print(currBgColour.getBlue());
      out.println(");");

      out.print("\t\t");
      out.println("\t"+FractalMate.SAVE_TAG_PALETTE + " = (");
      out.print("\t\t");
      for (i = 0; i < fractalPalette.length; i++) {
	  if (i > 0)
	      out.print(", ");
	  out.print("(");
	  out.print(fractalPalette[i].getRed());
	  out.print(",");
	  out.print(fractalPalette[i].getGreen());
	  out.print(",");
	  out.print(fractalPalette[i].getBlue());
	  out.print(")");
      }
      out.println();
      out.println("\t);");

      // And finally the pattern data:
      out.println();

      synchronized (patterns) {
	  savePatternDef(out, FractalMate.SAVE_TAG_PATTERN_MAIN,patterns[PAT_MAIN]);
	  if (properties.getBooleanValue(FractletProperties.FP_USE_1ST_LEVEL)) {
	      savePatternDef(out, FractalMate.SAVE_TAG_PATTERN_1ST_LEVEL,
			     patterns[PAT_FIRST]);
	  }
	  if (properties.getBooleanValue(FractletProperties.FP_USE_2NDARY)) {
	      savePatternDef(out, FractalMate.SAVE_TAG_PATTERN_2NDARY,
			     patterns[PAT_SECONDARY]);
	  }
      }
	  
      // And then we'll end the figure definition:
      out.println(");");
      out.println();
      out.println("</" + FractalMate.SAVE_FILE_TAG + ">");
      
      out.flush();
      
      return true;
  }

  final void
  saveStringAttribute(PrintWriter out, String attr, String value)
  {
     out.println("\t"+attr+" = \""+URLEncoder.encode(value)+"\";");
  }

  final void
  savePatternDef(PrintWriter out, String fig_name, FractalSegment [] fs)
  {
     out.println("\t"+fig_name+" = (");
     for (int i = 0; i < fs.length; i++) {
	 FractalSegment f = fs[i];
	 out.print("\t\t(");
	 out.print(f.x);
	 out.print(", ");
	 out.print(f.y);
	 out.print(", 0.0, \"");
	 if (f.invis)
	     out.print("I");
	 if (f.mirrored)
	     out.print("M");
	 if (f.upsideDown)
	     out.print("U");
	 if (f.doubleSided)
	     out.print("D");
	 if (f.Final)
	     out.print("F");
	 out.print("\")");
	 if (i < (fs.length - 1))
	     out.print(",");
	 out.println();
     }
     out.println("\t);");
  }

  /*** Transformations caused by keyboard events: ***/
  private double [] translationSteps = new double [] {
      1, 4, 16, 64 /* in pixels */
  }; 
  private double [] rotationSteps = new double [] {
      1.0 * Math.PI / 180.0,
      5.0 * Math.PI / 180.0,
      15.0 * Math.PI / 180.0,
      45.0 * Math.PI / 180.0,
  };
  private double [] scalingSteps = new double [] {
      5.0, 10.0, 25.0, 50.0, /* in percents */
      // Note that minimum is still the percentage that expands
      // at least by 1 pixel
  };

  final int
  getStep(int mod)
  {
      if ((mod & (InputEvent.ALT_MASK | InputEvent.META_MASK)) != 0)
	  return 3;
      if ((mod & InputEvent.CTRL_MASK) != 0)
	  return 2;
      if ((mod & InputEvent.SHIFT_MASK) != 0)
	  return 1;
      return 0;
  }

  public void
  keyboardTranslation(int key, int mod)
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_TRANSLATE, -1, fs);
	  
	  double dx = 0.0, dy = 0.0;
	  
	  switch (key) {
	      
	  case KeyEvent.VK_LEFT:
	  case KeyEvent.VK_NUMPAD4:
	      dx = -1.0;
	      break;
	      
	  case KeyEvent.VK_RIGHT:
	  case KeyEvent.VK_NUMPAD6:
	      dx = 1.0;
	      break;
	      
	  case KeyEvent.VK_UP:
	  case KeyEvent.VK_NUMPAD8:
	      dy = -1.0;
	      break;
	      
	  case KeyEvent.VK_DOWN:
	  case KeyEvent.VK_NUMPAD2:
	      dy = 1.0;
	      break;
	      
	  case KeyEvent.VK_NUMPAD7:
	  case KeyEvent.VK_HOME:
	      dx = -1.0;
	      dy = -1.0;
	      break;
	      
	  case KeyEvent.VK_NUMPAD9:
	  case KeyEvent.VK_PAGE_UP:
	      dx = 1.0;
	      dy = -1.0;
	      break;
	      
	  case KeyEvent.VK_NUMPAD1:
	  case KeyEvent.VK_END:
	      dx = -1.0;
	      dy = 1.0;
	  break;
	  
	  case KeyEvent.VK_NUMPAD3:
	  case KeyEvent.VK_PAGE_DOWN:
	      dx = 1.0;
	      dy = 1.0;
	      break;
	      
	  default:
	      return;
	  }
	  
	  double amount = translationSteps[getStep(mod)];
	  replaceCurrentPattern(translatePattern(fs, dx * amount, dy * amount));
      }
      repaint();
 }

  public void
  keyboardRotation(int key, int mod)
  {
      synchronized (patterns) {

	  FractalSegment [] fs = getCurrentPattern();
	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_ROTATE, -1, fs);
	  
	  double amount = rotationSteps[getStep(mod)];
	  
	  if (key == KeyEvent.VK_MULTIPLY)
	      amount = (Math.PI * 2.0) - amount;
	  
	  // We need to calculate the weighted average, ie.
	  // middle point by area... I hope this suffices:
	  // (simpler alternative is the middle of the bounding
	  // box, but that doesn't work well)

	  double x = 0.0, y = x;

	  for (int i = fs.length; --i >= 0; ) {
	      x += fs[i].x;
	      y += fs[i].y;
	  }
	  x /= fs.length;
	  y /= fs.length;
	  
	  replaceCurrentPattern(rotatePattern(fs, amount, x, y));
      }
      repaint();
  }

  public void
  keyboardScaling(int key, int mod)
  {
      synchronized (patterns) {
	  FractalSegment [] fs = getCurrentPattern();
	  if (fs == null)
	      return;

	  createUndo(Undo.OPER_SCALE, -1, fs);
	  
	  double amount = scalingSteps[getStep(mod)] / 100.0;
	  
	  if (key == KeyEvent.VK_SUBTRACT)
	      amount = 1.0 - amount;
	  else
	      amount = 1.0 + amount;
	  
	  float [] b = getBounds(fs, false);
	  
	  replaceCurrentPattern(scalePattern(fs, amount, amount,
		   (b[0] + b[2]) / (float) 2.0, (b[1] + b[3]) / (float) 2.0
	  ));
      }
      repaint();
  }

  public final Image
  getImage()
  {
      return fractalI;
  }

  double
  fractalLengthSquared(FractalSegment b, FractalSegment e) 
  {
      return (b.x - e.x) * (b.x - e.x) +
	  (b.y - e.y) * (b.y - e.y);
  }

  boolean
  extendingFractal(FractalSegment [] fs)
  {
      double end_to_end = fractalLengthSquared(fs[0], fs[fs.length - 1]);
      for (int i = fs.length; --i > 0; ) {
	  if (fractalLengthSquared(fs[i-1], fs[i]) > end_to_end)
	      return true;
      }
      return false;
  }

  public final boolean
  extendingFractal()
  {
      synchronized (patterns) {
	  return (extendingFractal(patterns[PAT_MAIN])) ||
	      (properties.getBooleanValue(FractletProperties.FP_USE_2NDARY)
	      && extendingFractal(patterns[PAT_SECONDARY]));

      }
  }

  /*** Implementation of FractletListener: ****/

  public void
  doMenuAction(String id, AWTEvent ev)
  {
      String second;
      int i = id.indexOf('.');

      if (i < 0)
	  second = "";
      else {
	  second = id.substring(i+1);
	  id = id.substring(0, i);
      }

      /****** Pattern-menu ****/
      if (id.equals("pattern")) {
	  if (second.equals("center")) {
	    centerPattern();
	    repaint();
	  } else if (second.equals("select_all")) {
	      selectAll();
	  } else if (second.equals("undo")) {
	      doUndo();
	  } else if (second.equals("redo")) {
	      doRedo();
	  } else if (second.equals("fit.window")) {
	      fitPattern(false);
	  } else if (second.equals("fit.preserve")) {
	      fitPattern(true);
	  } else if (second.startsWith("align_selected.")) {
	      alignSelected(second.substring(15));
	  } else if (second.startsWith("align.")) {
	      alignPattern(second.substring(6));
	  } else if (second.startsWith("to")) {
	      if (second.equals("to_main")) {
		  master.setViewTo(FractalMate.MODE_MAIN);
	      } else if (second.equals("to_1st_level")) {
		  master.setViewTo(FractalMate.MODE_1ST_LEVEL);
	      } else if (second.equals("to_2ndary")) {
		  master.setViewTo(FractalMate.MODE_2NDARY);
	      } else if (second.equals("to_figure")) {
		  master.setViewTo(FractalMate.MODE_RESULT);
	      } else {
		  throw new Error("Unknown popup-menu '"+second+"'");
	      }
	  } else if (second.equals("about")) {
	      master.doMenuAction("help.about", null);
	  } else if (second.equals("defs")) {
	      StringWriter sw = new StringWriter();
	      saveFigure(sw);
	      Point p = getLocationOnScreen();
	      // We perhaps could 'cache' FigureDef-instances too?
	      FigureDef fd = new FigureDef(master.frame,
		     (String) properties.getValue(FractletProperties.FP_NAME),
		      sw.toString(), p.x + popupX,
		      p.y + popupY);
	      fd.setVisible(true);
	      fd.dispose();
	  }

      } else if (id.equals("figure")) {
	      if (second.equals("to_main")) {
		  master.setViewTo(FractalMate.MODE_MAIN);
	      } else if (second.equals("to_1st_level")) {
		  master.setViewTo(FractalMate.MODE_1ST_LEVEL);
	      } else if (second.equals("to_2ndary")) {
		  master.setViewTo(FractalMate.MODE_2NDARY);
			} else if (second.equals("about")) {
		  master.doMenuAction("help.about", null);
	      } else {
		  throw new Error("Unknown figure-popup-menu '"+second+"'");
	      }
      } else if (id.equals("line")) {
	  if (selectedSegmentNr < 0)
	      return;
	  if (second.equals("split")) {
	      synchronized (patterns) {
		  FractalSegment [] fs = 
		      splitLine(selectedSegmentNr, selectedSegments, true);
		  if (fs != null)
		      replaceCurrentPattern(fs);
	      }
	  } else {
	      createUndo(Undo.OPER_LINE_PROP_CHANGE, selectedSegmentNr,
			 selectedSegments[selectedSegmentNr]);
	      if (second.equals("invis")) {
		  selectedSegments[selectedSegmentNr].invis =
		      !selectedSegments[selectedSegmentNr].invis;
	      } else if (second.equals("mirrored")) {
		  selectedSegments[selectedSegmentNr].mirrored =
		      !selectedSegments[selectedSegmentNr].mirrored;
	      } else if (second.equals("upside_down")) {
		  selectedSegments[selectedSegmentNr].upsideDown =
		      !selectedSegments[selectedSegmentNr].upsideDown;
	      } else if (second.equals("double_sided")) {
		  selectedSegments[selectedSegmentNr].doubleSided =
		      !selectedSegments[selectedSegmentNr].doubleSided;
	      } else if (second.equals("final")) {
		  selectedSegments[selectedSegmentNr].Final =
		      !selectedSegments[selectedSegmentNr].Final;
	      } else {
		  throw new Error("Action fired by an unknown line-menu-item '"+second+"'");
	      }
	  }
	  repaint();
      } else if (id.equals("point")) {
	  if (second.equals("delete")) {
	      if (selectedSegmentNr >= 0)
		  deletePoint(selectedSegmentNr, selectedSegments, true);
	  } else {
	      throw new Error("Action fired by an unknown menu '"+id+"'");
	  }
      } else {
	  throw new Error("Action fired by an unknown menu '"+id+"'");
      }
  }

  public void
  setMenuState(String id, boolean state)
  {
      if (popupComponents == null)
	  return;

      CheckboxMenuItem cb = (CheckboxMenuItem) popupComponents.get(id);

      if (cb == null) {
	  throw new Error("Unknown menu '"+id+"' to turn "+
			  (state ? "on" : "off")+".");
      }

      cb.setState(state);
  }

  public boolean getMenuState(String id)
  {
      if (popupComponents == null)
	  return false;

      CheckboxMenuItem cb = (CheckboxMenuItem) popupComponents.get(id);

      if (cb == null) {
          throw new Error("Unknown menu '"+id+"' in getMenuState().");
      }
      return cb.getState();
  }

  public void setMenuEnabled(String id, boolean state)
  {
      if (popupComponents == null)
	  return;

      MenuItem m = (MenuItem) popupComponents.get(id);

      if (m == null) {
	  throw new Error("Unknown popup-menu '"+id+"' to "+
			  (state ? "enable" : "disable")+".");
      }

      m.setEnabled(state);
  }

  public void
  setMenuTitle(String id, String t)
  {
      if (popupComponents == null)
	  return;

      MenuItem m = (MenuItem) popupComponents.get(id);

      if (m == null)
	  throw new Error("Unknown popup-menu '"+id+"' on setMenuTitle().");
      m.setLabel(t);
  }

  public void
  setMenuTitleAndEnabled(String id, String t, boolean state)
  {
      if (popupComponents == null)
	  return;

      MenuItem m = (MenuItem) popupComponents.get(id);

      if (m == null)
	  throw new Error("Unknown popup-menu '"+id+"' on setMenuTitle().");
      m.setLabel(t);
      m.setEnabled(state);
  }

  /*** And some other menu-related functions: ***/
  public PopupMenu
  getPopupMenu(String id)
  {
      if (popupComponents == null)
	  return null;

      PopupMenu m = (PopupMenu) popupComponents.get(id);

      if (m == null) {
	  throw new Error("Unknown popup-menu '"+id+"' in getPopupMenu().");
      }
      return m;
  }

  /****  Code for Undo/Redo - operations ****/

  private Undo lastUndo = null, lastRedo = null;

  private void updateUndoMenus(boolean force)
  {
      String s;
      if (lastUndo != undo || force) {
	  if (lastUndo != null) {
	      if (undo == null) {
		  master.setMenuTitleAndEnabled("edit.undo", "Undo", false);
		  setMenuTitleAndEnabled("pattern.undo", "Undo", false);
	      } else {
		  s = "Undo "+undo;
		  master.setMenuTitle("edit.undo", s);
		  setMenuTitle("pattern.undo", s);
	      } 
	  } else {
	      if (undo != null) {
		  s = "Undo "+undo;
		  master.setMenuTitleAndEnabled("edit.undo", s, true);
		  setMenuTitleAndEnabled("pattern.undo", s, true);
	      }
	  }
	  lastUndo = undo;
      }

      if (lastRedo != redo || force) {
	  if (lastRedo != null) {
	      if (redo == null) {
		  master.setMenuTitleAndEnabled("edit.redo", "Redo", false);
		  setMenuTitleAndEnabled("pattern.redo", "Redo", false);
	      } else {
		  s = "Redo "+redo;
		  master.setMenuTitle("edit.redo", s);
		  setMenuTitle("pattern.redo", s);
	      } 
	  } else {
	      if (redo != null) {
		  s = "Redo "+redo;
		  master.setMenuTitleAndEnabled("edit.redo", s, true);
		  setMenuTitleAndEnabled("pattern.redo", s, true);
	      }
	  }
	  lastRedo = redo;
      }

  }

  private void addUndo(Undo u, boolean from_redo) {
      if (u == null)
	  return;

      String s;
      u.prevUndo = undo;
      undo = u;

     // If doing a new 'non-redo' operation, all the existing
      // redo-operations become invalid:
      if (!from_redo && redo != null) {
	  redo = null;
      }
      updateUndoMenus(false);
      /*System.err.print("UNDO-list now: ");
	for (Undo foo = undo; foo != null; foo = foo.prevUndo) System.err.print(foo+",");System.err.println();*/

  }

  void
  createUndo(int oper, int index)
  {
      addUndo(Undo.createUndo(oper, index, view, undo), false);
  }

  void
  createUndo(int oper, int index, FractalSegment f)
  {
      addUndo(Undo.createUndo(oper, index, view, f, undo), false);
  }

  void
  createUndo(int oper, int index, FractalSegment [] fs)
  {
      addUndo(Undo.createUndo(oper, index, view, fs, undo), false);
  }

    // Called by menu items:
  public void
  doUndo()
  {
      if (undo == null)
	  return;
      // First we'll remove the undo-operation...
      Undo u = undo;
      undo = undo.prevUndo;

      if (undo == null) {
	  master.setMenuEnabled("edit.undo", false);
	  master.setMenuTitle("edit.undo", "Undo");
      } else {
	  master.setMenuTitle("edit.undo", "Undo "+undo);
      }

      u.applyUndo(patterns, master, this);

      // And then we'll add the (possibly altered, by Undo.applyUndo()!)
      // operation to Redo-list:
      u.prevUndo = redo;
      redo = u;

      /*System.err.print("REDO-list now: ");
	for (Undo foo = redo; foo != null; foo = foo.prevUndo) System.err.print(foo+", ");System.err.println();*/


      updateUndoMenus(false);
 
      // If the change occured on another view, we need to switch:
      // (possibly not 100% required with all operations, though)
      // Might fail, too, if the view is not enabled any more...
      if (u.viewIndex != view) {
	  master.setViewTo(u.viewIndex);
      }

      repaint();
  }
  
  public void
  doRedo()
  {
      if (redo == null)
	  return;

      Undo r = redo;
      redo = redo.prevUndo;

      r.applyRedo(patterns, master, this);
      // And once again, the operation is undoable:
      addUndo(r, true);

      updateUndoMenus(false);

      /*System.err.print("REDO-list now: ");
	for (Undo foo = redo; foo != null; foo = foo.prevUndo) System.err.print(foo+", ");System.err.println();*/

      repaint();
  }

  private void removeUndos() {
      undo = redo = null;
      updateUndoMenus(true);
  }
}

/**********************************************************************
 *
 * And one simple utility class that repaint()s the fractal
 * during the drawing process.
 *
 *************************************************************************/

final class FractletUpdater
    implements Runnable
{
    // If we were pedantic, we'd better use locking, even though there's
    // no real race condition (this thread reads, another thread writes
    // 'done')
    FractletCanvas parent;
    boolean done = false;
    long delay;
    
    FractletUpdater(FractletCanvas p, long d)
    {
	parent = p;
	// Let's make sure it's not unreasonably low... 10 msecs should
	// be good enough even for those with patiency impaired minds...
	if (d < 10)
	    d = 10;
	delay = d;
    }
    
    public void drawingDone() { done = true; }
    
    public void run() {
	while (!done) {
	    try {
		Thread.sleep(delay);
	    } catch (InterruptedException ie) {
	    }
	    /* 2 ways to do it:
	     *
	     * a) Call 'parent.repaint()'
	     * b) Get parent's gfx object and call parent.paint(gfx)...
	     *    at least on Java 1.1 should be faster; some claim on
	     *    1.2 it's not?
	     */
	//    parent.repaint();
	    parent.paint(parent.getGraphics());
	  }
    }
}

