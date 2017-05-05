/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications I've made.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    ts.util.TSArray

Description:
    A class that encapsulates various
    utility functions that handle arrays.

Last changed:

Changes:

***************************************/

package com.cowtowncoder.fr8r.util;

public final class TSArray
{
    public static int findClosestIndex(double [] arr, double v)
    {
      int i, sel;
      double dist = 100000000.0;
      for (i = sel = 0; i < arr.length; i++) {
	  double d2 = v - arr[i];
	  d2 = d2 * d2;
	  if (d2 < dist) {
	      sel = i;
	      dist = d2;
	  }
      }
      return sel;
    }
}
