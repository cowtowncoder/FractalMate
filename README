Update on 04-May-2017: This is the original README from circa 1999 -- I am slowly
trying to update this package, but for now enjoy this historical artifact!

-----

# GENERAL

FractalMate (nee Fractlet) is a simple application/applet that draws figures by recursively
replacing the individual line segments of the original wireframe pattern
by copies of the pattern. The resulting figure is called fractal, as its
properties do not change when zooming closer in, ie. no matter for close
you look, the same patterns and figures can be found at every level.

# COMPATIBILITY

FractalMate is written in pure Java, and in theory should be run on any
Java-platform. It has been developed on a Red Hat based x86 Linux PC
using Sun's JDK. So far, it has been succesfully tested on following
Java platforms:

- x86 (Red Hat) Linux, JDK's (1.1.7) Java VM
- - "" -, Kaffe-1.0b4
- Netscape communicator 4.5/4.6/4.08 on Linux x86
- Netscape communicator 4.5/4.6/4.08 on Windows98
- IE 5 on Windows 98; update packages (including new Java VM) installed.
  Runs well; also, IE 4 and un-updated IE 5 were tested at some point;
  there were some small problems, but I have not been able to test whether
  there are still problems (after all, the program code has changed
  significantly since then.

I would be grateful, if someone could test out other platforms, so I could
indicate which systems can run FractalMate without problems, and also fix
(or by-pass) problems that only affect certain platforms.

The important thing to note is that you definitely need java 1.1 (or
higher; 1.2 _should_ be ok AFAIK); 1.0.2 will not do. Older versions
of Netscape (pre-4 and some early 4.x versions) and IE (at least before
IE 4?) will not do.

There have been following problems on the platforms mentioned earlier
to run FractalMate. These include:

- Kaffe's AWT functions bit differently than JDK's AWT, and thus there
  are some cosmetic layout/drawing problems with UI. Functionally FractalMate
  appears to work, though, and it is possible that these problems may
  be fixed soon (by new versions of Kaffe and/or platform-dependant fixes
  to FractalMate).
- Netscape Communicator (4.5, 4.6) on Windows9x has a JIT (Just-In Time
  compiler), that used to have problems with some code in FractalMate;
  there were some internal errors... Fortunately I was able to by-pass
  them. All in all, Communicator's JVM is very buggy, and it's unfortunate
  Netscape/Symantec were not able to produce a better VM. A shame really.


# LICENSING, COPYRIGHT etc

FractalMate is copyright by me (Tatu Saloranta, tatu.saloranta@iki.fi), but
the source code is itself is open source, in that its freely available,
and you are free to use it for non-commercial uses without a specific
permission. You can modify the code, re-distribute the modified code and
all (for non-commercial purposes). If you use it, or distribute it (as is
or modified), the only thing I ask is that the original source of code
is mentioned (a http-reference or a link to the author's home page
should do nicely).

For commercial use, a written permission is needed. Not that I think there's
all that much commercial potential for selling fractal image things... 8-)
Nevertheless, I am not a free software bigot (not against commercial
software), and so getting the permission should be straightforward.

The program code and the application itself is provided 'as is'.
If you manage to hurt yourself by using the application, you must be a
real genius. :-p 

# FEEDBACK

I wrote FractalMate for fun. I hope you use it for fun, or education or
something. Some fractals are oddly beautiful, and probably could be used
for producing some nice graphics. I would appreciate it if:

- You create some nice 'new' fractals, and send them to me. I will probably
  dedicate a web-page to present such fractals, along with the proper credit
  for the authors
- You use FractalMate for something interesting (in addition to just using it
  for creating new fractals); I'd like to hear about this too. :-)
  Perhaps FractalMate could be used for explaining some mathematical things;
  fractal generation through complex number arithmetics (Mandelbrot-set
  etc) is not as intuitive as FractalMate's method, IMO.
- You find bugs from the application; perhaps even know how to fix them.
  Please send me the bug-reports and/or fixes!
- You think of some improvements to FractalMate; I might be able to implement
  them... Email suggestions!
- You are an optimization guru, and know how to speed up the fractal
  calculation or drawing... I have tried to optimize it a bit, and think
  it's relatively fast even as is... But I know there's no such thing as
  perfectly optimized code . :-)
  If so, send me a patch, or explain how I could improve the efficiency
  (things done so far: object allocation uses an object pool; made fractal
  generation 25-30% faster, tried replacing floats with doubles, made
  calculation ~5% slower, probably due to higher memory overhead; removed
  recursion from the drawing, no significant change in drawing time)


# FUTURE IDEAS

I have some ideas that I might be able to implement in the future.
Such ideas include:

- Use of curves (bezier, b-splines) for fractal patterns. It is possible
  that I will try to implement this for version 2.0, if I ever have the
  time to do that...
- 3-d'ish manipulations for the image, ie. surface mapping the resulting
  image to a non-rectangular 2-d area.
- Perhaps even do a 3-d version of FractalMate. User interface for such a
  thing would need more thinking, and there are also technical problems
  for fractal generation... There are many ways to 'add one more dimension'.
- Use of more than 3 separate fractals; each fractal segment could define
  which fractal to use for replacements.
- Scenes that would consist of actual fractal images... Think of a forest
  that consists of trees, each being a single fractal figure.
