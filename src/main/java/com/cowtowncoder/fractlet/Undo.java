/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    Undo.java

Description:
    Encapsulates information about a single
    undoable/redoable - operation: Much of
    the undo-functinality is in fact
    FractletCanvas, not here; it's practical
    to have the code there as its values are
    mostly handled...

Last changed:

Changes:

***************************************/

package com.cowtowncoder.fractlet;

public final class
Undo
{
    public final static int OPER_DELETE_POINT = 1;
    public final static int OPER_SPLIT_LINE = 2;
    public final static int OPER_DRAG_POINT = 3;
    public final static int OPER_LINE_PROP_CHANGE = 4;
    public final static int OPER_PALETTE_CHANGE = 5;
    public final static int OPER_TRANSLATE = 6;
    public final static int OPER_ROTATE = 7;
    public final static int OPER_SCALE = 8;
    public final static int OPER_CENTER = 9;
    public final static int OPER_ALIGN = 10;
    public final static int OPER_FIT = 11;
    public final static int OPER_COPY = 12;
    public final static int OPER_EXCHANGE = 13;

    public final static int SHIFT_SRC = 0;
    public final static int SHIFT_DST = 4;
    public final static int SHIFT_MASK = 0xF;

    public final static String [] undoPatternNames = new String [] {
	"main pattern", "first level pattern", "secondary pattern"
    };

    public static Class fsClass = new FractalSegment().getClass();

    public int operType;
    public int elementIndex, patternIndex, viewIndex;
    Object prevValue;
    Undo prevUndo; // Next operation in chain...

    public Undo(int opt, int elem, int view, Object v)
    {
	operType = opt;
	elementIndex = elem;
	viewIndex = view;
	prevValue = v;
    }

    public String
    toString()
    {
	int src, dst;

	switch (operType) {

	case OPER_DELETE_POINT:
	    if (elementIndex < 0)
		return "Deletion of points";
	    return "Deletion of point #"+elementIndex;

	case OPER_SPLIT_LINE:
	    if (elementIndex < 0)
		return "Splitting of lines";
	    return "Splitting of line #"+elementIndex;

	case OPER_DRAG_POINT:
	    if (elementIndex < 0)
		return "Moving of line/points";
	    return "Moving of point #"+elementIndex;

	case OPER_LINE_PROP_CHANGE:
	    // Should always affect just one line?
	    //if (elementIndex < 0) return "Changes to line properties";
	    return "Changes to properties of line #"+elementIndex;

	case OPER_ALIGN:
	    // This should always affect multiple points,
	    // on the other hand...
		return "Alignment-operation";

	case OPER_PALETTE_CHANGE:
	    return "Palette change";

	case OPER_TRANSLATE:
	    return "Pattern translation";

	case OPER_ROTATE:
	    return "Pattern rotation";

	case OPER_SCALE:
	    return "Pattern scaling";

	case OPER_CENTER:
	    return "Pattern centering";

	case OPER_FIT:
	    return "Pattern centering";

	case OPER_COPY:

	    src = (elementIndex >> SHIFT_SRC) & SHIFT_MASK;
	    dst = (elementIndex >> SHIFT_DST) & SHIFT_MASK;

	    return "Copy of "+undoPatternNames[src]+" to "
		    +undoPatternNames[dst];

	case OPER_EXCHANGE:

	    src = (elementIndex >> SHIFT_SRC) & SHIFT_MASK;
	    dst = (elementIndex >> SHIFT_DST) & SHIFT_MASK;

	    return "Exchange of "+undoPatternNames[src]+" and "
		    +undoPatternNames[dst];
	    
	}
	return "<unknown undo-operation>";
    }

    // In theory, create-funcs may refuse to create a
    // new Undo-operation; this means that the new operation is
    // already covered by the previous Undo-operation.

    // First version is the one that uses no args, used by:
    //
    // - OPER_EXCHANGE
    // - OPER_SPLIT_LINE, if only one line is split
    public static Undo
    createUndo(int opt, int index, int view, Undo prev_undo)
    {
	if (opt != OPER_EXCHANGE && opt != OPER_SPLIT_LINE)
	    throw new Error("Error: Can't create Undo nr "+opt+" without args.");
	return new Undo(opt, index, view, null);
    }

    // Second version stores a single point, used by:
    //
    // - OPER_DELETE_POINT, if deleting only one point
    // - OPER_DRAG_POINT, if only one point is being moved
    // - OPER_LINE_PROP_CHANGE
    public static Undo
    createUndo(int opt, int index, int view, FractalSegment item,
	       Undo prev_undo)
    {
	if (opt != OPER_DELETE_POINT && opt != OPER_DRAG_POINT
	    && opt != OPER_LINE_PROP_CHANGE)
	    throw new Error("Error: Can't create Undo nr "+opt+" with point argument.");

	return new Undo(opt, index, view, item.cloneNotSelected());
    }

    // And the third version is the most often used, used by all
    // the other operations:

    public static Undo
    createUndo(int opt, int index, int view, FractalSegment [] item,
	       Undo prev_undo)
    {
	// There are certain undoable-operations that can (and should?)
	// be combined:
	if (prev_undo != null && view == prev_undo.viewIndex
	    && opt == prev_undo.operType && index == prev_undo.elementIndex) {
	    // At the moment, this mostly concerns operations that
	    // can be done via keyboard:
	    if (opt == OPER_TRANSLATE || opt == OPER_ROTATE
		|| opt == OPER_SCALE) {
		return null;
	    }
	}

	return new Undo(opt, index, view, FractletCanvas.clonePattern(item));
    }

    // This reverts the operation, so it can be used as a redo...
    public void
    applyUndo(FractalSegment [][] patterns, Fractlet fractlet,
	      FractletCanvas canvas)
    {
	FractalSegment [] tmp_pattern;
	FractalSegment [] tmp_point;
	int src, dst;

	// Locking has probably already been done, but just in case:
	synchronized (patterns) {

	    // We can do a rather generic handling here I hope:

	    // NULL-value:
	    //    - exchange (OPER_EXCHANGE)
	    //    - OPER_SPLIT_LINE, if only one line split
	    // Both are different, no generic solution. However, both
	    // have pretty simple redos.

	    if (prevValue == null) {

		switch (operType) {
		case OPER_SPLIT_LINE:

		    // We simply remove the point that resulted from the
		    // split:
		    // (remember we need to remove the _new_ point, not the
		    // original beginning point of the line-to-split)
		    canvas.deletePoint(elementIndex + 1, patterns[viewIndex],
				       false);
		    // Redo doesn't need any more data either
		    break;

		case OPER_EXCHANGE: // Simple, and redo is identical!
		    src= (elementIndex>>SHIFT_SRC) & SHIFT_MASK;
		    dst= (elementIndex>>SHIFT_DST) & SHIFT_MASK;
		    tmp_pattern = patterns[src];
		    patterns[src] = patterns[dst];
		    patterns[dst] = tmp_pattern;
		}

	    } else  if (prevValue.getClass().isArray()) {
		Class c = prevValue.getClass().getComponentType();

	    // FractalSegment [], ie. pattern:
	    //
	    // - Most operations, actually

		if (c.isAssignableFrom(fsClass)) {
		    // Most operations can use the default version:

		    switch (operType) {
		    case OPER_COPY:

			// Redo-operation actually doesn't need extra info;
			// source will stay valid in any case.
			src= (elementIndex>>SHIFT_SRC) & SHIFT_MASK;
			dst= (elementIndex>>SHIFT_DST) & SHIFT_MASK;
			patterns[dst] = (FractalSegment []) prevValue;
			break;

		    default:
			// Redo probably needs to get the pattern before
			// restoration:
			FractalSegment [] redo_pattern = FractletCanvas.clonePattern(patterns[viewIndex]);
			patterns[viewIndex] = (FractalSegment []) prevValue;
			prevValue = redo_pattern;
		  }
		} else throw new Error("Error: Unknown prevValue type for Undo-element, in applyUndo!");

	    // FractalSegment, ie. point:
	    //
	    // - OPER_DELETE_POINT, if only 1 point deleted
	    // - OPER_DRAG_POINT, if only 1 point moved
	    // - OPER_LINE_PROP_CHANGE
	    //
	    // Deletion is different, other two use 'default':

	    } else if (prevValue instanceof FractalSegment) {

		FractalSegment [] curr_pattern = patterns[viewIndex];

		switch (operType) {

		case OPER_DELETE_POINT:

		    // So, now we need to insert the point:
		    canvas.insertPoint(elementIndex, curr_pattern,
				       (FractalSegment) prevValue);
		    // Redo doesn't need any extra info
		    break;

		default:
		    // For redo, we need to save the current point
		    // definition...
		    FractalSegment redo_point = curr_pattern[elementIndex].cloneNotSelected();
		    curr_pattern[elementIndex] = (FractalSegment) prevValue;
		    prevValue = redo_point;

		}
	    }
	}
    }

    public void
    applyRedo(FractalSegment [][] patterns, Fractlet fractlet,
	      FractletCanvas canvas)
    {
	int src, dst;
	synchronized (patterns) {

	    // We can do a rather generic handling here I hope:

	    // NULL-value:
	    //    - exchange (OPER_EXCHANGE)
	    //    - OPER_SPLIT_LINE, if only one line split
	    // Both are actually idempotent, so we can simply use
	    // applyUndo(). Um, no. SPLIT_LINE actually needs to
	    // re-split the line...

	    if (prevValue == null) {

		switch (operType) {
		case OPER_SPLIT_LINE:

		    FractalSegment [] fs = canvas.splitLine(elementIndex,
				     patterns[viewIndex], false);
		    canvas.replacePattern(patterns[viewIndex], fs);
		    break;

		default:
		    applyUndo(patterns, fractlet, canvas);
		}
	    } else  if (prevValue.getClass().isArray()) {
		Class c = prevValue.getClass().getComponentType();

	    // FractalSegment [], ie. pattern:
	    //
	    // - Most operations, actually. Most are idempotent, too,
	    //   which means that we can just fall back to applyUndo()

		if (c.isAssignableFrom(fsClass)) {
		    // Most operations can use the default version:

		    switch (operType) {
		    case OPER_COPY:

			// Redo-operation actually doesn't need extra info;
			// source will stay valid in any case.
			src= (elementIndex>>SHIFT_SRC) & SHIFT_MASK;
			dst= (elementIndex>>SHIFT_DST) & SHIFT_MASK;
			prevValue = patterns[dst];
			patterns[dst] = FractletCanvas.clonePattern(patterns[src]);
			break;

		    default:
			applyUndo(patterns, fractlet, canvas);
		  }
		} else throw new Error("Error: Unknown prevValue type for Undo-element, in applyUndo!");

	    // FractalSegment, ie. point:
	    //
	    // - OPER_DELETE_POINT, if only 1 point deleted
	    // - OPER_DRAG_POINT, if only 1 point moved
	    // - OPER_LINE_PROP_CHANGE, if only 1 line's props changed
	    //
	    // Deletion is different, other two use 'default' (and the
	    // default can use applyUndo()!)

	    } else if (prevValue instanceof FractalSegment) {

		FractalSegment [] curr_pattern = patterns[viewIndex];

		switch (operType) {

		case OPER_DELETE_POINT:

		    // First we need to save the point to be re-deleted:
		    prevValue = curr_pattern[elementIndex];
		    canvas.deletePoint(elementIndex, curr_pattern, false);
		    break;

		default:

		    // For redo, we need to save the current point
		    // definition...

		    applyUndo(patterns, fractlet, canvas);
		}
	    }
	}
    }
}
