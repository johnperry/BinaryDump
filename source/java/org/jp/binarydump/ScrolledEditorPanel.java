/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.jp.binarydump;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;

public class ScrolledEditorPanel extends JPanel {

	TrackingJTextPane editorPane;

	public ScrolledEditorPanel() {
		this("text/html", Color.white);
	}

	public ScrolledEditorPanel(String contentType, Color bgColor) {
		super();
		setBackground(bgColor);
		setLayout(new BorderLayout());
		JScrollPane jsp = new JScrollPane();
		add(jsp,BorderLayout.CENTER);
		editorPane = new TrackingJTextPane();
		editorPane.setContentType(contentType);
		editorPane.setEditable(false);
		editorPane.setBackground(bgColor);
		jsp.setViewportView(editorPane);

		EditorKit kit = editorPane.getEditorKit();
		if (kit instanceof HTMLEditorKit) {
			HTMLEditorKit htmlKit = (HTMLEditorKit)kit;
			StyleSheet sheet = htmlKit.getStyleSheet();
			sheet.addRule("body {font-family: arial; font-size:14;}");
			htmlKit.setStyleSheet(sheet);
		}
		else {
			Font font = new Font("Monospaced",Font.PLAIN,14);
			editorPane.setFont(font);
		}
	}

	public void setText(String text) {
		editorPane.setText(text);
		editorPane.setCaretPosition(0);
	}

	public JEditorPane getEditor() {
		return editorPane;
	}

	class TrackingJTextPane extends JTextPane {
		public TrackingJTextPane() {
			super();
		}
		public boolean getScrollableTracksViewportWidth() {
			return false;
		}
	}

}
