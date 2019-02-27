package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.text.*;

public class JPEGParser extends Parser implements MouseListener {

	LinkedList<Chunk> chunks;
	AttachedFrame listFrame = null;
	ScrolledEditorPanel editorPanel = null;
	JEditorPane editor = null;

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
				Chunk chunk;
				while ( (marker = getMarker(adrs)) != 0xFFD9 ) {
					if (marker == 0xFFE0) chunk = new APP0(marker, adrs);
					else if (marker == 0xFFFE) chunk = new COM(marker, adrs);
					else if (marker == 0xFFDB) chunk = new DQT(marker, adrs);
					else if (marker == 0xFFC0) chunk = new SOF0(marker, adrs);
					else if (marker == 0xFFC4) chunk = new DHT(marker, adrs);
					else if (marker == 0xFFDA) chunk = new SOS(marker, adrs);
					else chunk = new Chunk(marker, adrs);
					chunks.add(chunk);
					if (marker == 0xFFDA) break; //stop on the first scan
					adrs = chunk.dataEnd;
				}
			}
		}
		catch (Exception ex) { ex.printStackTrace(); type = false; }
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
		listFrame = new AttachedFrame(parent, "JPEG Parameters", 750, Color.white);
		editorPanel = new ScrolledEditorPanel();
		listFrame.setCenterComponent(editorPanel);
		ListIterator<Chunk> it = chunks.listIterator(0);
		StringBuffer sb = new StringBuffer();
		while (it.hasNext()) {
			sb.append(it.next().listParams());
		}
		editor = editorPanel.getEditor();
		editor.setText(sb.toString());		
		editor.addMouseListener(this);
		listFrame.setVisible(true);
		listFrame.attach();
	}

	//MouseListener
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) {
		if (editor != null) {
			int dot = editor.getCaretPosition();
			showElement(dot);
		}
	}
	
	static Pattern pattern = Pattern.compile("\\s*([0-9a-f-A-F]{1,8})/.*");
	private void showElement(int dot) {
		try {
			int rowStart = Utilities.getRowStart(editor, dot);
			int length = Math.min(15, editor.getDocument().getLength() - rowStart);
			String text = editor.getText(rowStart, length);
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) {
				String adrsString = matcher.group(1);
				int adrs = Integer.parseInt(adrsString, 16);
				parent.gotoAddress(adrs);
			}
		}
		catch (Exception ignore) { ignore.printStackTrace(); }
	}

	class Chunk {
		public int marker;
		public String type = "";
		public int address;
		public int length;
		public int dataStart;
		public int dataEnd;
		public Chunk(int marker, int address) {
			this.marker = marker;
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
			return 0xFFFF & (b[0]<<8) | (b[1] & 0xFF);
		}
		public Color getColorFor(int adrs) {
			int reladr = adrs - address;
			if (reladr < 2) return Color.red;
			if (reladr < 4) return Color.blue;
			return Color.black;
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer();
			if (!type.equals("")) {
				sb.append(String.format("%08X/ %04X type: %s\n", address, marker, type));
			}
			else {
				sb.append(String.format("%08X/ %04X\n", address, marker));
			}
			sb.append(String.format("          Length: %04X\n", length));
			return sb.toString();
		}
	}

	class APP0 extends Chunk {
		public APP0(int marker, int address) {
			super(marker, address);
			type = "APP0";
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer( super.listParams() );
			sb.append(String.format("          Identifier: %s\n", new String( getBytes(dataStart, 4) )));
			byte[] b = getBytes(dataStart+5, 2);
			sb.append(String.format("          Version: %d.%02d\n", b[0], b[1]));
			b = getBytes(dataStart+7, 1);
			sb.append(String.format("          Density Units: %02d\n", b[0]));
			b = getBytes(dataStart+8, 2);
			sb.append(String.format("          Xdensity: %2d\n", getIntLE(b)));
			b = getBytes(dataStart+10, 2);
			sb.append(String.format("          Ydensity: %2d\n", getIntLE(b)));
			b = getBytes(dataStart+12, 1);
			sb.append(String.format("          Xthumbnail: %d\n", b[0]));
			b = getBytes(dataStart+13, 1);
			sb.append(String.format("          Ythumbnail: %d\n", b[0]));
			return sb.toString();
		}
	}

	class COM extends Chunk {
		public COM(int marker, int address) {
			super(marker, address);
			type = "COM - Comment";
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer( super.listParams() );
			String text = new String( getBytes(dataStart, dataEnd - dataStart) ).trim();
			String[] lines = text.split("\n");
			for (String line : lines) {
				line = line.trim();
				if (!line.equals("")) {
					sb.append(String.format("          %s\n", line));
				}
			}
			return sb.toString();
		}
	}

	class DQT extends Chunk {
		public DQT(int marker, int address) {
			super(marker, address);
			type = "DQT - Discrete Quantization Table";
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer( super.listParams() );
			return sb.toString();
		}
	}

	class SOF0 extends Chunk {
		public SOF0(int marker, int address) {
			super(marker, address);
			type = "SOF0 - Start of Frame: Baseline DCT";
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer( super.listParams() );
			int d = getIntLE(dataStart, 1);
			sb.append(String.format("          Data precision: %d\n", d));
			d = getIntBE(dataStart+1, 2);
			sb.append(String.format("          Image height: %d\n", d));
			d = getIntBE(dataStart+3, 2);
			sb.append(String.format("          Image width: %d\n", d));
			d = getIntLE(dataStart+5, 1);
			String s = (d==1)?"(grey scale)" :
						(d==3)?"(color YCbCr or YIQ)" :
						 (d==4)?"(color CMYK)" : "";
			sb.append(String.format("          No. of components: %d %s\n", d, s));
			return sb.toString();
		}
	}

	class DHT extends Chunk {
		public DHT(int marker, int address) {
			super(marker, address);
			type = "DHT - Huffman Table";
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer( super.listParams() );
			return sb.toString();
		}
	}

	class SOS extends Chunk {
		public SOS(int marker, int address) {
			super(marker, address);
			type = "SOS - Start of Scan";
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer( super.listParams() );
			return sb.toString();
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

	private int getIntLE(int adrs, int length) {
		byte[] b = getBytes(adrs, length);
		return getIntLE(b);
	}

	private int getIntLE(byte[] b) {
		int x = 0;
		for (int k=b.length - 1; k>=0; k--) {
			int bx = b[k];
			bx = bx & 0xFF;
			x = (x<<8) | bx;
		}
		return x;
	}

	private int getIntBE(int adrs, int length) {
		byte[] b = getBytes(adrs, length);
		return getIntBE(b);
	}

	private int getIntBE(byte[] b) {
		int x = 0;
		for (int k=0; k<b.length; k++) {
			int bx = b[k];
			bx = bx & 0xFF;
			x = (x<<8) | bx;
		}
		return x;
	}

}
