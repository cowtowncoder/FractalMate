/**************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FractalSegment.java

Description:
    Encapsulates information about a single
    line of a pattern. Actually, only contains
    starting point of the line and properties;
    ending point is defined by the starting point
    of the next segment, and thus a pattern
    has one FractalSegment per edge, ie.
    one more than the actual number of lines.

Last changed:

Changes:

***************************************/

/**********************************************************************
 *
 A simple data structure that defines a point (and one segment, except
 * for the last point of a pattern)
 *
 *************************************************************************/

package com.cowtowncoder.fr8r;

final class
FractalSegment
implements Cloneable
{
  public float x, y; // Coordinates of the starting point. End points
                     // are always connected, thus they aren't explicitly
                     // defined (and arrays do need one 'extra' argument)

  // Attributes for line segments that start from this point
  public boolean invis = false;
  public boolean Final = false;
  public boolean upsideDown = false;
  public boolean mirrored = false;
  public boolean doubleSided = false;

  // And some attributes related to the UI:
  public boolean selected = false;

  // Hmmh. Why do we need to do this...
  public Object clone() {
      try {
	  return super.clone();
      } catch (CloneNotSupportedException foo) {
	  return null;
      }
  }

  public FractalSegment cloneNotSelected() {
      FractalSegment f;
      try {
	  f = (FractalSegment) super.clone();
      } catch (CloneNotSupportedException foo) {
	  return null;
      }
      f.selected = false;
      return f;
  }

  public final void copyFrom(FractalSegment fs) {
      x = fs.x;
      y = fs.y;
      invis = fs.invis;
      Final = fs.Final;
      upsideDown = fs.upsideDown;
      mirrored = fs.mirrored;
      doubleSided = fs.doubleSided;
      selected = fs.selected;
  }

  public String toString() {
      return "Line from "+x+","+y+" ("
	  +(doubleSided ? "d":"")
	  +(Final ? "f":"")
	  +(invis ? "i":"")
	  +(mirrored ? "m":"")
	  +(upsideDown ? "u":"")
	  +(selected ? "s":"")
	  +")";
  }
}
