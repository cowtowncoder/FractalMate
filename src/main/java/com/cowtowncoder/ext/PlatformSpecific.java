/**************************************

Project:
    Generic Java-code library. Used by
    various applications by me...

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    PlatformSpecific.java
Last changed:
    12-Jul-99.
Description:
    An utility class that contains
    (or, at least, should contain!)
    all platform-specific code, to be
    queried by the applications...

Last changed:
   19-Jul-1999, TSa
Changes:

**************************************/

package ts.ext;

import java.awt.Color;
import java.awt.SystemColor;

public final class
PlatformSpecific
{
  static boolean initialized = false;

  // Not a complete list; just 'generic platforms' that are
  // used at the moment (ie. MacOS, AmigaOS, BeOS etc will
  // be added as required):
  public final static int GEN_PLATFORM_UNIX = 1;
  public final static int GEN_PLATFORM_WINDOWS = 2;
  public final static int GEN_PLATFORM_OTHER = -1;

  // Same here:
  public final static int OS_LINUX = 1;
  public final static int OS_OTHER_UNIX = 2;
  public final static int OS_WINDOWS_3X = 3;
  public final static int OS_WINDOWS_9X = 4;
  public final static int OS_WINDOWS_NT = 5;
  public final static int OS_OTHER = -1;

  public final static int VENDOR_SUN = 1;
  public final static int VENDOR_NETSCAPE = 2;
  public final static int VENDOR_MICROSOFT = 3;
  public final static int VENDOR_SYMANTEC = 4;
  public final static int VENDOR_IBM = 5;
  public final static int VENDOR_OTHER= -1;
  

  private static int genPlatform; // GEN_PLATFORM_xxx
  private static int OS; // OS_xxx
  private static int vendor; // VENDOR_xxx

  // And these are just plain strings....
  private static String osName;
  private static String vendorName;

  public final static void
  initialize()
  {
     osName = System.getProperty("os.name");

    // Shouldn't happen?
    if (osName == null)
      osName = "unknown";
    else osName = osName.toLowerCase();
      
    // A windows-system of some kind?
    if (osName.startsWith("windows")) {
      genPlatform = GEN_PLATFORM_WINDOWS;
      OS = OS_WINDOWS_9X;
      
      // A linux-system?
    } else if (osName.startsWith("linux")) {
      genPlatform = GEN_PLATFORM_UNIX;
      OS = OS_LINUX;
    } else {
      genPlatform = GEN_PLATFORM_OTHER;
      OS = OS_OTHER;
    }

    vendorName = System.getProperty("java.vendor");
    if (vendorName == null)
	vendorName = "unknown";
    else vendorName = vendorName.toLowerCase();
    
    if (vendorName.indexOf("netscape") >= 0)
	vendor = VENDOR_NETSCAPE;
    else if (vendorName.indexOf("microsoft") >= 0)
	vendor = VENDOR_MICROSOFT;
    else if (vendorName.indexOf("symantec") >= 0)
	vendor = VENDOR_SYMANTEC;
    else if (vendorName.indexOf("ibm") >= 0)
	vendor = VENDOR_IBM;
    else if (vendorName.indexOf("sun") >= 0)
	vendor = VENDOR_SUN;
    else vendor = VENDOR_OTHER;

    initialized = true;
  }

  public final static String toDebugString() {
      if (!initialized)
	  initialize();
      return osName+"/"+vendor;
  }

  public final static int getGenericPlatform()
  {
      if (!initialized)
	  initialize();
      return genPlatform;
  }

  public final static int getOS()
  {
      if (!initialized)
	  initialize();
      return OS;
  }

  public final static int getVendor()
  {
      if (!initialized)
	  initialize();
      return vendor;
  }

  /** If the platform tends to underestimate the space window headers etc
   * use, some components at the top of windows may not be visible. If so,
   * this should result true. Happens at least under some window managers
   * on Linux.
   */
  public final static boolean
  addPaddingToTop()
  {
      if (!initialized)
	  initialize();
      return true;
  }  

  /** Similar to above, but concerns the bottom of various windows.
   * Problem at least on Netscape Communicator 4.6 on Linux.
   */
  public final static boolean
  addPaddingToBottom()
  {
      if (!initialized)
	  initialize();
      return true;
  }  

  /** Netscape, at least on Linux, sets the SystemColors to
   *  some insanely ugly colours. Thus:
   */
  public final static boolean
  systemColoursFuckedUp()
  {
      if (!initialized)
	  initialize();
      return vendor == VENDOR_NETSCAPE;
  }

  /** And due to above, we perhaps need this function: */
  public final static Color
  getSystemColour(Color x)
  {
      if (!initialized)
	  initialize();
      if (systemColoursFuckedUp()) {
	  if (x == SystemColor.controlText)
	      return Color.black;
	  if (x == SystemColor.control)
	      return Color.lightGray;
	  if (x == SystemColor.controlShadow)
	      return Color.gray;
	  if (x == SystemColor.controlDkShadow)
	      return Color.black;
      }
      return x;
  }
}
