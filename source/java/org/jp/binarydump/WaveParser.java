package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import javax.swing.*;

public class WaveParser extends Parser {

	Chunk headerChunk;
	FmtChunk fmtChunk;
	DataChunk dataChunk;
	JFrame listFrame = null;

	public WaveParser(BinaryDump parent, RandomAccessFile in) {
		super(parent, in);
		type = (checkForString(0x0, "RIFF") && checkForString(0x8, "WAVE"));
		if (type) {
			headerChunk = new Chunk(0x0);
			fmtChunk = new FmtChunk(headerChunk.dataEnd);
			dataChunk = new DataChunk(fmtChunk.dataEnd);
		}
	}

	public String getContentType() {
		return "RIFF: WAVE";
	}

	public Color getColorFor(int adrs) {
		if (adrs < headerChunk.dataEnd)
			return headerChunk.getColorFor(adrs);
		if (adrs < fmtChunk.dataEnd)
			return fmtChunk.getColorFor(adrs);
		if (adrs < dataChunk.dataEnd)
			return dataChunk.getColorFor(adrs);
		return Color.black;
	}

	public JMenu getMenu() {
		JMenu menu = new JMenu("WAVE");
		JMenuItem listItem = new JMenuItem("List Parameters");
		listItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				listParams();
			}
		});
		listItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,InputEvent.CTRL_MASK));
		menu.add(listItem);
		JMenuItem resetSampleRateItem = new JMenuItem("Reset Sample Rate");
		resetSampleRateItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				resetSampleRate();
			}
		});
		resetSampleRateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,InputEvent.CTRL_MASK));
		menu.add(resetSampleRateItem);
		return menu;
	}

	private void listParams() {
		if (listFrame != null) {
			listFrame.setVisible(false);
			listFrame.dispose();
		}
		listFrame = new JFrame("WAVE Parameters");
		JScrollPane jsp = new JScrollPane();
		listFrame.getContentPane().add(jsp,BorderLayout.CENTER);
		JTextPane jtp = new JTextPane();
		jtp.setContentType("text/html");
		jsp.setViewportView(jtp);
		jtp.setText(fmtChunk.toString());
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

	private void resetSampleRate() {
		String sampleRateString =
				JOptionPane.showInputDialog(
					parent,
					"Enter a new sample rate.",
					Integer.toString(fmtChunk.sampleRate)
				);
		if (sampleRateString != null) {
			try {
				int sampleRate = Integer.parseInt(sampleRateString);
				fmtChunk.resetSampleRate(sampleRate);
			}
			catch (Exception doNothing) { }
		}
		listParams();
		parent.redisplay();
	}

	class Chunk {
		public int address;
		public String name;
		public int length;
		public int dataStart;
		public int dataEnd;
		public Chunk(int address) {
			this.address = address;
			byte[] b = getBytes(address, 4);
			name = new String(b);
			length = getLength();
			dataStart = address + 8;
			dataEnd = address + 8 + length;
		}
		private int getLength() {
			return 4;
		}
		public Color getColorFor(int adrs) {
			if (adrs < 4) return Color.red;
			if (adrs < 8) return Color.gray;
			return Color.blue;
		}
	}

	class FmtChunk extends Chunk {
		int audioFormat;
		int numChannels;
		int sampleRate;
		int byteRate;
		int blockAlign;
		int bitsPerSample;
		public FmtChunk(int address) {
			super(address);
			length = getLength();
			dataStart = address + 8;
			dataEnd = address + 8 + length;
			audioFormat		= getInt(dataStart    , 2);
			numChannels		= getInt(dataStart +  2, 2);
			sampleRate		= getInt(dataStart +  4, 4);
			byteRate		= getInt(dataStart +  8, 4);
			blockAlign		= getInt(dataStart + 12, 2);
			bitsPerSample	= getInt(dataStart + 14, 2);
		}
		private int getLength() {
			byte[] b = getBytes(address+4, 4);
			return getIntFromBytes(b);
		}
		public void resetSampleRate(int sampleRate) {
			this.sampleRate = sampleRate;
			byteRate = sampleRate * numChannels * (bitsPerSample/8);
			putInt(sampleRate, dataStart + 4, 4);
			putInt(byteRate, dataStart + 8, 4);
		}
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("<pre>");
			sb.append("Audio Format:       "+audioFormat);
			if (audioFormat == 1) sb.append(" [PCM]");
			sb.append("\n");
			sb.append("Number of Channels: "+numChannels+"\n");
			sb.append("Sample Rate:        "+sampleRate+"\n");
			sb.append("Byte Rate:          "+byteRate+"\n");
			sb.append("Block Align:        "+blockAlign+"\n");
			sb.append("Bits per Sample:    "+bitsPerSample+"\n");
			sb.append("</pre>");
			return sb.toString();
		}
		public Color getColorFor(int adrs) {
			if (adrs < address +  4) return Color.red;	//name
			if (adrs < address +  8) return Color.gray;	//length
			if (adrs < dataStart +  2) return Color.blue;	//audioFormat
			if (adrs < dataStart +  4) return Color.green;	//numChannels
			if (adrs < dataStart +  8) return Color.blue;	//sampleRate
			if (adrs < dataStart + 12) return Color.green;	//byteRate
			if (adrs < dataStart + 14) return Color.blue;	//blockAlign
			if (adrs < dataStart + 16) return Color.green;	//bitsPerSample
			return Color.black;
		}
	}

	class DataChunk extends Chunk {
		public DataChunk(int address) {
			super(address);
			length = getLength();
			dataStart = address + 8;
			dataEnd = address + 8 + length;
		}
		private int getLength() {
			byte[] b = getBytes(address+4, 4);
			return getIntFromBytes(b);
		}
		public Color getColorFor(int adrs) {
			if (adrs <  address + 4) return Color.red;	//name
			if (adrs <  address + 8) return Color.gray;	//length
			return Color.black;
		}
	}

	private boolean checkForString(int adrs, String string) {
		try {
			byte[] b = new byte[4];
			in.seek(adrs);
			in.read(b);
			String name = new String(b);
			return name.equals(string);
		}
		catch (Exception ex) { return false; }
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
