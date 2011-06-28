package org.jp.binarydump;

import java.io.RandomAccessFile;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JMenu;

public class Parser {

	boolean type = true;
	RandomAccessFile in;
	BinaryDump parent;

	public static Parser getInstance(BinaryDump parent, RandomAccessFile in) {
		Parser parser = new DicomParser(parent,in);
		if (parser.isType()) return parser;
		parser = new WaveParser(parent, in);
		if (parser.isType()) return parser;
		return new Parser(parent,in);
	}

	public Parser(BinaryDump parent, RandomAccessFile in) {
		this.parent = parent;
		this.in = in;
	}

	public String getContentType() {
		return "Content-Type: binary";
	}

	public boolean isType() {
		return type;
	}

	public Color getColorFor(int adrs) {
		return Color.black;
	}

	public JMenu getMenu() {
		return null;
	}
}

