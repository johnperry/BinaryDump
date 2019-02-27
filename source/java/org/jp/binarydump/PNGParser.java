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

public class PNGParser extends Parser implements MouseListener {

	LinkedList<Chunk> chunks;
	AttachedFrame listFrame = null;
	ScrolledEditorPanel editorPanel = null;
	JEditorPane editor = null;
	
	byte[] header = { (byte)0x89, (byte)0x50, (byte)0x4e, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A };

	public PNGParser(BinaryDump parent, RandomAccessFile in) {
		super(parent, in);
		chunks = new LinkedList<Chunk>();
		try {
			byte[] b = getBytes(0, header.length);
			type = match(b, header);
			if (type) {
				int adrs = 8;
				Chunk chunk;
				while ( adrs < in.length() ) {
					chunk = new Chunk(adrs);
					chunks.add(chunk);
					adrs += chunk.length + 12;
				}
			}
		}
		catch (Exception ex) { ex.printStackTrace(); type = false; }
	}
	
	private boolean match(byte[] a, byte[] b) {
		if (a.length == b.length) {
			for (int i=0; i<a.length; i++) {
				if (a[i] != b[i]) return false;
			}
			return true;
		}
		return false;
	}

	public String getContentType() {
		return "Content-Type: PNG";
	}

	public Color getColorFor(int adrs) {
		if (adrs < 8) return Color.red;
		for (Chunk chunk : chunks) {
			if (chunk.contains(adrs)) {
				return chunk.getColorFor(adrs);
			}
		}
		return Color.black;
	}

	public JMenu getMenu() {
		JMenu menu = new JMenu("PNG");
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
		listFrame = new AttachedFrame(parent, "PNG Parameters", 750, Color.white);
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
		public String type;
		public byte[] typeBytes;
		public int address;
		public int length;
		public int dataStart;
		public int dataEnd;
		public Chunk(int address) {
			this.address = address;
			length = getIntBE(address, 4);
			typeBytes = getBytes(address + 4, 4);
			type = new String(typeBytes);
			dataStart = address + 8;
			dataEnd = dataStart + length;
		}
		public boolean contains(int addr) {
			return (addr >= address) && (addr < dataEnd + 4);
		}
		public Color getColorFor(int adrs) {
			int reladr = adrs - address;
			if (reladr < 4) return Color.blue;
			if (reladr < 8) return Color.red;
			if (reladr < 8 + length) return Color.black;
			return Color.green;
		}
		public String listParams() {
			StringBuffer sb = new StringBuffer();
			byte mask = 1<< 5;
			sb.append(String.format("%08X/ type: %4s length: %08X\n", address, type, length));
			String typeLC = type.toLowerCase();
			if (!typeLC.equals("iend")) {
				sb.append("   Safe-to-copy: "+(((typeBytes[3] & mask)!=0)?"1":"0")+"\n");
				sb.append("   Reserved:     "+(((typeBytes[2] & mask)!=0)?"1":"0")+"\n");
				sb.append("   Private:      "+(((typeBytes[1] & mask)!=0)?"1":"0")+"\n");
				sb.append("   Ancillary:    "+(((typeBytes[0] & mask)!=0)?"1":"0")+"\n");
			}
			if (typeLC.equals("ihdr")) listIHDRParams(sb);
			else if (typeLC.equals("srgb")) listSRGBParams(sb);
			else if (typeLC.equals("gama")) listGAMAParams(sb);
			else if (typeLC.equals("phys")) listPHYSParams(sb);
			return sb.toString();
		}
		String[] colorTypes = {"(grayscale)", "", "(RGB)", "(Palette index)", "(Grayscale with alpha)", "", "(RGB with alpha)"};
		private void listIHDRParams(StringBuffer sb) {
			int compression = getIntBE(dataStart+10,1);
			String compressionString = (compression==0) ? "(inflate/deflate)" : "";
			int colorType = getIntBE(dataStart+9,1);
			String colorTypeString = (colorType < colorTypes.length) ? colorTypes[colorType] : "";
			int interlace = getIntBE(dataStart+12,1);
			String interlaceString = (interlace==0) ? "(no interlace)" :
										(interlace==1) ? "(Adam 7)" : "";
			sb.append("   Width:        "+getIntBE(dataStart,4)+"\n");
			sb.append("   Height:       "+getIntBE(dataStart+4,4)+"\n");
			sb.append("   Bit depth:    "+getIntBE(dataStart+8,1)+"\n");
			sb.append("   Color type:   "+colorType+" "+colorTypeString+"\n");
			sb.append("   Compression:  "+compression+" "+compressionString+"\n");
			sb.append("   Filter:       "+getIntBE(dataStart+11,1)+"\n");
			sb.append("   Interlace:    "+interlace+" "+interlaceString+"\n");
		}
		private void listSRGBParams(StringBuffer sb) {
			int intent = getIntBE(dataStart,1);
			String s = (intent==0) ? "(perceptual)" :
							(intent==1) ? "(relative colorimetric)" :
								(intent==2) ? "(saturation)" :
									(intent==3) ? "(absolute colorimetric)" : "";
			sb.append("   Intent:       "+intent+" "+s+"\n");
		}
		private void listGAMAParams(StringBuffer sb) {
			int gamma = getIntBE(dataStart,4);
			sb.append("   Gamma:        "+gamma+"\n");
		}
		private void listPHYSParams(StringBuffer sb) {
			int xppu = getIntBE(dataStart,4);
			int yppu = getIntBE(dataStart+4,4);
			int unit = getIntBE(dataStart+8,1);
			String s = (unit==1) ? "(meter)" : "(unknown)";
			sb.append("   Pixels/unitX: "+xppu+"\n");
			sb.append("   Pixels/unitY: "+yppu+"\n");
			sb.append("   Unit:         "+unit+" "+s+"\n");
		}
	}

	private byte getByte(int adrs) {
		byte b;
		try {
			in.seek(adrs);
			b = (byte)in.read();
		}
		catch (Exception ex) { b = 0; }
		return b;
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
			bx = bx & (byte)0xFF;
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
			int bx = b[k] & 0xFF;
			x = (x<<8) | bx;
		}
		return x;
	}

}
