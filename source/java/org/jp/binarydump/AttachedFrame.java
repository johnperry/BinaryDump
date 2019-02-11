/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;

public class AttachedFrame extends JFrame implements ComponentListener {

	int width;
	Component parent;

	public AttachedFrame(Component parent, String title, int width) {
		this(parent, title, width, Color.white);
	}

	public AttachedFrame(Component parent, String title, int width, Color bgColor) {
		super(title);
		this.width = width;
		this.parent = parent;
		setBackground(bgColor);
		parent.addComponentListener(this);
	}

	public void setCenterComponent(Component c) {
		getContentPane().add(c, BorderLayout.CENTER);
	}

	public void attach() {
		Dimension componentSize = parent.getSize();
		Point componentLocation = parent.getLocation();
		System.out.println("parent size: "+componentSize.width+", "+componentSize.height+"\n");
		int x = componentLocation.x + componentSize.width;
		setLocation(new Point(x-15, componentLocation.y));
		setSize(width, componentSize.height);
		validate();
	}

	//Implement the ComponentListener interface
	public void componentHidden(ComponentEvent e) { }
	public void componentMoved(ComponentEvent e) { reattach(); }
	public void componentResized(ComponentEvent e) { reattach(); }
	public void componentShown(ComponentEvent e) { }
	private void reattach() {
		if (this.isVisible()) {
			attach();
		}
	}

}
