package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import javax.swing.*;

public class JPEGParser extends Parser {

	JFrame listFrame = null;
	LinkedList<Chunk> chunks;

	public JPEGParser(BinaryDump parent, RandomAccessFile in) {
		super(parent, in);
		chunks = new LinkedList<Chunk>();
		try {
			int length = (int)in.length();
			type = checkByte(0x0, 0xFF)
						&& checkByte(0x1, 0xD8)
							&& checkByte(length-2, 0xFF)
								&& checkByte(length-1, 0xD9);
			if (type) {
				int adrs = 2;
				int marker;
				while ( (marker = getMarker(adrs)) != 0xFFD9 ) {
					Chunk chunk = new Chunk(adrs);
					chunks.add(chunk);
					if (marker == 0xFFDA) break; //stop on the first scan
					adrs = chunk.dataEnd;
				}
			}
		}
		catch (Exception ex) { type = false; }
	}

	public String getContentType() {
		return "Content-Type: JPEG";
	}

	public Color getColorFor(int adrs) {
		if (adrs < 0x2) return Color.red;
		for (Chunk chunk : chunks) {
			if (chunk.contains(adrs)) {
				return chunk.getColorFor(adrs);
			}
		}
		return Color.black;
	}

	public JMenu getMenu() {
		JMenu menu = new JMenu("JPEG");
		JMenuItem listItem = new JMenuItem("List Parameters");
		listItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				listParams();
			}
		});
		listItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,InputEvent.CTRL_MASK));
		menu.add(listItem);
		return menu;
	}

	private void listParams() {
		if (listFrame != null) {
			listFrame.setVisible(false);
			listFrame.dispose();
		}
		listFrame = new JFrame("JPEG Parameters");
		JScrollPane jsp = new JScrollPane();
		listFrame.getContentPane().add(jsp,BorderLayout.CENTER);
		JTextPane jtp = new JTextPane();
		jtp.setContentType("text/html");
		jsp.setViewportView(jtp);
		//jtp.setText(fmtChunk.toString());
		jtp.setCaretPosition(0);

		//Now, size and position the JFrame and display it.
		//The height is set to the height of the parent, and
		//the location is set just to the right of the parent.
		int width = 300;
		int height = 700;
		Toolkit t = parent.getToolkit();
		Dimension scr = t.getScreenSize ();
		Dimension parentSize = parent.getSize();
		listFrame.setSize(width,parentSize.height);
		Point parentLocation = parent.getLocation();
		int x = parentLocation.x + parentSize.width;
		if (x + width > scr.width) x = scr.width - width;
		listFrame.setLocation(new Point(x,parentLocation.y));
		listFrame.setVisible(true);
	}

	class Chunk {
		public int address;
		public int length;
		public int dataStart;
		public int dataEnd;
		public Chunk(int address) {
			this.address = address;
			length = getLength();
			dataStart = address + 4;
			dataEnd = address + 2 + length;
		}
		public boolean contains(int addr) {
			return (addr >= address) && (addr < dataEnd);
		}
		private int getLength() {
			byte[] b = getBytes(address+2, 2);
			return (b[0]<<8) | (b[1] & 0xFF);
		}
		public Color getColorFor(int adrs) {
			int reladr = adrs - address;
			if (reladr < 2) return Color.red;
			if (reladr < 4) return Color.gray;
			return Color.black;
		}
	}

	class APP0 extends Chunk {
		public APP0(int address) {
			super(address);
		}
		public Color getColorFor(int adrs) {
			int reladr = adrs - address;
			if (adrs < 2) return Color.red;
			if (adrs < 4) return Color.gray;
			return Color.black;
		}
	}

	private boolean checkByte(int adrs, int value) {
		try {
			byte[] b = new byte[1];
			in.seek(adrs);
			in.read(b);
			int bb = b[0] & 0xFF;
			return (bb == value);
		}
		catch (Exception ex) { return false; }
	}

	private boolean checkForString(int adrs, String string) {
		try {
			byte[] b = new byte[string.length()];
			in.seek(adrs);
			in.read(b);
			String name = new String(b);
			return name.equals(string);
		}
		catch (Exception ex) { return false; }
	}

	private int getMarker(int adrs) {
		byte[] b = getBytes(adrs, 2);
		return 0xFFFF & ( (b[0] << 8) | (b[1] & 0xFF));
	}

	private byte[] getBytes(int adrs, int length) {
		byte[] b = new byte[length];
		try {
			in.seek(adrs);
			in.read(b);
		}
		catch (Exception ex) { b = new byte[0]; }
		return b;
	}

	private int getInt(int adrs, int length) {
		byte[] b = getBytes(adrs, length);
		return getIntFromBytes(b);
	}

	private int getIntFromBytes(byte[] b) {
		int x = 0;
		for (int k=b.length - 1; k>=0; k--) {
			int bx = b[k];
			bx = bx & 0xFF;
			x = (x<<8) | bx;
		}
		return x;
	}

	private void putInt(int value, int adrs, int length) {
		byte[] b = new byte[length];
		for (int k=0; k<length; k++) {
			b[k] = (byte)(value & 0xFF);
			value = value >> 8;
		}
		try {
			in.seek(adrs);
			in.write(b);
		}
		catch (Exception ex) {
			JOptionPane
				.showMessageDialog(
					parent,
					"Unable to write to the file.\n\n"+ex.getMessage());
		}
	}
}
