package org.jp.binarydump;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

public class ColorPane extends JTextPane {

	public int lineHeight;

	public ColorPane() {
		super();
		Font font = new Font("Monospaced",Font.PLAIN,12);
		FontMetrics fm = getFontMetrics(font);
		lineHeight = fm.getHeight();
		setFont(font);
		setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
	}

	public boolean getScrollableTracksViewportWidth() {
		return false;
	}

	public void append(String s) {
		int len = getDocument().getLength(); // same value as getText().length();
		setCaretPosition(len);  // place caret at the end (with no selection)
		replaceSelection(s); // there is no selection, so inserts at caret
	}

	public void append(Color c, String s) {
		StyleContext sc = StyleContext.getDefaultStyleContext();
		AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY,
									StyleConstants.Foreground, c);
		int len = getDocument().getLength();
		setCaretPosition(len);
		setCharacterAttributes(aset, false);
		replaceSelection(s);
	}
}

