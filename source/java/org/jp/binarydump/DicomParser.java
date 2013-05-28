package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import javax.swing.*;

public class DicomParser extends Parser {

	int offset;
	ColorField[] fields;
	int[] addresses;
	DicomElement pixels = null;
	DicomXfrSyntax dxs = null;
	DicomSopClass dsc = null;
	int group2End = Integer.MAX_VALUE;
	int lastIndex = 0;
	LinkedList<DicomElement> elementList;

	public DicomParser(BinaryDump parent, RandomAccessFile in) {
		super(parent, in);
		getParams();
		if (type) parse();
	}

	public String getContentType() {
		if (dxs == null) return "Content-Type: DICOM [No TS, IVRLE assumed]";
		else return "Content-Type: DICOM " + dxs.name;
	}

	public Color getColorFor(int adrs) {
		if (lastIndex < addresses.length - 1) {
			if ((adrs >= addresses[lastIndex]) &&
				(adrs < addresses[lastIndex+1])) {
				return fields[lastIndex].color;
			}
			else if (adrs == addresses[lastIndex+1]) {
				lastIndex++;
				return fields[lastIndex].color;
			}
		}
		int index = Arrays.binarySearch(addresses,adrs);
		if (index < addresses.length) {
			if (index >= 0) lastIndex = index;
			else lastIndex = -index - 2;
			return fields[lastIndex].color;
		}
		return Color.black;
	}

	public JMenu getMenu() {
		JMenu menu = new JMenu("DICOM");
		JMenuItem listItem = new JMenuItem("List Elements");
		listItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				listElements();
			}
		});
		listItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_MASK));
		menu.add(listItem);
		if (pixels != null) {
			JMenuItem saveItem = new JMenuItem("Save Pixels");
			saveItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					savePixels();
				}
			});
			saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK));
			menu.add(saveItem);

			JMenuItem truncateItem = new JMenuItem("Truncate after Pixels");
			truncateItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					truncate();
				}
			});
			truncateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK));
			menu.add(truncateItem);
		}
		return menu;
	}

	private void listElements() {
		JFrame listFrame = new JFrame(parent.getFile().getName());
		JScrollPane jsp = new JScrollPane();
		listFrame.getContentPane().add(jsp,BorderLayout.CENTER);
		JTextPane jtp = new JTextPane();
		jtp.setContentType("text/html");
		jsp.setViewportView(jtp);
		ListIterator<DicomElement> it = elementList.listIterator(0);
		StringBuffer sb = new StringBuffer();
		sb.append("<pre>");
		if (dsc != null) sb.append(dsc.name + "\n\n");
		while (it.hasNext()) sb.append(it.next().toString());
		sb.append("</pre>");
		jtp.setText(sb.toString());
		jtp.setCaretPosition(0);

		//Now, size and position the JFrame and display it.
		//The height is set to the height of the parent, and
		//the location is set just to the right of the parent.
		int width = 450;
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

	private void savePixels() {
		File outputFile = null;
		if (pixels != null) {
			JFileChooser chooser = new JFileChooser(parent.dataFile.getParentFile());
			chooser.setSelectedFile(new File(parent.dataFile.getAbsolutePath() + ".pixels"));
			if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
				outputFile = chooser.getSelectedFile();
				BufferedOutputStream bos = null;
				try {
					bos = new BufferedOutputStream(new FileOutputStream(outputFile));
					if (pixels.lenValue > 0) write(bos, pixels.valueAdrs, pixels.lenValue);
					else if (pixels.lenValue == 0xffffffff) {
						boolean firstItem = true;
						DicomElement el;
						int adrs = pixels.tagAdrs + pixels.length;
						while ((el = nextElement(adrs)) != null) {
							if (el.tag == 0xfffee000) {
								if (!firstItem) write(bos, el.valueAdrs, el.lenValue);
								adrs += el.length;
								firstItem = false;
							}
							else break;
						}
					}
				}
				catch (Exception ex) { }
				finally {
					if (bos != null) {
						try { bos.flush(); }
						catch (Exception ignore) { }
						try { bos.close(); }
						catch (Exception ignore) { }
					}
				}
			}
		}
	}

	private void truncate() {
		DicomElement stopElement = nextElementAfter(pixels);
		File outputFile = null;
		if (stopElement != null) {
			JFileChooser chooser = new JFileChooser(parent.dataFile.getParentFile());
			chooser.setSelectedFile(new File(parent.dataFile.getAbsolutePath() + ".truncated.dcm"));
			if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
				outputFile = chooser.getSelectedFile();
				BufferedOutputStream bos = null;
				try {
					bos = new BufferedOutputStream(new FileOutputStream(outputFile));
					write(bos, 0, stopElement.tagAdrs);
				}
				catch (Exception ex) { }
				finally {
					if (bos != null) {
						try { bos.flush(); }
						catch (Exception ignore) { }
						try { bos.close(); }
						catch (Exception ignore) { }
					}
				}
			}
		}
	}

	private void write(BufferedOutputStream bos, int adrs, int length) throws Exception {
		int n;
		in.seek(adrs);
		byte[] b = new byte[Math.min(length, 4096)];
		while ((length > 0) && ((n=in.read(b, 0, Math.min(b.length, length))) > 0)) {
			bos.write(b, 0, n);
			length -= n;
		}
	}

	private void getParams() {
		type = true;
		if (checkForDICM(0x80)) offset = 0x80 + 4;
		else if (checkForDICM(0)) offset = 4;
		else if (checkBeginning()) offset = 0;
		else type = false;
	}

	private boolean checkForDICM(int adrs) {
		try {
			byte[] b = new byte[4];
			in.seek(adrs);
			in.read(b);
			String dicm = new String(b);
			return dicm.equals("DICM");
		}
		catch (Exception ex) { return false; }
	}

	private boolean checkBeginning() {
		try {
			byte[] b = new byte[2];
			byte[] sig2 = new byte[] { 0x02, 0x00 };
			byte[] sig8 = new byte[] { 0x08, 0x00 };
			in.seek(0);
			in.read(b);
			return ((b[0] == sig2[0]) && (b[1] == sig2[1])) ||
				   ((b[0] == sig8[0]) && (b[1] == sig8[1]));
		}
		catch (Exception ex) { return false; }
	}

	private void parse() {
		elementList = new LinkedList<DicomElement>();
		LinkedList<ColorField> list = new LinkedList<ColorField>();
		//Set up for the preamble and the identifier
		if (offset > 4) list.add(new ColorField(Color.black,0));
		if (offset > 0) list.add(new ColorField(Color.gray,offset-4));
		//Now do all the elements
		int adrs = offset;
		DicomElement el;
		int count = 0;
		while ((el = nextElement(adrs)) != null) {
			elementList.add(el);
			if (el.tag == 0x00020000) group2End = el.tagAdrs + el.length + el.getIntValue();
			if (el.tag == 0x00020010) dxs = DicomXfrSyntax.getSyntax(el.getStringValue());
			if ((el.tag >= 0x00030000) && (group2End == Integer.MAX_VALUE)) group2End = el.tagAdrs;
			if (el.tag == 0x00080016) dsc = DicomSopClass.getSopClass(el.getStringValue());
			if (el.tag == 0x7fe00010) pixels = el;
			list.add(new ColorField(Color.red,el.tagAdrs));
			if (el.vrLen > 0) list.add(new ColorField(Color.orange,el.vrAdrs));
			list.add(new ColorField(Color.blue,el.lenAdrs));
			if (el.lenValue > 0) list.add(new ColorField(Color.black,el.lenAdrs+el.lenLen));
			adrs += el.length;
		}
		int len = list.size();
		fields = new ColorField[len];
		addresses = new int[len];
		ListIterator<ColorField> it = list.listIterator(0);
		for (int i=0; i<len; i++) {
			fields[i] = it.next();
			addresses[i] = fields[i].address;
		}
	}

	private DicomElement nextElement(int adrs) {
		DicomElement el;
		try { el = new DicomElement(in, adrs, dxs, group2End); }
		catch (Exception ex) { return null; }
		if (el.length <= 0) return null;
		return el;
	}

	private DicomElement nextElementAfter(DicomElement target) {
		ListIterator<DicomElement> lit = elementList.listIterator();
		while (lit.hasNext()) {
			DicomElement current = lit.next();
			if (current.tag == target.tag) {
				DicomElement next = null;
				while (lit.hasNext()) {
					next = lit.next();
					if ((next.tag & 0xffff0000) != 0xfffe0000) break;
					next = null;
				}
				if (next != null) {
					return next;
				}
				else {
					return null;
				}
			}
		}
		return null;
	}

	class ColorField {
		Color color;
		int address;
		public ColorField(Color color, int address) {
			this.color = color;
			this.address = address;
		}
	}

}
