/**************************************
                                       
Project:
    FractalMate; a simple application/applet
    that draws figures by recursively
    replacing the individual line segments
    by the original wireframe pattern.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    FigureProps.java

Description:
    A class (window) that allows viewing
    and modifying various Image properties,
    such as the name of the figure, the
    author of the image etc.

Last changed:
    26-Oct-1999, TSa

Changes:

  26-Oct-1999, TSa:
    Now uses ClosableDialog as the base
    class... And allows closing via the
    platform-specific close widget etc.

***************************************/

package com.cowtowncoder.fr8r;

import java.awt.*;
import java.awt.event.*;

import com.cowtowncoder.fr8r.ext.PlatformSpecific;
import com.cowtowncoder.fr8r.gui.ClosableDialog;
import com.cowtowncoder.fr8r.gui.TSProperty;

public final class FigureProps
    extends ClosableDialog
    implements ActionListener
{
    Color textColour = PlatformSpecific.getSystemColour(SystemColor.controlText);
    Color backgroundColour = PlatformSpecific.getSystemColour(SystemColor.control);
    Color borderColour = PlatformSpecific.getSystemColour(SystemColor.controlDkShadow);

    Button bOk, bCancel;
    TextArea textComment;
    TextField textName, textAuthor;

    FractletProperties properties;

    public
    FigureProps(Frame parent, FractletProperties pr)
    {
	super(parent, "Figure properties", true);

	setForeground(textColour);
	setBackground(backgroundColour);
	setLayout(new GridBagLayout());

	properties = pr;

	GridBagConstraints gb1 = new GridBagConstraints();
	GridBagConstraints gb2 = new GridBagConstraints();

	//gb1.anchor = GridBagConstraints.WEST;
	gb1.anchor = GridBagConstraints.NORTHWEST;
	gb1.weightx = 0.1;
	gb1.weighty = 0.1;
	gb1.fill = GridBagConstraints.NONE;
	gb1.gridx = 0;
	gb1.gridy = 0;

	gb2.weightx = 9.9;
	gb2.weighty = 0.1;
	gb2.anchor = GridBagConstraints.WEST;
	gb2.fill = GridBagConstraints.HORIZONTAL;
	gb2.gridx = 1;
	gb2.gridy = 0;

	Label l = new Label("Name: ");
	add(l, gb1);
	gb1.gridy += 1;
	l = new Label("Author: ");
	add(l, gb1);
	gb1.gridy += 1;
	l = new Label("Comment: ");
	add(l, gb1);
	gb1.gridy += 1;

	textName = new TextField((String) properties.
				 getValue(FractletProperties.FP_NAME),
				 FractletProperties.DEF_LEN_NAME);
	properties.addListener(FractletProperties.FP_NAME, textName,
			       TSProperty.LISTEN_SYNC_VALUE);
	add(textName, gb2);
	gb2.gridy += 1;

	textAuthor = new TextField((String) properties.
				   getValue(FractletProperties.FP_AUTHOR),
				   FractletProperties.DEF_LEN_AUTHOR);
	properties.addListener(FractletProperties.FP_AUTHOR, textName,
			       TSProperty.LISTEN_SYNC_VALUE);
	add(textAuthor, gb2);
	gb2.gridy += 1;

	int rows = FractletProperties.DEF_LEN_COMMENT /
	    FractletProperties.DEF_LEN_NAME;
	textComment = new TextArea((String) properties.
				   getValue(FractletProperties.FP_COMMENT),
				   rows,
				   FractletProperties.DEF_LEN_COMMENT / rows);
	gb2.weighty = 9.9;
	gb2.fill = GridBagConstraints.BOTH;
	add(textComment, gb2);
	gb2.gridy += 1;

	Panel foop = new Panel();

	bOk = new Button("Ok");
	bOk.addActionListener(this);
	bCancel = new Button("Cancel");
	bCancel.addActionListener(this);
	bOk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	bCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	foop.add(bOk);
	foop.add(bCancel);

	gb1.weighty = 0.1;
	gb1.anchor = GridBagConstraints.CENTER;
	gb1.gridwidth = 2;
	gb1.fill = GridBagConstraints.NONE;

	add(foop, gb1);

	pack();

	if (parent != null) {
	    Dimension scr_s = Toolkit.getDefaultToolkit().getScreenSize();
	    Rectangle r = parent.getBounds();
	    Dimension d = getSize();
	    
	    int x = r.x + (r.width / 2 - d.width / 2);
	    int y = r.y + (r.height / 2 - d.height / 2);
	    if ((x + d.width) > scr_s.width)
		x = scr_s.width - d.width;
	    if ((y + d.height) > scr_s.height)
		x = scr_s.height - d.height;
	    if (x < 0)
		x = 0;
	    if (y < 0)
		y = 0;
	    setLocation(x, y);
	    
	}
    }

    public void
    init(String nm, String au, String co)
    {
	textName.setText(nm);
	textAuthor.setText(au);
	textComment.setText(co);
    }

    /* We need to override this method to draw borders: */
    public void
    paint(Graphics g)
    {
	super.paint(g);
	if (borderColour != null) {
	    Dimension d = getSize();    
	    // should never be null but...
	    if (d != null) {
		g.setColor(borderColour);
		g.drawRect(0, 0, d.width - 1, d.height - 1);
		g.drawRect(2, 2, d.width - 5, d.height - 5);
		g.setColor(Color.black);
		g.drawRect(1, 1, d.width - 3, d.height - 3);
	    }
	}
    }

  /*** ActionListener-interface: ***/

    public void
    actionPerformed(ActionEvent e) 
    {
	Component c = (Component) e.getSource();
	
	// Here we'll either update the value in database:
	if (c == bOk) {
	    properties.setValue(FractletProperties.FP_NAME,
				textName.getText(), this);
	    properties.setValue(FractletProperties.FP_AUTHOR,
				textAuthor.getText(), this);
	    properties.setValue(FractletProperties.FP_COMMENT,
				textComment.getText(), this);
	    setVisible(false);

	// or restore the values from database:
	} else if (c == bCancel) {
	    textName.setText(properties.getStringValue(FractletProperties.FP_NAME));
	    textAuthor.setText(properties.getStringValue(FractletProperties.FP_AUTHOR));
	    textComment.setText(properties.getStringValue(FractletProperties.FP_COMMENT));
	    setVisible(false);
	} else {
	    System.err.println("Warning: Unknown action on FigureProps.");
	}
    }
}

