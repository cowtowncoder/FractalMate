/*************************************************
                                       
Project:
    Fractlet; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    PreviewLimit.java

Description:
    This class implements a thread that takes
    care of limiting the maximum time taken by
    drawing the preview figure (in preview mode).

Last changed:

Changes:

*************************************************/

package com.cowtowncoder.fractlet;

public final class
PreviewLimit
    extends Thread
{
    FractletCanvas parent;

    private Integer limitLock = new Integer(0);
    private long nextLimit = -1L;
    private int previewStep = -1;
    private boolean sleeping = false;

    public PreviewLimit(FractletCanvas f) {
	parent = f;
	start();
    }

    public void run() {
	long delay, now;
	int step;

	while (true) {
	    synchronized (limitLock) {
		now = System.currentTimeMillis();
		while (nextLimit < now) {
		    try {
			sleeping = true;
			limitLock.wait();
		    } catch (InterruptedException ie) {
		    }
		    sleeping = false;
		}
		delay = nextLimit - System.currentTimeMillis();
		step = previewStep;
	    }

	    if (delay > 0) {
		try {
		    sleep(delay);
		} catch (InterruptedException ie) {
		    // Who cares...
		}
	    }

	    synchronized (limitLock) {
		// We've gotten a new limit while sleeping?
		if (step < previewStep)
		    continue;
		// Doesn't necessarily mean we hit the limit, but:
		parent.interruptPreview(step);
	    }
	}
    }

    public void enableLimit(long limit, int step) {
	synchronized (limitLock) {
	    nextLimit = System.currentTimeMillis() + limit;
	    previewStep = step;
	    if (sleeping)
		limitLock.notify();
	}
    }
}
