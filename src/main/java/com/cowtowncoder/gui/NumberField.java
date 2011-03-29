/**************************************
                                       
Project:
    Generic Java-code library. Used by
    various applications I've made.

    (C) 1999 Tatu Saloranta (aka Doomdark),
    doomdark@iki.fi.

Module:
    NumberField.java

Description:
    A GUI widget that can be used for
    getting numeric input, much like
    its counterparts in Windows, for
    example (Spin-whatever it was).
    Looks bit like:

    -----
   | 37 | < >
    -----

    Current implementation just consists
    of a text field that displays the number
    as well as allows for editing the value,
    and of 2 buttons that can be used for
    decreasing/increasing the value.

Last changed:

Changes:

***************************************/

package ts.gui;

import java.awt.*;
import java.awt.event.*;

public class
NumberField
extends
    Container
implements
    Adjustable,
    TextListener, 
    ActionListener,
    KeyListener
{
    int min = 0, max = 0, curr = 0;

    Button bLeft, bRight;
    TextField tValue;
    AdjustmentListener adjustmentListener = null;
    
    public NumberField(int curr, int min, int max)
    {
	int cols, i;

	if (min > max)
	    min = max;
	if (curr > max)
	    curr = max;
	else if (curr < min)
	    curr = min;

	this.curr = curr;
	this.min = min;
	this.max = max;

	bLeft = new Button("<");
	bLeft.addActionListener(this);
	bRight = new Button(">");
	bRight.addActionListener(this);

	cols = (Integer.toString(min)).length();
	i = (Integer.toString(max)).length();
	if (i > cols)
	    cols = i;
	tValue = new TextField(Integer.toString(curr), cols);
	tValue.addTextListener(this);
	tValue.addKeyListener(this);

	setLayout(new GridBagLayout());

	GridBagConstraints gbc= new GridBagConstraints();
	gbc.gridx = 0;
	gbc.gridy = 0;
	gbc.weighty = 100.0;
	gbc.weightx = 100.0;
	gbc.fill = GridBagConstraints.BOTH;

	add(tValue, gbc);

	gbc.gridx++;
	gbc.weightx = 0.0;
	gbc.fill = GridBagConstraints.NONE;
	add(bLeft, gbc);

	gbc.gridx++;
	add(bRight, gbc);

	setValue(curr);
    }

    public void processAdjustmentEvent(int type) {
	if (adjustmentListener != null) {
	    adjustmentListener.adjustmentValueChanged(new
		AdjustmentEvent(this, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
				type, curr));
	}
    }

  /***** KeyListener - interface: *****/

    public void keyTyped(KeyEvent e) {
    }
    public void keyPressed(KeyEvent e) {
	int code = e.getKeyCode();
	int mod = e.getModifiers();
	int step = 1;
	int old = curr;

	if ((mod & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK
		   | InputEvent.ALT_MASK  |InputEvent.META_MASK)) != 0)
	    step = 10;

	switch (code) {
	case KeyEvent.VK_UP:

	    curr += step;
	    if (curr > max)
		curr = max;
	    break;

	case KeyEvent.VK_DOWN:

	    curr -= step;
	    if (curr < min)
		curr = min;
	    break;

	default:
	    return;
	}
	if (curr != old)
	    setValue(curr);
    }

    public void keyReleased(KeyEvent e) {
    }

    /*** TextListener - interface: ******/
    public void textValueChanged(TextEvent e) {
	int new_r = -1;

	try {
	    String s = tValue.getText();
	    if (s.length() < 1 || (min < 0 && s.equals("-")))
		new_r = 0;
	    else {
		new_r = Integer.parseInt(tValue.getText());
	    }

	  if (new_r >= min &&
	      new_r <= max) {
	      curr = new_r;
	  // Now we need to inform AdjustmentListeners:
	      
	      processAdjustmentEvent(AdjustmentEvent.TRACK);

	  // ....
	      return;
	  }
	} catch (NumberFormatException ne) {
	}
	
	tValue.setText(Integer.toString(curr));
    }

    /*** ActionListener - interface: ******/

    public void actionPerformed(ActionEvent e) {
	Component c = (Component) e.getSource();
	int type;
	if (c == bLeft) {
	    if (curr <= min)
		return;
	    curr--;
	    type = AdjustmentEvent.UNIT_DECREMENT;
	} else if (c == bRight) {
	    if (curr >= max)
		return;
	    curr++;
	    type = AdjustmentEvent.UNIT_INCREMENT;
	} else return;

	processAdjustmentEvent(type);
	setValue(curr);
	// And once again, we need to inform AdjustmentListeners....
    }

    /*** Then the Adjustable - interface: ******/
    public int getOrientation() { return Adjustable.HORIZONTAL; }
    public  void setMinimum(int min) {
	if (min > max)
	    min = max;
	this.min = min;
	// Possibly need to change the current value...
	if (curr < min)
	    setValue(min);
    }
    public  int getMinimum() { return min; }
    public  void setMaximum(int max) {
	if (max < min)
	    max = min;
	this.max = max;
	// Possibly need to change the current value...
	if (curr > max)
	    setValue(max);
    }
    public  int getMaximum() { return max; }
    public  void setUnitIncrement(int u) { }
    public  int getUnitIncrement() { return 1; }
    public  void setBlockIncrement(int b) { }
    public  int getBlockIncrement() { return 1; }
    public  void setVisibleAmount(int v) { }
    public  int getVisibleAmount() { return 1; }
    public  void setValue(int v) {
	curr = v;
	tValue.setText(Integer.toString(curr));
    }

    public  int getValue() { return curr; }
    public  void addAdjustmentListener(AdjustmentListener l) {
	adjustmentListener = AWTEventMulticaster.add(adjustmentListener, l);
    }
    public  void removeAdjustmentListener(AdjustmentListener l) {
	adjustmentListener = AWTEventMulticaster.remove(adjustmentListener, l);
    }
}
