package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.regex.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.text.*;

public class PNGParser extends Parser implements MouseListener {

	LinkedList<Chunk> chunks;
	AttachedFrame listFrame = null;
	ScrolledEditorPanel editorPanel = null;
	JEditorPane editor = null;
	
	static final Charset latin1 = Charset.forName("ISO-8859-1");
	static final Charset utf8 = Charset.forName("UTF-8");
	static byte[] header = { (byte)0x89, (byte)0x50, (byte)0x4e, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A };

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
		listItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,InputEvent.CTRL_DOWN_MASK));
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
		editor.setCaretPosition(0);
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
			sb.append(String.format("%08X/ type: %4s length: %04X [%d]\n", address, type, length, length));
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
			else if (typeLC.equals("chrm")) listCHRMParams(sb);
			else if (typeLC.equals("bkgd")) listBKGDParams(sb);
			else if (typeLC.equals("splt")) listSPLTParams(sb);
			else if (typeLC.equals("time")) listTIMEParams(sb);
			else if (typeLC.equals("text")) listTEXTParams(sb);
			else if (typeLC.equals("itxt")) listITXTParams(sb);
			else if (typeLC.equals("ztxt")) listZTXTParams(sb);
			return sb.toString();
		}
		String[] colorTypes = {"(grayscale)", "", "(RGB)", "(Palette index)", "(Grayscale with alpha)", "", "(RGB with alpha)"};
		private void listIHDRParams(StringBuffer sb) {
			int compression = getIntBE(dataStart+10,1);
			String compressionString = (compression==0) ? "(inflate/deflate)" : "";
			int colorType = getIntBE(dataStart+9,1);
			String colorTypeString = (colorType < colorTypes.length) ? colorTypes[colorType] : "";
			int interlace = getIntBE(dataStart+12,1);
			String interlaceString = (interlace==0) ? "(no interlace)" : (interlace==1) ? "(Adam 7)" : "";
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
		private void listCHRMParams(StringBuffer sb) {
			sb.append("   White X:       "+getIntBE(dataStart,4)+"\n");
			sb.append("   White Y:       "+getIntBE(dataStart+4,4)+"\n");
			sb.append("   Red X:         "+getIntBE(dataStart+8,4)+"\n");
			sb.append("   Red Y:         "+getIntBE(dataStart+12,4)+"\n");
			sb.append("   Green X:       "+getIntBE(dataStart+16,4)+"\n");
			sb.append("   Green Y:       "+getIntBE(dataStart+20,4)+"\n");
			sb.append("   Blue X:        "+getIntBE(dataStart+24,4)+"\n");
			sb.append("   Blue Y:        "+getIntBE(dataStart+28,4)+"\n");
		}
		private void listBKGDParams(StringBuffer sb) {
			sb.append("   Red:           "+getIntBE(dataStart,2)+"\n");
			sb.append("   Green:         "+getIntBE(dataStart+2,2)+"\n");
			sb.append("   Blue:          "+getIntBE(dataStart+4,2)+"\n");
		}
		private void listTIMEParams(StringBuffer sb) {
			int year = getIntBE(dataStart,2);
			int month = getIntBE(dataStart+2,1);
			int day = getIntBE(dataStart+3,1);
			int hour = getIntBE(dataStart+4,1);
			int minute = getIntBE(dataStart+5,1);
			int second = getIntBE(dataStart+6,1);
			sb.append(String.format("   Time:          %4d/%02d/%02d %02d:%02d:%02d\n", year, month, day, hour, minute, second));
		}
		private void listSPLTParams(StringBuffer sb) {
			byte[] b = getBytes(dataStart, length);
			int k = 0;
			while (b[k] != 0) k++;
			String name = new String(b, 0, k, latin1);
			int depth = 0xFF & (int)b[k+1];
			sb.append("   " + name + "\n");
			sb.append("   Depth:         " + depth + "\n");			
		}
		private void listTEXTParams(StringBuffer sb) {
			byte[] b = getBytes(dataStart, length);
			int k = 0;
			while (b[k] != 0) k++;
			String keyword = new String(b, 0, k, latin1);
			String value = new String(b, k+1, b.length-(k+1), latin1);
			sb.append(String.format("   %s: %s\n", keyword, value));			
		}
		private void listZTXTParams(StringBuffer sb) {
			byte[] b = getBytes(dataStart, length);
			int k = 0;
			while ((k < b.length) && (b[k] != 0)) k++;
			String keyword = new String(b, 0, k, utf8);
			sb.append(String.format("   %s\n", keyword));
			int cMethod = 0xFF & (int)b[++k];
			String cMethodMeaning = (cMethod==0) ? "(inflate/deflate)" : "(undefined)";
			sb.append("   Method:        " + cMethod + " " + cMethodMeaning + "\n");
			String text = "";
			if (cMethod == 0) text = inflate(b, ++k, b.length, latin1);
			sb.append("   " + text + "\n");
		}
		private void listITXTParams(StringBuffer sb) {
			byte[] b = getBytes(dataStart, length);
			int k = 0;
			while ((k < b.length) && (b[k] != 0)) k++;
			String keyword = new String(b, 0, k, utf8);
			sb.append(String.format("   %s\n", keyword));
			if (k < b.length - 2) {
				int cFlag = 0xFF & (int)b[++k];
				int cMethod = 0xFF & (int)b[++k];
				String cFlagMeaning = (cFlag==0) ? "(uncompressed)" : (cFlag==1) ? "(compresed)" : "(undefined)";
				String cMethodMeaning = (cMethod==0) ? "(inflate/deflate)" : "(undefined)";
				sb.append("   Compression:   " + cFlag + " " + cFlagMeaning + "\n");
				sb.append("   Method:        " + cMethod + " " + cMethodMeaning + "\n");
				k++;
				int kStart = k;
				while ((k < b.length) && (b[k] != 0)) k++;
				if (k < b.length) {
					String language = new String(b, kStart, k, utf8);
					sb.append("   Language:      " + language + "\n");
					k++;
					kStart = k;
					while ((k < b.length) && (b[k] != 0)) k++;
					if (k < b.length) {
						String xKeyword = new String(b, kStart, k, utf8);
						sb.append("   Translated kwd:"+ xKeyword + "\n");
						k++;
						if (k < b.length) {
							String text = "";
							if (cFlag == 0) text = new String(b, k, b.length, utf8);
							else if (cMethod == 0) text = inflate(b, k, b.length, utf8);
							sb.append("   " + text + "\n");
						}
					}
				}
			}
		}
	}
	
	private String inflate(byte[] b, int k1, int k2, Charset cs) {
		String text = "";
		try {
			Inflater inflater = new Inflater();
			inflater.setInput(b, k1, k2);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();  
			byte[] buffer = new byte[1024];  
			while (!inflater.finished()) {  
			int count = inflater.inflate(buffer);  
				outputStream.write(buffer, 0, count);  
			}  
			outputStream.close();
			byte[] textBytes = outputStream.toByteArray();
			text = new String(textBytes, cs);
		}
		catch (Exception ignore) { }
		return text;
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
